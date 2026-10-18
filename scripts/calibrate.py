"""Fit the tyre model from a real race: python scripts/calibrate.py --year 2024 --gp Bahrain"""
import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from f1_strategy.calibrate import calibrate  # noqa: E402
from f1_strategy.data import clean_laps, estimate_pit_loss, load_race_laps  # noqa: E402
from f1_strategy.strategy import save_model  # noqa: E402

p = argparse.ArgumentParser()
p.add_argument("--year", type=int, required=True)
p.add_argument("--gp", required=True, help='e.g. "Bahrain" or "Monza"')
p.add_argument("--session", default="R")
p.add_argument("--out", help="output JSON (default models/<gp>_<year>.json)")
a = p.parse_args()

raw = load_race_laps(a.year, a.gp, a.session, cache_dir=str(ROOT / "data" / "cache"))
model, metrics = calibrate(clean_laps(raw), pit_loss=estimate_pit_loss(raw))
out = Path(a.out) if a.out else ROOT / "models" / f"{a.gp.lower().replace(' ', '_')}_{a.year}.json"
save_model(model, out, meta={"year": a.year, "grand_prix": a.gp, **metrics})
print(f"Saved {out}\n{metrics}\n{model}")
