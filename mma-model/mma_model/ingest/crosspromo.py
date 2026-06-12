"""Ingest cross-promotion (Sherdog-format) records into the global bout table.

Feeds the Tier 1 rating layer the multi-promotion data it needs so that UFC
debutants who fought in ONE / PFL / Bellator / RIZIN first carry a real prior
instead of entering at 1500.

Rules enforced here:
  * MMA-rules bouts only -- non-MMA ONE bouts (muay thai, kickboxing,
    submission grappling) are tagged and excluded (promotions.infer_ruleset).
  * UFC bouts are skipped -- ufcstats is the authoritative UFC source and is
    already loaded; ingesting UFC again would double-count. Cross-promotion
    ingest contributes the NON-UFC bouts only.
  * De-duplication -- Sherdog lists each fight on both fighters' pages, so the
    same bout arrives twice (mirrored). Bouts are keyed by
    {canonical id pair} + date and inserted once, winner oriented correctly.
  * Cross-source entity resolution -- fighter/opponent names are resolved
    through the alias-aware EntityResolver seeded from the canonical UFC
    fighters, so a Sherdog "Conor McGregor" links to his ufcstats id. Genuinely
    new (non-UFC) fighters get a stable synthetic id and a fighters row.

Input can be SherdogFight objects (from sherdog_scraper) or a CSV in the
Montanaz0r column layout (Fighter,Opponent,Result,Event,Event_date,Method,
Referee,Round,Time[,Division]).
"""
from __future__ import annotations

import csv
import hashlib
import sqlite3
from pathlib import Path
from typing import Iterable

from ..db.database import set_source_ts
from ..entity.resolver import EntityResolver, normalize_name
from . import parse
from .promotions import infer_promotion, infer_ruleset
from .sherdog_scraper import SherdogFight, _parse_sherdog_date, _split_method

SOURCE = "sherdog"


# --- input adapters ---------------------------------------------------------

def read_csv_records(path: Path | str) -> list[SherdogFight]:
    """Read Montanaz0r-layout CSV rows into SherdogFight objects."""
    out = []
    with open(path, newline="", encoding="utf-8") as fh:
        for r in csv.DictReader(fh):
            method, detail = _split_method(r.get("Method", ""))
            rnd = r.get("Round", "")
            out.append(SherdogFight(
                fighter=r.get("Fighter", "").strip(),
                opponent=r.get("Opponent", "").strip(),
                result=(r.get("Result", "") or "").strip().lower() or "nc",
                event=r.get("Event", "").strip(),
                date=_parse_sherdog_date(r.get("Event_date", "")),
                method=method,
                method_detail=detail,
                rnd=int(rnd) if str(rnd).isdigit() else None,
                time=r.get("Time", "").strip(),
                division=r.get("Division", "").strip(),
            ))
    return out


# --- resolver seeded from canonical UFC fighters ----------------------------

def build_resolver(conn: sqlite3.Connection) -> EntityResolver:
    resolver = EntityResolver()
    for r in conn.execute("SELECT fighter_id, name FROM fighters"):
        resolver.add_canonical(r["fighter_id"], r["name"])
    for a in conn.execute("SELECT alias, fighter_id FROM fighter_aliases"):
        resolver._add_alias(a["fighter_id"], a["alias"])
    return resolver


def _bout_key(fid_a: str, fid_b: str, date: str | None) -> str:
    a, b = sorted((fid_a, fid_b))
    return hashlib.sha1(f"{a}|{b}|{date or ''}".encode()).hexdigest()[:16]


def load(
    conn: sqlite3.Connection,
    records: Iterable[SherdogFight],
    *,
    exclude_ufc: bool = True,
) -> dict:
    """Resolve, filter, de-dup and insert cross-promotion bouts. Returns a summary."""
    resolver = build_resolver(conn)
    set_source_ts(conn, SOURCE, "Sherdog cross-promotion records")

    existing_ids = {r[0] for r in conn.execute("SELECT fighter_id FROM fighters")}
    new_fighters: dict[str, str] = {}     # fid -> display name
    bouts: dict[str, dict] = {}           # bout_key -> bout payload
    n_total = n_nonmma = n_ufc = n_nc_dropped = 0

    for f in records:
        n_total += 1
        promotion = infer_promotion(f.event)
        if infer_ruleset(f.event, f.division, f.method) != "MMA":
            n_nonmma += 1
            continue
        if exclude_ufc and promotion == "UFC":
            n_ufc += 1
            continue

        fid_self = resolver.get_or_create(f.fighter, context=f.event)
        fid_opp = resolver.get_or_create(f.opponent, context=f.event)
        if fid_self == fid_opp:
            continue
        for fid, nm in ((fid_self, f.fighter), (fid_opp, f.opponent)):
            if fid.startswith("syn:") and fid not in existing_ids:
                new_fighters[fid] = nm

        key = _bout_key(fid_self, fid_opp, f.date)
        # winner from the *self* fighter's perspective
        if f.result == "win":
            winner, result = fid_self, "win"
        elif f.result == "loss":
            winner, result = fid_opp, "win"
        elif f.result == "draw":
            winner, result = None, "draw"
        else:
            winner, result = None, "nc"

        if key in bouts:
            # mirror row already seen; only fill winner if the first was a draw/nc
            if bouts[key]["winner_id"] is None and winner is not None:
                bouts[key]["winner_id"] = winner
                bouts[key]["result"] = result
            continue

        # canonical fighter1/fighter2 ordering = sorted ids (stable, dedups)
        f1, f2 = sorted((fid_self, fid_opp))
        method, detail = f.method, f.method_detail
        bouts[key] = {
            "bout_id": f"sd:{key}",
            "date": f.date,
            "promotion": promotion,
            "fighter1_id": f1,
            "fighter2_id": f2,
            "winner_id": winner,
            "result": result,
            "method": method,
            "method_detail": detail,
            "round": f.rnd,
            "time": f.time,
            "scheduled_rounds": None,
            "ruleset": "MMA",
            "event": f.event,
        }

    # persist any new (non-UFC) fighters
    if new_fighters:
        conn.executemany(
            "INSERT OR IGNORE INTO fighters(fighter_id,name,source) VALUES(?,?,?)",
            [(fid, nm, SOURCE) for fid, nm in new_fighters.items()],
        )

    # skip bouts already present (idempotent re-runs)
    rows = [
        (b["bout_id"], None, b["date"], b["promotion"], b["fighter1_id"],
         b["fighter2_id"], b["winner_id"], None, b["result"], None, 0,
         b["method"], b["method_detail"], b["round"], b["time"],
         b["scheduled_rounds"], None, b["ruleset"], SOURCE, None)
        for b in bouts.values()
    ]
    conn.executemany(
        "INSERT OR REPLACE INTO bouts"
        "(bout_id,event_id,date,promotion,fighter1_id,fighter2_id,winner_id,outcome,"
        "result,weight_class,is_title,method,method_detail,round,time,scheduled_rounds,"
        "referee,ruleset,source,source_url) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        rows,
    )
    # persist learned aliases for future cross-source matches
    alias_rows = []
    seen = set()
    for norm, fids in resolver._index.items():
        if len(fids) == 1:
            fid = next(iter(fids))
            if (norm, fid) not in seen:
                alias_rows.append((resolver._names.get(fid, norm), norm, fid, SOURCE))
                seen.add((norm, fid))
    conn.executemany(
        "INSERT OR IGNORE INTO fighter_aliases(alias,norm_alias,fighter_id,source)"
        " VALUES(?,?,?,?)",
        alias_rows,
    )
    conn.commit()
    return {
        "records_in": n_total,
        "non_mma_excluded": n_nonmma,
        "ufc_skipped": n_ufc,
        "new_fighters": len(new_fighters),
        "bouts_inserted": len(rows),
        "promotions": _promo_counts(conn),
    }


def _promo_counts(conn: sqlite3.Connection) -> dict:
    return {
        r["promotion"]: r["n"]
        for r in conn.execute(
            "SELECT promotion, COUNT(*) n FROM bouts WHERE source=? GROUP BY promotion",
            (SOURCE,),
        )
    }
