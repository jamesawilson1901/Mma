"""Central paths and constants for the MMA model project."""
from __future__ import annotations

from pathlib import Path

# Project layout -------------------------------------------------------------
PKG_DIR = Path(__file__).resolve().parent
PROJECT_DIR = PKG_DIR.parent
DATA_DIR = PROJECT_DIR / "data"
RAW_DIR = DATA_DIR / "raw"
DB_PATH = DATA_DIR / "mma.sqlite"
SCHEMA_PATH = PKG_DIR / "db" / "schema.sql"

# Greco1899/scrape_ufc_stats is a regularly-updated CSV mirror of ufcstats.com.
# Used as the data source in environments where ufcstats.com is unreachable
# (the live scraper in ingest/ufc_scraper.py targets ufcstats.com directly).
UFC_MIRROR_BASE = "https://raw.githubusercontent.com/Greco1899/scrape_ufc_stats/main"
UFC_MIRROR_FILES = {
    "events": "ufc_event_details.csv",
    "fights": "ufc_fight_results.csv",
    "stats": "ufc_fight_stats.csv",
    "tott": "ufc_fighter_tott.csv",
    "details": "ufc_fighter_details.csv",
}

# Glicko-2 defaults (Glickman 2013). tau controls volatility sensitivity.
GLICKO_DEFAULT_RATING = 1500.0
GLICKO_DEFAULT_RD = 350.0
GLICKO_DEFAULT_VOL = 0.06
GLICKO_TAU = 0.5
# Glicko-2 internal scale factor.
GLICKO_SCALE = 173.7178
# One rating period == this many days. Used to inflate RD across layoffs.
RATING_PERIOD_DAYS = 180.0
