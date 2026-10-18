"""Load race laps (FastF1) and prepare them for calibration."""
from pathlib import Path

import pandas as pd

COMPOUNDS = ("SOFT", "MEDIUM", "HARD")
CLEAN_COLUMNS = ["Driver", "LapNumber", "Compound", "TyreLife", "LapTimeSeconds"]


def load_race_laps(year: int, grand_prix: str, session: str = "R",
                   cache_dir: str = "data/cache") -> pd.DataFrame:
    """Download (and cache) all laps of a session using FastF1."""
    try:
        import fastf1
    except ImportError as exc:
        raise ImportError("Install FastF1 first: pip install fastf1") from exc
    Path(cache_dir).mkdir(parents=True, exist_ok=True)
    fastf1.Cache.enable_cache(cache_dir)
    ses = fastf1.get_session(year, grand_prix, session)
    ses.load(telemetry=False, weather=False, messages=False)
    laps = pd.DataFrame(ses.laps)
    laps["LapTimeSeconds"] = laps["LapTime"].dt.total_seconds()
    return laps


def _green_mask(df: pd.DataFrame) -> pd.Series:
    return df["TrackStatus"].astype(str).eq("1")


def clean_laps(laps: pd.DataFrame, outlier_pct: float = 0.07) -> pd.DataFrame:
    """Keep representative green-flag racing laps on dry compounds.

    Drops lap 1, in/out laps, safety car / VSC / yellow laps, inaccurate or
    deleted laps, and laps more than `outlier_pct` slower than the driver median.
    """
    df = laps.copy()
    keep = df["LapTimeSeconds"].notna() & df["TyreLife"].notna()
    keep &= df["Compound"].isin(COMPOUNDS)
    keep &= df["PitInTime"].isna() & df["PitOutTime"].isna()
    keep &= df["LapNumber"] > 1
    keep &= _green_mask(df)
    if "IsAccurate" in df:
        keep &= df["IsAccurate"].fillna(False).astype(bool)
    if "Deleted" in df:
        keep &= ~df["Deleted"].fillna(False).astype(bool)
    df = df[keep]
    median = df.groupby("Driver")["LapTimeSeconds"].transform("median")
    df = df[df["LapTimeSeconds"] <= median * (1 + outlier_pct)]
    return df[CLEAN_COLUMNS].reset_index(drop=True)


def estimate_pit_loss(laps: pd.DataFrame, default: float = 21.5) -> float:
    """Median time lost per stop: (in-lap + out-lap) minus two normal laps."""
    clean = clean_laps(laps)
    ref = clean.groupby("Driver")["LapTimeSeconds"].median().rename("ref")
    ins = laps[laps["PitInTime"].notna() & _green_mask(laps)]
    outs = laps[laps["PitOutTime"].notna() & _green_mask(laps)].copy()
    outs["LapNumber"] -= 1  # align the out-lap with the in-lap before it
    pairs = ins.merge(outs, on=["Driver", "LapNumber"], suffixes=("_in", "_out"))
    pairs = pairs.join(ref, on="Driver").dropna(
        subset=["ref", "LapTimeSeconds_in", "LapTimeSeconds_out"])
    if pairs.empty:
        return default
    loss = pairs["LapTimeSeconds_in"] + pairs["LapTimeSeconds_out"] - 2 * pairs["ref"]
    return float(loss.median())
