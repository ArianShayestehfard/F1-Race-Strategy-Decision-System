import os
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from f1_strategy import ingest  # noqa: E402


@pytest.fixture(scope="session")
def demo_db(tmp_path_factory):
    """A SQLite file with one synthetic demo race, shared by all tests."""
    db = tmp_path_factory.mktemp("db") / "test.db"
    os.environ["F1_DB_PATH"] = str(db)
    ingest.generate_demo_race()
    return db


@pytest.fixture()
def conn(demo_db):
    from f1_strategy import db as dbm
    c = dbm.connect()
    yield c
    c.close()
