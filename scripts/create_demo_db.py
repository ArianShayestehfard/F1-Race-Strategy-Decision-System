"""(Re)create the demo race in database/race.db — offline, no network needed."""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from f1_strategy.ingest import generate_demo_race  # noqa: E402

if __name__ == "__main__":
    p = argparse.ArgumentParser(description="Generate a demo race into database/race.db")
    p.add_argument("--year", type=int, default=2024)
    p.add_argument("--round", dest="rnd", type=int, default=8)
    p.add_argument("--laps", type=int, default=57)
    args = p.parse_args()
    print(generate_demo_race(year=args.year, rnd=args.rnd, laps=args.laps))
