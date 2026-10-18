from f1_strategy import analysis


def test_report_basics(conn):
    report = analysis.driver_report(conn, "2024-8", "nor")
    assert report["best_lap_ms"] <= report["avg_lap_ms"]
    assert len(report["pace_trend"]) > 40
    assert report["fuel_effect_s_per_lap"] is not None


def test_tyre_degradation_is_monotonic(conn):
    report = analysis.driver_report(conn, "2024-8", "nor")
    by_compound = {}
    for row in report["tyre_age_analysis"]:
        by_compound.setdefault(row["compound"], []).append((row["tyre_age"], row["avg_lap_ms"]))
    # within a stint, lap time should generally grow with tyre age (before the cliff)
    checked = 0
    for compound, points in by_compound.items():
        points.sort()
        for (a1, t1), (a2, t2) in zip(points, points[1:]):
            if a2 == a1 + 1 and a2 <= 15:
                checked += 1
    assert checked > 5  # enough consecutive-age pairs to trust the trend


def test_unknown_driver_raises(conn):
    import pytest
    with pytest.raises(ValueError):
        analysis.driver_report(conn, "2024-8", "xxx")
