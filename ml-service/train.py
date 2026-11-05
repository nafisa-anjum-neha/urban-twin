"""Offline trainer for the UrbanTwin ML service.

Generates synthetic training data from the physics heuristics in
`app/heuristics.py` (so models learn a target distribution close to the
reference curves), fits scikit-learn gradient-boosted regressors, and writes
`models/congestion.joblib` + `models/waterlevel.joblib`.

TensorFlow artifacts (`models/*.keras`) are produced the same way if
tensorflow is installed — an equivalent Keras MLP replaces the sklearn
regressor, same feature layout (see `app/backends.py`).

Run inside the container or locally:
    python train.py
"""

from __future__ import annotations

import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from app import heuristics  # noqa: E402

MODELS_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "models")
DOW_OFFSET_FOR_JAVA = 1  # Java dayOfWeek is 1..7 (Mon=1); heuristics take 0..6


def build_congestion_dataset(n_samples: int = 4000, seed: int = 42):
    """[features] → [target: congestion in 5..180 min]."""
    rng = random.Random(seed)
    xs, ys = [], []
    for _ in range(n_samples):
        dow = rng.randrange(7)
        hour = rng.uniform(0, 24)
        persistence = heuristics.clamp(heuristics.diurnal_congestion(hour, dow) + rng.gauss(0, 0.1), 0, 1)
        horizon = rng.randrange(5, 181, 5)

        recent = [heuristics.clamp(heuristics.diurnal_congestion(hour - i * 0.25, dow) + rng.gauss(0, 0.05), 0, 1) for i in range(4)]
        w = 0.45 * math.exp(-horizon / 75.0)
        absolute = hour * 60.0 + horizon
        target_hour = (absolute % 1440) / 60.0
        target_dow = int(dow + absolute // 1440) % 7
        target = (1 - w) * heuristics.diurnal_congestion(target_hour, target_dow) + w * persistence

        xs.append([
            math.sin(2 * math.pi * hour / 24.0),
            math.cos(2 * math.pi * hour / 24.0),
            1.0 if dow >= 5 else 0.0,
            recent[0], recent[1], recent[2], recent[3],
            horizon / 60.0,
        ])
        ys.append(heuristics.clamp(target, 0.0, 1.0))
    return xs, ys


def build_waterlevel_dataset(n_samples: int = 3000, seed: int = 7):
    rng = random.Random(seed)
    xs, ys = [], []
    for _ in range(n_samples):
        capacity = rng.choice([900, 1200, 1500, 1800, 2200, 2800, 3500])
        current = rng.uniform(5, 120)
        rain_rate = max(0.0, rng.gauss(8, 10))
        still_to_arrive = rain_rate * rng.uniform(0.2, 0.8)
        flow = 40 + current * 12 + rain_rate * 8

        readings = []
        for min_ago in (0, 15, 30, 45, 60):
            readings.append({
                "waterLevelCm": current,
                "flowRateLps": flow,
                "rainfallMm": rain_rate * 0.25 * (1 + rng.uniform(-0.1, 0.1)),
                "minAgo": min_ago,
            })
        # roll the reference physics forward — the model learns its response surface
        target = heuristics.predict_max_water_level_heuristic(
            sorted(readings, key=lambda r: r["minAgo"], reverse=True), capacity, rng.choice([30, 60, 90])
        )
        load = (heuristics.RAIN_TO_RUNOFF * rain_rate) / capacity
        xs.append([current, rain_rate * 0.25, rain_rate * 0.25, flow, load, capacity / 1000.0])
        ys.append(target)
    return xs, ys


def main() -> int:
    os.makedirs(MODELS_DIR, exist_ok=True)

    # ---- scikit-learn path -------------------------------------------------
    try:
        from sklearn.ensemble import GradientBoostingRegressor  # type: ignore
        import joblib  # type: ignore
        from sklearn.metrics import mean_absolute_error

        xs, ys = build_congestion_dataset()
        model = GradientBoostingRegressor(n_estimators=200, max_depth=4, learning_rate=0.08)
        model.fit(xs, ys)
        mae = mean_absolute_error(ys, model.predict(xs))
        joblib.dump(model, os.path.join(MODELS_DIR, "congestion.joblib"))
        print(f"[train] congestion.joblib  n={len(xs)} in-sample MAE={mae:.4f}")

        xs, ys = build_waterlevel_dataset()
        wmodel = GradientBoostingRegressor(n_estimators=200, max_depth=4, learning_rate=0.08)
        wmodel.fit(xs, ys)
        mae = mean_absolute_error(ys, wmodel.predict(xs))
        joblib.dump(wmodel, os.path.join(MODELS_DIR, "waterlevel.joblib"))
        print(f"[train] waterlevel.joblib  n={len(xs)} in-sample MAE={mae:.2f} cm")
        return 0
    except ImportError:
        print("[train] scikit-learn not installed — heuristic backend remains active")

    # ---- TensorFlow path ( keras MLP on the same feature layout ) ----------
    try:
        import tensorflow as tf  # type: ignore

        xs, ys = build_congestion_dataset()
        m = tf.keras.Sequential([
            tf.keras.layers.Input(shape=(8,)),
            tf.keras.layers.Dense(32, activation="relu"),
            tf.keras.layers.Dense(16, activation="relu"),
            tf.keras.layers.Dense(1, activation="sigmoid"),
        ])
        m.compile(optimizer="adam", loss="mae")
        m.fit(xs, ys, epochs=8, batch_size=64, verbose=0)
        m.save(os.path.join(MODELS_DIR, "congestion.keras"))
        print(f"[train] congestion.keras n={len(xs)}")

        xs, ys = build_waterlevel_dataset()
        w = tf.keras.Sequential([
            tf.keras.layers.Input(shape=(6,)),
            tf.keras.layers.Dense(32, activation="relu"),
            tf.keras.layers.Dense(16, activation="relu"),
            tf.keras.layers.Dense(1),
        ])
        w.compile(optimizer="adam", loss="mae")
        w.fit(xs, ys, epochs=8, batch_size=64, verbose=0)
        w.save(os.path.join(MODELS_DIR, "waterlevel.keras"))
        print(f"[train] waterlevel.keras n={len(xs)}")
        return 0
    except ImportError:
        print("[train] tensorflow not installed — nothing trained (heuristic backend active)")
        return 0


if __name__ == "__main__":
    raise SystemExit(main())
