# JavaFX Desktop Client

Functional UI (Phase 10): five tabs wired through the Spring Boot backend.

| Tab | Screen | Calls |
|---|---|---|
| Races | race selection | `GET /api/races` |
| Drivers | driver selection | `GET /api/races/{id}/drivers` |
| Analyze | pace trend chart + report | `GET /api/analyze/{race}/{driver}` |
| Simulate | pit plan input + lap timeline table | `POST /api/simulate` |
| Compare Strategies | recommendation + all scenarios | `POST /api/strategy/recommend` |

## Run

```bash
mvn compile exec:exec
```

Start the backend (:8080) and the Python engine (:8000) first.
Base URL override: `-Dapi.base=http://localhost:8080/api`.
