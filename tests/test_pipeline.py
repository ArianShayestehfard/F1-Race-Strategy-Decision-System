import numpy as np
import pandas as pd
import pytest

from f1_strategy import DEFAULT_MODEL, lap_time, load_model, save_model
from f1_strategy.calibrate import calibrate
from f1_strategy.data import clean_laps, estimate_pit_loss


def _synthetic(model=DEFAULT_MODEL, seed=0, n_drivers=14, total=57):
    rng = np.random.default_rng(seed)
    rows = []
    for d in range(n_drivers):
        pace = rng.normal(0, 0.4)
        cuts = sorted(rng.choice(range(10, 48), size=rng.integers(1, 3), replace=False))
        bounds = [0, *cuts, total]
        comps = rng.choice(model.names, size=len(bounds) - 1)
        for (a, b), c in zip(zip(bounds, bounds[1:]), comps):
            for age in range(b - a):
                lap = a + age + 1
                t = lap_time(c, age, lap, model) + pace + rng.normal(0, 0.12)
                rows.append((f"D{d:02d}", lap, c, age + 1, t))
    return pd.DataFrame(rows, columns=["Driver", "LapNumber", "Compound", "TyreLife", "LapTimeSeconds"])


def test_calibration_recovers_parameters():
    model, metrics = calibrate(_synthetic())
    assert metrics["rmse"] < 0.2
    assert model.fuel_gain == pytest.approx(DEFAULT_MODEL.fuel_gain, abs=0.01)
    for name in ("SOFT", "MEDIUM"):
        fit, true = model.compound(name), DEFAULT_MODEL.compound(name)
        assert fit.wear == pytest.approx(true.wear, abs=0.02)


def test_calibration_needs_two_compounds():
    df = _synthetic()
    with pytest.raises(ValueError):
        calibrate(df[df["Compound"] == "SOFT"])


def test_model_roundtrip(tmp_path):
    save_model(DEFAULT_MODEL, tmp_path / "m.json", meta={"year": 2024})
    assert load_model(tmp_path / "m.json") == DEFAULT_MODEL


def _raw(rows):
    cols = ["Driver", "LapNumber", "Compound", "TyreLife", "LapTimeSeconds",
            "PitInTime", "PitOutTime", "TrackStatus"]
    return pd.DataFrame(rows, columns=cols)


def test_clean_laps_filters_unrepresentative_laps():
    n = np.nan
    rows = [("A", 1, "SOFT", 1, 100, n, n, "1"),      # lap 1
            ("A", 2, "SOFT", 2, 90, n, n, "1"),       # keep
            ("A", 3, "SOFT", 3, 90, n, n, "4"),       # safety car
            ("A", 4, "SOFT", 4, 95, 1.0, n, "1"),     # in-lap
            ("A", 5, "INTERMEDIATE", 1, 90, n, n, "1"),  # wet tyre
            ("A", 6, "SOFT", 5, 90.5, n, n, "1")]     # keep
    out = clean_laps(_raw(rows))
    assert out["LapNumber"].tolist() == [2, 6]


def test_estimate_pit_loss():
    n = np.nan
    rows = [("A", i, "SOFT", i, 90.0, n, n, "1") for i in range(2, 8)]
    rows += [("A", 8, "SOFT", 8, 100.0, 1.0, n, "1"), ("A", 9, "HARD", 1, 102.0, n, 1.0, "1")]
    assert estimate_pit_loss(_raw(rows)) == pytest.approx(22.0)
