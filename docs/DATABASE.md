# Database

SQLite file `database/race.db` — schema in [`schema.sql`](../database/schema.sql).
PostgreSQL migration is deferred until needed (same logical schema).

## Entity-Relationship diagram

```mermaid
erDiagram
    RACES ||--o{ LAPS : "has"
    RACES ||--o{ PIT_STOPS : "has"
    RACES ||--o{ WEATHER : "has"
    RACES ||--o{ TELEMETRY : "has"
    RACES ||--o{ STRATEGIES : "has"
    RACES ||--o{ PREDICTIONS : "has"
    DRIVERS ||--o{ LAPS : "drives"
    DRIVERS ||--o{ PIT_STOPS : "makes"
    DRIVERS ||--o{ STRATEGIES : "plans"
    DRIVERS ||--o{ PREDICTIONS : "receives"

    RACES {
        text race_id PK
        int year
        int round
        text name
        text circuit
        text race_date
        int total_laps
    }
    DRIVERS {
        text driver_id PK
        text full_name
        text code
        text team
    }
    LAPS {
        int id PK
        text race_id FK
        text driver_id FK
        int lap_number
        int lap_time_ms
        text tyre_compound
        int tyre_age
        int is_pit_lap
        real fuel_kg
        int position
    }
    PIT_STOPS {
        int id PK
        text race_id FK
        text driver_id FK
        int lap_number
        int duration_ms
        int stop_count
        text tyre_new
    }
    WEATHER {
        int id PK
        text race_id FK
        int lap_number
        real rainfall_mm
        text condition
    }
    STRATEGIES {
        int id PK
        text race_id FK
        text driver_id FK
        text params_json
        text result_json
    }
    PREDICTIONS {
        int id PK
        text race_id FK
        text driver_id FK
        text model
        text kind
        text payload_json
        real confidence
    }
```

## Conventions

- All lap/sector times are **integer milliseconds**.
- `tyre_age` = laps completed on the current set (1 = out lap of a fresh set).
- `TELEMETRY` stores per-sample rows and grows fast; it is only populated by
  the FastF1 ingest path (the offline demo race has no telemetry).
- Python writes the race data (ingest); Spring Boot reads it and writes
  `STRATEGIES` (saved simulation results).
