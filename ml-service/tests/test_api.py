"""API contract tests — the FastAPI endpoints must match docs/CONTRACT.md §8.

Runs against the active backend (whatever was auto-detected); the heuristic
backend is guaranteed everywhere, so these never need trained artifacts.
"""

from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health_reports_backend():
    r = client.get("/ml/v1/health")
    assert r.status_code == 200
    body = r.json()
    assert body["status"] == "ok"
    assert body["backend"] in {"tensorflow", "sklearn", "heuristic"}


def test_congestion_response_shape():
    r = client.post(
        "/ml/v1/predict/congestion",
        json={"zoneId": "z1", "history": [[8, 1, 0.8], [8, 1, 0.85], [8, 1, 0.9]], "horizonMinutes": 30},
    )
    assert r.status_code == 200
    body = r.json()
    assert body["backend"] in {"tensorflow", "sklearn", "heuristic"}
    preds = body["predictions"]
    assert len(preds) > 0
    for p in preds:
        assert 0.0 <= p["congestion"] <= 1.0
        assert p["horizonMinute"] >= 0
        assert 0.0 < p["confidence"] <= 1.0


def test_congestion_empty_history_defaults():
    r = client.post("/ml/v1/predict/congestion", json={"horizonMinutes": 15})
    assert r.status_code == 200
    assert r.json()["predictions"]


def test_waterlogging_response_shape():
    # Java posts [waterLevelCm, flowRateLps, rainfallMm] triples
    r = client.post(
        "/ml/v1/predict/waterlogging",
        json={
            "zoneId": "z2",
            "stationReadings": [[55.0, 240.0, 30.0], [38.0, 180.0, 18.0], [22.0, 150.0, 4.0]],
            "networkCapacityM3h": 1200,
        },
    )
    assert r.status_code == 200
    body = r.json()
    assert body["risk"] in {"low", "moderate", "high", "severe"}
    assert body["maxLevelCm"] >= 55.0  # never sinks below the current level
    assert 0.0 < body["confidence"] <= 1.0


def test_waterlogging_light_rain_is_low_risk():
    r = client.post(
        "/ml/v1/predict/waterlogging",
        json={"stationReadings": [[12.0, 70.0, 0.0], [9.0, 60.0, 0.5]]},
    )
    assert r.status_code == 200
    assert r.json()["risk"] == "low"
