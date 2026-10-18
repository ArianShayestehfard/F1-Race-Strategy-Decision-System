from app.recommend import duel_strategy


def test_duel_contract(demo_db):
    res = duel_strategy("2024-8", "nor", "lec", current_lap=18)
    assert res["driver_id"] == "nor" and res["rival_id"] == "lec"
    assert res["best_option"]
    assert res["best_pit_lap"] >= 18
    assert res["best_compound"] in ("SOFT", "MEDIUM", "HARD")
    assert isinstance(res["beats_rival"], bool)
    assert len(res["scenarios"]) == 3
    labels = {s["option"] for s in res["scenarios"]}
    assert any("UNDERCUT" in l for l in labels)
    assert any("OVERCUT" in l for l in labels)
    # best scenario is ranked first
    best = res["scenarios"][0]
    assert best["my_finish_position"] == res["scenarios"][0]["my_finish_position"]
    positions = [s["my_finish_position"] for s in res["scenarios"]]
    assert positions == sorted(positions)
