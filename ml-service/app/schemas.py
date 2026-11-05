"""Pydantic request/response schemas (contract §8 — ML API shapes).

The Java backend posts these exact bodies (see `com.urbantwin.ml.MlClient`);
field names and the `predictions[]` / top-level keys are part of the contract.
"""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class CongestionRequest(BaseModel):
    """`POST /ml/v1/predict/congestion`.

    `history` rows are `[hourOfDay(0-23), dayOfWeek(1-7 Mon=1 Java, congestion)]`
    triples, oldest first, subsampled by the backend to ≤ 400 points.
    """

    zoneId: str | None = None
    history: list[list[float]] = Field(default_factory=list)
    horizonMinutes: int = Field(default=30, ge=1, le=180)


class PredictionPhase(BaseModel):
    horizonMinute: int
    congestion: float
    confidence: float = 0.75


class CongestionResponse(BaseModel):
    zoneId: str | None = None
    backend: str
    predictions: list[PredictionPhase]


class WaterloggingRequest(BaseModel):
    """`POST /ml/v1/predict/waterlogging`.

    `stationReadings` rows are `[waterLevelCm, flowRateLps, rainfallMm]`
    triples — the latest reading of each gauge in the zone.
    """

    zoneId: str | None = None
    stationReadings: list[list[float]] = Field(default_factory=list)
    networkCapacityM3h: float = Field(default=1500, ge=1)
    horizonMinutes: int = Field(default=60, ge=1, le=360)


class WaterloggingResponse(BaseModel):
    zoneId: str | None = None
    backend: str
    risk: str
    maxLevelCm: float
    confidence: float
    details: dict[str, Any] = Field(default_factory=dict)


class HealthResponse(BaseModel):
    status: str
    backend: str
    version: str
    models: dict[str, Any] = Field(default_factory=dict)
