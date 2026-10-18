"""Phase 7 — lap-by-lap race simulator for a 20-car field.

Model per lap (per car):
  lap_time = driver_pace + compound base + tyre degradation (+ cliff)
             - fuel effect + traffic (dirty air) + weather penalty + noise
Pit laps add pit_stop_overhead_s. Positions are ranked by cumulative
race time each lap, so overtakes happen when cumulative times cross.

Deterministic for a fixed seed.
"""
from __future__ import annotations

import random

from .db import connect
from .ingest import COMPOUND_MODEL, FUEL_MS_PER_KG, FUEL_START_KG_PER_LAP

DIRTY_AIR_WINDOW_MS = 1000
DIRTY_AIR_PENALTY_MS = 250
WET_PIT_EXTRA_S = 3.5     # pit lane is slower in the wet
RAIN_THRESHOLD_MM = 0.5


def _pit_overhead_s(base_s: float, rainfall: float) -> int:
    """Pit stops take longer when the pit lane is wet."""
    if rainfall > RAIN_THRESHOLD_MM:
        return int((base_s + WET_PIT_EXTRA_S * min(rainfall, 3.0) / 3.0) * 1000)
    return int(base_s * 1000)


def _weather_by_lap(conn, race_id: str, total_laps: int) -> dict[int, float]:
    rain = {lap: 0.0 for lap in range(1, total_laps + 1)}
    for r in conn.execute(
        "SELECT lap_number, rainfall_mm FROM weather WHERE race_id=?", (race_id,)
    ):
        if r["lap_number"] is not None and 1 <= r["lap_number"] <= total_laps:
            rain[r["lap_number"]] = float(r["rainfall_mm"] or 0.0)
    return rain


def _driver_pace(conn, race_id: str) -> dict[str, int]:
    """Reference pace per driver: median of non-pit, non-cliff laps."""
    pace: dict[str, list[int]] = {}
    for r in conn.execute(
        "SELECT driver_id, lap_time_ms, is_pit_lap, tyre_age FROM laps"
        " WHERE race_id=? AND lap_time_ms IS NOT NULL",
        (race_id,),
    ):
        if r["is_pit_lap"]:
            continue
        pace.setdefault(r["driver_id"], []).append(r["lap_time_ms"])
    return {d: int(sorted(v)[len(v) // 2]) for d, v in pace.items()}


def _default_pit_laps(conn, race_id: str) -> dict[str, list[int]]:
    stops: dict[str, list[int]] = {}
    for r in conn.execute(
        "SELECT driver_id, lap_number FROM pit_stops WHERE race_id=? ORDER BY lap_number",
        (race_id,),
    ):
        stops.setdefault(r["driver_id"], []).append(r["lap_number"])
    return stops


def _choose_compound(stint_len: int, previous: str | None) -> str:
    if stint_len > 34:
        comp = "HARD"
    elif stint_len < 18:
        comp = "SOFT"
    else:
        comp = "MEDIUM"
    if comp == previous:
        comp = {"MEDIUM": "HARD", "HARD": "MEDIUM", "SOFT": "MEDIUM"}[comp]
    return comp


def _lap_time(pace_ms: int, compound: str, tyre_age: int, lap_no: int,
              total_laps: int, rainfall: float, rng: random.Random) -> int:
    m = COMPOUND_MODEL[compound]
    t = float(pace_ms)
    rain = rainfall > 0.5
    if rain:
        # dry compounds are heavily penalised; wets are close to their slick pace
        wet_penalty = {"SOFT": 9000, "MEDIUM": 8500, "HARD": 8000,
                       "INTERMEDIATE": 1500, "WET": 300}[compound]
        t += wet_penalty * min(rainfall, 3.0) / 3.0
    else:
        t += m["base"]
        cliff_over = tyre_age - m["cliff"]
        if cliff_over > 0:
            t += cliff_over * m["degr"] * 6
        else:
            t += tyre_age * m["degr"]
    t -= FUEL_MS_PER_KG * FUEL_START_KG_PER_LAP * (lap_no - 1)
    t += rng.gauss(0, 350)
    return int(t)


def simulate_race(race_id: str, driver_id: str, start_compound: str = "MEDIUM",
                  pit_laps: list[int] | None = None,
                  pit_compounds: dict[int, str] | None = None,
                  pit_stop_overhead_s: float = 22.5, seed: int = 42) -> dict:
    conn = connect()
    try:
        race = conn.execute("SELECT total_laps FROM races WHERE race_id=?", (race_id,)).fetchone()
        if race is None:
            raise ValueError(f"unknown race {race_id}")
        total_laps = int(race["total_laps"] or 57)
        pace = _driver_pace(conn, race_id)
        if driver_id not in pace:
            raise ValueError(f"unknown driver {driver_id} in {race_id}")
        others_pits = _default_pit_laps(conn, race_id)
        rain = _weather_by_lap(conn, race_id, total_laps)
    finally:
        conn.close()

    drivers = sorted(pace, key=lambda d: pace[d])  # grid: fastest on pole
    rng = random.Random(seed)
    overrides = dict(pit_compounds or {})
    planned = sorted(set(int(x) for x in (pit_laps or [])))
    planned = [x for x in planned if 1 <= x <= total_laps - 1]

    # stints: (pit_lap_that_ends_stint or None, compound). Stint 1 uses start_compound.
    def build_stints(pits: list[int], first: str) -> list[tuple[int | None, str]]:
        stints, comp, prev_end = [], first, 0
        boundaries = pits + [total_laps]
        for i, end in enumerate(boundaries):
            stint_len = end - prev_end
            if i > 0:
                comp = overrides.get(pits[i - 1]) or _choose_compound(stint_len, stints[-1][1])
            stints.append((end, comp))
            prev_end = end
        return stints

    target_stints = build_stints(planned, start_compound)
    # stints per car -> set of pit laps for lookup
    others_stints = {d: build_stints(others_pits.get(d, []), "MEDIUM") for d in drivers}

    # state per car
    cum = {d: 0 for d in drivers}
    tyre_age = {d: 0 for d in drivers}
    timeline: list[dict] = []
    last_lap_ms: dict[str, int] = {d: 0 for d in drivers}

    def stint_compound(stints, lap_no: int) -> str:
        for end, comp in stints:
            if lap_no <= end:
                return comp
        return stints[-1][1]

    for lap_no in range(1, total_laps + 1):
        r = rain[lap_no]
        # order by cumulative time to find gaps for dirty air
        order = sorted(drivers, key=lambda d: cum[d])
        gaps = {d: 0 for d in drivers}
        for i in range(1, len(order)):
            gaps[order[i]] = cum[order[i]] - cum[order[i - 1]]

        for d in drivers:
            comp = stint_compound(target_stints if d == driver_id else others_stints[d], lap_no)
            ms = _lap_time(pace[d], comp, tyre_age[d], lap_no, total_laps, r, rng)
            if d != driver_id and gaps[d] < DIRTY_AIR_WINDOW_MS:
                ms += DIRTY_AIR_PENALTY_MS
            if lap_no in (planned if d == driver_id else others_pits.get(d, [])):
                ms += _pit_overhead_s(pit_stop_overhead_s, r)
                tyre_age[d] = 0
                is_pit = True
            else:
                tyre_age[d] += 1
                is_pit = False
            cum[d] += ms
            last_lap_ms[d] = ms
            if d == driver_id:
                timeline.append({"lap": lap_no, "lap_time_ms": ms, "compound": comp,
                                 "tyre_age": tyre_age[d], "position": 0, "pit": is_pit})
        # positions after this lap
        pos_map = {dd: i + 1 for i, dd in enumerate(sorted(drivers, key=lambda dd: cum[dd]))}
        for t in timeline:
            if t["lap"] == lap_no:
                t["position"] = pos_map[driver_id]

    return {
        "race_id": race_id,
        "driver_id": driver_id,
        "finish_position": pos_map[driver_id],
        "total_time_ms": cum[driver_id],
        "timeline": timeline,
    }
