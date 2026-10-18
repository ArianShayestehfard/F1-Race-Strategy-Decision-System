# UML

## Component diagram

```mermaid
flowchart LR
    subgraph desktop [JavaFX desktop client]
        V1[RaceSelectionView]
        V2[DriverSelectionView]
        V3[AnalyzeView]
        V4[SimulateView]
        V5[StrategyView]
        API1[ApiClient / HttpClient]
    end
    subgraph server [Spring Boot backend]
        C1[RaceController /api/drivers /api/races]
        C2[EngineController /api/ingest /api/analyze /api/simulate]
        S1[SimulationService]
        R1[Spring Data JPA repositories]
        API2[PythonEngineClient]
    end
    subgraph engine [Python engine FastAPI]
        M1[ingest FastF1 / demo]
        M2[analysis]
        M3[simulator]
        M4[ml scikit-learn]
        M5[recommend]
    end
    DB[(SQLite race.db)]

    V1 & V2 & V3 & V4 & V5 --> API1 --> C1 & C2
    C2 --> S1 --> API2
    C1 --> R1
    API2 --> M1 & M2 & M3 & M4 & M5
    R1 --> DB
    M1 --> DB
    M2 & M3 & M4 & M5 --> DB
```

## Key classes (sequence: "Simulate Race")

```mermaid
sequenceDiagram
    actor User
    participant FX as SimulateView (JavaFX)
    participant BE as EngineController (Spring)
    participant SS as SimulationService
    participant PY as Python /simulate
    participant DB as race.db

    User->>FX: choose compound + pit laps, click Simulate
    FX->>BE: POST /api/simulate
    BE->>SS: simulate(request)
    SS->>DB: read race context (via Python later; JPA for race rows)
    SS->>PY: POST /simulate {race_id, driver_id, pit_laps, ...}
    PY->>DB: SELECT laps, weather, pit_stops
    PY-->>SS: {finish_position, total_time_ms, timeline}
    SS->>DB: INSERT INTO strategies (params_json, result_json)
    SS-->>BE: result JSON
    BE-->>FX: result JSON
    FX-->>User: table + finish position + race time
```
