"""Tests for the SQLite -> calibrated-model bridge and the search endpoint."""
from fastapi.testclient import TestClient

from f1_strategy import racecalib


def test_search_plans_schema(demo_db):
    data = racecalib.search_plans("2024-8")
    assert data["race_id"] == "2024-8"
    assert data["laps"] == 57
    stops = [s["stops"] for s in data["strategies"]]
    assert stops == sorted(stops) and stops[0] == 1
    for s in data["strategies"]:
        assert sum(x["laps"] for x in s["stints"]) == 57
        assert len({x["compound"] for x in s["stints"]}) >= 2
    fastest = min(s["time"] for s in data["strategies"])
    assert data["strategies"][0]["delta"] == 0.0
    assert all(s["time"] >= fastest for s in data["strategies"])
    assert set(data["degradation"]) == {"SOFT", "MEDIUM", "HARD"}
    assert all(len(v) == 41 for v in data["degradation"].values())


def test_calibrate_race_persists_model(demo_db, tmp_path, monkeypatch):
    monkeypatch.setattr(racecalib, "MODEL_DIR", tmp_path)
    out = racecalib.calibrate_race("2024-8")
    assert out["n_laps"] > 0
    assert (tmp_path / "2024-8.json").exists()
    model, meta = racecalib.model_for_race("2024-8")
    assert meta is not None and meta["race_id"] == "2024-8"
    data = racecalib.search_plans("2024-8")
    assert data["model"] == "calibrated"
    assert data["model_meta"]["race_id"] == "2024-8"


def test_search_endpoint(demo_db):
    from f1_strategy.api.main import app
    client = TestClient(app)
    r = client.post("/strategy/search", json={"race_id": "2024-8"})
    assert r.status_code == 200
    assert r.json()["race"] == "Demo Grand Prix"
    r = client.post("/strategy/search", json={"race_id": "9999-9"})
    assert r.status_code == 404


def test_races_and_drivers_endpoints(demo_db):
    from f1_strategy.api.main import app
    client = TestClient(app)
    races = client.get("/races").json()
    assert len(races) >= 1
    drivers = client.get("/races/2024-8/drivers").json()
    codes = {d["driver_id"] for d in drivers}
    assert {"ver", "nor", "ham"} <= codes
