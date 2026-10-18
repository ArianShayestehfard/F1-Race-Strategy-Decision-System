# Python Engine

FastAPI service that owns all data work: ingestion, analysis, simulation,
ML, strategy recommendation. See `../docs/API_CONTRACT.md` for the wire
format and `../docs/ARCHITECTURE.md` for where it sits.

## Run

```bash
pip install -r requirements.txt
python -m app.ingest --demo                   # offline demo race
python -m app.ingest --year 2024 --round 8    # real race via FastF1 (needs open network)
uvicorn app.main:app --port 8000              # interactive docs at /docs
```

DB location defaults to `../database/race.db`; override with `F1_DB_PATH`.

## Modules

| Module | Phase | What it does |
|---|---|---|
| `db.py` | 3 | SQLite access, schema bootstrap |
| `ingest.py` | 2–3 | FastF1 ingest + offline synthetic race generator |
| `analysis.py` | 6 | pace trend, tyre-age curves, fuel effect, gaps |
| `simulator.py` | 7 | lap-by-lap 20-car race simulation (deterministic) |
| `ml.py` | 8 | LinearRegression / RandomForest / GradientBoosting lap-time prediction |
| `recommend.py` | 9 | strategy recommendation engine (pit windows × compounds) |
| `main.py` | 5 | FastAPI service |

## Simulator model (short version)

```
lap_time = driver_pace + compound_base + tyre_age * degr (+ cliff penalty)
           - fuel_effect + rain_penalty + dirty_air + noise
```

Compounds trade base pace for degradation (SOFT fastest base, degrades at
75 ms/lap with a cliff at 22 laps; HARD is 480 ms slower but lasts 45).
Rain penalises dry compounds heavily. Positions are ranked by cumulative
time each lap, so overtakes emerge when cumulative times cross. Pit laps
add ~22.5 s. Everything is seeded — same inputs, same race.
