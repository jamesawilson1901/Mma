#!/usr/bin/env python3
"""Step 5: ingest cross-promotion records and recompute global ratings.

Two data routes (Sherdog is the source; results-level only):
  --csv PATH    load Montanaz0r-layout records from a CSV (works offline).
  --crawl FILE  crawl Sherdog fighter pages listed one-URL-per-line in FILE
                (requires network + honours robots.txt; pages are cached).

After loading, the global Glicko-2 replay runs over ALL promotions together so
that fighters who crossed orgs link the opponent pools and UFC debutants with
prior non-UFC bouts carry real priors.

Usage:
    python -m scripts.run_crosspromo --csv data/raw/crosspromo.csv
    python -m scripts.run_crosspromo --crawl seeds.txt
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from mma_model.config import DB_PATH  # noqa: E402
from mma_model.db.database import connect, init_db  # noqa: E402
from mma_model.ingest import crosspromo  # noqa: E402
from mma_model.ingest import sherdog_scraper as sd  # noqa: E402
from mma_model.ratings import rate_fighters  # noqa: E402


def crawl(seed_file: Path) -> list:
    urls = [u.strip() for u in seed_file.read_text().splitlines() if u.strip()]
    records = []
    for url in urls:
        try:
            html = sd.fetch(url)
        except Exception as exc:  # network blocked / robots / 4xx
            print(f"  skip {url}: {exc}")
            continue
        records.extend(sd.parse_fight_history(html))
    return records


def main() -> None:
    ap = argparse.ArgumentParser()
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--csv", type=Path, help="Montanaz0r-layout records CSV")
    g.add_argument("--crawl", type=Path, help="file of Sherdog fighter URLs")
    args = ap.parse_args()

    conn = connect(DB_PATH)
    init_db(conn)

    if args.csv:
        records = crosspromo.read_csv_records(args.csv)
    else:
        records = crawl(args.crawl)
    print(f"Loaded {len(records)} raw records.")

    print("== Cross-promotion ingest ==")
    for k, v in crosspromo.load(conn, records).items():
        print(f"  {k:18s}: {v}")

    print("== Global ratings replay (all promotions) ==")
    for k, v in rate_fighters.run(conn).items():
        print(f"  {k:18s}: {v}")
    conn.close()


if __name__ == "__main__":
    main()
