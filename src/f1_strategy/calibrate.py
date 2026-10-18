"""Fit a `Model` from cleaned race laps.

Least squares on: lap_time = driver offset + compound intercept
                  + wear_c * age + cliff_c * max(age - cliff_age_c, 0) - fuel * lap
Cliff ages are found by grid search (coordinate descent over compounds).
"""
import numpy as np
import pandas as pd

from .strategy import DEFAULT_MODEL, Compound, Model

MIN_LAPS = 30  # minimum laps on a compound to fit it


def _fit(df, names, cliffs, drivers):
    age = df["age"].to_numpy(float)
    cols = []
    for n in names:
        m = (df["Compound"] == n).to_numpy(float)
        cols += [m, m * age, m * np.maximum(age - cliffs[n], 0)]
    cols.append(df["LapNumber"].to_numpy(float))
    for d in drivers[1:]:
        cols.append((df["Driver"] == d).to_numpy(float))
    X = np.column_stack(cols)
    y = df["LapTimeSeconds"].to_numpy(float)
    coef, *_ = np.linalg.lstsq(X, y, rcond=None)
    resid = y - X @ coef
    return coef, float(resid @ resid)


def calibrate(laps: pd.DataFrame, pit_loss: float | None = None,
              base_model: Model = DEFAULT_MODEL):
    """Return (Model, metrics). Compounds with too little data keep defaults."""
    df = laps.copy()
    df["age"] = df["TyreLife"] - 1
    counts = df["Compound"].value_counts()
    names = [n for n in base_model.names if counts.get(n, 0) >= MIN_LAPS]
    if len(names) < 2:
        raise ValueError(f"Need at least 2 compounds with >= {MIN_LAPS} laps, got {dict(counts)}")
    df = df[df["Compound"].isin(names)]
    drivers = sorted(df["Driver"].unique())
    cliffs = {n: base_model.compound(n).cliff_age for n in names}

    for _ in range(2):
        for n in names:
            max_age = int(df.loc[df["Compound"] == n, "age"].max())
            best_sse, best_cliff = None, cliffs[n]
            for cand in range(6, max(7, min(45, max_age - 2))):
                _, sse = _fit(df, names, {**cliffs, n: cand}, drivers)
                if best_sse is None or sse < best_sse:
                    best_sse, best_cliff = sse, cand
            cliffs[n] = best_cliff

    coef, sse = _fit(df, names, cliffs, drivers)
    intercepts = {n: coef[3 * i] for i, n in enumerate(names)}
    base = min(intercepts.values())
    fuel_gain = max(-coef[3 * len(names)], 0.0)

    fitted = {}
    for i, n in enumerate(names):
        fitted[n] = Compound(
            offset=round(float(intercepts[n] - base), 3),
            wear=round(float(max(coef[3 * i + 1], 0.0)), 4),
            cliff_age=int(cliffs[n]),
            cliff_penalty=round(float(max(coef[3 * i + 2], 0.0)), 4),
        )
    compounds = tuple((n, fitted.get(n, base_model.compound(n))) for n in base_model.names)
    model = Model(round(float(base), 3), round(float(fuel_gain), 4),
                  base_model.pit_loss if pit_loss is None else round(pit_loss, 2), compounds)
    metrics = {"n_laps": int(len(df)), "rmse": round(float(np.sqrt(sse / len(df))), 3),
               "laps_per_compound": {n: int(counts[n]) for n in names}}
    return model, metrics
