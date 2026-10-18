# F1 Race Strategy Decision System

<!-- Add a screenshot after your first run: save it as docs/dashboard.png, then uncomment the line below. -->
<!-- ![Dashboard](docs/dashboard.png) -->

A three-tier decision-support app for Formula 1 pit strategy: a FastAPI
strategy engine (Python), a Spring Boot backend that proxies it and persists
results (SQLite), and a dark web dashboard with the full set of tools —
import, analyze, simulate, compare, duel.

## What it does

- Models lap time as a function of compound, tyre age and fuel load, including
  a degradation cliff, calibrated per race by least squares with per-driver
  pace offsets and a grid-searched cliff per compound.
- Searches 1, 2 and 3 stop plans (minimum two compounds, minimum stint length)
  and ranks them with race-time deltas and degradation curves.
- Analyzes a driver's real laps: pace trend, tyre stints, pit stops, fuel
  effect, gap to leader.
- Simulates a full 20-car race (tyre degradation, fuel, pit overhead, dirty
  air, rain) for any pit plan — deterministic for a fixed seed.
- Recommends the next pit lap and compound with a confidence score, rain-aware
  (INTERMEDIATE on any rain ahead, WET from 2.5 mm, slower wet pit stops).
- Runs the undercut / overcut duel against a rival's actual plan.
- Predicts the next 10 laps with Linear Regression / Random Forest /
  Gradient Boosting.
- Imports real races from OpenF1 (no auth) or FastF1, or generates an offline
  demo race with a rain phase.

## Architecture

```
browser  ──►  Spring Boot :8088  ──►  FastAPI engine :8000
   UI            /api/** proxy,          analysis, simulation,
 (static/        SQLite persistence      ML, strategy search,
  app ui)        (strategies table)      calibration, ingest
```

All times cross the API as integer milliseconds; compounds are
`SOFT|MEDIUM|HARD|INTERMEDIATE|WET`; `race_id` is `{year}-{round}`; `driver_id`
is the lowercase three-letter code.

## Project layout

```
src/f1_strategy/       engine: strategy.py, data.py, calibrate.py (calibrated core)
                       db.py, ingest.py, openf1_ingest.py, analysis.py,
                       simulator.py, recommend.py, ml.py, racecalib.py
                       api/ (FastAPI app, port 8000)
backend/               Spring Boot backend (port 8088, UI in src/main/resources/static)
database/              schema.sql + race.db (gitignored)
models/                per-race calibrated model JSON
dashboard/             static single-file report (offline workflow, unchanged)
tests/                 pytest suite (engine + API)
scripts/               calibrate.py, export_dashboard_data.py, run_engine.py,
                       create_demo_db.py
```

## Quick start

IntelliJ: the run configurations cover everything —
**0- ALL SERVICES** starts engine + backend + dashboard in one go.

Manual:

```bash
pip install -r requirements.txt
pytest                                   # 35 engine/API tests

python scripts/create_demo_db.py         # offline demo race into database/race.db
python scripts/run_engine.py             # FastAPI engine on :8000

cd backend
mvn spring-boot:run                      # or run BackendApplication from the IDE
# backend on :8088 serving the dashboard — open http://localhost:8088/
```

Offline static report (no services needed):

```bash
python scripts/calibrate.py --year 2024 --gp Bahrain
python scripts/export_dashboard_data.py --model models/bahrain_2024.json --laps 57 --race "Bahrain 2024"
open dashboard/index.html
```

## Roadmap

- [x] Data pipeline: load race laps and clean outliers (pit laps, safety cars)
- [x] Fit per-compound degradation models from real data
- [x] Strategy comparison dashboard (1/2/3-stop plans + degradation curves)
- [x] Driver analysis, 20-car simulator, rain-aware recommendation, rival duel
- [x] ML lap-time prediction (3 models) + JSON report export
- [ ] Validate calibrated models against actual race strategies
- [ ] Live lap-by-lap recommendations during a session

## License

MIT, see [LICENSE](LICENSE).
