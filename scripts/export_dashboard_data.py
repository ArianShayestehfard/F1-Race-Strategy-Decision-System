"""Run the strategy search and write dashboard/data.js for the UI."""
import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from f1_strategy import DEFAULT_MODEL, best_by_stops, lap_time, load_model  # noqa: E402

ap = argparse.ArgumentParser()
ap.add_argument("--model", help="calibrated model JSON (default: built-in model)")
ap.add_argument("--laps", type=int, default=57)
ap.add_argument("--race", default="Demo Grand Prix")
args = ap.parse_args()

model = load_model(args.model) if args.model else DEFAULT_MODEL
LAPS = args.laps
best = best_by_stops(LAPS, model=model)
fastest = min(t for t, _ in best.values())
data = {
    "race": args.race,
    "laps": LAPS,
    "strategies": [
        {"stops": s, "time": round(t, 3), "delta": round(t - fastest, 3),
         "stints": [{"compound": c, "laps": n} for c, n in st]}
        for s, (t, st) in sorted(best.items())
    ],
    "degradation": {c: [round(lap_time(c, a, 20, model), 3) for a in range(0, 41)] for c in model.names},
}
(ROOT / "dashboard" / "data.js").write_text(
    "window.F1_DATA = " + json.dumps(data, indent=1) + ";\n", encoding="utf-8")
print("wrote dashboard/data.js")
