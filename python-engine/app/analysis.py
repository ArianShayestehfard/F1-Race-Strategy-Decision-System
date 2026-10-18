"""Phase 6 — statistical analysis (no AI): lap times, pace trend,
fuel estimation, tyre age, gap analysis."""
from __future__ import annotations

import numpy as np

from .db import connect


def driver_report(conn, race_id: str, driver_id: str) -> dict:
    rows = conn.execute(
        "SELECT lap_number, lap_time_ms, tyre_compound, tyre_age, fuel_kg, is_pit_lap"
        " FROM laps WHERE race_id=? AND driver_id=? ORDER BY lap_number",
        (race_id, driver_id),
    ).fetchall()

    valid = [r for r in rows if r["lap_time_ms"] is not None]
    if not valid:
        raise ValueError(f"no valid laps for {race_id}/{driver_id}")

    times = np.array([r["lap_time_ms"] for r in valid], dtype=float)
    best = int(times.min())
    avg = int(round(times.mean()))

    pace_trend = [
        {"lap": r["lap_number"], "lap_time_ms": r["lap_time_ms"]} for r in valid
    ]

    # tyre age: average lap time per (compound, tyre_age)
    agg: dict[tuple, list[int]] = {}
    for r in valid:
        if r["tyre_compound"] and r["tyre_age"] is not None and not r["is_pit_lap"]:
            agg.setdefault((r["tyre_compound"], r["tyre_age"]), []).append(r["lap_time_ms"])
    tyre_age_analysis = [
        {"compound": c, "tyre_age": a, "avg_lap_ms": int(round(np.mean(v))), "samples": len(v)}
        for (c, a), v in sorted(agg.items())
    ]

    # fuel effect: slope of lap time vs fuel load (ms per kg) -> seconds/lap/kg
    fuel_rows = [r for r in valid if r["fuel_kg"] is not None and not r["is_pit_lap"]]
    fuel_effect_s_per_lap = None
    if len(fuel_rows) > 5:
        fuel = np.array([r["fuel_kg"] for r in fuel_rows], dtype=float)
        ft = np.array([r["lap_time_ms"] for r in fuel_rows], dtype=float)
        slope_ms_per_kg = float(np.polyfit(fuel, ft, 1)[0])
        fuel_effect_s_per_lap = round(slope_ms_per_kg / 1000.0, 4)

    # gap to race leader per lap (cumulative race time)
    gap_analysis = _gap_to_leader(conn, race_id, driver_id,
                                  [r["lap_number"] for r in valid])

    return {
        "race_id": race_id,
        "driver_id": driver_id,
        "best_lap_ms": best,
        "avg_lap_ms": avg,
        "pace_trend": pace_trend,
        "tyre_age_analysis": tyre_age_analysis,
        "fuel_effect_s_per_lap": fuel_effect_s_per_lap,
        "gap_analysis": gap_analysis,
        "stints": _stints(valid),
        "pit_stops": _actual_pit_stops(conn, race_id, driver_id),
    }


def _stints(valid: list) -> list[dict]:
    """Consecutive same-compound stretches of the race -> stint list."""
    stints: list[dict] = []
    for r in valid:
        comp = r["tyre_compound"]
        lap = r["lap_number"]
        if not comp:
            continue
        if stints and stints[-1]["compound"] == comp and lap == stints[-1]["end_lap"] + 1:
            stints[-1]["end_lap"] = lap
        else:
            stints.append({"compound": comp, "start_lap": lap, "end_lap": lap})
    return stints


def _actual_pit_stops(conn, race_id: str, driver_id: str) -> list[dict]:
    rows = conn.execute(
        "SELECT lap_number, duration_ms FROM pit_stops WHERE race_id=? AND driver_id=?"
        " ORDER BY lap_number",
        (race_id, driver_id),
    ).fetchall()
    return [{"lap": r["lap_number"], "duration_ms": r["duration_ms"]} for r in rows]


def _gap_to_leader(conn, race_id: str, driver_id: str, laps: list[int]) -> list[dict]:
    all_laps = conn.execute(
        "SELECT driver_id, lap_number, lap_time_ms FROM laps"
        " WHERE race_id=? AND lap_time_ms IS NOT NULL ORDER BY lap_number",
        (race_id,),
    ).fetchall()
    cumulative: dict[str, int] = {}
    by_lap: dict[int, dict[str, int]] = {}
    for r in all_laps:
        cumulative[r["driver_id"]] = cumulative.get(r["driver_id"], 0) + r["lap_time_ms"]
        by_lap.setdefault(r["lap_number"], {})[r["driver_id"]] = cumulative[r["driver_id"]]
    out = []
    for lap in laps:
        if lap not in by_lap or driver_id not in by_lap[lap]:
            continue
        leader = min(by_lap[lap].values())
        out.append({"lap": lap, "gap_to_leader_ms": by_lap[lap][driver_id] - leader})
    return out
