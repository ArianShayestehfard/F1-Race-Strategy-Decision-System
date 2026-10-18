from app.recommend import recommend_strategy


def test_recommend_contract(demo_db):
    res = recommend_strategy("2024-8", "nor", current_lap=18)
    assert 18 <= res["recommended_pit_lap"] <= 30
    assert res["recommended_compound"] in ("SOFT", "MEDIUM", "HARD")
    assert 1 <= res["expected_finish_position"] <= 20
    assert 0.5 <= res["confidence"] <= 0.95
    assert len(res["scenarios"]) >= 2
    # scenarios sorted best-first
    pos = [s["finish_position"] for s in res["scenarios"]]
    assert pos == sorted(pos)


def test_recommend_late_race_single_stop(demo_db):
    res = recommend_strategy("2024-8", "nor", current_lap=35)
    assert res["recommended_pit_lap"] >= 35
