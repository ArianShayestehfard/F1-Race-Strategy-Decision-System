import os
import sys
from pathlib import Path

import pytest

REPO_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(REPO_ROOT / "python-engine"))

from app import ingest  # noqa: E402


@pytest.fixture(scope="session")
def demo_db(tmp_path_factory):
    """A SQLite file with one synthetic demo race, shared by all tests."""
    db = tmp_path_factory.mktemp("db") / "test.db"
    os.environ["F1_DB_PATH"] = str(db)
    ingest.generate_demo_race()
    return db


@pytest.fixture()
def conn(demo_db):
    from app import db as dbm
    c = dbm.connect()
    yield c
    c.close()
