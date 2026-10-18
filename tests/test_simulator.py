from f1_strategy.simulator import simulate_race


def test_deterministic_and_valid(demo_db):
    a = simulate_race("2024-8", "nor", start_compound="MEDIUM", pit_laps=[20, 41])
    b = simulate_race("2024-8", "nor", start_compound="MEDIUM", pit_laps=[20, 41])
    assert a["total_time_ms"] == b["total_time_ms"]
    assert 1 <= a["finish_position"] <= 20
    assert len(a["timeline"]) == 57
    assert a["timeline"][0]["lap"] == 1


def test_more_pits_are_slower_on_same_compound(demo_db):
    one = simulate_race("2024-8", "nor", pit_laps=[28])
    two = simulate_race("2024-8", "nor", pit_laps=[18, 38])
    assert two["total_time_ms"] > one["total_time_ms"]


def test_pit_lap_shows_in_timeline(demo_db):
    res = simulate_race("2024-8", "nor", pit_laps=[20, 41])
    pits = [t for t in res["timeline"] if t["pit"]]
    assert [p["lap"] for p in pits] == [20, 41]
    # tyre age resets after a pit stop
    after_pit = next(t for t in res["timeline"] if t["lap"] == 21)
    assert after_pit["tyre_age"] == 1


def test_unknown_race_or_driver(demo_db):
    import pytest
    with pytest.raises(ValueError):
        simulate_race("1999-1", "nor")
    with pytest.raises(ValueError):
        simulate_race("2024-8", "xxx")
