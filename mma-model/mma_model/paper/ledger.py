"""Paper-bet ledger: log hypothetical stakes, settle from results, track ROI/CLV.

No real money (v1 non-goal). A bet records the side wagered on, the odds taken
at capture time, and the model probability behind it. Settlement reads the
bout result; CLV compares the taken odds to the closing odds for the same side.

CLV (closing-line value) here = taken_decimal / closing_decimal - 1. Positive
CLV means we beat the closing line (got better odds than the market closed at),
which is the leading indicator of a real edge even before results land.
"""
from __future__ import annotations

import sqlite3
from datetime import datetime, timezone

from ..backtest import metrics as M


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def place_bet(
    conn: sqlite3.Connection,
    bout_id: str,
    fighter_id: str,
    stake: float,
    odds_decimal: float,
    p_model: float,
    model: str = "tier1_glicko",
    captured_at: str | None = None,
) -> int:
    """Log an open paper bet on `fighter_id`. Returns the bet_id."""
    edge = p_model * odds_decimal - 1.0
    cur = conn.execute(
        "INSERT INTO paper_bets"
        "(bout_id,fighter_id,model,p_model,stake,odds_decimal,edge,captured_at,status)"
        " VALUES(?,?,?,?,?,?,?,?, 'open')",
        (bout_id, fighter_id, model, p_model, stake, odds_decimal, edge,
         captured_at or _now()),
    )
    conn.commit()
    return cur.lastrowid


def suggest_and_place(
    conn: sqlite3.Connection,
    bout_id: str,
    fighter_id: str,
    p_model: float,
    odds_decimal: float,
    bankroll: float = 100.0,
    kelly_fraction: float = 0.25,
    edge_threshold: float = 0.0,
    model: str = "tier1_glicko",
) -> int | None:
    """Place a fractional-Kelly bet iff the model edge clears the threshold."""
    edge = p_model * odds_decimal - 1.0
    if edge <= edge_threshold:
        return None
    stake = bankroll * kelly_fraction * M.kelly_fraction(p_model, odds_decimal)
    if stake <= 0:
        return None
    return place_bet(conn, bout_id, fighter_id, round(stake, 2), odds_decimal,
                     p_model, model)


def settle(conn: sqlite3.Connection) -> dict:
    """Settle every open bet whose bout now has a result."""
    open_bets = conn.execute(
        "SELECT p.bet_id, p.bout_id, p.fighter_id, p.stake, p.odds_decimal, "
        "b.result, b.winner_id "
        "FROM paper_bets p JOIN bouts b ON b.bout_id = p.bout_id "
        "WHERE p.status = 'open' AND b.result IS NOT NULL"
    ).fetchall()
    n_won = n_lost = n_void = 0
    for r in open_bets:
        if r["result"] == "win" and r["winner_id"] is not None:
            won = r["winner_id"] == r["fighter_id"]
            status = "won" if won else "lost"
            pnl = r["stake"] * (r["odds_decimal"] - 1.0) if won else -r["stake"]
            n_won += won
            n_lost += not won
        else:  # draw / nc -> stake returned
            status, pnl = "void", 0.0
            n_void += 1
        conn.execute(
            "UPDATE paper_bets SET status=?, pnl=?, settled_at=? WHERE bet_id=?",
            (status, pnl, _now(), r["bet_id"]),
        )
    conn.commit()
    return {"settled": len(open_bets), "won": n_won, "lost": n_lost, "void": n_void}


def record_closing(conn: sqlite3.Connection, bout_id: str, fighter_id: str,
                   closing_odds: float) -> int:
    """Attach the closing line for a side and compute CLV on matching bets."""
    rows = conn.execute(
        "SELECT bet_id, odds_decimal FROM paper_bets "
        "WHERE bout_id=? AND fighter_id=?",
        (bout_id, fighter_id),
    ).fetchall()
    for r in rows:
        clv = r["odds_decimal"] / closing_odds - 1.0
        conn.execute(
            "UPDATE paper_bets SET closing_odds=?, clv=? WHERE bet_id=?",
            (closing_odds, clv, r["bet_id"]),
        )
    conn.commit()
    return len(rows)


def summary(conn: sqlite3.Connection) -> dict:
    """Running ledger stats: ROI (with bootstrap CI), record, CLV."""
    settled = conn.execute(
        "SELECT stake, odds_decimal, pnl, status FROM paper_bets "
        "WHERE status IN ('won','lost')"
    ).fetchall()
    total_staked = sum(r["stake"] for r in settled)
    total_pnl = sum(r["pnl"] for r in settled)
    roi = total_pnl / total_staked if total_staked else 0.0

    # bootstrap ROI CI over settled bets (unit-staked profit per bet)
    bets = [
        M.Bet(won=(r["status"] == "won"), odds=r["odds_decimal"], p=0.0, edge=0.0)
        for r in settled
    ]
    roi_ci = M.bootstrap_roi_ci(bets, M.roi_flat) if bets else (0.0, 0.0)

    clv_rows = conn.execute(
        "SELECT clv FROM paper_bets WHERE clv IS NOT NULL").fetchall()
    clvs = [r["clv"] for r in clv_rows]
    counts = {
        r["status"]: r["n"] for r in conn.execute(
            "SELECT status, COUNT(*) n FROM paper_bets GROUP BY status")
    }
    return {
        "n_bets": sum(counts.values()),
        "by_status": counts,
        "total_staked": round(total_staked, 2),
        "total_pnl": round(total_pnl, 2),
        "roi": roi,
        "roi_ci": roi_ci,
        "avg_clv": (sum(clvs) / len(clvs)) if clvs else None,
        "pct_positive_clv": (sum(c > 0 for c in clvs) / len(clvs)) if clvs else None,
        "n_clv": len(clvs),
    }
