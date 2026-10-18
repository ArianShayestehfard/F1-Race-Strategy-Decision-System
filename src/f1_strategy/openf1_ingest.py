"""Real-data ingestion via the OpenF1 API (https://openf1.org).

F1's own live-timing server now requires authentication (401), but OpenF1
serves the same data — sessions, drivers, laps, stints (tyre compound!),
pit stops and weather — with no auth. This module maps it onto our
SQLite schema so the whole pipeline runs on REAL race data.

 tyre_age / compound come from /v1/stints; fuel load is an estimate.
"""
from __future__ import annotations

import urllib.request
import urllib.parse

from .db import connect

API = "https://api.openf1.org/v1"
FUEL_START_KG_PER_LAP = 1.75  # same estimate as the demo generator


def _get(path: str, **params) -> list:
    query = urllib.parse.urlencode({k: v for k, v in params.items() if v is not None})
    url = f"{API}/{path}" + (f"?{query}" if query else "")
    req = urllib.request.Request(url, headers={"User-Agent": "f1-strategy/0.5"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        import json
        return json.loads(resp.read().decode())


def _sec_to_ms(v) -> int | None:
    return None if v in (None, "") else int(round(float(v) * 1000))


def list_races(year: int) -> list[dict]:
    """All races of a year, oldest first (index+1 == round)."""
    return sorted(
        _get("sessions", year=year, session_name="Race"),
        key=lambda s: s["date_start"],
    )


def _resolve_session(year: int, rnd: int | None, country: str | None) -> dict:
    races = list_races(year)
    if not races:
        raise ValueError(f"no races found for {year}")
    if country:
        for s in races:
            if s["country_name"].lower() == country.lower():
                return s
        raise ValueError(f"no {year} race in {country}")
    if rnd is not None:
        return races[rnd - 1]
    return races[-1]


def ingest_openf1(year: int, rnd: int | None = None, country: str | None = None) -> dict:
    ses = _resolve_session(year, rnd, country)
    session_key = ses["session_key"]
    round_no = list_races(year).index(ses) + 1
    race_id = f"{year}-{round_no}"

    drivers = _get("drivers", session_key=session_key)
    laps = _get("laps", session_key=session_key)
    stints = _get("stints", session_key=session_key)
    pits = _get("pit", session_key=session_key)
    weather = _get("weather", session_key=session_key)

    total_laps = max((l["lap_number"] for l in laps), default=0)
    compounds: dict[tuple[int, int], tuple[str, int]] = {}
    for st in stints:
        if st.get("lap_start") is None or st.get("lap_end") is None:
            continue  # in-progress / malformed stint
        for lap in range(st["lap_start"], st["lap_end"] + 1):
            compounds[(st["driver_number"], lap)] = (
                st.get("compound") or "UNKNOWN",
                (st.get("tyre_age_at_start") or 0) + (lap - st["lap_start"]),
            )
    pit_laps = {(p["driver_number"], p["lap_number"]) for p in pits if p.get("lap_number")}
    pit_dur = {(p["driver_number"], p["lap_number"]): p.get("pit_duration") for p in pits}

    # weather samples -> nearest lap by start time
    lap_starts = sorted((l["date_start"], l["lap_number"]) for l in laps if l.get("date_start"))
    def lap_for(t: str) -> int | None:
        if not lap_starts or not t:
            return None
        from datetime import datetime
        target = datetime.fromisoformat(t.replace("Z", "+00:00"))
        best = min(lap_starts, key=lambda ls: abs(
            (datetime.fromisoformat(ls[0].replace("Z", "+00:00")) - target).total_seconds()))
        return best[1]

    conn = connect()
    try:
        _replace_race(conn, race_id)
        conn.execute(
            "INSERT INTO races (race_id, year, round, name, circuit, race_date, total_laps)"
            " VALUES (?,?,?,?,?,?,?)",
            (race_id, year, round_no, f"{ses['country_name']} Grand Prix",
             ses["circuit_short_name"], ses["date_start"][:10], total_laps),
        )
        num_to_id: dict[int, str] = {}
        for d in drivers:
            did = str(d["name_acronym"]).lower()
            num_to_id[d["driver_number"]] = did
            conn.execute("INSERT OR REPLACE INTO drivers VALUES (?,?,?,?,?)",
                         (did, d["full_name"], d["name_acronym"], d.get("team_name"),
                          d.get("country_code") or ""))

        laps_stored = 0
        for l in laps:
            did = num_to_id.get(l["driver_number"])
            if did is None or not l.get("lap_duration"):
                continue
            lap_no = l["lap_number"]
            comp, tyre_age = compounds.get((l["driver_number"], lap_no), ("UNKNOWN", None))
            is_pit = 1 if (l["driver_number"], lap_no) in pit_laps else 0
            conn.execute(
                "INSERT OR REPLACE INTO laps (race_id, driver_id, lap_number, lap_time_ms,"
                " sector1_ms, sector2_ms, sector3_ms, tyre_compound, tyre_age, track_status,"
                " is_pit_lap, fuel_kg, position) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                (race_id, did, lap_no, _sec_to_ms(l["lap_duration"]),
                 _sec_to_ms(l.get("duration_sector_1")), _sec_to_ms(l.get("duration_sector_2")),
                 _sec_to_ms(l.get("duration_sector_3")), comp, tyre_age, "1", is_pit,
                 round(FUEL_START_KG_PER_LAP * (total_laps - lap_no + 1), 2), None),
            )
            laps_stored += 1

        for (num, lap_no), dur in pit_dur.items():
            did = num_to_id.get(num)
            if did is None or lap_no is None:
                continue
            conn.execute(
                "INSERT INTO pit_stops (race_id, driver_id, lap_number, duration_ms, stop_count, tyre_new)"
                " VALUES (?,?,?,?,?,?)",
                (race_id, did, lap_no, _sec_to_ms(dur), None, None),
            )

        seen_laps = set()
        for w in weather:
            lap_no = lap_for(w.get("date"))
            if lap_no is None or (lap_no, round(w.get("air_temperature") or 0)) in seen_laps:
                continue
            seen_laps.add((lap_no, round(w.get("air_temperature") or 0)))
            conn.execute(
                "INSERT INTO weather (race_id, lap_number, air_temp_c, track_temp_c, humidity, rainfall_mm, condition)"
                " VALUES (?,?,?,?,?,?,?)",
                (race_id, lap_no, w.get("air_temperature"), w.get("track_temperature"),
                 w.get("humidity"), w.get("rainfall"),
                 "WET" if (w.get("rainfall") or 0) > 0 else "DRY"),
            )
        conn.commit()
        drivers_with_laps = conn.execute(
            "SELECT COUNT(DISTINCT driver_id) c FROM laps WHERE race_id=?", (race_id,)
        ).fetchone()["c"]
        return {"race_id": race_id, "name": f"{ses['country_name']} Grand Prix",
                "source": "openf1", "session_key": session_key,
                "laps_stored": laps_stored, "drivers_stored": drivers_with_laps,
                "pit_stops_stored": len(pit_dur),
                "note": "drivers_stored counts drivers that actually have lap rows;"
                        " OpenF1 occasionally lacks laps for some drivers in a session"}
    finally:
        conn.close()


def _replace_race(conn, race_id: str) -> None:
    """Idempotent ingest: drop any previous version of this race first."""
    for table in ("laps", "pit_stops", "weather", "telemetry", "strategies", "predictions"):
        conn.execute(f"DELETE FROM {table} WHERE race_id=?", (race_id,))
    conn.execute("DELETE FROM races WHERE race_id=?", (race_id,))
