#!/usr/bin/env python3
"""Run the Glicko-2 rating engine over the database and print a sanity ranking.

Usage:
    python -m scripts.run_ratings [--top N]
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from mma_model.config import DB_PATH  # noqa: E402
from mma_model.db.database import connect  # noqa: E402
from mma_model.ratings import rate_fighters  # noqa: E402


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--top", type=int, default=25)
    args = ap.parse_args()

    conn = connect(DB_PATH)
    summary = rate_fighters.run(conn)
    print("Glicko-2 run complete:")
    for k, v in summary.items():
        print(f"  {k:18s}: {v}")

    print(f"\nTop {args.top} fighters by current Glicko rating (RD <= 110):")
    print(f"  {'Rk':>3} {'Rating':>7} {'RD':>5}  {'Last bout':10}  Fighter")
    for i, row in enumerate(rate_fighters.current_ratings(conn, limit=args.top), 1):
        print(f"  {i:>3} {row['rating']:7.0f} {row['rd']:5.0f}  "
              f"{row['date'] or '?':10}  {row['name']}")
    conn.close()


if __name__ == "__main__":
    main()
