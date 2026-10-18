"""Smoke tests of the FastAPI app over the demo database."""
from fastapi.testclient import TestClient

from app.main import app


def test_health(demo_db):
    client = TestClient(app)
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json()["status"] == "ok"
    assert r.json()["races"] >= 1


def test_analyze_endpoint(demo_db):
    client = TestClient(app)
    r = client.get("/analyze/2024-8/nor")
    assert r.status_code == 200
    assert r.json()["driver_id"] == "nor"


def test_simulate_and_recommend_endpoints(demo_db):
    client = TestClient(app)
    r = client.post("/simulate", json={"race_id": "2024-8", "driver_id": "nor",
                                       "start_compound": "MEDIUM", "pit_laps": [20, 41]})
    assert r.status_code == 200
    body = r.json()
    assert 1 <= body["finish_position"] <= 20

    r = client.post("/strategy/recommend",
                    json={"race_id": "2024-8", "driver_id": "nor", "current_lap": 18})
    assert r.status_code == 200
    assert "recommended_pit_lap" in r.json()
