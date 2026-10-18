"""SQLite access layer. Single source of truth for race data."""
import os
import sqlite3
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_DB = REPO_ROOT / "database" / "race.db"


def db_path() -> Path:
    return Path(os.environ.get("F1_DB_PATH", DEFAULT_DB))


def connect() -> sqlite3.Connection:
    path = db_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(str(path))
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    _ensure_schema(conn)
    return conn


def _ensure_schema(conn: sqlite3.Connection) -> None:
    schema = REPO_ROOT / "database" / "schema.sql"
    conn.executescript(schema.read_text(encoding="utf-8"))
    conn.commit()
