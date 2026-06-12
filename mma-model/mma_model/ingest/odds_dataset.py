"""Load historical UFC bookmaker odds (jansen88/ufc-data, sourced betmma.tips).

The dataset compiles odds from Nov 2014 onward as (favourite, underdog,
decimal odds) per bout, keyed by event_date + fighter names. Known issues
(documented upstream): odds missing for older years, and name-matching gaps
between betmma.tips and ufcstats spellings -- hence the fuzzy matcher here and
the unmatched_log audit trail.

Matching strategy, per odds row:
  1. exact:  frozenset{norm(name1), norm(name2)} -> bout index, filter by date
     within +/-3 days (events can differ by a day across timezones).
  2. fuzzy:  resolve each name through the alias-aware EntityResolver, then
     look the canonical-id pair up in a second index.
  3. fail -> unmatched_log (never silently dropped).

Loaded odds are tagged is_closing=1: betmma.tips historical odds are
closing-ish; good enough for the v1 backtest baseline (documented caveat).
"""
from __future__ import annotations

import math
import sqlite3
from datetime import date
from pathlib import Path

import pandas as pd
import requests
from rapidfuzz import fuzz

from ..config import RAW_DIR
from ..db.database import set_source_ts
from ..entity.resolver import EntityResolver, normalize_name

SOURCE = "jansen88_odds"
BOOK = "betmma"
ODDS_URL = "https://raw.githubusercontent.com/jansen88/ufc-data/master/data/complete_ufc_data.csv"
ODDS_CSV = RAW_DIR / "complete_ufc_data.csv"
DATE_TOLERANCE_DAYS = 3


def ensure_raw(refresh: bool = False) -> Path:
    if refresh or not ODDS_CSV.exists():
        r = requests.get(ODDS_URL, timeout=120)
        r.raise_for_status()
        ODDS_CSV.write_bytes(r.content)
    return ODDS_CSV


def _build_indexes(conn: sqlite3.Connection):
    """Index DB bouts by normalized-name pair and by canonical-id pair."""
    rows = conn.execute(
        """
        SELECT b.bout_id, b.date, b.fighter1_id, b.fighter2_id,
               f1.name AS name1, f2.name AS name2
        FROM bouts b
        JOIN fighters f1 ON f1.fighter_id = b.fighter1_id
        JOIN fighters f2 ON f2.fighter_id = b.fighter2_id
        WHERE b.date IS NOT NULL
        """
    ).fetchall()
    by_names: dict[frozenset, list] = {}
    by_ids: dict[frozenset, list] = {}
    resolver = EntityResolver()
    seen_fids = set()
    for r in rows:
        n1, n2 = normalize_name(r["name1"]), normalize_name(r["name2"])
        entry = {
            "bout_id": r["bout_id"],
            "date": date.fromisoformat(r["date"]),
            "fids": {n1: r["fighter1_id"], n2: r["fighter2_id"]},
            "names": {r["fighter1_id"]: n1, r["fighter2_id"]: n2},
        }
        by_names.setdefault(frozenset((n1, n2)), []).append(entry)
        by_ids.setdefault(frozenset((r["fighter1_id"], r["fighter2_id"])), []).append(entry)
        for fid, nm in ((r["fighter1_id"], r["name1"]), (r["fighter2_id"], r["name2"])):
            if fid not in seen_fids:
                resolver.add_canonical(fid, nm)
                seen_fids.add(fid)
    # fold in learned aliases from previous runs
    for a in conn.execute("SELECT alias, fighter_id FROM fighter_aliases"):
        resolver._add_alias(a["fighter_id"], a["alias"])
    return by_names, by_ids, resolver


def _pick_by_date(cands: list, d: date):
    """Among candidate bouts for a name pair, pick the unique one near `d`."""
    near = [c for c in cands if abs((c["date"] - d).days) <= DATE_TOLERANCE_DAYS]
    return near[0] if len(near) == 1 else None


def _fav_fighter_id(entry: dict, fav_name: str) -> str | None:
    """Map the odds-site favourite name onto one of the bout's two fighters."""
    fav_norm = normalize_name(fav_name)
    if fav_norm in entry["fids"]:
        return entry["fids"][fav_norm]
    # fuzzy: whichever of the two bout names is closer, if clearly closer
    scored = sorted(
        ((fuzz.token_sort_ratio(fav_norm, nm), fid) for fid, nm in entry["names"].items()),
        reverse=True,
    )
    if scored[0][0] >= 75 and scored[0][0] - scored[1][0] >= 10:
        return scored[0][1]
    return None


def load_odds(conn: sqlite3.Connection, refresh: bool = False) -> dict:
    """Match odds rows to bouts and populate the `odds` table."""
    path = ensure_raw(refresh=refresh)
    df = pd.read_csv(path, dtype=str, keep_default_na=False)
    by_names, by_ids, resolver = _build_indexes(conn)

    set_source_ts(conn, SOURCE, "jansen88/ufc-data historical odds (betmma.tips)")
    conn.execute("DELETE FROM odds WHERE source=?", (SOURCE,))
    conn.execute("DELETE FROM unmatched_log WHERE source=?", (SOURCE,))

    odds_rows, unmatched = [], []
    n_no_odds = n_matched = 0
    for _, r in df.iterrows():
        fav, und = r.get("favourite", ""), r.get("underdog", "")
        fav_o, und_o = r.get("favourite_odds", ""), r.get("underdog_odds", "")
        if not (fav and und and fav_o and und_o):
            n_no_odds += 1
            continue
        try:
            d = date.fromisoformat(r["event_date"])
            fav_odds, und_odds = float(fav_o), float(und_o)
        except (ValueError, KeyError):
            n_no_odds += 1
            continue
        # Guard against malformed odds: the source file contains literal 'inf'
        # strings (which float() accepts) and sub-1.0 decimals.
        if not all(math.isfinite(o) and 1.0 < o <= 100.0 for o in (fav_odds, und_odds)):
            n_no_odds += 1
            continue

        ctx = f"{r['event_date']} {r.get('fighter1','')} vs {r.get('fighter2','')}"
        # 1. exact normalized-name pair
        key = frozenset((normalize_name(r.get("fighter1", "")),
                         normalize_name(r.get("fighter2", ""))))
        entry = _pick_by_date(by_names.get(key, []), d)
        # 2. fuzzy via resolver -> id pair
        if entry is None:
            fid1 = resolver.resolve(r.get("fighter1", ""), ctx)
            fid2 = resolver.resolve(r.get("fighter2", ""), ctx)
            if fid1 and fid2:
                entry = _pick_by_date(by_ids.get(frozenset((fid1, fid2)), []), d)
        if entry is None:
            unmatched.append((f"{r.get('fighter1','')} vs {r.get('fighter2','')}",
                              ctx, SOURCE, "no bout match"))
            continue

        fav_fid = _fav_fighter_id(entry, fav)
        if fav_fid is None:
            unmatched.append((fav, ctx, SOURCE, "favourite name maps to neither fighter"))
            continue
        und_fid = next(f for f in entry["names"] if f != fav_fid)
        captured = r.get("odds_extract_ts", "") or r["event_date"]
        odds_rows.append((entry["bout_id"], fav_fid, BOOK, fav_odds, captured, 1, SOURCE))
        odds_rows.append((entry["bout_id"], und_fid, BOOK, und_odds, captured, 1, SOURCE))
        n_matched += 1

    conn.executemany(
        "INSERT OR REPLACE INTO odds"
        "(bout_id,fighter_id,book,decimal_odds,captured_at,is_closing,source)"
        " VALUES(?,?,?,?,?,?,?)",
        odds_rows,
    )
    if unmatched:
        conn.executemany(
            "INSERT INTO unmatched_log(raw_name,context,source,note) VALUES(?,?,?,?)",
            unmatched,
        )
    conn.commit()
    return {
        "rows_in_file": len(df),
        "rows_without_odds": n_no_odds,
        "bouts_matched": n_matched,
        "bouts_unmatched": len(unmatched),
        "odds_rows": len(odds_rows),
    }
