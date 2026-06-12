#!/usr/bin/env python3
"""Seed a DEMO upcoming card + paper bets so the dashboard shows live data.

This is a demonstration utility (real live odds are firewalled in the build
sandbox). It:
  * stages one fantasy upcoming bout between two top-rated fighters, with a
    current odds snapshot that leaves an edge on the model's pick, and places
    a 1/4-Kelly open paper bet on it;
  * places + settles a handful of paper bets on real historical bouts (using
    their stored odds) so the ledger shows realized P/L and CLV.

It RESETS prior demo state (demo bouts/odds and the whole paper_bets ledger),
so re-running is idempotent. Nothing here touches the historical ratings or the
backtest tables.

Usage:  python -m scripts.seed_demo_card
"""
from __future__ import annotations

import sys
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from mma_model.config import DB_PATH  # noqa: E402
from mma_model.db.database import connect, init_db  # noqa: E402
from mma_model.ingest.live_odds import OddsSnapshot, record_snapshot  # noqa: E402
from mma_model.models.predict import predict_pair  # noqa: E402
from mma_model.paper import ledger as L  # noqa: E402

DEMO_BOUT = "demo:upcoming-1"
FUTURE_DATE = "2026-07-01"


def _top_two(conn):
    rows = conn.execute(
        """
        WITH latest AS (SELECT fighter_id, MAX(date) d FROM ratings_history
                        GROUP BY fighter_id)
        SELECT rh.fighter_id, f.name, rh.rating
        FROM ratings_history rh
        JOIN latest l ON l.fighter_id=rh.fighter_id AND l.d=rh.date
        JOIN fighters f ON f.fighter_id=rh.fighter_id
        WHERE rh.rd <= 110 GROUP BY rh.fighter_id
        ORDER BY rh.rating DESC LIMIT 2
        """
    ).fetchall()
    return rows


def main() -> None:
    conn = connect(DB_PATH)
    init_db(conn)

    # reset prior demo state
    conn.execute("DELETE FROM paper_bets")
    conn.execute("DELETE FROM odds WHERE bout_id LIKE 'demo:%'")
    conn.execute("DELETE FROM bouts WHERE bout_id LIKE 'demo:%'")
    conn.commit()

    conn.execute("INSERT OR IGNORE INTO sources(source,description) "
                 "VALUES('demo_card','staged demo upcoming card')")
    a, b = _top_two(conn)
    conn.execute(
        "INSERT INTO bouts(bout_id,date,promotion,fighter1_id,fighter2_id,"
        "result,ruleset,source) VALUES(?,?, 'UFC', ?, ?, NULL, 'MMA', 'demo_card')",
        (DEMO_BOUT, FUTURE_DATE, a["fighter_id"], b["fighter_id"]),
    )
    conn.commit()

    pred = predict_pair(conn, a["fighter_id"], b["fighter_id"])
    p_a = pred.p_fighter1
    # offer odds that leave a clear edge on the model favourite, fair-ish on dog
    odds_a = round(1.0 / max(p_a - 0.08, 0.05), 2)
    odds_b = round(1.0 / max((1 - p_a) - 0.02, 0.05), 2)
    now = datetime.now(timezone.utc).isoformat()
    record_snapshot(conn, [
        OddsSnapshot(DEMO_BOUT, a["fighter_id"], "demo_book", odds_a, now),
        OddsSnapshot(DEMO_BOUT, b["fighter_id"], "demo_book", odds_b, now),
    ])
    bet_id = L.suggest_and_place(conn, DEMO_BOUT, a["fighter_id"], p_a, odds_a,
                                 bankroll=100.0, edge_threshold=0.05)

    # realized history: settle paper bets on real past bouts with stored odds
    hist = conn.execute(
        """
        SELECT b.bout_id, b.fighter1_id, o1.decimal_odds d1, o2.decimal_odds d2,
               b.winner_id
        FROM bouts b
        JOIN odds o1 ON o1.bout_id=b.bout_id AND o1.fighter_id=b.fighter1_id
        JOIN odds o2 ON o2.bout_id=b.bout_id AND o2.fighter_id=b.fighter2_id
        WHERE b.result='win' AND b.winner_id IS NOT NULL AND b.source='ufc_mirror'
        ORDER BY b.date DESC LIMIT 6
        """
    ).fetchall()
    for h in hist:
        L.place_bet(conn, h["bout_id"], h["fighter1_id"], stake=5.0,
                    odds_decimal=h["d1"], p_model=0.5)
        # closing line slightly worse than taken -> small positive CLV demo
        L.record_closing(conn, h["bout_id"], h["fighter1_id"],
                         round(h["d1"] * 0.97, 2))
    L.settle(conn)

    print(f"Demo card staged: {a['name']} ({a['rating']:.0f}) vs "
          f"{b['name']} ({b['rating']:.0f})")
    print(f"  model P({a['name']}) = {p_a:.1%}; odds {odds_a}/{odds_b}; "
          f"open bet_id={bet_id}")
    print(f"  settled {len(hist)} historical paper bets")
    print("  ledger summary:", L.summary(conn))
    conn.close()


if __name__ == "__main__":
    main()
