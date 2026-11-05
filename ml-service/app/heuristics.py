"""Physics-style heuristic predictors for the UrbanTwin ML service.

These functions are pure-Python (stdlib only) reference implementations of the
two forecasting tasks served by ``/ml/v1/predict/*``.  They are used:

* as the statistical fallback when no trained model is available
  (TensorFlow / scikit-learn not installed, or models not trained yet), and
* as the generator of "ground truth" behaviour inside ``train.py`` so the
  trained models learn a target distribution close to the fallback curve.

Physics-style reasoning
-----------------------

Congestion (diurnal traffic curves):
    Urban traffic demand follows a strongly diurnal "travel need" curve: two
    attraction peaks on weekdays (journey-to-work ~08:30 and return home
    ~17:45) and a single broad late-morning leisure peak on weekends, with a
    deep trough at night.  Congestion is additionally *persistent* over short
    horizons: queues take 30-90 minutes to dissipate, roughly exponentially.
    The forecast therefore blends a recency-weighted moving average of the
    latest readings (the "persistence" term) with the expected diurnal curve,
    where the persistence weight decays exponentially with forecast offset.

Waterlogging (rainfall accumulation vs. network capacity):
    Rainfall depth (mm) over the contributing catchment generates runoff
    approximately proportional to the rain *rate* (mm/h), scaled here by a
    nominal runoff coefficient (``RAIN_TO_RUNOFF`` m3/h per mm/h).  The
    drainage network can evacuate at most ``networkCapacityM3h``; any excess
    inflow accumulates as standing water and raises the level at a rate
    proportional to the excess.  Rain also reaches the sensor with a lag
    (gutter/pipe travel time), so rain that fell 60-120 minutes ago is still
    arriving progressively.  Once rain stops, the level recedes at a
    capacity-limited drain rate until the network catches up.
"""

from __future__ import annotations

import math
from typing import Any, Mapping, Sequence

# --- Tunable physical constants (shared with the ML feature builders) ------
#: Cubic metres of runoff per millimetre of rain, per nominal catchment unit.
#: 1 mm of rain over ~1.2 ha is roughly 12 m3.
RAIN_TO_RUNOFF: float = 12.0

#: Centimetres of water-level rise per cubic metre of *excess* runoff.
EXCESS_TO_LEVEL_CM: float = 0.15

#: Fraction of rain that fell 60-120 minutes ago which is *still arriving*
#: now, because of pipe/gutter travel lag.
RAIN_LAG_FRACTION: float = 0.5

#: Recession speed bounds (cm per 5-minute step) once rain stops.
RECESSION_MIN: float = 0.15
RECESSION_MAX: float = 1.0

#: Physical ceiling applied to simulations and forecasts (cm).
MAX_LEVEL_CM: float = 180.0

#: Waterlogging severity bands in cm (contract risk vocabulary).
RISK_THRESHOLDS_CM: tuple[tuple[float, str], ...] = (
    (25.0, "low"),
    (50.0, "moderate"),
    (85.0, "high"),
)


def clamp(value: float, lo: float, hi: float) -> float:
    """Constrain ``value`` to the closed interval ``[lo, hi]``."""
    if value < lo:
        return lo
    if value > hi:
        return hi
    return value


def classify_risk(level_cm: float) -> str:
    """Map a forecast water level (cm) to ``low|moderate|high|severe``.

    Bands: < 25 cm low, < 50 cm moderate, < 85 cm high, else severe — both the
    heuristic path and the trained-model path use this single classifier so
    risk labels are consistent across backends.
    """
    for threshold, risk in RISK_THRESHOLDS_CM:
        if level_cm < threshold:
            return risk
    return "severe"


def diurnal_congestion(hour_of_day: float, day_of_week: int) -> float:
    """Expected congestion level (0..1) at a given moment of the week.

    Weekdays show a narrow morning peak (~08:30) plus a slightly wider
    evening return peak (~17:45).  Weekends peak midday (~13:30) with a small
    evening leisure bump, at roughly 35 % lower overall demand.
    """
    h = float(hour_of_day) % 24.0
    if int(day_of_week) % 7 >= 5:  # Saturday / Sunday
        v = (
            0.14
            + 0.42 * math.exp(-(((h - 13.5) / 2.7) ** 2))
            + 0.16 * math.exp(-(((h - 20.5) / 2.4) ** 2))
        )
    else:
        v = (
            0.16
            + 0.66 * math.exp(-(((h - 8.5) / 1.7) ** 2))
            + 0.60 * math.exp(-(((h - 17.75) / 2.2) ** 2))
            + 0.08 * math.exp(-(((h - 21.5) / 1.8) ** 2))
        )
    return clamp(v, 0.0, 1.0)


def predict_congestion_heuristic(
    history: Sequence[Sequence[float]], horizon_minutes: int
) -> list[dict[str, Any]]:
    """Forecast congestion for every 5-minute step up to ``horizon_minutes``.

    Parameters
    ----------
    history:
        Readings as ``[hourOfDay(0-23), dayOfWeek(0-6, Mon=0), congestion]``
        triples, oldest first, assumed to be regular-ish samples.
    horizon_minutes:
        Forecast length; steps are multiples of 5 minutes.

    Returns
    -------
    list of ``{"horizonMinute": int, "congestion": float}`` (0..1).

    Logic: (1) a recency-weighted moving average of the last 4 readings gives
    the current congestion "persistence" level, with weights 1:2:4:8 (the
    newest reading dominates); (2) each future step relaxes toward the
    physical diurnal curve with an exponential persistence weight
    ``0.45 * exp(-offset/75)``, i.e. queues wash out within roughly an hour.
    """
    horizon = max(1, int(horizon_minutes))
    if history:
        last = history[-1]
        base_minute_of_day = float(last[0]) * 60.0 + 30.0  # assume mid-of-hour
        base_dow = int(last[1]) % 7
        recent = [float(row[2]) for row in history[-4:]]
        weights = (1.0, 2.0, 4.0, 8.0)[-len(recent):]
        total = sum(weights)
        persistence = sum(w * c for w, c in zip(weights, recent)) / total
    else:
        base_minute_of_day = 8.5 * 60.0  # assume a weekday morning
        base_dow = 0
        persistence = 0.35

    predictions: list[dict[str, Any]] = []
    step = 5 if horizon >= 5 else horizon
    for offset in range(step, horizon + 1, step):
        absolute_minute = base_minute_of_day + offset
        target_dow = int(base_dow + absolute_minute // 1440) % 7
        target_hour = (absolute_minute % 1440) / 60.0
        w_persist = 0.45 * math.exp(-offset / 75.0)
        value = (1.0 - w_persist) * diurnal_congestion(
            target_hour, target_dow
        ) + w_persist * persistence
        predictions.append(
            {
                "horizonMinute": int(offset),
                "congestion": round(clamp(value, 0.0, 1.0), 4),
            }
        )
    return predictions


def predict_max_water_level_heuristic(
    station_readings: Sequence[Mapping[str, Any]],
    network_capacity_m3h: float,
    horizon_minutes: int,
) -> float:
    """Forecast the maximum water level (cm) within the horizon.

    Parameters
    ----------
    station_readings:
        ``{"waterLevelCm": float, "rainfallMm": float, "minAgo": int}`` dicts.
        ``minAgo`` is minutes before "now"; ``rainfallMm`` is the rain that
        accumulated during that reading interval (tipping-bucket style).
    network_capacity_m3h:
        Evacuation capacity of the drainage network (m3/h).
    horizon_minutes:
        Numerically roll the level forward this many minutes.

    Returns
    -------
    float — maximal forecast level in cm (clamped to ``MAX_LEVEL_CM``).

    Logic: (1) build the effective current rain rate from the window sums —
    rain from the last hour acts at ~65 % strength immediately, the reminder
    of a 2 h window follows the pipe lag; (2) convert rain rate to a runoff
    flow via ``RAIN_TO_RUNOFF``; (3) whatever exceeds the network capacity
    ponds on the streets, raising the level each 5-minute step, while rain
    still in the pipes keeps arriving; (4) with no excess, the level recedes
    at a bounded drain rate capped by physical storage.
    """
    capacity = max(float(network_capacity_m3h), 1e-6)
    horizon = max(1, int(horizon_minutes))
    readings = sorted(
        station_readings, key=lambda r: float(r.get("minAgo", 0))
    )  # newest first (minAgo == 0)
    current_level = float(readings[0].get("waterLevelCm", 0.0))

    rain60 = sum(
        float(r.get("rainfallMm", 0.0))
        for r in readings
        if float(r.get("minAgo", 0)) <= 60
    )
    rain120 = sum(
        float(r.get("rainfallMm", 0.0))
        for r in readings
        if float(r.get("minAgo", 0)) <= 120
    )
    older = max(0.0, rain120 - rain60)

    # Recent rain acts at ~65 % strength right now; the remainder of the older
    # window follows the pipe travel lag below.
    rain_rate_mm_h = 0.65 * rain60 + 0.35 * RAIN_LAG_FRACTION * older
    runoff_m3_h = RAIN_TO_RUNOFF * rain_rate_mm_h

    still_to_arrive_mm = older * (1.0 - 0.35 * RAIN_LAG_FRACTION)
    step = 5.0
    dt_h = step / 60.0
    level = clamp(current_level, 0.0, MAX_LEVEL_CM)
    peak = level
    steps = int(math.ceil(horizon / step))

    for _ in range(steps):
        arriving_chunk_mm = still_to_arrive_mm * dt_h  # linear arrival over 1 h
        still_to_arrive_mm = max(0.0, still_to_arrive_mm - arriving_chunk_mm)
        effective_rate = rain_rate_mm_h + arriving_chunk_mm / dt_h
        inflow = RAIN_TO_RUNOFF * effective_rate
        excess = max(0.0, inflow - capacity)
        if excess > 0.0:
            level += excess * EXCESS_TO_LEVEL_CM * dt_h
        else:
            recession = min(
                RECESSION_MAX, RECESSION_MIN + 0.008 * level
            )
            level = max(0.0, level - recession)
        level = clamp(level, 0.0, MAX_LEVEL_CM)
        peak = max(peak, level)
    return round(peak, 2)
