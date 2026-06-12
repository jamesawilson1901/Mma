"""Stream Glicko-2 over the full bout history in chronological order.

Each bout is treated as a one-game rating period for both fighters. Before a
fighter's bout is scored, their RD is inflated for the time elapsed since their
previous bout (did_not_compete) so layoffs widen uncertainty -- the whole
reason for picking Glicko-2 over Elo.

Writes a post-bout snapshot per fighter to `ratings_history`. The latest
snapshot per fighter is that fighter's current rating; the full history powers
as-of-date feature lookups in the backtest (no leakage).

Also writes a pre-fight win probability for every rated bout to `predictions`
(model='tier1_glicko'). Because the probability is computed from the ratings as
they stand BEFORE the bout is scored, inside a single chronological pass, the
prediction stream is walk-forward by construction (verified by
tests/test_leakage.py).
"""
from __future__ import annotations

import sqlite3
from datetime import date

from ..config import RATING_PERIOD_DAYS
from .glicko2 import Glicko2, Rating


def _to_date(s: str | None) -> date | None:
    if not s:
        return None
    try:
        return date.fromisoformat(s)
    except ValueError:
        return None


def run(conn: sqlite3.Connection, engine: Glicko2 | None = None) -> dict:
    """Compute ratings over every bout (skipping no-contests) and persist them.

    Draws score 0.5/0.5; wins 1/0. No-contests are skipped (no rating signal).
    Returns a summary dict.
    """
    engine = engine or Glicko2()
    conn.execute("DELETE FROM ratings_history")
    conn.execute("DELETE FROM prefight_ratings")
    conn.execute("DELETE FROM predictions WHERE model='tier1_glicko'")

    bouts = conn.execute(
        "SELECT bout_id, date, fighter1_id, fighter2_id, winner_id, result "
        "FROM bouts WHERE fighter1_id IS NOT NULL AND fighter2_id IS NOT NULL "
        "ORDER BY date ASC, bout_id ASC"
    ).fetchall()

    ratings: dict[str, Rating] = {}
    last_date: dict[str, date] = {}
    n_bouts: dict[str, int] = {}
    snapshots = []
    preds = []
    prefight = []
    n_rated = n_skipped = 0

    for b in bouts:
        # Only completed, decisive-or-draw bouts carry a rating signal. This
        # also skips upcoming/scheduled bouts (result NULL, no winner) so the
        # dashboard can stage future cards in the same table without polluting
        # ratings.
        if b["result"] not in ("win", "draw"):
            n_skipped += 1
            continue
        if b["result"] == "win" and b["winner_id"] is None:
            n_skipped += 1
            continue
        bdate = _to_date(b["date"])
        f1, f2 = b["fighter1_id"], b["fighter2_id"]

        r1 = ratings.get(f1) or engine.create_rating()
        r2 = ratings.get(f2) or engine.create_rating()

        # Inflate RD for inactivity since each fighter's previous bout.
        if bdate is not None:
            for fid, r in ((f1, r1), (f2, r2)):
                prev = last_date.get(fid)
                if prev is not None and bdate > prev:
                    periods = (bdate - prev).days / RATING_PERIOD_DAYS
                    r = engine.did_not_compete(r, periods)
                    if fid == f1:
                        r1 = r
                    else:
                        r2 = r

        # Pre-fight state + prediction from the (layoff-inflated) PRE-bout ratings.
        prefight.append((b["bout_id"], f1, r1.rating, r1.rd, r1.vol))
        prefight.append((b["bout_id"], f2, r2.rating, r2.rd, r2.vol))
        preds.append((
            b["bout_id"], "tier1_glicko", engine.expected_score(r1, r2),
            n_bouts.get(f1, 0), n_bouts.get(f2, 0),
        ))

        if b["result"] == "draw":
            s1 = s2 = 0.5
        else:  # win
            s1 = 1.0 if b["winner_id"] == f1 else 0.0
            s2 = 1.0 - s1

        # Update both off their PRE-bout ratings (simultaneous, no leakage).
        new1 = engine.rate_period(r1, [(r2, s1)])
        new2 = engine.rate_period(r2, [(r1, s2)])
        ratings[f1], ratings[f2] = new1, new2
        n_bouts[f1] = n_bouts.get(f1, 0) + 1
        n_bouts[f2] = n_bouts.get(f2, 0) + 1
        if bdate is not None:
            last_date[f1] = last_date[f2] = bdate

        iso = b["date"]
        snapshots.append((f1, b["bout_id"], iso, new1.rating, new1.rd, new1.vol))
        snapshots.append((f2, b["bout_id"], iso, new2.rating, new2.rd, new2.vol))
        n_rated += 1

    conn.executemany(
        "INSERT OR REPLACE INTO ratings_history"
        "(fighter_id,bout_id,date,rating,rd,vol) VALUES(?,?,?,?,?,?)",
        snapshots,
    )
    conn.executemany(
        "INSERT OR REPLACE INTO predictions"
        "(bout_id,model,p_fighter1,n_prior1,n_prior2) VALUES(?,?,?,?,?)",
        preds,
    )
    conn.executemany(
        "INSERT OR REPLACE INTO prefight_ratings"
        "(bout_id,fighter_id,rating,rd,vol) VALUES(?,?,?,?,?)",
        prefight,
    )
    conn.commit()
    return {
        "bouts_rated": n_rated,
        "bouts_skipped_nc": n_skipped,
        "fighters_rated": len(ratings),
        "snapshots": len(snapshots),
        "predictions": len(preds),
    }


def current_ratings(conn: sqlite3.Connection, limit: int = 25, min_rd: float = 110.0):
    """Latest rating per fighter, ordered by rating. min_rd filters out
    fighters whose rating is still too uncertain (few/old bouts)."""
    return conn.execute(
        """
        WITH latest AS (
            SELECT fighter_id, MAX(date) AS d FROM ratings_history GROUP BY fighter_id
        )
        SELECT f.name, rh.rating, rh.rd, rh.vol, rh.date
        FROM ratings_history rh
        JOIN latest l ON l.fighter_id = rh.fighter_id AND l.d = rh.date
        JOIN fighters f ON f.fighter_id = rh.fighter_id
        WHERE rh.rd <= ?
        GROUP BY rh.fighter_id
        ORDER BY rh.rating DESC
        LIMIT ?
        """,
        (min_rd, limit),
    ).fetchall()
