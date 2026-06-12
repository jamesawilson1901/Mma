"""Live ufcstats.com scraper (BeautifulSoup) with local HTML caching.

This is the canonical UFC data source. It targets ufcstats.com directly and
caches every fetched page under data/raw/html/ so re-runs are incremental and
polite. In sandboxes where ufcstats.com is unreachable, use ufc_dataset.py
(the Greco1899 CSV mirror) instead -- it produces the same normalized rows.

Design notes:
  * One function per page type, each returning plain dicts/lists (no DB writes)
    so the parsing is testable against cached fixtures.
  * Rate limited + cached; safe to re-run for incremental event updates.
"""
from __future__ import annotations

import time
from pathlib import Path

import requests
from bs4 import BeautifulSoup

from ..config import RAW_DIR
from .parse import (
    id_from_url,
    parse_date,
    parse_height_cm,
    parse_reach_cm,
    parse_weight_lbs,
)

EVENTS_URL = "http://ufcstats.com/statistics/events/completed?page=all"
HTML_CACHE = RAW_DIR / "html"
HEADERS = {"User-Agent": "Mozilla/5.0 (personal research; low volume)"}
RATE_LIMIT_S = 1.0  # be polite


def _cache_path(url: str) -> Path:
    key = id_from_url(url) or url.rsplit("/", 1)[-1] or "index"
    sub = "events" if "event" in url else "fighters" if "fighter" in url else "misc"
    return HTML_CACHE / sub / f"{key}.html"


def fetch(url: str, *, use_cache: bool = True, session: requests.Session | None = None) -> str:
    """GET a page, caching the HTML. Returns the HTML text."""
    cp = _cache_path(url)
    if use_cache and cp.exists():
        return cp.read_text(encoding="utf-8", errors="replace")
    sess = session or requests
    resp = sess.get(url, headers=HEADERS, timeout=30)
    resp.raise_for_status()
    cp.parent.mkdir(parents=True, exist_ok=True)
    cp.write_text(resp.text, encoding="utf-8")
    time.sleep(RATE_LIMIT_S)
    return resp.text


# --- parsers (operate on HTML strings, independent of network) -------------

def parse_events_index(html: str) -> list[dict]:
    """List of {name, url, date, location} from the completed-events page."""
    soup = BeautifulSoup(html, "lxml")
    out = []
    for row in soup.select("tr.b-statistics__table-row"):
        a = row.select_one("a.b-link")
        if not a:
            continue
        date_span = row.select_one("span.b-statistics__date")
        loc = row.select("td.b-statistics__table-col")
        out.append({
            "name": a.get_text(strip=True),
            "url": a.get("href", ""),
            "date": parse_date(date_span.get_text(strip=True)) if date_span else None,
            "location": loc[-1].get_text(strip=True) if loc else None,
        })
    return out


def parse_event_page(html: str) -> list[dict]:
    """List of bout summary rows (with fight-details URLs) for one event."""
    soup = BeautifulSoup(html, "lxml")
    rows = []
    for tr in soup.select("tr.b-fight-details__table-row__hover, tr.js-fight-details-click"):
        href = tr.get("data-link") or ""
        cols = tr.select("td")
        fighters = [a.get_text(strip=True) for a in tr.select("a.b-link")]
        rows.append({
            "url": href,
            "fighters": fighters[:2],
            "weight_class": cols[6].get_text(strip=True) if len(cols) > 6 else "",
        })
    return rows


def parse_fighter_page(html: str) -> dict:
    """Tale-of-the-tape for one fighter-details page."""
    soup = BeautifulSoup(html, "lxml")
    name_el = soup.select_one("span.b-content__title-highlight")
    info = {"name": name_el.get_text(strip=True) if name_el else None}
    label_map = {
        "Height:": "height", "Reach:": "reach", "Weight:": "weight",
        "STANCE:": "stance", "DOB:": "dob",
    }
    for li in soup.select("li.b-list__box-list-item"):
        txt = li.get_text(" ", strip=True)
        for label, key in label_map.items():
            if txt.startswith(label):
                info[key] = txt[len(label):].strip()
    return {
        "name": info.get("name"),
        "height_cm": parse_height_cm(info.get("height", "")),
        "reach_cm": parse_reach_cm(info.get("reach", "")),
        "weight_lbs": parse_weight_lbs(info.get("weight", "")),
        "stance": (info.get("stance") or "").strip() or None,
        "dob": parse_date(info.get("dob", "")),
    }


def scrape_events_index(*, use_cache: bool = True) -> list[dict]:
    """Fetch + parse the completed-events index (entry point for a full crawl)."""
    return parse_events_index(fetch(EVENTS_URL, use_cache=use_cache))
