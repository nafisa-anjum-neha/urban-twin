"""UrbanTwin ML microservice (FastAPI) — contract §8.

Endpoints
---------
GET  /ml/v1/health                  → backend + model status
POST /ml/v1/predict/congestion      → per-5-min congestion phases
POST /ml/v1/predict/waterlogging    → risk band + projected peak level (cm)

The backend (tensorflow / sklearn / heuristic) is auto-detected at import;
the response bodies are identical across backends, so the Java side and the
heuristic fallback chain stay agnostic.
"""

from __future__ import annotations

import logging

from fastapi import FastAPI

from app import backends
from app.schemas import (
    CongestionRequest,
    CongestionResponse,
    HealthResponse,
    PredictionPhase,
    WaterloggingRequest,
    WaterloggingResponse,
)

logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
log = logging.getLogger("urbantwin.ml")

app = FastAPI(
    title="UrbanTwin ML Service",
    description="Congestion + waterlogging forecasting for the UrbanTwin digital twin "
    "(contract docs/CONTRACT.md §8). Auto-detects TensorFlow / scikit-learn "
    "artifacts and degrades to pure-Python heuristics.",
    version="1.0.0",
)


@app.get("/ml/v1/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(
        status="ok",
        backend=backends.active_backend(),
        version=backends.VERSION,
        models=backends.model_info(),
    )


@app.get("/health", response_model=HealthResponse, include_in_schema=False)
def health_alias() -> HealthResponse:
    """Conventional liveness path — identical payload to /ml/v1/health so
    standard probes / orchestrator health checks don't 404."""
    return health()


@app.post(
    "/ml/v1/predict/congestion",
    response_model=CongestionResponse,
    summary="Forecast zone congestion in 5-minute steps",
)
def congestion(req: CongestionRequest) -> CongestionResponse:
    result = backends.predict_congestion(req.history, req.horizonMinutes)
    phases = [PredictionPhase(**p) for p in result["predictions"]]
    return CongestionResponse(
        zoneId=req.zoneId,
        backend=result["backend"],
        predictions=phases,
    )


@app.post(
    "/ml/v1/predict/waterlogging",
    response_model=WaterloggingResponse,
    summary="Forecast the peak water level and risk band",
)
def waterlogging(req: WaterloggingRequest) -> WaterloggingResponse:
    result = backends.predict_waterlogging(
        req.stationReadings, req.networkCapacityM3h, req.horizonMinutes
    )
    return WaterloggingResponse(
        zoneId=req.zoneId,
        backend=result["backend"],
        risk=result["risk"],
        maxLevelCm=result["maxLevelCm"],
        confidence=result["confidence"],
        details=result["details"],
    )


@app.get("/")
def root() -> dict[str, str]:
    return {"service": "urbantwin-ml", "docs": "/docs", "health": "/ml/v1/health"}
