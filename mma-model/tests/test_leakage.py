"""Leakage test: predictions must depend only on bouts strictly before them.

We build a small fixture DB, run the rating replay, and assert that the
prediction for a bout at date T is bit-for-bit identical when every bout after
T is deleted. If future results influenced past predictions in any way, this
would fail.
"""
import sqlite3

import pytest

from mma_model.config import SCHEMA_PATH
from mma_model.ratings import rate_fighters


FIGHTERS = ["fa", "fb", "fc", "fd"]
# (bout_id, date, f1, f2, winner)
BOUTS = [
    ("b1", "2020-01-01", "fa", "fb", "fa"),
    ("b2", "2020-03-01", "fc", "fd", "fd"),
    ("b3", "2020-06-01", "fa", "fc", "fa"),
    ("b4", "2020-09-01", "fb", "fd", "fd"),
    ("b5", "2021-01-01", "fa", "fd", "fa"),   # <- prediction under test
    ("b6", "2021-06-01", "fa", "fd", "fd"),   # future: must not affect b5
    ("b7", "2021-09-01", "fb", "fc", "fb"),
]


def make_db(bouts) -> sqlite3.Connection:
    conn = sqlite3.connect(":memory:")
    conn.row_factory = sqlite3.Row
    conn.executescript(SCHEMA_PATH.read_text())
    conn.executemany(
        "INSERT INTO fighters(fighter_id, name) VALUES(?,?)",
        [(f, f.upper()) for f in FIGHTERS],
    )
    conn.executemany(
        "INSERT INTO bouts(bout_id, date, fighter1_id, fighter2_id, winner_id, result)"
        " VALUES(?,?,?,?,?, 'win')",
        bouts,
    )
    conn.commit()
    return conn


def pred(conn, bout_id):
    row = conn.execute(
        "SELECT p_fighter1 FROM predictions WHERE bout_id=? AND model='tier1_glicko'",
        (bout_id,),
    ).fetchone()
    return row[0] if row else None


def test_prediction_unchanged_when_future_removed():
    full = make_db(BOUTS)
    rate_fighters.run(full)
    p_full = pred(full, "b5")
    assert p_full is not None

    truncated = make_db([b for b in BOUTS if b[1] <= "2021-01-01"])
    rate_fighters.run(truncated)
    assert pred(truncated, "b5") == pytest.approx(p_full, abs=1e-12)


def test_prediction_reflects_past_results():
    conn = make_db(BOUTS)
    rate_fighters.run(conn)
    # fa is 2-0 entering b5; fd is 2-0 as well -- but fa beat fc who lost to fd:
    # just assert the prediction is a proper probability not stuck at the prior.
    p = pred(conn, "b5")
    assert 0.0 < p < 1.0
    # debut bout between two unrated fighters must sit at the prior (0.5)
    assert pred(conn, "b1") == pytest.approx(0.5)
    # and ratings history exists for both fighters of every rated bout
    n = conn.execute("SELECT COUNT(*) FROM ratings_history").fetchone()[0]
    assert n == 2 * len(BOUTS)
