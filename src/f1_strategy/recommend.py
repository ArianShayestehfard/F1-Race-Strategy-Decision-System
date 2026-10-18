"""Phase 9 — Strategy Recommendation Engine.

Given the current race state, tries several pit windows x compounds,
simulates every scenario and recommends the best one with a confidence
derived from the margin over the runner-up.

Weather-aware: the candidate list widens to INTERMEDIATE (any rain ahead)
and WET (heavy rain) when the race's rain profile calls for it, and the
rain-onset lap is surfaced so the UI can say "pit before the rain".
"""
from __future__ import annotations

from .db import connect
from .simulator import RAIN_THRESHOLD_MM, simulate_race

PIT_OFFSETS = [0, 2, 3, 5, 8, 12]
DRY_COMPOUNDS = ["SOFT", "MEDIUM", "HARD"]
HEAVY_RAIN_MM = 2.5          # beyond this, WET becomes a sensible candidate


def _rain_by_lap(race_id: str, total_laps: int) -> dict[int, float]:
    conn = connect()
    try:
        rain = {lap: 0.0 for lap in range(1, total_laps + 1)}
        for r in conn.execute(
            "SELECT lap_number, rainfall_mm FROM weather WHERE race_id=?", (race_id,)
        ):
            if r["lap_number"] is not None and 1 <= r["lap_number"] <= total_laps:
                rain[r["lap_number"]] = float(r["rainfall_mm"] or 0.0)
        return rain
    finally:
        conn.close()


def _total_laps(race_id: str) -> int:
    conn = connect()
    try:
        return int(conn.execute(
            "SELECT total_laps FROM races WHERE race_id=?", (race_id,)
        ).fetchone()["total_laps"] or 57)
    finally:
        conn.close()


def _candidate_compounds(rain_at_pit: float, rain_max_in_stint: float) -> list[str]:
    """Wet compounds enter the candidate list only when they can pay off."""
    candidates = list(DRY_COMPOUNDS)
    if rain_at_pit > RAIN_THRESHOLD_MM or rain_max_in_stint > RAIN_THRESHOLD_MM:
        candidates.append("INTERMEDIATE")
    if max(rain_at_pit, rain_max_in_stint) >= HEAVY_RAIN_MM:
        candidates.append("WET")
    return candidates


def recommend_strategy(race_id: str, driver_id: str, current_lap: int,
                       current_compound: str = "MEDIUM",
                       current_tyre_age: int = 1, seed: int = 42) -> dict:
    total_laps = _total_laps(race_id)
    rain = _rain_by_lap(race_id, total_laps)

    # first wet lap still ahead of us (proactive: pit BEFORE it)
    rain_onset_lap = next(
        (lap for lap in range(current_lap, total_laps + 1) if rain[lap] > RAIN_THRESHOLD_MM),
        None)

    windows = sorted({min(current_lap + off, total_laps - 1) for off in PIT_OFFSETS})
    scenarios: list[dict] = []
    for pit_lap in windows:
        # rain exposure of the stint that starts at this pit lap
        stint_end = min(total_laps, pit_lap + 35)
        rain_at_pit = rain[pit_lap]
        rain_max_in_stint = max(rain[lap] for lap in range(pit_lap, stint_end + 1))

        for comp in _candidate_compounds(rain_at_pit, rain_max_in_stint):
            if pit_lap == min(windows) and comp == current_compound and current_tyre_age < 5:
                # switching compound on a nearly-new tyre makes no sense
                continue
            pits = [pit_lap]
            remaining = total_laps - pit_lap
            if remaining > 35:  # too long for one tyre: plan a second stop
                pits.append(min(pit_lap + 28, total_laps - 2))
            res = simulate_race(race_id, driver_id, start_compound=current_compound,
                                pit_laps=pits, pit_compounds={pit_lap: comp}, seed=seed)
            scenarios.append({
                "pit_lap": pit_lap,
                "compound": comp,
                "finish_position": res["finish_position"],
                "total_time_ms": res["total_time_ms"],
            })

    if not scenarios:
        raise ValueError("no feasible scenario for the given race state")

    scenarios.sort(key=lambda s: (s["finish_position"], s["total_time_ms"]))
    best, second = scenarios[0], scenarios[1] if len(scenarios) > 1 else scenarios[0]
    margin_ms = max(second["total_time_ms"] - best["total_time_ms"], 0)
    confidence = min(0.95, max(0.5, 0.5 + margin_ms / 20000 * 0.45))

    return {
        "race_id": race_id,
        "driver_id": driver_id,
        "recommended_pit_lap": best["pit_lap"],
        "recommended_compound": best["compound"],
        "expected_finish_position": best["finish_position"],
        "confidence": round(confidence, 2),
        "rain_ahead": rain_onset_lap is not None,
        "rain_onset_lap": rain_onset_lap,
        "scenarios": scenarios[:10],
    }


def duel_strategy(race_id: str, driver_id: str, rival_id: str,
                  current_lap: int, current_compound: str = "MEDIUM",
                  seed: int = 42) -> dict:
    """The classic pit-cycle question: undercut or overcut the car ahead?

    The rival runs his historical plan (actual pit laps from the data).
    We try pitting now / +2 / +5 laps, each with the best compound found
    by a quick probe (wet compounds included when rain calls for them),
    and rank by our finishing position relative to the rival.
    """
    conn = connect()
    try:
        rival_pits = [r["lap_number"] for r in conn.execute(
            "SELECT lap_number FROM pit_stops WHERE race_id=? AND driver_id=?"
            " ORDER BY lap_number", (race_id, rival_id))]
    finally:
        conn.close()
    total_laps = _total_laps(race_id)
    rain = _rain_by_lap(race_id, total_laps)

    windows = sorted({min(current_lap + off, total_laps - 2) for off in (0, 2, 5)})

    def label_for(offset: int) -> str:
        if offset == 0:
            return "UNDERCUT — pit now, attack on fresh tyres"
        if offset == 2:
            return "SHORT EXTEND — stay out 2 laps"
        return "OVERCUT — stay out 5 laps"

    scenarios = []
    for offset, pit_lap in zip((0, 2, 5), windows):
        stint_end = min(total_laps, pit_lap + 35)
        candidates = _candidate_compounds(
            rain[pit_lap], max(rain[lap] for lap in range(pit_lap, stint_end + 1)))
        best_for_window = None
        for comp in candidates:
            res = simulate_race(race_id, driver_id, start_compound=current_compound,
                                pit_laps=[pit_lap], pit_compounds={pit_lap: comp}, seed=seed)
            if best_for_window is None or res["finish_position"] < best_for_window["finish_position"]:
                best_for_window = {**res, "compound": comp}
        rival = simulate_race(race_id, rival_id, pit_laps=rival_pits, seed=seed)
        scenarios.append({
            "option": label_for(offset),
            "pit_lap": pit_lap,
            "compound": best_for_window["compound"],
            "my_finish_position": best_for_window["finish_position"],
            "rival_finish_position": rival["finish_position"],
            "beats_rival": best_for_window["finish_position"] < rival["finish_position"],
        })

    scenarios.sort(key=lambda s: (s["my_finish_position"], s["rival_finish_position"]))
    best = scenarios[0]
    return {
        "race_id": race_id,
        "driver_id": driver_id,
        "rival_id": rival_id,
        "current_lap": current_lap,
        "rival_plan_pit_laps": rival_pits,
        "best_option": best["option"],
        "best_pit_lap": best["pit_lap"],
        "best_compound": best["compound"],
        "beats_rival": best["beats_rival"],
        "scenarios": scenarios,
    }
