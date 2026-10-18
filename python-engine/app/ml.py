"""Phase 8 — ML lap-time prediction.

Models: LinearRegression, RandomForestRegressor, GradientBoostingRegressor.
Features per lap: lap_number, tyre_age, compound (one-hot), fuel_kg, track_status.
Trained models are cached under python-engine/artifacts/.
"""
from __future__ import annotations

import os
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import GradientBoostingRegressor, RandomForestRegressor
from sklearn.linear_model import LinearRegression
from sklearn.metrics import mean_absolute_error
from sklearn.model_selection import train_test_split

from .db import connect
from .ingest import COMPOUND_MODEL, FUEL_MS_PER_KG, FUEL_START_KG_PER_LAP

COMPOUNDS = ["SOFT", "MEDIUM", "HARD", "INTERMEDIATE", "WET"]
MODELS = {
    "linear_regression": LinearRegression,
    "random_forest": RandomForestRegressor,
    "gradient_boosting": GradientBoostingRegressor,
}
ARTIFACTS = Path(__file__).resolve().parent.parent / "artifacts"


def _features(conn, race_id: str, driver_id: str) -> pd.DataFrame:
    rows = conn.execute(
        "SELECT lap_number, lap_time_ms, tyre_compound, tyre_age, fuel_kg, track_status, is_pit_lap"
        " FROM laps WHERE race_id=? AND driver_id=? AND lap_time_ms IS NOT NULL",
        (race_id, driver_id),
    ).fetchall()
    if len(rows) < 10:
        raise ValueError(f"not enough laps to train for {race_id}/{driver_id}")
    df = pd.DataFrame([dict(r) for r in rows])
    df = df[df["is_pit_lap"] == 0].drop(columns=["is_pit_lap"])
    for c in COMPOUNDS:
        df[f"comp_{c}"] = (df["tyre_compound"] == c).astype(int)
    df["track_status"] = pd.to_numeric(df["track_status"], errors="coerce").fillna(1).astype(int)
    df["fuel_kg"] = df["fuel_kg"].fillna(
        FUEL_START_KG_PER_LAP * (df["lap_number"].max() - df["lap_number"] + 1))
    return df


def train(race_id: str, driver_id: str, model_name: str = "gradient_boosting"):
    if model_name not in MODELS:
        raise ValueError(f"unknown model {model_name}; choose from {list(MODELS)}")
    conn = connect()
    try:
        df = _features(conn, race_id, driver_id)
    finally:
        conn.close()
    X = df.drop(columns=["lap_time_ms", "tyre_compound"])
    y = df["lap_time_ms"]
    X_tr, X_te, y_tr, y_te = train_test_split(X, y, test_size=0.2, random_state=42)
    reg = MODELS[model_name](**({"random_state": 42} if model_name != "linear_regression" else {}))
    reg.fit(X_tr, y_tr)
    mae = float(mean_absolute_error(y_te, reg.predict(X_te)))

    ARTIFACTS.mkdir(exist_ok=True)
    joblib.dump({"model": reg, "columns": list(X.columns)},
                ARTIFACTS / f"{race_id}_{driver_id}_{model_name}.joblib")

    # degradation slope: predicted lap time drift per lap over a fresh stint
    last = df.iloc[-1]
    future = _future_frame(df, int(last["lap_number"]), str(last["tyre_compound"]), 10)
    preds = reg.predict(future.drop(columns=["lap_time_ms", "tyre_compound"], errors="ignore"))
    slope = float(np.polyfit(future["lap_number"], preds, 1)[0]) if len(future) > 2 else 0.0
    return reg, X.columns.tolist(), mae, slope, future, df


def _future_frame(df: pd.DataFrame, last_lap: int, compound: str, n: int) -> pd.DataFrame:
    rows = []
    for i in range(1, n + 1):
        lap = last_lap + i
        r = {"lap_number": lap, "tyre_age": i, "fuel_kg": float(df["fuel_kg"].iloc[-1]) - FUEL_START_KG_PER_LAP * i,
             "track_status": int(df["track_status"].iloc[-1])}
        for c in COMPOUNDS:
            r[f"comp_{c}"] = 1 if c == compound else 0
        rows.append(r)
    return pd.DataFrame(rows)


def predict(race_id: str, driver_id: str, model_name: str = "gradient_boosting") -> dict:
    reg, cols, mae, slope, future, df = train(race_id, driver_id, model_name)
    X = future[cols]
    preds = reg.predict(X)
    return {
        "race_id": race_id,
        "driver_id": driver_id,
        "model": model_name,
        "mae_ms": int(round(mae)),
        "predictions": [
            {"lap": int(future["lap_number"].iloc[i]), "predicted_lap_time_ms": int(round(preds[i]))}
            for i in range(len(preds))
        ],
        "tyre_degradation_ms_per_lap": round(slope, 1),
    }
