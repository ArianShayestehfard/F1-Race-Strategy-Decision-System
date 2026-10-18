"""Data ingestion: FastF1 (real) or synthetic demo race (offline)."""
from __future__ import annotations

import random
import sqlite3

from .db import connect


def _ms(td) -> int | None:
    if td is None:
        return None
    return int(td.total_seconds() * 1000)


# ---------------------------------------------------------------- FastF1 path
def ingest_race(year: int, rnd: int, session: str = "R") -> dict:
    """Download a real session via FastF1 and store it in SQLite."""
    import fastf1  # imported lazily: not needed for offline/demo mode

    ses = fastf1.get_session(year, rnd, session)
    ses.load()
    return _store_from_fastf1(ses)


def _store_from_fastf1(ses) -> dict:
    conn = connect()
    try:
        race_id = f"{ses.event['EventDate'].year}-{ses.event['RoundNumber']}"
        conn.execute(
            "INSERT OR REPLACE INTO races (race_id, year, round, name, circuit, race_date, total_laps)"
            " VALUES (?,?,?,?,?,?,?)",
            (race_id, int(ses.event['EventDate'].year), int(ses.event['RoundNumber']),
             str(ses.event['EventName']), str(ses.event.get('Location', '')),
             str(ses.event['EventDate'].date()), int(ses.laps['LapNumber'].max() or 0)),
        )
        for code, row in ses.results.iterrows():
            driver_id = str(row['Abbreviation']).lower()
            conn.execute(
                "INSERT OR REPLACE INTO drivers (driver_id, full_name, code, team, country)"
                " VALUES (?,?,?,?,?)",
                (driver_id, str(row['FullName']), str(row['Abbreviation']),
                 str(row['TeamName']), str(row.get('CountryCode', '') or '')),
            )
        laps_stored = 0
        for _, lap in ses.laps.iterrows():
            driver_id = str(lap['Driver']).lower()
            compound = lap['Compound'] if isinstance(lap['Compound'], str) and lap['Compound'] else None
            tyre_age = int(lap['TyreLife']) if lap['TyreLife'] == lap['TyreLife'] else None
            is_pit = 1 if (lap['PitInTime'] is not None and lap['PitInTime'] == lap['PitInTime']) else 0
            conn.execute(
                "INSERT OR REPLACE INTO laps (race_id, driver_id, lap_number, lap_time_ms,"
                " sector1_ms, sector2_ms, sector3_ms, tyre_compound, tyre_age, track_status,"
                " is_pit_lap, fuel_kg, position)"
                " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                (race_id, driver_id, int(lap['LapNumber']), _ms(lap['LapTime']),
                 _ms(lap['Sector1Time']), _ms(lap['Sector2Time']), _ms(lap['Sector3Time']),
                 compound, tyre_age, str(lap['TrackStatus'] or ''), is_pit, None,
                 int(lap['Position']) if lap['Position'] == lap['Position'] else None),
            )
            laps_stored += 1
        pits_stored = 0
        for _, row in ses.pit_stops.iterrows() if hasattr(ses, "pit_stops") else []:
            conn.execute(
                "INSERT INTO pit_stops (race_id, driver_id, lap_number, duration_ms, stop_count, tyre_new)"
                " VALUES (?,?,?,?,?,?)",
                (race_id, str(row['Driver']).lower(), int(row['LapNumber']),
                 int(row['PitStopTime'] * 1000) if row['PitStopTime'] == row['PitStopTime'] else None, None, None),
            )
            pits_stored += 1
        if ses.weather_data is not None and len(ses.weather_data):
            for _, w in ses.weather_data.iterrows():
                conn.execute(
                    "INSERT INTO weather (race_id, lap_number, air_temp_c, track_temp_c, humidity, rainfall_mm, condition)"
                    " VALUES (?,?,?,?,?,?,?)",
                    (race_id, None, float(w['AirTemp']), float(w['TrackTemp']),
                     float(w['Humidity']), float(w['Rainfall']),
                     'WET' if float(w['Rainfall']) > 0 else 'DRY'),
                )
        conn.commit()
        return {"race_id": race_id, "name": str(ses.event['EventName']),
                "laps_stored": laps_stored, "drivers_stored": conn.execute(
                    "SELECT COUNT(*) c FROM drivers").fetchone()["c"],
                "pit_stops_stored": pits_stored}
    finally:
        conn.close()


# ---------------------------------------------------------------- demo mode
FIELDS_2024 = [
    # driver_id must match the official 3-letter code (lowercase) so demo and
    # real (OpenF1) data deduplicate onto the same driver rows
    ("ver", "Max Verstappen", "VER", "Red Bull", 0.0),
    ("per", "Sergio Perez", "PER", "Red Bull", 0.15),
    ("lec", "Charles Leclerc", "LEC", "Ferrari", 0.12),
    ("sai", "Carlos Sainz", "SAI", "Ferrari", 0.18),
    ("nor", "Lando Norris", "NOR", "McLaren", 0.20),
    ("pia", "Oscar Piastri", "PIA", "McLaren", 0.25),
    ("ham", "Lewis Hamilton", "HAM", "Mercedes", 0.22),
    ("rus", "George Russell", "RUS", "Mercedes", 0.28),
    ("alo", "Fernando Alonso", "ALO", "Aston Martin", 0.45),
    ("str", "Lance Stroll", "STR", "Aston Martin", 0.85),
    ("sar", "Logan Sargeant", "SAR", "Williams", 0.60),
    ("alb", "Alexander Albon", "ALB", "Williams", 0.55),
    ("gas", "Pierre Gasly", "GAS", "Alpine", 0.65),
    ("oco", "Esteban Ocon", "OCO", "Alpine", 0.70),
    ("tsu", "Yuki Tsunoda", "TSU", "RB", 0.68),
    ("ric", "Daniel Ricciardo", "RIC", "RB", 0.72),
    ("mag", "Kevin Magnussen", "MAG", "Haas", 0.80),
    ("hul", "Nico Hulkenberg", "HUL", "Haas", 0.78),
    ("bot", "Valtteri Bottas", "BOT", "Sauber", 0.90),
    ("zho", "Zhou Guanyu", "ZHO", "Sauber", 0.95),
]

# base lap offset (ms) and degradation (ms per lap) per compound
COMPOUND_MODEL = {
    "SOFT":         {"base": 0,    "degr": 75,  "cliff": 22},
    "MEDIUM":       {"base": 250,  "degr": 48,  "cliff": 32},
    "HARD":         {"base": 480,  "degr": 30,  "cliff": 45},
    "INTERMEDIATE": {"base": 8000, "degr": 20,  "cliff": 40},
    "WET":          {"base": 15000, "degr": 15, "cliff": 50},
}
FUEL_MS_PER_KG = 32.0          # lap time gain per kg burned
FUEL_START_KG_PER_LAP = 1.75   # race-start fuel load per lap of the race


def generate_demo_race(year: int = 2024, rnd: int = 8, laps: int = 57, seed: int = 42) -> dict:
    """Generate a realistic synthetic race and store it (offline mode).

    Lap time = driver base pace + compound base + tyre degradation
    + fuel effect + traffic-free noise. Rain starts at lap 40.
    """
    rng = random.Random(seed)
    race_id = f"{year}-{rnd}"
    rain_from = int(laps * 0.7)
    conn = connect()
    try:
        conn.execute("INSERT OR REPLACE INTO races VALUES (?,?,?,?,?,?,?)",
                     (race_id, year, rnd, "Demo Grand Prix", "Demo Circuit",
                      f"{year}-06-01", laps))

        drivers = []
        for i, (did, name, code, team, off) in enumerate(FIELDS_2024):
            conn.execute("INSERT OR REPLACE INTO drivers VALUES (?,?,?,?,?)",
                         (did, name, code, team, None))
            drivers.append((did, off))

        strategy: dict[str, list[int]] = {}
        for did, _ in drivers:
            n_stops = 1 if rng.random() < 0.55 else 2
            if n_stops == 1:
                strategy[did] = [rng.randint(laps // 2 - 6, laps // 2 + 8)]
            else:
                a = rng.randint(14, 22)
                strategy[did] = [a, rng.randint(a + 16, laps - 8)]
        for did, stops in strategy.items():
            for i, lap_no in enumerate(stops):
                conn.execute(
                    "INSERT INTO pit_stops (race_id, driver_id, lap_number, duration_ms, stop_count, tyre_new)"
                    " VALUES (?,?,?,?,?,?)",
                    (race_id, did, lap_no, rng.randint(21000, 26000), i + 1, "HARD"),
                )

        # choose compounds between stops
        def compounds_for(stops: list[int], total: int) -> list[str]:
            segs = [s + 1 for s in stops] + [None]
            seg_starts = [0] + [s for s in stops]
            comps = []
            prev = None
            for i, start in enumerate(seg_starts):
                end = segs[i]
                seg_len = (end or total + 1) - start
                pool = ["SOFT", "MEDIUM", "HARD"]
                if prev:
                    pool = [c for c in pool if c != prev] or pool
                if seg_len > 34:
                    comp = "HARD"
                elif seg_len < 18:
                    comp = "SOFT"
                else:
                    comp = rng.choice(["MEDIUM", "HARD"])
                comps.append(comp)
                prev = comp
            return comps

        compound_plans = {did: compounds_for(stops, laps) for did, stops in strategy.items()}

        laps_stored = 0
        for did, off in drivers:
            stops = strategy[did]
            comps = compound_plans[did]
            for lap_no in range(1, laps + 1):
                seg_idx = sum(1 for s in stops if s < lap_no)
                comp = comps[seg_idx]
                tyre_age = lap_no - (stops[seg_idx - 1] if seg_idx > 0 else 0)
                fuel_kg = round(FUEL_START_KG_PER_LAP * (laps - lap_no + 1), 2)
                m = COMPOUND_MODEL[comp]
                lap_ms = 80000 + off * 1000
                rain = lap_no >= rain_from
                if rain:
                    lap_ms += 12000 + rng.random() * 3000
                    if comp in ("INTERMEDIATE", "WET"):
                        lap_ms -= 11000
                else:
                    lap_ms += m["base"]
                    cliff_over = tyre_age - m["cliff"]
                    if cliff_over > 0:
                        lap_ms += cliff_over * m["degr"] * 6
                    else:
                        lap_ms += tyre_age * m["degr"]
                # fuel burn makes the car faster: ~56 ms per lap at 1.75 kg/lap
                lap_ms -= FUEL_MS_PER_KG * FUEL_START_KG_PER_LAP * (lap_no - 1)
                lap_ms += rng.gauss(0, 450)
                is_pit_lap = 1 if lap_no in stops else 0
                if is_pit_lap:
                    lap_ms += 23000
                s1 = int(lap_ms * 0.32); s2 = int(lap_ms * 0.35); s3 = lap_ms - s1 - s2
                conn.execute(
                    "INSERT OR REPLACE INTO laps (race_id, driver_id, lap_number, lap_time_ms,"
                    " sector1_ms, sector2_ms, sector3_ms, tyre_compound, tyre_age, track_status,"
                    " is_pit_lap, fuel_kg, position) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    (race_id, did, lap_no, int(lap_ms), s1, s2, s3, comp, tyre_age,
                     "4" if rain else "1", is_pit_lap, fuel_kg, None),
                )
                laps_stored += 1

        for lap_no in range(1, laps + 1):
            rain = lap_no >= rain_from
            conn.execute(
                "INSERT INTO weather (race_id, lap_number, air_temp_c, track_temp_c, humidity, rainfall_mm, condition)"
                " VALUES (?,?,?,?,?,?,?)",
                (race_id, lap_no, 24.0 - (2.0 if rain else 0), 38.0 - (10 if rain else 0),
                 55.0 + (30 if rain else 0), 2.8 if rain else 0.0, "WET" if rain else "DRY"),
            )
        conn.commit()
        return {"race_id": race_id, "name": "Demo Grand Prix", "laps_stored": laps_stored,
                "drivers_stored": len(drivers), "pit_stops_stored": sum(len(s) for s in strategy.values())}
    finally:
        conn.close()


def main() -> None:
    import argparse
    p = argparse.ArgumentParser(description="Ingest an F1 race into SQLite")
    p.add_argument("--year", type=int)
    p.add_argument("--round", dest="rnd", type=int)
    p.add_argument("--source", choices=["openf1", "fastf1"], default="openf1",
                   help="openf1 = real data without auth (default); fastf1 = official F1 server")
    p.add_argument("--demo", action="store_true", help="generate offline demo race instead of downloading")
    args = p.parse_args()
    if args.demo:
        print(generate_demo_race())
    elif args.year and args.rnd:
        if args.source == "openf1":
            from .openf1_ingest import ingest_openf1
            print(ingest_openf1(args.year, args.rnd))
        else:
            print(ingest_race(args.year, args.rnd))
    else:
        p.error("either --demo or --year/--round required")


if __name__ == "__main__":
    main()
