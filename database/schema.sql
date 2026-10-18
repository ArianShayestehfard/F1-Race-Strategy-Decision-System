-- ============================================================
-- F1 Race Strategy & Decision Support System - SQLite Schema
-- Version 0.1 (SQLite) - migrate to PostgreSQL later
-- ============================================================

PRAGMA foreign_keys = ON;

-- ------------------------------------------------------------
-- Drivers
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS drivers (
    driver_id   TEXT PRIMARY KEY,          -- e.g. "hamilton"
    full_name   TEXT NOT NULL,
    code        TEXT NOT NULL,             -- 3-letter code, e.g. "HAM"
    team        TEXT,
    country     TEXT
);

-- ------------------------------------------------------------
-- Races
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS races (
    race_id     TEXT PRIMARY KEY,          -- e.g. "2024-05-monaco"
    year        INTEGER NOT NULL,
    round       INTEGER,
    name        TEXT NOT NULL,
    circuit     TEXT,
    race_date   TEXT,
    total_laps  INTEGER
);

-- ------------------------------------------------------------
-- Laps (one row per driver per lap)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS laps (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    race_id        TEXT NOT NULL REFERENCES races(race_id),
    driver_id      TEXT NOT NULL REFERENCES drivers(driver_id),
    lap_number     INTEGER NOT NULL,
    lap_time_ms    INTEGER,                -- NULL for in/out laps
    sector1_ms     INTEGER,
    sector2_ms     INTEGER,
    sector3_ms     INTEGER,
    tyre_compound  TEXT,                   -- SOFT / MEDIUM / HARD / INTERMEDIATE / WET
    tyre_age       INTEGER,                -- laps since last pit
    track_status   TEXT,                   -- "1" green, "4" safety car, ...
    is_pit_lap     INTEGER DEFAULT 0,
    fuel_kg        REAL,                   -- estimated fuel at lap start
    position       INTEGER,
    UNIQUE(race_id, driver_id, lap_number)
);
CREATE INDEX IF NOT EXISTS idx_laps_race   ON laps(race_id);
CREATE INDEX IF NOT EXISTS idx_laps_driver ON laps(race_id, driver_id);

-- ------------------------------------------------------------
-- Pit Stops
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pit_stops (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    race_id     TEXT NOT NULL REFERENCES races(race_id),
    driver_id   TEXT NOT NULL REFERENCES drivers(driver_id),
    lap_number  INTEGER NOT NULL,
    duration_ms INTEGER,                   -- stationary + total pit time
    stop_count  INTEGER,                   -- 1st, 2nd, ... stop of the race
    tyre_new    TEXT                       -- compound fitted
);

-- ------------------------------------------------------------
-- Weather (per lap sample)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS weather (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    race_id      TEXT NOT NULL REFERENCES races(race_id),
    lap_number   INTEGER,
    air_temp_c   REAL,
    track_temp_c REAL,
    humidity     REAL,
    rainfall_mm  REAL,
    condition    TEXT                      -- DRY / WET / ...
);

-- ------------------------------------------------------------
-- Telemetry (per sample inside a lap) - large table
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS telemetry (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    race_id      TEXT NOT NULL REFERENCES races(race_id),
    driver_id    TEXT NOT NULL REFERENCES drivers(driver_id),
    lap_number   INTEGER NOT NULL,
    timestamp_s  REAL,                     -- seconds since lap start
    speed_kmh    REAL,
    rpm          INTEGER,
    gear         INTEGER,
    throttle     REAL,                     -- 0..100
    brake        REAL,                     -- 0..100
    drs          INTEGER,
    x            REAL,                     -- track coordinates
    y            REAL
);
CREATE INDEX IF NOT EXISTS idx_tel_race ON telemetry(race_id, driver_id, lap_number);

-- ------------------------------------------------------------
-- Strategies (saved simulation scenarios)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS strategies (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    race_id     TEXT NOT NULL REFERENCES races(race_id),
    driver_id   TEXT NOT NULL REFERENCES drivers(driver_id),
    name        TEXT NOT NULL,
    params_json TEXT NOT NULL,             -- simulator input parameters
    result_json TEXT,                      -- simulator output summary
    created_at  TEXT DEFAULT (datetime('now'))
);

-- ------------------------------------------------------------
-- Predictions (ML model outputs)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS predictions (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    race_id     TEXT NOT NULL REFERENCES races(race_id),
    driver_id   TEXT NOT NULL REFERENCES drivers(driver_id),
    model       TEXT NOT NULL,             -- linear_regression / random_forest / gradient_boosting
    kind        TEXT NOT NULL,             -- lap_time / tyre_degradation / driver_pace
    payload_json TEXT NOT NULL,
    confidence  REAL,
    created_at  TEXT DEFAULT (datetime('now'))
);
