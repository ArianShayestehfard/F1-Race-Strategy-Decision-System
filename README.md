# F1 Race Strategy Decision System

A small, testable engine that compares pit-stop strategies for a Formula 1 race and a dashboard that presents the recommendation.

## What it does

- Models lap time as a function of compound, tyre age and fuel load, including a degradation cliff.
- Searches 1, 2 and 3 stop plans (minimum two compounds, minimum stint length) and returns the fastest plan for each stop count.
- Exports the results to a dashboard where you can compare plans and see the degradation curves.

- Loads real race laps with FastF1, cleans them (no lap 1, pit laps, safety car or yellow laps, wet tyres, outliers) and fits the model by least squares with per-driver pace offsets and a grid-searched degradation cliff per compound. Pit loss is estimated from real in/out laps.

The built-in constants are illustrative defaults; a calibrated model replaces them.

## Project layout

```
src/f1_strategy/   strategy.py (model + search), data.py (FastF1 + cleaning), calibrate.py
models/            calibrated model JSON files
tests/             pytest suite
scripts/           calibrate.py (fit from a real race), export_dashboard_data.py
dashboard/         static UI (open index.html in a browser)
```

## Quick start

```bash
pip install -r requirements.txt
pytest                                   # run tests

# Calibrate from a real race (downloads data on first run)
python scripts/calibrate.py --year 2024 --gp Bahrain

# Build the dashboard data from that model and open the UI
python scripts/export_dashboard_data.py --model models/bahrain_2024.json --laps 57 --race "Bahrain 2024"
open dashboard/index.html                # or double-click the file
```

## Roadmap

- [x] Data pipeline: load race laps from FastF1 and clean outliers (pit laps, safety cars)
- [x] Fit per-compound degradation models from real data
- [ ] Validate calibrated models against actual race strategies
- [ ] Add safety car and weather scenarios
- [ ] Live lap-by-lap recommendations

## License

MIT, see [LICENSE](LICENSE).
