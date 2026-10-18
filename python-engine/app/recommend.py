"""Phase 9 — Strategy Recommendation Engine.

Given the current race state, tries several pit windows x compounds,
simulates every scenario and recommends the best one with a confidence
derived from the margin over the runner-up.
"""
from __future__ import annotations

from .simulator import simulate_race

PIT_OFFSETS = [0, 2, 3, 5, 8, 12]
CANDIDATE_COMPOUNDS = ["SOFT", "MEDIUM", "HARD"]


def recommend_strategy(race_id: str, driver_id: str, current_lap: int,
                       current_compound: str = "MEDIUM",
                       current_tyre_age: int = 1, seed: int = 42) -> dict:
    conn_stub = None  # simulator opens its own connection
    from .db import connect
    conn = connect()
    try:
        total_laps = int(conn.execute(
            "SELECT total_laps FROM races WHERE race_id=?", (race_id,)
        ).fetchone()["total_laps"] or 57)
    finally:
        conn.close()

    windows = sorted({min(current_lap + off, total_laps - 1) for off in PIT_OFFSETS})
    scenarios: list[dict] = []
    for pit_lap in windows:
        for comp in CANDIDATE_COMPOUNDS:
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
        "scenarios": scenarios[:10],
    }


def duel_strategy(race_id: str, driver_id: str, rival_id: str,
                  current_lap: int, current_compound: str = "MEDIUM",
                  seed: int = 42) -> dict:
    """The classic pit-cycle question: undercut or overcut the car ahead?

    The rival runs his historical plan (actual pit laps from the data).
    We try pitting now / +2 / +5 laps, each with the best compound found
    by a quick 3-compound probe, and rank by our finishing position
    relative to the rival.
    """
    from .db import connect
    from .simulator import simulate_race

    conn = connect()
    try:
        total_laps = int(conn.execute(
            "SELECT total_laps FROM races WHERE race_id=?", (race_id,)
        ).fetchone()["total_laps"] or 57)
        rival_pits = [r["lap_number"] for r in conn.execute(
            "SELECT lap_number FROM pit_stops WHERE race_id=? AND driver_id=?"
            " ORDER BY lap_number", (race_id, rival_id))]
    finally:
        conn.close()

    windows = sorted({min(current_lap + off, total_laps - 2) for off in (0, 2, 5)})

    def label_for(offset: int) -> str:
        if offset == 0:
            return "UNDERCUT — pit now, attack on fresh tyres"
        if offset == 2:
            return "SHORT EXTEND — stay out 2 laps"
        return "OVERCUT — stay out 5 laps"

    scenarios = []
    for offset, pit_lap in zip((0, 2, 5), windows):
        # pick the compound that scores best for this window
        best_for_window = None
        for comp in CANDIDATE_COMPOUNDS:
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
