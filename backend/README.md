# Backend (Spring Boot 3.5)

The manager: owns SQLite, serves the REST API the JavaFX client talks to,
and proxies analysis/simulation/ML calls to the Python engine.

## Run

```bash
mvn spring-boot:run        # :8088 (8080 is occupied by a local service on the dev machine)
```

Requires the Python engine on `:8000` for `/api/simulate`, `/api/analyze`,
`/api/predict`, `/api/strategy/recommend`, `/api/ingest`.
Swagger UI: http://localhost:8080/swagger-ui.html

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

## Configuration

| Key | Default | Meaning |
|---|---|---|
| `f1.db.path` | `../database/race.db` | SQLite file |
| `python.engine.url` | `http://localhost:8000` | Python engine base URL |
| `server.port` | `8088` | HTTP port |

Tests run without Python (`mvn test`): the engine client is mocked.
