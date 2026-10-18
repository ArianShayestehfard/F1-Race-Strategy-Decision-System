"""Tyre degradation model and pit-stop strategy search.

Lap time = base + compound offset + linear wear + cliff penalty - fuel burn gain.
`DEFAULT_MODEL` holds illustrative constants; use `calibrate.calibrate` to fit a
`Model` from real race laps and pass it via the `model` argument.
"""
import json
from dataclasses import asdict, dataclass
from functools import lru_cache
from itertools import combinations, product
from pathlib import Path

MIN_STINT = 8


@dataclass(frozen=True)
class Compound:
    offset: float          # pace delta vs. the fastest compound (s)
    wear: float            # linear degradation (s per lap of tyre age)
    cliff_age: int         # age after which degradation accelerates
    cliff_penalty: float   # extra seconds per lap beyond the cliff


@dataclass(frozen=True)
class Model:
    base_lap: float = 92.0    # reference lap time, fresh tyres, no fuel effect (s)
    fuel_gain: float = 0.035  # seconds gained per lap as the car gets lighter
    pit_loss: float = 21.5    # seconds lost per stop (in-lap + out-lap + stationary)
    compounds: tuple = (
        ("SOFT", Compound(0.00, 0.095, 14, 0.35)),
        ("MEDIUM", Compound(0.45, 0.055, 24, 0.30)),
        ("HARD", Compound(0.85, 0.032, 36, 0.25)),
    )

    @property
    def names(self):
        return tuple(n for n, _ in self.compounds)

    def compound(self, name: str) -> Compound:
        return dict(self.compounds)[name]

    def to_dict(self) -> dict:
        d = asdict(self)
        d["compounds"] = {n: asdict(c) for n, c in self.compounds}
        return d

    @classmethod
    def from_dict(cls, d: dict) -> "Model":
        comps = tuple((n, Compound(**c)) for n, c in d["compounds"].items())
        return cls(d["base_lap"], d["fuel_gain"], d["pit_loss"], comps)


DEFAULT_MODEL = Model()
PARAMS = dict(DEFAULT_MODEL.compounds)


def save_model(model: Model, path, meta: dict | None = None) -> None:
    data = model.to_dict()
    if meta:
        data["meta"] = meta
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(json.dumps(data, indent=2), encoding="utf-8")


def load_model(path) -> Model:
    return Model.from_dict(json.loads(Path(path).read_text(encoding="utf-8")))


def lap_time(compound: str, age: int, lap: int, model: Model = DEFAULT_MODEL) -> float:
    c = model.compound(compound)
    t = model.base_lap + c.offset + c.wear * age - model.fuel_gain * lap
    if age > c.cliff_age:
        t += c.cliff_penalty * (age - c.cliff_age)
    return t


@lru_cache(maxsize=None)
def _stint_time(model: Model, compound: str, start_lap: int, length: int) -> float:
    return sum(lap_time(compound, a, start_lap + a, model) for a in range(length))


def race_time(stints, total_laps: int | None = None, model: Model = DEFAULT_MODEL) -> float:
    """Total race time for stints = [(compound, laps), ...]."""
    if total_laps is not None and sum(n for _, n in stints) != total_laps:
        raise ValueError("stint lengths must add up to total_laps")
    t, lap = 0.0, 1
    for compound, n in stints:
        t += _stint_time(model, compound, lap, n)
        lap += n
    return t + model.pit_loss * (len(stints) - 1)


def best_by_stops(total_laps: int, max_stops: int = 3, step: int = 2,
                  model: Model = DEFAULT_MODEL):
    """Best valid strategy for each stop count (needs >= 2 compounds)."""
    results = {}
    for stops in range(1, max_stops + 1):
        best = None
        points = range(MIN_STINT, total_laps - MIN_STINT + 1, step)
        for cuts in combinations(points, stops):
            bounds = (0, *cuts, total_laps)
            lengths = [b - a for a, b in zip(bounds, bounds[1:])]
            if min(lengths) < MIN_STINT:
                continue
            for seq in product(model.names, repeat=stops + 1):
                if len(set(seq)) < 2:
                    continue
                stints = tuple(zip(seq, lengths))
                t = race_time(stints, model=model)
                if best is None or t < best[0]:
                    best = (t, stints)
        if best:
            results[stops] = best
    return results
