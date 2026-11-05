"""Unit tests for the pure physics heuristics (stdlib only, no API)."""

from app import heuristics


def test_free_flow_day_is_calm():
    # Wednesday 04:00 should be quiet everywhere
    v = heuristics.diurnal_congestion(4.0, 2)
    assert 0.0 <= v <= 0.25


def test_weekday_morning_peak_beats_midnight():
    peak = heuristics.diurnal_congestion(8.5, 1)
    night = heuristics.diurnal_congestion(3.0, 1)
    assert peak > 0.6
    assert peak > night * 2.5


def test_forecast_relaxes_toward_diurnal_curve():
    # stuck morning traffic (0.95) relaxes toward the curve over the hour
    preds = heuristics.predict_congestion_heuristic(
        [[8, 1, 0.95], [8, 1, 0.93], [8, 1, 0.90]], 60
    )
    assert preds[0]["congestion"] >= preds[-1]["congestion"] - 1e-9
    assert all(0.0 <= p["congestion"] <= 1.0 for p in preds)


def test_waterlogging_never_below_current_when_raining():
    readings = [
        {"waterLevelCm": 60, "flowRateLps": 300, "rainfallMm": 40, "minAgo": 10},
        {"waterLevelCm": 40, "flowRateLps": 200, "rainfallMm": 25, "minAgo": 40},
    ]
    peak = heuristics.predict_max_water_level_heuristic(readings, 1200, 60)
    assert peak >= 60.0


def test_risk_bands_match_thresholds():
    assert heuristics.classify_risk(10) == "low"
    assert heuristics.classify_risk(30) == "moderate"
    assert heuristics.classify_risk(60) == "high"
    assert heuristics.classify_risk(120) == "severe"
