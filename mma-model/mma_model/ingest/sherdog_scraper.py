"""Sherdog fighter-record scraper for cross-promotion fight history.

Sherdog is the most complete public source of results-level MMA records across
every promotion (ONE, PFL, Bellator, RIZIN, regional shows...), which is what
the Tier 1 global rating layer needs. There are no per-fight performance stats
here -- results only -- which is exactly the cross-promotion data contract.

Scope / etiquette (the ToS-greyest part of the project, per the spec):
  * personal-use, low-volume, aggressively cached, rate-limited;
  * honours robots.txt via can_fetch() before any live request;
  * every page cached under data/raw/sherdog/ so a record is fetched at most once.

The HTML parsers are pure (operate on a string) so they are unit-tested against
a committed fixture without any network. In this sandbox sherdog.com is
firewalled, so production crawls must run where the host is reachable -- the
loader (crosspromo.py) can also read the same normalized rows from a CSV.
"""
from __future__ import annotations

import time
import urllib.robotparser
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

import requests
from bs4 import BeautifulSoup

from ..config import RAW_DIR

BASE = "https://www.sherdog.com"
ROBOTS_URL = f"{BASE}/robots.txt"
HTML_CACHE = RAW_DIR / "sherdog"
HEADERS = {"User-Agent": "mma-model personal research (low volume; contact via repo)"}
RATE_LIMIT_S = 2.0  # deliberately gentle

_robots: urllib.robotparser.RobotFileParser | None = None


@dataclass
class SherdogFight:
    """One row of a fighter's pro record, normalized."""

    fighter: str
    opponent: str
    result: str          # win / loss / draw / nc
    event: str
    date: str | None     # ISO
    method: str
    method_detail: str
    rnd: int | None
    time: str
    division: str = ""   # ONE lists non-MMA divisions here when available


# --- politeness -------------------------------------------------------------

def _get_robots() -> urllib.robotparser.RobotFileParser:
    global _robots
    if _robots is None:
        rp = urllib.robotparser.RobotFileParser()
        rp.set_url(ROBOTS_URL)
        try:
            rp.read()
        except Exception:
            rp.parse([])  # if robots is unreachable, default to permissive-but-cautious
        _robots = rp
    return _robots


def can_fetch(url: str) -> bool:
    return _get_robots().can_fetch(HEADERS["User-Agent"], url)


def _cache_path(url: str) -> Path:
    slug = url.rstrip("/").rsplit("/", 1)[-1] or "index"
    return HTML_CACHE / f"{slug}.html"


def fetch(url: str, *, use_cache: bool = True,
          session: requests.Session | None = None) -> str:
    """GET a Sherdog page (cached, robots-checked, rate-limited)."""
    cp = _cache_path(url)
    if use_cache and cp.exists():
        return cp.read_text(encoding="utf-8", errors="replace")
    if not can_fetch(url):
        raise PermissionError(f"robots.txt disallows fetching {url}")
    resp = (session or requests).get(url, headers=HEADERS, timeout=30)
    resp.raise_for_status()
    cp.parent.mkdir(parents=True, exist_ok=True)
    cp.write_text(resp.text, encoding="utf-8")
    time.sleep(RATE_LIMIT_S)
    return resp.text


# --- parsing (pure) ---------------------------------------------------------

def _parse_sherdog_date(raw: str) -> str | None:
    """Sherdog dates: 'Mar / 20 / 2005' or 'Mar 20, 2005'."""
    if not raw:
        return None
    cleaned = raw.replace("/", " ").replace(",", " ")
    cleaned = " ".join(cleaned.split())
    for fmt in ("%b %d %Y", "%B %d %Y"):
        try:
            return datetime.strptime(cleaned, fmt).date().isoformat()
        except ValueError:
            continue
    return None


def _split_method(raw: str) -> tuple[str, str]:
    """'KO (Punches)' -> ('KO', 'Punches'); 'TKO (Corner Stoppage)' -> ('TKO',...)."""
    raw = (raw or "").strip()
    if "(" in raw and raw.endswith(")"):
        head, detail = raw.split("(", 1)
        return head.strip(), detail[:-1].strip()
    return raw, ""


def parse_fighter_name(html: str) -> str | None:
    soup = BeautifulSoup(html, "lxml")
    el = soup.select_one("span.fn") or soup.select_one("h1 span")
    return el.get_text(strip=True) if el else None


def parse_fight_history(html: str, fighter_name: str | None = None) -> list[SherdogFight]:
    """Parse the pro fight-history table from a Sherdog fighter page."""
    soup = BeautifulSoup(html, "lxml")
    name = fighter_name or parse_fighter_name(html) or ""
    fights: list[SherdogFight] = []
    # Sherdog renders the record in a table with class 'new_table fighter'.
    for table in soup.select("table.new_table.fighter, table.fight_history"):
        for tr in table.select("tr"):
            cells = tr.find_all("td")
            if len(cells) < 6:
                continue
            result = cells[0].get_text(strip=True).lower()
            if result not in {"win", "loss", "draw", "nc", "n/a"}:
                continue  # header / non-data row
            opponent = cells[1].get_text(strip=True)
            event = cells[2].get_text(" ", strip=True)
            # event cell often contains the date on a second line
            date_el = cells[2].select_one("span.sub_line")
            date_raw = date_el.get_text(strip=True) if date_el else ""
            if not date_raw and len(cells) >= 3:
                date_raw = cells[2].get_text("\n", strip=True).split("\n")[-1]
            # The method cell carries the referee in a sub_line; strip it so the
            # method text is clean ('KO (Punches)' not 'KO (Punches) Ref Name').
            method_cell = cells[3]
            ref_el = method_cell.select_one("span.sub_line")
            ref_txt = ref_el.get_text(strip=True) if ref_el else ""
            method_raw = method_cell.get_text(" ", strip=True)
            if ref_txt:
                method_raw = method_raw.replace(ref_txt, "").strip()
            method, detail = _split_method(method_raw)
            rnd_txt = cells[4].get_text(strip=True)
            time_txt = cells[5].get_text(strip=True)
            fights.append(SherdogFight(
                fighter=name,
                opponent=opponent,
                result="nc" if result in {"nc", "n/a"} else result,
                event=event.replace(date_raw, "").strip(" -"),
                date=_parse_sherdog_date(date_raw),
                method=method,
                method_detail=detail,
                rnd=int(rnd_txt) if rnd_txt.isdigit() else None,
                time=time_txt,
            ))
    return fights
