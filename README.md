# 🏎 F1 Race Strategy & Decision Support System

A full-stack **decision support system** for Formula 1 pit-stop strategy:
ingest real race data, simulate the race lap by lap, predict tyre
degradation with ML, and answer the question every race engineer faces —
**"should we pit now or not?"**

Built with **Python (FastAPI)** for analysis/simulation/ML,
**Java 25 + Spring Boot 3** as the backend manager,
**JavaFX** as the desktop client, and **SQLite** as the store.

```
JavaFX  ──REST──►  Spring Boot  ──HTTP──►  Python Engine (FastAPI)
                      │                        │      │
                      ▼                        │      ▼
                  SQLite ◄────────ingest───────┘   analysis · simulator
                  race.db                          ML · strategy engine
```

> Full architecture: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) ·
> API contract: [docs/API_CONTRACT.md](docs/API_CONTRACT.md) ·
> ERD: [docs/DATABASE.md](docs/DATABASE.md) · UML: [docs/UML.md](docs/UML.md)

## What it does

| Capability | How |
|---|---|
| **Data ingestion** | FastF1 downloads a real race (laps, pit stops, weather, telemetry) into SQLite — or an offline `--demo` race when the network is blocked |
| **Statistical analysis** | pace trend, best/avg lap, tyre-age degradation curves, fuel effect (s/lap), gap-to-leader per lap (Pandas/NumPy) |
| **Race simulator** | lap-by-lap 20-car simulation: tyre compounds + degradation cliffs, fuel burn, pit loss, dirty-air traffic, rain — deterministic for a fixed seed |
| **ML prediction** | Linear Regression / Random Forest / Gradient Boosting predict lap time & tyre degradation (scikit-learn, models cached with joblib) |
| **Strategy engine** | tries pit windows × compounds, simulates every scenario, recommends *pit lap X on compound Y → expected P3, confidence 84%* |
| **Desktop UI** | JavaFX tabs: Races → Drivers → Analyze (pace chart) → Simulate (timeline table) → Compare Strategies |

Every feature answers a real team decision; dashboards without a decision
behind them are deliberately out of scope.

## Project layout

```text
backend/         Spring Boot 3.5 REST API + JPA (the manager)
python-engine/   FastAPI service: ingest / analyze / simulate / predict / recommend
javafx/          JavaFX desktop client
database/        schema.sql (+ race.db at runtime)
docs/            architecture, API contract, DB diagram, UML, roadmap
tests/           pytest integration tests for the Python engine
```

## Quickstart

**1. Python engine** (Python 3.11+)

```bash
cd python-engine
pip install -r requirements.txt
python -m app.ingest --demo                  # offline demo race into database/race.db
python -m app.ingest --year 2024 --round 8   # real race (needs unblocked F1 data access)
uvicorn app.main:app --port 8000             # API + docs at /docs
```

**2. Backend** (Java 17+, Maven)

```bash
cd backend
mvn spring-boot:run                          # REST API on :8088 (8080 is taken on the dev machine)
```

**3. Desktop client**

```bash
cd javafx
mvn compile exec:exec
```

**Tests**

```bash
python -m pytest tests -q                    # 17 tests: ingest, analysis, simulator, ML, recommend, API
cd backend && mvn test                       # controller + service tests
```

## Example: the core question

```bash
curl -s localhost:8000/strategy/recommend -H "Content-Type: application/json" \
  -d '{"race_id":"2024-8","driver_id":"nor","current_lap":18,"current_compound":"MEDIUM"}'
```

```json
{
  "recommended_pit_lap": 22,
  "recommended_compound": "HARD",
  "expected_finish_position": 3,
  "confidence": 0.84,
  "scenarios": [ ... ]
}
```

## Notes on real F1 data

FastF1 pulls from F1's live-timing servers. Some networks (and, for recent
seasons, unauthenticated access) return `401/403`. In that case use
`--demo` mode — the synthetic race exercises the exact same code path,
and every model in the pipeline runs on it. With a working proxy/VPN the
real ingest needs no code changes.

## Roadmap & status

See [docs/ROADMAP.md](docs/ROADMAP.md) for the phased plan (v0.1 → v1.0) and
what is implemented. License: [MIT](LICENSE).
