#!/usr/bin/env python3
"""Run the full Step 3 pipeline: odds ingest -> ratings replay -> backtest report.

Usage:
    python -m scripts.run_backtest [--refresh-odds]
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from mma_model.config import DB_PATH  # noqa: E402
from mma_model.db.database import connect, init_db  # noqa: E402
from mma_model.ingest import odds_dataset  # noqa: E402
from mma_model.ratings import rate_fighters  # noqa: E402
from mma_model.backtest import report  # noqa: E402


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--refresh-odds", action="store_true")
    args = ap.parse_args()

    conn = connect(DB_PATH)
    init_db(conn)  # idempotent; creates the predictions table on older DBs

    print("== Odds ingest ==")
    for k, v in odds_dataset.load_odds(conn, refresh=args.refresh_odds).items():
        print(f"  {k:18s}: {v}")

    print("== Ratings replay (with pre-fight predictions) ==")
    for k, v in rate_fighters.run(conn).items():
        print(f"  {k:18s}: {v}")

    print("== Backtest report ==")
    out = report.write_report(conn)
    print(f"  sample            : {out['sample']}")
    for res in (out["all"], out["established"]):
        print(f"  [{res.label}] n={res.n}")
        print(f"    model   log_loss={res.model['log_loss']:.4f} brier={res.model['brier']:.4f} "
              f"acc={res.model['accuracy']:.1%}")
        print(f"    implied log_loss={res.implied['log_loss']:.4f} brier={res.implied['brier']:.4f} "
              f"acc={res.implied['accuracy']:.1%}")
    print(f"  report written    : {out['report']}")
    print(f"  plot written      : {out['plot']}")
    conn.close()


if __name__ == "__main__":
    main()
