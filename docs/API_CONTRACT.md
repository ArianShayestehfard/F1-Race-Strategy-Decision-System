# API Contract — Python Engine (FastAPI) ↔ Spring Boot

Python engine runs as an HTTP service (default `http://localhost:8000`).
Spring Boot is the **only** client; JavaFX never talks to Python directly.

## Endpoints

### `GET /health`
```json
{ "status": "ok", "db": "database/race.db", "models_trained": 0 }
```

### `POST /ingest`
Download one race and store it in SQLite. `source` picks the data provider:
`openf1` (default — real data, no auth), `fastf1` (official F1 server,
needs open network/auth) or `"demo": true` (offline synthetic race).
```json
// request
{ "year": 2024, "round": 8, "source": "openf1" }
// response
{ "race_id": "2024-8", "name": "Monaco Grand Prix", "source": "openf1",
  "session_key": 9523, "laps_stored": 1217, "drivers_stored": 16,
  "pit_stops_stored": 23, "note": "..." }
```

### `GET /analyze/{race_id}/{driver_id}`
Statistical report for one driver (Phase 6 modules).
```json
{
  "race_id": "2024-8", "driver_id": "nor",
  "best_lap_ms": 74123,
  "avg_lap_ms": 76110,
  "pace_trend": [{ "lap": 1, "lap_time_ms": 79000 }, ...],
  "tyre_age_analysis": [{ "compound": "MEDIUM", "tyre_age": 3, "avg_lap_ms": 75500 }, ...],
  "fuel_effect_s_per_lap": 0.032,
  "gap_analysis": [{ "lap": 20, "gap_to_leader_ms": 12400 }, ...],
  "stints": [{ "compound": "MEDIUM", "start_lap": 1, "end_lap": 19 }, ...],
  "pit_stops": [{ "lap": 20, "duration_ms": 23400 }, ...]
}
```

### `POST /predict`
ML lap-time prediction (Phase 8).
```json
// request
{ "race_id": "2024-08", "driver_id": "nor", "model": "gradient_boosting" }
// response
{ "model": "gradient_boosting", "mae_ms": 480,
  "predictions": [{ "lap": 61, "predicted_lap_time_ms": 74980 }, ...],
  "tyre_degradation_ms_per_lap": 62.4 }
```

### `POST /simulate`
Run the race simulator (Phase 7).
```json
// request
{ "race_id": "2024-08", "driver_id": "nor",
  "start_compound": "MEDIUM",
  "pit_laps": [20, 41],
  "pit_stop_overhead_s": 22.5 }
// response
{ "finish_position": 4, "total_time_ms": 5321000,
  "timeline": [{ "lap": 1, "lap_time_ms": 81000, "compound": "MEDIUM",
                 "tyre_age": 1, "position": 10, "pit": false }, ...] }
```

### `POST /strategy/recommend`
Strategy Recommendation Engine (Phase 9). Tries several pit windows and
returns the best scenario.
```json
// request
{ "race_id": "2024-08", "driver_id": "nor", "current_lap": 18,
  "current_compound": "MEDIUM", "current_tyre_age": 17 }
// response
{
  "recommended_pit_lap": 22,
  "recommended_compound": "HARD",
  "expected_finish_position": 3,
  "confidence": 0.84,
  "scenarios": [
    { "pit_lap": 22, "compound": "HARD", "finish_position": 3, "total_time_ms": 5310000 },
    { "pit_lap": 20, "compound": "HARD", "finish_position": 4, "total_time_ms": 5313400 },
    { "pit_lap": 25, "compound": "HARD", "finish_position": 5, "total_time_ms": 5318900 }
  ]
}
```

### `POST /strategy/duel`
Pit-cycle duel vs a rival — the undercut/overcut question. The rival runs
his historical plan; we try pitting now / +2 / +5 laps (best compound per
window) and rank by finishing position relative to the rival.
```json
// request
{ "race_id": "2024-8", "driver_id": "nor", "rival_id": "lec",
  "current_lap": 18, "current_compound": "MEDIUM" }
// response
{ "best_option": "UNDERCUT — pit now, attack on fresh tyres",
  "best_pit_lap": 18, "best_compound": "SOFT", "beats_rival": true,
  "rival_plan_pit_laps": [21, 44],
  "scenarios": [{ "option": "...", "pit_lap": 18, "compound": "SOFT",
                  "my_finish_position": 3, "rival_finish_position": 4,
                  "beats_rival": true }, ...] }
```

## Conventions
- All times are **integer milliseconds**.
- Compounds: `SOFT | MEDIUM | HARD | INTERMEDIATE | WET`.
- `driver_id` is the lowercase FastF1 driver id (e.g. `nor`); `race_id` is `"{year}-{round}"`.
- Errors: HTTP 4xx/5xx with `{ "detail": "..." }` (FastAPI default).
