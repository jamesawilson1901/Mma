"""Pure parsing helpers shared by the live scraper and the CSV-mirror loader.

Kept side-effect free so they are trivially unit-testable.
"""
from __future__ import annotations

import re
from datetime import datetime

# --- ids ------------------------------------------------------------------

def id_from_url(url: str) -> str | None:
    """Extract the trailing ufcstats hash from a details URL."""
    if not url:
        return None
    m = re.search(r"/([0-9a-fA-F]{8,})\s*$", url.strip())
    return m.group(1) if m else None


# --- dates ----------------------------------------------------------------

def parse_date(raw: str) -> str | None:
    """'May 16, 2026' -> '2026-05-16' (ISO). Returns None on failure."""
    if not raw:
        return None
    raw = raw.strip()
    for fmt in ("%B %d, %Y", "%b %d, %Y", "%Y-%m-%d"):
        try:
            return datetime.strptime(raw, fmt).date().isoformat()
        except ValueError:
            continue
    return None


# --- tale of the tape -----------------------------------------------------

def parse_height_cm(raw: str) -> float | None:
    """'5' 11\"' -> centimetres."""
    if not raw or raw.strip() in {"--", ""}:
        return None
    m = re.match(r"(\d+)'\s*(\d+)", raw.strip())
    if not m:
        return None
    feet, inches = int(m.group(1)), int(m.group(2))
    return round((feet * 12 + inches) * 2.54, 1)


def parse_reach_cm(raw: str) -> float | None:
    """'76\"' -> centimetres."""
    if not raw or raw.strip() in {"--", ""}:
        return None
    m = re.match(r"([\d.]+)", raw.strip())
    return round(float(m.group(1)) * 2.54, 1) if m else None


def parse_weight_lbs(raw: str) -> float | None:
    if not raw or raw.strip() in {"--", ""}:
        return None
    m = re.match(r"([\d.]+)", raw.strip())
    return float(m.group(1)) if m else None


# --- fight result fields --------------------------------------------------

def parse_outcome(outcome: str) -> str:
    """Map raw W/L,L/W,D/D,NC/NC to a normalized result label."""
    o = (outcome or "").strip().upper()
    if o in {"W/L", "L/W"}:
        return "win"
    if o == "D/D":
        return "draw"
    if o == "NC/NC":
        return "nc"
    return "unknown"


def winner_index(outcome: str) -> int | None:
    """0 if fighter1 won, 1 if fighter2 won, None for draw/nc/unknown."""
    o = (outcome or "").strip().upper()
    if o == "W/L":
        return 0
    if o == "L/W":
        return 1
    return None


def split_bout(bout: str) -> tuple[str, str] | None:
    """'Arnold Allen vs. Melquizael Costa' -> ('Arnold Allen','Melquizael Costa')."""
    if not bout:
        return None
    parts = re.split(r"\s+vs\.?\s+", bout.strip(), maxsplit=1, flags=re.IGNORECASE)
    if len(parts) != 2:
        return None
    return parts[0].strip(), parts[1].strip()


def parse_method(method: str) -> tuple[str, str]:
    """Split 'Decision - Unanimous' into ('Decision','Unanimous')."""
    m = (method or "").strip()
    if " - " in m:
        head, detail = m.split(" - ", 1)
        return head.strip(), detail.strip()
    return m, ""


def scheduled_rounds(time_format: str) -> int | None:
    """'5 Rnd (5-5-5-5-5)' -> 5 ; '3 Rnd (5-5-5)' -> 3 ; '1 Rnd + OT' -> 1."""
    if not time_format:
        return None
    m = re.match(r"\s*(\d+)\s*Rnd", time_format)
    return int(m.group(1)) if m else None


def is_title_bout(weightclass: str) -> int:
    return 1 if "title" in (weightclass or "").lower() else 0


_LANDED_OF = re.compile(r"(\d+)\s+of\s+(\d+)")


def parse_of(raw: str) -> tuple[int, int]:
    """'9 of 20' -> (9, 20). Missing -> (0, 0)."""
    if not raw:
        return (0, 0)
    m = _LANDED_OF.search(raw)
    return (int(m.group(1)), int(m.group(2))) if m else (0, 0)


def parse_int(raw: str) -> int:
    if raw is None:
        return 0
    m = re.search(r"-?\d+", str(raw))
    return int(m.group(0)) if m else 0


def parse_ctrl_seconds(raw: str) -> int:
    """'1:44' -> 104 seconds ; '--' -> 0."""
    if not raw or raw.strip() in {"--", ""}:
        return 0
    parts = raw.strip().split(":")
    try:
        if len(parts) == 2:
            return int(parts[0]) * 60 + int(parts[1])
        return int(parts[0])
    except ValueError:
        return 0
