"""SQLite connection + initialization helpers."""
from __future__ import annotations

import sqlite3
from datetime import datetime, timezone
from pathlib import Path

from ..config import DB_PATH, SCHEMA_PATH


def connect(db_path: Path | str = DB_PATH) -> sqlite3.Connection:
    """Open a connection with foreign keys and Row access enabled."""
    conn = sqlite3.connect(str(db_path))
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


def init_db(conn: sqlite3.Connection, schema_path: Path = SCHEMA_PATH) -> None:
    """Create all tables from schema.sql (idempotent)."""
    conn.executescript(Path(schema_path).read_text())
    conn.commit()


def set_source_ts(conn: sqlite3.Connection, source: str, description: str = "") -> None:
    """Record that `source` was successfully ingested just now."""
    now = datetime.now(timezone.utc).isoformat()
    conn.execute(
        "INSERT INTO sources(source, description, last_ingested) VALUES(?,?,?) "
        "ON CONFLICT(source) DO UPDATE SET last_ingested=excluded.last_ingested, "
        "description=COALESCE(NULLIF(excluded.description,''), sources.description)",
        (source, description, now),
    )
    conn.commit()


def get_source_ts(conn: sqlite3.Connection, source: str) -> str | None:
    row = conn.execute(
        "SELECT last_ingested FROM sources WHERE source=?", (source,)
    ).fetchone()
    return row[0] if row else None
