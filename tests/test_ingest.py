def test_demo_race_ingested(conn):
    race = conn.execute("SELECT * FROM races").fetchone()
    assert race["race_id"] == "2024-8"
    assert race["total_laps"] == 57
    drivers = conn.execute("SELECT COUNT(*) c FROM drivers").fetchone()["c"]
    assert drivers == 20
    laps = conn.execute("SELECT COUNT(*) c FROM laps").fetchone()["c"]
    assert laps == 20 * 57
    assert conn.execute("SELECT COUNT(*) c FROM pit_stops").fetchone()["c"] >= 20
    assert conn.execute("SELECT COUNT(*) c FROM weather").fetchone()["c"] == 57


def test_laps_have_required_fields(conn):
    rows = conn.execute(
        "SELECT lap_time_ms, tyre_compound, tyre_age, fuel_kg FROM laps"
        " WHERE is_pit_lap = 0 LIMIT 50").fetchall()
    for r in rows:
        assert r["lap_time_ms"] and r["lap_time_ms"] > 60_000
        assert r["tyre_compound"] in ("SOFT", "MEDIUM", "HARD", "INTERMEDIATE", "WET")
        assert r["tyre_age"] >= 1
        assert r["fuel_kg"] > 0


def test_rain_registered(conn):
    wet = conn.execute(
        "SELECT COUNT(*) c FROM weather WHERE rainfall_mm > 0").fetchone()["c"]
    assert wet > 0
