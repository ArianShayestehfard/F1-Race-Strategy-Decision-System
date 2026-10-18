"""Bridge between the SQLite race store and the calibrated lap-time model.

Loads a race's laps from the database in the FastF1-style frame
`clean_laps`/`calibrate` expect, calibrates a per-race model and keeps it
under `models/<race_id>.json` so the strategy search can use it. Falls back
to DEFAULT_MODEL whenever a race has not enough clean dry-tyre laps.
"""
from __future__ import annotations

import numpy as np
import pandas as pd

from .calibrate import calibrate
from .data import clean_laps, estimate_pit_loss
from .db import REPO_ROOT, connect
from .strategy import DEFAULT_MODEL, Model, best_by_stops, lap_time, load_model, save_model

MODEL_DIR = REPO_ROOT / "models"
DEGRADATION_AGES = 40  # curves shown for tyre ages 0..40
FUEL_REF_LAP = 20      # degradation curves drawn at lap-20 fuel load


def laps_dataframe(race_id: str) -> pd.DataFrame:
    """FastF1-style lap frame straight from the SQLite store."""
    conn = connect()
    try:
        rows = conn.execute(
            "SELECT driver_id, lap_number, lap_time_ms, tyre_compound, tyre_age,"
            " track_status, is_pit_lap FROM laps WHERE race_id=? ORDER BY driver_id, lap_number",
            (race_id,),
        ).fetchall()
    finally:
        conn.close()
    if not rows:
        raise ValueError(f"unknown race {race_id}")
    df = pd.DataFrame([dict(r) for r in rows])
    df = df.rename(columns={
        "driver_id": "Driver", "lap_number": "LapNumber",
        "tyre_compound": "Compound", "tyre_age": "TyreLife",
    })
    df["LapTimeSeconds"] = df["lap_time_ms"] / 1000.0
    df["TrackStatus"] = df["track_status"].fillna("0").astype(str)
    df["IsAccurate"] = True
    # pit laps must be excluded: non-null PitInTime/PitOutTime marks them
    pit = df["is_pit_lap"] == 1
    df["PitInTime"] = np.where(pit, object(), np.nan)
    df["PitOutTime"] = np.where(pit, object(), np.nan)
    return df


def calibrate_race(race_id: str) -> dict:
    """Calibrate a model for this race and persist it under models/."""
    raw = laps_dataframe(race_id)
    model, metrics = calibrate(clean_laps(raw), pit_loss=estimate_pit_loss(raw))
    MODEL_DIR.mkdir(exist_ok=True)
    path = MODEL_DIR / f"{race_id}.json"
    save_model(model, path, meta={"race_id": race_id, **metrics})
    return {"race_id": race_id, "model_path": str(path), "model": model.to_dict(),
            **metrics}


def model_for_race(race_id: str) -> tuple[Model, dict | None]:
    """Saved per-race model if present, else the built-in default."""
    path = MODEL_DIR / f"{race_id}.json"
    if path.exists():
        model = load_model(path)
        meta = None
        try:
            import json
            meta = json.loads(path.read_text(encoding="utf-8")).get("meta")
        except Exception:
            pass
        return model, meta
    return DEFAULT_MODEL, None


def search_plans(race_id: str) -> dict:
    """Best 1/2/3-stop plans + degradation curves, in the dashboard schema."""
    conn = connect()
    try:
        row = conn.execute(
            "SELECT name, total_laps FROM races WHERE race_id=?", (race_id,)).fetchone()
        if row is None:
            raise ValueError(f"unknown race {race_id}")
        name, total_laps = row["name"], int(row["total_laps"] or 57)
    finally:
        conn.close()

    model, meta = model_for_race(race_id)
    best = best_by_stops(total_laps, model=model)
    fastest = min(t for t, _ in best.values())
    strategies = [
        {
            "stops": stops,
            "time": round(t, 3),
            "delta": round(t - fastest, 3),
            "stints": [{"compound": c, "laps": n} for c, n in stints],
        }
        for stops, (t, stints) in sorted(best.items())
    ]
    degradation = {
        c: [round(lap_time(c, a, FUEL_REF_LAP, model), 3)
            for a in range(DEGRADATION_AGES + 1)]
        for c in model.names
    }
    return {
        "race_id": race_id,
        "race": name,
        "laps": total_laps,
        "model": "calibrated" if meta else "default",
        "model_meta": meta,
        "strategies": strategies,
        "degradation": degradation,
    }
