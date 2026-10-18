# Backend (Spring Boot 3.5)

The manager: owns SQLite, serves the REST API, hosts the web dashboard
(`src/main/resources/static` — open http://localhost:8088/), and proxies
analysis/simulation/ML calls to the Python engine.

## Run

```bash
mvn spring-boot:run        # :8088 (8080 is occupied by a local service on the dev machine)
```

Requires the Python engine on `:8000` (repo root: `python scripts/run_engine.py`)
for `/api/simulate`, `/api/analyze`, `/api/predict`, `/api/strategy/*`,
`/api/ingest`, `/api/calibrate`.

## Endpoints

| Method | Path | Source |
|---|---|---|
| GET | `/api/drivers` | SQLite (JPA) |
| GET | `/api/races` | SQLite (JPA) |
| GET | `/api/races/{raceId}/drivers` | SQLite (JPA) |
| GET | `/api/races/{raceId}/laps/{driverId}` | SQLite (JPA) |
| GET | `/api/engine/health` | Python `/health` |
| POST | `/api/ingest` | Python `/ingest` |
| GET | `/api/analyze/{raceId}/{driverId}` | Python `/analyze` |
| POST | `/api/predict` | Python `/predict` |
| POST | `/api/simulate` | Python `/simulate` + saves result into `strategies` |
| POST | `/api/strategy/recommend` | Python `/strategy/recommend` |
| POST | `/api/strategy/duel` | Python `/strategy/duel` |
| POST | `/api/strategy/search` | Python `/strategy/search` (calibrated plans) |
| POST | `/api/calibrate/{raceId}` | Python `/calibrate/{raceId}` |

## Configuration

| Key | Default | Meaning |
|---|---|---|
| `f1.db.path` | `../database/race.db` | SQLite file |
| `python.engine.url` | `http://localhost:8000` | Python engine base URL |
| `server.port` | `8088` | HTTP port |

Tests run without Python (`mvn test`): the engine client is mocked.
