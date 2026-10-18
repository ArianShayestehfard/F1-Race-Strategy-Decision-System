import pytest

from f1_strategy import best_by_stops, lap_time, race_time


def test_tyres_get_slower_with_age():
    assert lap_time("MEDIUM", 20, 30) > lap_time("MEDIUM", 2, 12)


def test_race_time_counts_pit_loss():
    one = race_time([("MEDIUM", 20), ("HARD", 20)])
    split = race_time([("MEDIUM", 20), ("HARD", 10), ("HARD", 10)])
    assert split > one - 5  # extra stop must cost roughly a pit loss


def test_stint_lengths_validated():
    with pytest.raises(ValueError):
        race_time([("SOFT", 10), ("HARD", 10)], total_laps=57)


def test_best_strategies_use_two_compounds():
    for _, stints in best_by_stops(40).values():
        assert len({c for c, _ in stints}) >= 2
        assert sum(n for _, n in stints) == 40
