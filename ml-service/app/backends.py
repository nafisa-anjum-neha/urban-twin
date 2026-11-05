"""Model registry with automatic backend detection (contract §8).

Backend priority at import time:
    1. ``tensorflow``  — keras artifacts in ``models/`` (heaviest, best MAE)
    2. ``scikit-learn``— joblib pickles in ``models/`` (light, quick to retrain)
    3. ``heuristic``   — pure-stdlib physics functions (always available)

All three produce the same response shapes, so the Java side is agnostic to
which one served the request; `health` reports the active backend.
"""

from __future__ import annotations

import logging
import os
from typing import Any, Sequence

from app import heuristics

log = logging.getLogger("urbantwin.ml")

MODELS_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "models")

VERSION = "1.0.0"


class ModelBundle:
    """Loaded artifacts + metadata for one backend."""

    def __init__(self, backend: str, models: dict[str, Any]):
        self.backend = backend
        self.models = models

    def info(self) -> dict[str, Any]:
        return {"backend": self.backend, "artifacts": sorted(self.models.keys())}


# ---------------------------------------------------------------------------
# loading
# ---------------------------------------------------------------------------

def _load() -> ModelBundle:
    # 1. TensorFlow / Keras
    try:
        import tensorflow as tf  # type: ignore

        artifacts: dict[str, Any] = {}
        for name in ("congestion", "waterlevel"):
            path = os.path.join(MODELS_DIR, f"{name}.keras")
            if os.path.exists(path):
                artifacts[name] = tf.keras.models.load_model(path)
        if artifacts:
            log.info("backend=tensorflow artifacts=%s", sorted(artifacts))
            return ModelBundle("tensorflow", artifacts)
    except Exception:  # noqa: BLE001 — absence is the normal path in slim images
        pass

    # 2. scikit-learn
    try:
        import joblib  # type: ignore

        artifacts = {}
        for name in ("congestion", "waterlevel"):
            path = os.path.join(MODELS_DIR, f"{name}.joblib")
            if os.path.exists(path):
                artifacts[name] = joblib.load(path)
        if artifacts:
            log.info("backend=sklearn artifacts=%s", sorted(artifacts))
            return ModelBundle("sklearn", artifacts)
    except Exception:  # noqa: BLE001
        pass

    # 3. heuristic — pure Python, never fails
    log.info("backend=heuristic (no trained artifacts in %s)", MODELS_DIR)
    return ModelBundle("heuristic", {})


BUNDLE: ModelBundle = _load()


def active_backend() -> str:
    return BUNDLE.backend


def model_info() -> dict[str, Any]:
    return BUNDLE.info()


# ---------------------------------------------------------------------------
# inference
# ---------------------------------------------------------------------------

def _sklearn_congestion_features(history: Sequence[Sequence[float]]) -> list[list[float]]:
    """Compact per-history feature row (model input, keep in sync with train.py)."""
    if not history:
        # calm 08:00 weekday default (7 features: sin, cos, weekend, r0..r3)
        return [0.866, 0.5, 0.0, 0.35, 0.35, 0.35, 0.35]
    last = history[-1]
    recent = [float(r[2]) for r in history[-4:]]
    while len(recent) < 4:
        recent.insert(0, recent[0] if recent else 0.35)
    # hour_sin/cos keep the diurnal cycle continuous across midnight
    hour = float(last[0])
    dow = (int(last[1]) - 1) % 7  # Java 1..7 → Python 0..6
    import math

    return [
        math.sin(2 * math.pi * hour / 24.0),
        math.cos(2 * math.pi * hour / 24.0),
        1.0 if dow >= 5 else 0.0,
        recent[0],
        recent[1],
        recent[2],
        recent[3],
    ]


def predict_congestion(history: Sequence[Sequence[float]], horizon_minutes: int) -> dict[str, Any]:
    """Unified congestion forecast → contract response body."""
    hz = max(1, int(horizon_minutes))

    if BUNDLE.backend == "tensorflow" and "congestion" in BUNDLE.models:
        model = BUNDLE.models["congestion"]
        features = _sklearn_congestion_features(history)  # same row layout
        phases = []
        for m in range(5, hz + 1, 5):
            row = list(features) + [m / 60.0]
            value = float(model.predict([row])[0])
            phases.append({"horizonMinute": m, "congestion": round(min(max(value, 0.0), 1.0), 4)})
        return {"backend": BUNDLE.backend, "predictions": _with_confidence(phases, 0.82)}

    if BUNDLE.backend == "sklearn" and "congestion" in BUNDLE.models:
        model = BUNDLE.models["congestion"]
        base = _sklearn_congestion_features(history)
        phases = []
        for m in range(5, hz + 1, 5):
            value = float(model.predict([base + [m / 60.0]])[0])
            phases.append({"horizonMinute": m, "congestion": round(min(max(value, 0.0), 1.0), 4)})
        return {"backend": BUNDLE.backend, "predictions": _with_confidence(phases, 0.78)}

    # heuristic reference path (contract §8 fallback)
    phases = heuristics.predict_congestion_heuristic(history, hz)
    for p in phases:
        p["confidence"] = 0.75
    return {"backend": BUNDLE.backend, "predictions": phases}


def predict_waterlogging(
    station_readings: Sequence[Sequence[float]],
    network_capacity_m3h: float,
    horizon_minutes: int,
) -> dict[str, Any]:
    """Unified waterlogging forecast → contract response body.

    `[waterLevelCm, flowRateLps, rainfallMm]` triples are expanded to the
    reading dicts the heuristics/model expect (minAgo stacked 15 min apart).
    """
    rows = list(station_readings)
    readings = [
        {
            "waterLevelCm": float(row[0]),
            "flowRateLps": float(row[1]),
            "rainfallMm": float(row[2]),
            "minAgo": i * 15,  # row 0 = latest reading (Java sends newest first)
        }
        for i, row in enumerate(rows)
    ]

    current_level = max((r["waterLevelCm"] for r in readings), default=0.0)
    hz = max(1, int(horizon_minutes))
    capacity = max(float(network_capacity_m3h), 1.0)

    if BUNDLE.backend == "tensorflow" and "waterlevel" in BUNDLE.models:
        model = BUNDLE.models["waterlevel"]
        features = _waterlevel_features(readings, capacity)
        level = float(model.predict([features])[0])
        peak = round(min(max(level, current_level), heuristics.MAX_LEVEL_CM), 2)
        conf = 0.82
    elif BUNDLE.backend == "sklearn" and "waterlevel" in BUNDLE.models:
        model = BUNDLE.models["waterlevel"]
        peak = float(model.predict([_waterlevel_features(readings, capacity)])[0])
        # never forecast below the observed level: water recedes after peaks,
        # it does not vanish mid-event
        peak = round(min(max(peak, current_level), heuristics.MAX_LEVEL_CM), 2)
        conf = 0.78
    else:
        peak = heuristics.predict_max_water_level_heuristic(readings, capacity, hz)
        # confidence decays with how far the projection moves past observations
        conf = round(max(0.45, 1.0 - abs(peak - current_level) / 120.0), 2)

    return {
        "backend": BUNDLE.backend,
        "risk": heuristics.classify_risk(peak),
        "maxLevelCm": peak,
        "confidence": conf,
        "details": {
            "currentLevelCm": round(current_level, 2),
            "capacityM3h": capacity,
            "horizonMinutes": hz,
        },
    }


def _with_confidence(phases: list[dict[str, Any]], confidence: float) -> list[dict[str, Any]]:
    for p in phases:
        p["confidence"] = confidence
    return phases


def _waterlevel_features(readings: Sequence[dict[str, Any]], capacity: float) -> list[float]:
    """Flat feature row for trained water-level regressors (sync with train.py)."""
    level = max((r["waterLevelCm"] for r in readings), default=0.0)
    rain60 = sum(r["rainfallMm"] for r in readings if r["minAgo"] <= 60)
    rain120 = sum(r["rainfallMm"] for r in readings if r["minAgo"] <= 120)
    flow = sum(r["flowRateLps"] for r in readings)
    load = (heuristics.RAIN_TO_RUNOFF * rain60) / capacity
    return [level, rain60, rain120 - rain60, flow, load, capacity / 1000.0]
