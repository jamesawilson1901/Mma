#!/usr/bin/env python3
"""Build the SQLite database from the UFC data mirror.

Usage:
    python -m scripts.build_db [--refresh]
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from mma_model.config import DB_PATH  # noqa: E402
from mma_model.db.database import connect, init_db  # noqa: E402
from mma_model.ingest import ufc_dataset  # noqa: E402


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--refresh", action="store_true", help="re-download mirror CSVs")
    args = ap.parse_args()

    conn = connect(DB_PATH)
    init_db(conn)
    summary = ufc_dataset.build(conn, refresh=args.refresh)
    print(f"Database built at {DB_PATH}")
    for k, v in summary.items():
        print(f"  {k:22s}: {v}")
    conn.close()


if __name__ == "__main__":
    main()
