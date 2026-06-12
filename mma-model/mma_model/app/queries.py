"""Read queries backing the dashboard (kept separate so they are unit-testable)."""
from __future__ import annotations

import sqlite3

from ..models.predict import predict_pair

EDGE_THRESHOLD = 0.05


def _latest_odds(conn: sqlite3.Connection, bout_id: str, fighter_id: str):
    return conn.execute(
        "SELECT decimal_odds, book, captured_at, is_closing FROM odds "
        "WHERE bout_id=? AND fighter_id=? ORDER BY is_closing DESC, captured_at DESC "
        "LIMIT 1",
        (bout_id, fighter_id),
    ).fetchone()


def fighter_name(conn: sqlite3.Connection, fighter_id: str) -> str:
    row = conn.execute(
        "SELECT name FROM fighters WHERE fighter_id=?", (fighter_id,)).fetchone()
    return row["name"] if row else fighter_id


def upcoming_bouts(conn: sqlite3.Connection, threshold: float = EDGE_THRESHOLD) -> list[dict]:
    """Scheduled bouts (no result yet) with model prob, current odds, edge flag."""
    bouts = conn.execute(
        "SELECT bout_id, date, promotion, fighter1_id, fighter2_id "
        "FROM bouts WHERE result IS NULL "
        "AND fighter1_id IS NOT NULL AND fighter2_id IS NOT NULL "
        "ORDER BY date ASC"
    ).fetchall()
    out = []
    for b in bouts:
        f1, f2 = b["fighter1_id"], b["fighter2_id"]
        pred = predict_pair(conn, f1, f2)
        sides = []
        for fid, p in ((f1, pred.p_fighter1), (f2, 1.0 - pred.p_fighter1)):
            o = _latest_odds(conn, b["bout_id"], fid)
            odds = o["decimal_odds"] if o else None
            edge = (p * odds - 1.0) if odds else None
            sides.append({
                "fighter_id": fid,
                "name": fighter_name(conn, fid),
                "p_model": round(p, 4),
                "odds": odds,
                "implied": round(1.0 / odds, 4) if odds else None,
                "edge": round(edge, 4) if edge is not None else None,
                "bet": edge is not None and edge > threshold,
            })
        out.append({
            "bout_id": b["bout_id"], "date": b["date"],
            "promotion": b["promotion"], "model": pred.model, "sides": sides,
            "has_edge": any(s["bet"] for s in sides),
        })
    return out


def rating_history(conn: sqlite3.Connection, fighter_id: str) -> list[dict]:
    rows = conn.execute(
        "SELECT date, rating, rd, vol FROM ratings_history "
        "WHERE fighter_id=? ORDER BY date ASC, bout_id ASC",
        (fighter_id,),
    ).fetchall()
    return [dict(r) for r in rows]


def fighter_profile(conn: sqlite3.Connection, fighter_id: str) -> dict:
    f = conn.execute(
        "SELECT fighter_id, name, nickname, height_cm, reach_cm, stance, dob "
        "FROM fighters WHERE fighter_id=?", (fighter_id,)).fetchone()
    hist = rating_history(conn, fighter_id)
    record = conn.execute(
        "SELECT "
        " SUM(CASE WHEN winner_id=? THEN 1 ELSE 0 END) AS wins, "
        " SUM(CASE WHEN result='win' AND winner_id IS NOT NULL AND winner_id<>? "
        "          AND (fighter1_id=? OR fighter2_id=?) THEN 1 ELSE 0 END) AS losses "
        "FROM bouts WHERE (fighter1_id=? OR fighter2_id=?) AND result='win'",
        (fighter_id,) * 6,
    ).fetchone()
    return {
        "fighter": dict(f) if f else None,
        "current": hist[-1] if hist else None,
        "history": hist,
        "record": {"wins": record["wins"] or 0, "losses": record["losses"] or 0},
    }


def ledger_view(conn: sqlite3.Connection) -> list[dict]:
    rows = conn.execute(
        "SELECT p.*, f.name AS fighter_name FROM paper_bets p "
        "LEFT JOIN fighters f ON f.fighter_id = p.fighter_id "
        "ORDER BY p.captured_at DESC"
    ).fetchall()
    return [dict(r) for r in rows]
