"""Live / upcoming odds capture for paper-trading.

Two ingest routes, both writing to the `odds` table (captured_at + is_closing):
  * The Odds API (https://the-odds-api.com) -- `OddsAPIClient`. NOTE: verify the
    free-tier request quota and MMA coverage before relying on it; coverage of
    non-UFC promotions is thin, and the free tier is rate limited. Reads the key
    from the ODDS_API_KEY env var. Network is firewalled in the build sandbox,
    so this is exercised in production only.
  * Manual CSV entry -- `snapshot_from_csv` (the always-available fallback).

A "snapshot" is one odds observation per fighter at a moment in time. Capturing
daily snapshots plus a closing-line snapshot builds the closing-line history
that is this project's most valuable asset for non-UFC markets.
"""
from __future__ import annotations

import csv
import os
import sqlite3
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

import requests

from ..db.database import set_source_ts

ODDS_API_BASE = "https://api.the-odds-api.com/v4"
SOURCE = "live_odds"


@dataclass
class OddsSnapshot:
    bout_id: str
    fighter_id: str
    book: str
    decimal_odds: float
    captured_at: str
    is_closing: bool = False


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def record_snapshot(conn: sqlite3.Connection, snaps: list[OddsSnapshot]) -> int:
    """Persist odds snapshots (idempotent on the natural key)."""
    rows = [
        (s.bout_id, s.fighter_id, s.book, s.decimal_odds, s.captured_at,
         1 if s.is_closing else 0, SOURCE)
        for s in snaps
    ]
    conn.executemany(
        "INSERT OR REPLACE INTO odds"
        "(bout_id,fighter_id,book,decimal_odds,captured_at,is_closing,source)"
        " VALUES(?,?,?,?,?,?,?)",
        rows,
    )
    set_source_ts(conn, SOURCE, "live/upcoming odds capture")
    conn.commit()
    return len(rows)


def snapshot_from_csv(conn: sqlite3.Connection, path: Path | str,
                      is_closing: bool = False) -> int:
    """Load a manual odds snapshot.

    CSV columns: bout_id, fighter_id, book, decimal_odds[, captured_at].
    """
    snaps = []
    with open(path, newline="", encoding="utf-8") as fh:
        for r in csv.DictReader(fh):
            snaps.append(OddsSnapshot(
                bout_id=r["bout_id"].strip(),
                fighter_id=r["fighter_id"].strip(),
                book=r.get("book", "manual").strip() or "manual",
                decimal_odds=float(r["decimal_odds"]),
                captured_at=r.get("captured_at", "").strip() or _now(),
                is_closing=str(r.get("is_closing", is_closing)).lower()
                in {"1", "true", "yes"} or is_closing,
            ))
    return record_snapshot(conn, snaps)


class OddsAPIClient:
    """Thin client for The Odds API MMA endpoint.

    Returns raw event dicts; mapping event/fighter names to our canonical ids is
    the caller's job (reuse the EntityResolver). Kept minimal and network-gated.
    """

    def __init__(self, api_key: str | None = None, timeout: float = 20.0):
        self.api_key = api_key or os.environ.get("ODDS_API_KEY")
        self.timeout = timeout

    def available(self) -> bool:
        return bool(self.api_key)

    def fetch_mma(self, regions: str = "us,uk", markets: str = "h2h") -> list[dict]:
        if not self.api_key:
            raise RuntimeError("ODDS_API_KEY not set; use snapshot_from_csv instead")
        url = f"{ODDS_API_BASE}/sports/mma_mixed_martial_arts/odds"
        resp = requests.get(
            url,
            params={"apiKey": self.api_key, "regions": regions,
                    "markets": markets, "oddsFormat": "decimal"},
            timeout=self.timeout,
        )
        resp.raise_for_status()
        return resp.json()
