"""Load the Greco1899 CSV mirror of ufcstats.com into the SQLite database.

Produces the same normalized rows the live scraper would, but reads the
regularly-updated CSV snapshots (works where ufcstats.com is firewalled).

Pipeline:
  1. fighters   <- ufc_fighter_tott.csv  (+ nicknames from ufc_fighter_details)
  2. resolver   <- canonical names indexed by the ufcstats fighter hash
  3. events     <- ufc_event_details.csv
  4. bouts      <- ufc_fight_results.csv (names resolved to canonical ids)
  5. bout_stats <- ufc_fight_stats.csv   (resolved + aggregated per round)
"""
from __future__ import annotations

import sqlite3
from pathlib import Path

import pandas as pd
import requests

from ..config import RAW_DIR, UFC_MIRROR_BASE, UFC_MIRROR_FILES
from ..db.database import set_source_ts
from ..entity.resolver import EntityResolver, normalize_name
from . import parse

SOURCE = "ufc_mirror"


# --- raw file access ------------------------------------------------------

def ensure_raw(refresh: bool = False) -> dict[str, Path]:
    """Ensure all mirror CSVs exist in data/raw/, downloading if needed."""
    RAW_DIR.mkdir(parents=True, exist_ok=True)
    paths = {}
    for key, fname in UFC_MIRROR_FILES.items():
        p = RAW_DIR / fname
        if refresh or not p.exists():
            url = f"{UFC_MIRROR_BASE}/{fname}"
            r = requests.get(url, timeout=120)
            r.raise_for_status()
            p.write_bytes(r.content)
        paths[key] = p
    return paths


def _read(path: Path) -> pd.DataFrame:
    df = pd.read_csv(path, dtype=str, keep_default_na=False)
    df.columns = [c.strip() for c in df.columns]
    return df


# --- loaders --------------------------------------------------------------

def load_fighters(conn: sqlite3.Connection, paths: dict[str, Path]) -> EntityResolver:
    """Populate `fighters` from tale-of-the-tape + details; return a resolver."""
    tott = _read(paths["tott"])
    details = _read(paths["details"])

    # nickname lookup by fighter hash
    nick = {}
    canon_name = {}
    for _, r in details.iterrows():
        fid = parse.id_from_url(r.get("URL", ""))
        if not fid:
            continue
        nm = " ".join(x for x in [r.get("FIRST", ""), r.get("LAST", "")] if x).strip()
        if nm:
            canon_name[fid] = nm
        if r.get("NICKNAME"):
            nick[fid] = r["NICKNAME"]

    resolver = EntityResolver()
    rows = []
    seen = set()
    for _, r in tott.iterrows():
        fid = parse.id_from_url(r.get("URL", ""))
        if not fid or fid in seen:
            continue
        seen.add(fid)
        name = (r.get("FIGHTER") or canon_name.get(fid) or "").strip()
        if not name:
            continue
        rows.append((
            fid, name, nick.get(fid),
            parse.parse_height_cm(r.get("HEIGHT", "")),
            parse.parse_reach_cm(r.get("REACH", "")),
            parse.parse_weight_lbs(r.get("WEIGHT", "")),
            (r.get("STANCE") or "").strip() or None,
            parse.parse_date(r.get("DOB", "")),
            SOURCE, r.get("URL", ""),
        ))
        resolver.add_canonical(fid, name)
        # register the details-form name as an alias too (covers ordering diffs)
        if fid in canon_name and normalize_name(canon_name[fid]) != normalize_name(name):
            resolver._add_alias(fid, canon_name[fid])

    conn.executemany(
        "INSERT OR REPLACE INTO fighters"
        "(fighter_id,name,nickname,height_cm,reach_cm,weight_lbs,stance,dob,source,source_url)"
        " VALUES(?,?,?,?,?,?,?,?,?,?)",
        rows,
    )
    conn.commit()
    return resolver


def load_events(conn: sqlite3.Connection, paths: dict[str, Path]) -> dict[str, str]:
    """Populate `events`; return name -> (event_id, date) map for bout linking."""
    ev = _read(paths["events"])
    rows, by_name = [], {}
    for _, r in ev.iterrows():
        url = r.get("URL", "")
        eid = parse.id_from_url(url) or normalize_name(r.get("EVENT", ""))
        date = parse.parse_date(r.get("DATE", ""))
        name = (r.get("EVENT") or "").strip()
        rows.append((eid, name, date, r.get("LOCATION", "").strip(), "UFC", SOURCE, url))
        by_name[name] = (eid, date)
    conn.executemany(
        "INSERT OR REPLACE INTO events"
        "(event_id,name,date,location,promotion,source,source_url) VALUES(?,?,?,?,?,?,?)",
        rows,
    )
    conn.commit()
    return by_name


def load_bouts(conn, paths, resolver, events) -> dict:
    """Populate `bouts`; resolve fighter names; return stats keyed by (event,bout)."""
    fr = _read(paths["fights"])
    rows = []
    unmatched = []
    # remember canonical ids per (event_name, bout_string) so stats can join
    bout_index: dict[tuple[str, str], dict] = {}
    for _, r in fr.iterrows():
        outcome = (r.get("OUTCOME") or "").strip()
        if parse.parse_outcome(outcome) == "unknown":
            continue  # skips the one stray header row and malformed rows
        event_name = (r.get("EVENT") or "").strip()
        bout_str = (r.get("BOUT") or "").strip()
        names = parse.split_bout(bout_str)
        if not names:
            continue
        url = r.get("URL", "")
        bid = parse.id_from_url(url) or normalize_name(f"{event_name} {bout_str}")
        eid, date = events.get(event_name, (None, None))

        f1 = resolver.get_or_create(names[0], context=bout_str)
        f2 = resolver.get_or_create(names[1], context=bout_str)
        for raw, fid in ((names[0], f1), (names[1], f2)):
            if fid.startswith("syn:"):
                unmatched.append((raw, bout_str, SOURCE, "minted synthetic id"))

        widx = parse.winner_index(outcome)
        winner = (f1, f2)[widx] if widx is not None else None
        method, detail = parse.parse_method(r.get("METHOD", ""))
        wc = r.get("WEIGHTCLASS", "")
        rows.append((
            bid, eid, date, "UFC", f1, f2, winner, outcome,
            parse.parse_outcome(outcome), wc.strip(), parse.is_title_bout(wc),
            method, detail, parse.parse_int(r.get("ROUND", "")),
            (r.get("TIME") or "").strip(),
            parse.scheduled_rounds(r.get("TIME FORMAT", "")),
            (r.get("REFEREE") or "").strip(), "MMA", SOURCE, url,
        ))
        bout_index[(event_name, bout_str)] = {"bout_id": bid, "f1": f1, "f2": f2}

    # Persist any synthetic fighters minted during resolution (no tale-of-the-tape
    # row exists for them) so the bout foreign keys resolve.
    existing = {r[0] for r in conn.execute("SELECT fighter_id FROM fighters")}
    syn_rows = [
        (fid, resolver._names.get(fid, fid.replace("syn:", "").replace("_", " ").title()),
         None, None, None, None, None, None, SOURCE, None)
        for fid in resolver._names
        if fid.startswith("syn:") and fid not in existing
    ]
    if syn_rows:
        conn.executemany(
            "INSERT OR REPLACE INTO fighters"
            "(fighter_id,name,nickname,height_cm,reach_cm,weight_lbs,stance,dob,source,source_url)"
            " VALUES(?,?,?,?,?,?,?,?,?,?)",
            syn_rows,
        )

    conn.executemany(
        "INSERT OR REPLACE INTO bouts"
        "(bout_id,event_id,date,promotion,fighter1_id,fighter2_id,winner_id,outcome,"
        "result,weight_class,is_title,method,method_detail,round,time,scheduled_rounds,"
        "referee,ruleset,source,source_url) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        rows,
    )
    if unmatched:
        conn.executemany(
            "INSERT INTO unmatched_log(raw_name,context,source,note) VALUES(?,?,?,?)",
            unmatched,
        )
    conn.commit()
    return bout_index


def load_stats(conn, paths, resolver, bout_index) -> int:
    """Populate `bout_stats` per fighter per round."""
    st = _read(paths["stats"])
    rows = []
    for _, r in st.iterrows():
        key = ((r.get("EVENT") or "").strip(), (r.get("BOUT") or "").strip())
        meta = bout_index.get(key)
        if not meta:
            continue
        fid = resolver.resolve(r.get("FIGHTER", ""), context=key[1])
        if fid is None:
            continue
        rnd_raw = (r.get("ROUND") or "").strip()
        rnd = parse.parse_int(rnd_raw)  # 'Round 1' -> 1
        sig_l, sig_a = parse.parse_of(r.get("SIG.STR.", ""))
        tot_l, tot_a = parse.parse_of(r.get("TOTAL STR.", ""))
        td_l, td_a = parse.parse_of(r.get("TD", ""))
        head_l, _ = parse.parse_of(r.get("HEAD", ""))
        body_l, _ = parse.parse_of(r.get("BODY", ""))
        leg_l, _ = parse.parse_of(r.get("LEG", ""))
        rows.append((
            meta["bout_id"], fid, rnd, parse.parse_int(r.get("KD", "")),
            sig_l, sig_a, tot_l, tot_a, td_l, td_a,
            parse.parse_int(r.get("SUB.ATT", "")), parse.parse_int(r.get("REV.", "")),
            parse.parse_ctrl_seconds(r.get("CTRL", "")), head_l, body_l, leg_l,
        ))
    conn.executemany(
        "INSERT OR REPLACE INTO bout_stats"
        "(bout_id,fighter_id,round,kd,sig_str_landed,sig_str_att,total_str_landed,"
        "total_str_att,td_landed,td_att,sub_att,rev,ctrl_sec,head_landed,body_landed,leg_landed)"
        " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        rows,
    )
    conn.commit()
    return len(rows)


def build(conn: sqlite3.Connection, refresh: bool = False) -> dict:
    """Full load. Returns a summary dict of row counts."""
    paths = ensure_raw(refresh=refresh)
    # Register the source first so fighters/events FK references resolve.
    set_source_ts(conn, SOURCE, "Greco1899 ufcstats CSV mirror")
    resolver = load_fighters(conn, paths)
    events = load_events(conn, paths)
    bout_index = load_bouts(conn, paths, resolver, events)
    n_stats = load_stats(conn, paths, resolver, bout_index)
    set_source_ts(conn, SOURCE, "Greco1899 ufcstats CSV mirror")

    def count(t):
        return conn.execute(f"SELECT COUNT(*) FROM {t}").fetchone()[0]

    return {
        "fighters": count("fighters"),
        "events": count("events"),
        "bouts": count("bouts"),
        "bout_stats": count("bout_stats"),
        "unmatched": count("unmatched_log"),
        "stat_rows_loaded": n_stats,
        "resolver_unmatched": len(resolver.unmatched),
        "resolver_ambiguous": len(resolver.ambiguous),
    }
