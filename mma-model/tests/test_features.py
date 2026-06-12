"""Tier 2 feature builder: accumulator correctness, leakage, symmetry."""
import sqlite3

import numpy as np
import pytest

from mma_model.config import SCHEMA_PATH
from mma_model.models.features import (
    Career,
    _fight_seconds,
    _method_class,
    build_features,
    flip_orientation,
    FEATURE_COLS,
)
from mma_model.ratings import rate_fighters


# --- unit: career accumulator ----------------------------------------------

def test_career_rates():
    c = Career()
    c.update(1.0, "KO/TKO", None,
             {"seconds": 300, "sig_landed": 50, "td_landed": 2, "td_att": 4,
              "sub_att": 1, "kd": 1},
             {"seconds": 300, "sig_landed": 20, "td_landed": 0, "td_att": 3,
              "sub_att": 0, "kd": 0})
    assert c.n_bouts == 1 and c.wins == 1 and c.wins_ko == 1
    assert c.slpm == pytest.approx(10.0)        # 50 landed / 5 min
    assert c.sapm == pytest.approx(4.0)         # 20 absorbed / 5 min
    assert c.td_acc == pytest.approx(0.5)
    assert c.td_def == pytest.approx(1.0)       # 0 of 3 faced takedowns landed
    assert c.sub_per15 == pytest.approx(3.0)    # 1 per 5min -> 3 per 15
    assert c.win_streak == 1


def test_career_streak_and_form():
    c = Career()
    for won in (1.0, 1.0, 0.0):
        c.update(won, "Decision - Unanimous", None, None, None)
    assert c.win_streak == -1
    # last3 = [0,1,1] weighted 3/2/1 -> (0*3+1*2+1*1)/6
    assert c.form3() == pytest.approx(0.5)
    assert c.win_rate == pytest.approx(2 / 3)


def test_method_class_and_fight_seconds():
    assert _method_class("KO/TKO Punches") == "ko"
    assert _method_class("TKO - Doctor's Stoppage") == "ko"
    assert _method_class("Submission") == "sub"
    assert _method_class("Decision - Split") == "dec"
    assert _fight_seconds(3, "4:32") == 2 * 300 + 272
    assert _fight_seconds(1, "0:30") == 30


# --- integration: fixture DB ------------------------------------------------

FIGHTERS = ["fa", "fb", "fc", "fd"]
BOUTS = [
    ("b1", "2020-01-01", "fa", "fb", "fa"),
    ("b2", "2020-03-01", "fc", "fd", "fd"),
    ("b3", "2020-06-01", "fa", "fc", "fa"),
    ("b4", "2020-09-01", "fb", "fd", "fd"),
    ("b5", "2021-01-01", "fa", "fd", "fa"),
    ("b6", "2021-06-01", "fa", "fd", "fd"),
]


def make_db(bouts) -> sqlite3.Connection:
    conn = sqlite3.connect(":memory:")
    conn.row_factory = sqlite3.Row
    conn.executescript(SCHEMA_PATH.read_text())
    conn.executemany(
        "INSERT INTO fighters(fighter_id, name, height_cm, reach_cm, dob)"
        " VALUES(?,?,180,185,'1990-01-01')",
        [(f, f.upper()) for f in FIGHTERS],
    )
    conn.executemany(
        "INSERT INTO bouts(bout_id, date, fighter1_id, fighter2_id, winner_id,"
        " result, method, round, time, scheduled_rounds)"
        " VALUES(?,?,?,?,?, 'win', 'Decision - Unanimous', 3, '5:00', 3)",
        bouts,
    )
    # simple per-bout stats: fighter1 outlands fighter2
    stats = []
    for bid, _, f1, f2, _w in bouts:
        stats.append((bid, f1, 1, 40, 80, 2, 4, 1, 0))
        stats.append((bid, f2, 1, 25, 70, 0, 2, 0, 0))
    conn.executemany(
        "INSERT INTO bout_stats(bout_id, fighter_id, round, sig_str_landed,"
        " sig_str_att, td_landed, td_att, sub_att, kd)"
        " VALUES(?,?,?,?,?,?,?,?,?)",
        stats,
    )
    conn.commit()
    rate_fighters.run(conn)  # populates prefight_ratings
    return conn


def test_eligibility_requires_stat_history_for_both():
    conn = make_db(BOUTS)
    df = build_features(conn)
    # b1/b2 are debuts (no prior stats) -> no rows; b3 onward eligible
    assert set(df["bout_id"]) == {"b3", "b4", "b5", "b6"}


def test_feature_values_as_of_date():
    conn = make_db(BOUTS)
    df = build_features(conn).set_index("bout_id")
    b3 = df.loc["b3"]
    # entering b3: fa won b1 (1-0), fc lost b2 (0-1)
    assert b3["win_rate_diff"] == pytest.approx(1.0)
    assert b3["streak_diff"] == 2          # +1 vs -1
    assert b3["n_prior_diff"] == 0 and b3["n_prior_min"] == 1
    # both landed 40 over 15:00 in their debuts (each was fighter1 once)
    assert b3["slpm_diff"] == pytest.approx(0.0, abs=1e-9)
    # fa's rating rose, fc's fell
    assert b3["rating_diff"] > 0
    assert b3["y"] == 1.0
    # entering b5: fa landed 40+40 over 30min, fd landed 25+25 over 30min
    b5 = df.loc["b5"]
    assert b5["slpm_diff"] == pytest.approx((80 - 50) / 30, abs=1e-9)
    # and fa absorbed 25+25 while fd absorbed 40+40
    assert b5["sapm_diff"] == pytest.approx((50 - 80) / 30, abs=1e-9)


def test_feature_leakage_future_removal():
    full = make_db(BOUTS)
    df_full = build_features(full).set_index("bout_id")
    truncated = make_db([b for b in BOUTS if b[1] <= "2021-01-01"])
    df_trunc = build_features(truncated).set_index("bout_id")
    for col in FEATURE_COLS:
        a, b = df_full.loc["b5", col], df_trunc.loc["b5", col]
        if a is None or (isinstance(a, float) and np.isnan(a)):
            assert b is None or np.isnan(b)
        else:
            assert a == pytest.approx(b, abs=1e-12), col


def test_flip_orientation_roundtrip():
    conn = make_db(BOUTS)
    df = build_features(conn)
    flipped = flip_orientation(df)
    twice = flip_orientation(flipped)
    for col in FEATURE_COLS + ["y"]:
        np.testing.assert_allclose(
            twice[col].astype(float), df[col].astype(float), equal_nan=True)
    # antisymmetric column actually flips sign
    np.testing.assert_allclose(flipped["rating_diff"], -df["rating_diff"])
    # rd1/rd2 swap
    np.testing.assert_allclose(flipped["rd1"].astype(float), df["rd2"].astype(float))
