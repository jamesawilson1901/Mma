"""Serve win probabilities for arbitrary (upcoming) bouts from current state.

The backtest replay writes a prediction per *historical* bout. For upcoming
cards there is no replay row, so the dashboard/ledger ask here for a live
probability computed from each fighter's most recent Glicko rating.

v1 serves the Tier 1 (Glicko-2) probability, which is always available for any
fighter with a rating. Tier 2 serving (build a feature row as-of today and run
the trained GBM) is a future enhancement; the function signature returns the
model name so callers and the ledger record which tier produced the number.
"""
from __future__ import annotations

import sqlite3
from dataclasses import dataclass

from ..ratings.glicko2 import Glicko2, Rating


@dataclass
class Prediction:
    p_fighter1: float
    model: str
    r1: Rating
    r2: Rating


def current_rating(conn: sqlite3.Connection, fighter_id: str,
                   engine: Glicko2 | None = None) -> Rating:
    """Latest rating snapshot for a fighter, or the prior if none exists."""
    engine = engine or Glicko2()
    row = conn.execute(
        "SELECT rating, rd, vol FROM ratings_history WHERE fighter_id=? "
        "ORDER BY date DESC, bout_id DESC LIMIT 1",
        (fighter_id,),
    ).fetchone()
    if row is None:
        return engine.create_rating()
    return Rating(row["rating"], row["rd"], row["vol"])


def predict_pair(conn: sqlite3.Connection, f1: str, f2: str,
                 engine: Glicko2 | None = None) -> Prediction:
    """P(f1 beats f2) from current Glicko ratings (RD-aware)."""
    engine = engine or Glicko2()
    r1 = current_rating(conn, f1, engine)
    r2 = current_rating(conn, f2, engine)
    return Prediction(engine.expected_score(r1, r2), "tier1_glicko", r1, r2)
