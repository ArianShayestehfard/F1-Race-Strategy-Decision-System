"""FastAPI service exposing ingest / analyze / predict / simulate /
strategy tools to the Spring Boot backend (and the web dashboard)."""
from __future__ import annotations

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from .. import analysis, ingest, ml, racecalib, recommend, simulator
from ..db import connect, db_path

app = FastAPI(title="F1 Race Strategy Engine", version="1.0")
app.add_middleware(
    CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"]
)


class IngestRequest(BaseModel):
    year: int
    round: int
    session: str = "R"
    source: str = Field("openf1", description="openf1 (real data, no auth) | fastf1 (official server)")
    demo: bool = Field(False, description="generate an offline demo race instead of downloading")


class PredictRequest(BaseModel):
    race_id: str
    driver_id: str
    model: str = "gradient_boosting"


class SimulateRequest(BaseModel):
    race_id: str
    driver_id: str
    start_compound: str = "MEDIUM"
    pit_laps: list[int] = []
    pit_stop_overhead_s: float = 22.5


class RecommendRequest(BaseModel):
    race_id: str
    driver_id: str
    current_lap: int
    current_compound: str = "MEDIUM"
    current_tyre_age: int = 1


class DuelRequest(BaseModel):
    race_id: str
    driver_id: str
    rival_id: str
    current_lap: int
    current_compound: str = "MEDIUM"


class SearchRequest(BaseModel):
    race_id: str


def _maybe_calibrate(race_id: str) -> None:
    """Best effort: after an ingest, fit a per-race model when the data allows."""
    try:
        racecalib.calibrate_race(race_id)
    except Exception:
        pass  # too few clean dry laps (e.g. wet demo) -> keep the default model


@app.get("/health")
def health() -> dict:
    conn = connect()
    try:
        races = conn.execute("SELECT COUNT(*) c FROM races").fetchone()["c"]
        trained = len(list((ml.ARTIFACTS).glob("*.joblib"))) if ml.ARTIFACTS.exists() else 0
        return {"status": "ok", "db": str(db_path()), "races": races, "models_trained": trained}
    finally:
        conn.close()


@app.get("/races")
def races() -> list[dict]:
    conn = connect()
    try:
        rows = conn.execute(
            "SELECT race_id, year, round, name, circuit, race_date, total_laps"
            " FROM races ORDER BY year, round").fetchall()
        return [dict(r) for r in rows]
    finally:
        conn.close()


@app.get("/races/{race_id}/drivers")
def race_drivers(race_id: str) -> list[dict]:
    conn = connect()
    try:
        rows = conn.execute(
            "SELECT DISTINCT d.driver_id, d.full_name, d.code, d.team"
            " FROM drivers d JOIN laps l ON l.driver_id = d.driver_id"
            " WHERE l.race_id=? ORDER BY d.driver_id", (race_id,)).fetchall()
        return [dict(r) for r in rows]
    finally:
        conn.close()


@app.post("/ingest")
def ingest_endpoint(req: IngestRequest) -> dict:
    try:
        if req.demo:
            result = ingest.generate_demo_race(year=req.year or 2024, rnd=req.round or 8)
        elif req.source == "openf1":
            from ..openf1_ingest import ingest_openf1
            result = ingest_openf1(req.year, req.round)
        else:
            result = ingest.ingest_race(req.year, req.round, req.session)
    except Exception as exc:  # network errors, bad round, ...
        raise HTTPException(status_code=502, detail=f"ingest failed: {exc}") from exc
    _maybe_calibrate(result["race_id"])
    return result


@app.post("/calibrate/{race_id}")
def calibrate_endpoint(race_id: str) -> dict:
    try:
        return racecalib.calibrate_race(race_id)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc


@app.post("/strategy/search")
def strategy_search(req: SearchRequest) -> dict:
    try:
        return racecalib.search_plans(req.race_id)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


@app.get("/analyze/{race_id}/{driver_id}")
def analyze(race_id: str, driver_id: str) -> dict:
    conn = connect()
    try:
        try:
            return analysis.driver_report(conn, race_id, driver_id)
        except ValueError as exc:
            raise HTTPException(status_code=404, detail=str(exc)) from exc
    finally:
        conn.close()


@app.post("/predict")
def predict(req: PredictRequest) -> dict:
    try:
        return ml.predict(req.race_id, req.driver_id, req.model)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"prediction failed: {exc}") from exc


@app.post("/simulate")
def simulate(req: SimulateRequest) -> dict:
    try:
        return simulator.simulate_race(req.race_id, req.driver_id, req.start_compound,
                                       req.pit_laps, pit_stop_overhead_s=req.pit_stop_overhead_s)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


@app.post("/strategy/recommend")
def recommend_endpoint(req: RecommendRequest) -> dict:
    try:
        return recommend.recommend_strategy(req.race_id, req.driver_id, req.current_lap,
                                            req.current_compound, req.current_tyre_age)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


@app.post("/strategy/duel")
def duel_endpoint(req: DuelRequest) -> dict:
    try:
        return recommend.duel_strategy(req.race_id, req.driver_id, req.rival_id,
                                       req.current_lap, req.current_compound)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


if __name__ == "__main__":
    # lets you right-click -> Run in IntelliJ/PyCharm instead of using uvicorn CLI
    import uvicorn
    uvicorn.run(app, host="127.0.0.1", port=8000)
