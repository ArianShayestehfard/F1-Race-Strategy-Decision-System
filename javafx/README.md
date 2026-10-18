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
mvn javafx:run
```

(In IntelliJ IDEA: Maven tool window → javafx → Plugins → javafx → `javafx:run`,
or right-click `MainApp` → Run once a Maven run configuration exists.)

Start the backend (:8080 or :8088 if :8080 is taken) and the Python engine (:8000) first.
Base URL override: `-Dapi.base=http://localhost:8080/api` (set in the run configuration's
VM options via the javafx-maven-plugin `<options>`).
