#!/usr/bin/env python
"""UrbanTwin live telemetry seeder.

Continuously feeds realistic, diurnal-patterned telemetry into a running
stack via the REST ingest bridge (/api/v1/ingest/*) — the same payloads the
Kafka topics `traffic.readings`, `drainage.readings` and `energy.readings`
carry (contract §5).

The seeder discovers the real sensor / grid IDs from the API's list
endpoints at startup, anchors to each device's `latestReading`, then pushes
one round of readings every `--interval` seconds:

* traffic    — vehicle counts / occupancy follow the same two-peak weekday
               curve the heuristics model; queues persist between rounds
* drainage   — water level accumulates through storm episodes and recedes
               between them; flow tracks the level
* energy     — consumption follows a morning + evening peak, voltage sags
               with load; rare simulated outages, self-recovering

Usage
-----
    python scripts/seed.py                         # http://localhost:8080, forever
    python scripts/seed.py --iterations 60 --interval 2
    python scripts/seed.py --api-url http://localhost:8080 --storm

Stdlib only — no dependencies.
"""

from __future__ import annotations

import argparse
import json
import math
import random
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone

# ---------------------------------------------------------------------------
# HTTP helpers (stdlib)
# ---------------------------------------------------------------------------


def http_json(method: str, url: str, body: dict | None = None, timeout: float = 10.0):
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=timeout) as resp:
         raw = resp.read()
    return json.loads(raw) if raw else None


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


# ---------------------------------------------------------------------------
# Physics-style diurnal helpers (mirror app/heuristics.py + Java heuristics)
# ---------------------------------------------------------------------------

_MORNING_PEAK, _EVENING_PEAK = 8.5, 17.75
_WEEKEND_DAMP = 0.65


def demand(hour: float, dow: int) -> float:
    """0..1 travel/energy demand for a given (fractional) hour + day of week."""
    def peak(centre: float, width: float, amp: float) -> float:
        return amp * math.exp(-((hour - centre) ** 2) / (2 * width ** 2))

    if dow >= 5:  # Saturday/Sunday: single broad leisure peak
        return 0.25 + peak(13.5, 3.4, 0.5) + 0.25 * peak(19.0, 2.6, 0.4)
    return 0.12 + peak(_MORNING_PEAK, 1.7, 0.8) + peak(_EVENING_PEAK, 2.1, 0.72)


def weathersync(rng: random.Random, state: dict) -> float:
    """Advance the storm narrative stored in `state` and return mm/h intensity."""
    intensity = state.get("rain_mm_h", 0.0)
    left = state.get("storm_left", 0)
    if left > 0:
        # inside an episode: ramp toward this episode's peak
        intensity += (state["storm_peak"] - intensity) * 0.35 + rng.gauss(0, 2)
        state["storm_left"] = left - 1
    elif rng.random() < 0.05:  # start a new episode every ~20 rounds on average
        state["storm_left"] = rng.randint(4, 14)
        state["storm_peak"] = rng.uniform(10, 60)
        intensity = rng.uniform(1, 5)
    else:
        intensity = max(0.0, intensity * 0.55 - 0.2)  # clear out
    return max(0.0, min(float(intensity), 80.0))


def rain_mm_for_interval(intensity_mm_h: float, interval_s: float) -> float:
    """Tipping-bucket style accumulation for one reading interval."""
    return intensity_mm_h * interval_s / 3600.0


# ---------------------------------------------------------------------------
# seeding / discovery
# ---------------------------------------------------------------------------


def wait_for_api(api: str, deadline_s: float = 180.0) -> bool:
    print(f"[seed] waiting for {api} ...", flush=True)
    end = time.time() + deadline_s
    while time.time() < end:
        try:
            http_json("GET", f"{api}/api/v1/twin/ping", timeout=5)
            print("[seed] API is up", flush=True)
            return True
        except Exception:
            time.sleep(3)
    print("[seed] API did not come up in time", file=sys.stderr, flush=True)
    return False


def discover(api: str) -> dict:
    traffic = http_json("GET", f"{api}/api/v1/traffic/sensors")
    drainage = http_json("GET", f"{api}/api/v1/drainage/sensors")
    grids = http_json("GET", f"{api}/api/v1/energy/grids")

    def active(items):
        return [s for s in items if s.get("status", "active") == "active" or s.get("status") is None]

    catalogue = {
        "traffic": active(traffic),
        "drainage": active(drainage),
        "grids": grids,
    }
    print(
        f"[seed] discovered {len(catalogue['traffic'])} traffic sensors, "
        f"{len(catalogue['drainage'])} drainage sensors, {len(catalogue['grids'])} grids",
        flush=True,
    )
    if not (catalogue["traffic"] or catalogue["drainage"] or catalogue["grids"]):
        print("[seed] nothing discovered — did the app seed its data? "
              "Set APP_SEED_ENABLED=true.", file=sys.stderr)
    return catalogue


# ---------------------------------------------------------------------------
# per-module generators
# ---------------------------------------------------------------------------


def anchor(value, fallback: float) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return fallback


def traffic_round(cat: dict, hour: float, dow: int, rng: random.Random,
                  state: dict, api: str, interval_s: float) -> int:
    base = demand(hour, dow)
    sent = 0
    for s in cat["traffic"]:
        prev = (s.get("latestReading") or {})
        level = anchor(prev.get("congestionLevel"), base)
        level = level * 0.65 + base * 0.45 + rng.gauss(0, 0.04)  # persistence + curve
        level = max(0.0, min(1.0, level))
        s.setdefault("_capacity", rng.uniform(1200, 2400))       # fixed per sensor
        vehicles = int(s["_capacity"] * (0.25 + 0.75 * level) * interval_s / 60.0)
        free_speed = anchor(prev.get("avgSpeedKph"), 55.0) or 55.0
        speed = round(max(5.0, free_speed * (1.0 - 0.8 * level) + rng.gauss(0, 2)), 1)
        occupancy = round(min(95.0, 65.0 * level + 10 + rng.gauss(0, 3)), 1)
        payload = {
            "sensorId": s["id"],
            "vehicleCount": max(0, vehicles),
            "avgSpeedKph": speed,
            "occupancyPct": occupancy,
            "recordedAt": now_iso(),
        }
        try:
            http_json("POST", f"{api}/api/v1/ingest/traffic", payload)
            sent += 1
        except Exception as e:
            print(f"[seed] traffic POST failed: {e}", file=sys.stderr)
    return sent


def drain_round(cat: dict, rain_mm_h: float, rng: random.Random,
                state: dict, api: str, interval_s: float) -> int:
    rain_mm = rain_mm_for_interval(rain_mm_h, interval_s)
    sent = 0
    for s in cat["drainage"]:
        prev = (s.get("latestReading") or {})
        level = anchor(prev.get("waterLevelCm"), 12.0)
        flow = anchor(prev.get("flowRateLps"), 90.0)
        if rain_mm_h > 2:
            level += rain_mm_h * rng.uniform(0.05, 0.12)      # incoming runoff
            level = min(level, 140.0)
            flow += rain_mm_h * rng.uniform(6, 14)
        else:
            level = max(3.0, level * 0.86)                    # recede between storms
            flow *= 0.9
        payload = {
            "sensorId": s["id"],
            "waterLevelCm": round(level + rng.gauss(0, 0.8), 2),
            "flowRateLps": round(max(10, flow + rng.gauss(0, 8)), 1),
            "rainfallMm": round(rain_mm, 2),
            "recordedAt": now_iso(),
        }
        try:
            http_json("POST", f"{api}/api/v1/ingest/drainage", payload)
            sent += 1
        except Exception as e:
            print(f"[seed] drainage POST failed: {e}", file=sys.stderr)
    return sent


def energy_round(cat: dict, hour: float, dow: int, rng: random.Random,
                 state: dict, api: str) -> int:
    curve = demand(hour, dow) * 0.85 + demand((hour + 6) % 24, dow) * 0.15
    sent = 0
    for g in cat["grids"]:
        prev = (g.get("latestReading") or {})
        capacity = anchor(g.get("capacityKw"), 1200.0) or 1200.0
        last_kw = anchor(prev.get("consumptionKw"), capacity * 0.55)
        target = capacity * (0.35 + 0.55 * curve)
        kw = max(capacity * 0.08, last_kw * 0.7 + target * 0.4 + rng.gauss(0, capacity * 0.015))
        if g.get("id") in state.get("outages", set()):
            kw = 0.0
            if rng.random() < 0.35:
                state["outages"].discard(g["id"])            # self-recovering
        elif kw > capacity * 0.97 and rng.random() < 0.05:
            state["outages"].add(g["id"])                    # rare storm outage
            print(f"[seed] simulated outage on grid {g.get('name')}", flush=True)
        voltage = 24700 * (1.0 - 0.03 * (kw / capacity)) if kw > 0 else 24000.0
        payload = {
            "gridId": g["id"],
            "consumptionKw": round(kw, 1),
            "voltage": round(voltage + rng.gauss(0, 40), 1),
            "outageReported": g.get("id") in state.get("outages", set()),
            "recordedAt": now_iso(),
        }
        try:
            http_json("POST", f"{api}/api/v1/ingest/energy", payload)
            sent += 1
        except Exception as e:
            print(f"[seed] energy POST failed: {e}", file=sys.stderr)
    return sent


# ---------------------------------------------------------------------------
# main loop
# ---------------------------------------------------------------------------


def main() -> int:
    ap = argparse.ArgumentParser(description="UrbanTwin live telemetry seeder")
    ap.add_argument("--api-url", default="http://localhost:8080")
    ap.add_argument("--interval", type=float, default=5.0, help="seconds between rounds")
    ap.add_argument("--iterations", type=int, default=0, help="0 = run forever")
    ap.add_argument("--wait-for-api", dest="wait_for_api", default=False, action="store_true")
    ap.add_argument("--storm", action="store_true", help="start with an ongoing storm")
    args = ap.parse_args()
    api = args.api_url.rstrip("/")

    if args.wait_for_api and not wait_for_api(api):
        return 2

    cat = discover(api)
    rng = random.Random()
    state: dict = {"outages": set(), "rain_mm_h": 0.0}
    if args.storm:
        state.update(storm_left=8, storm_peak=45.0, rain_mm_h=12.0)

    round_no = 0
    while True:
        round_no += 1
        dt = datetime.now(timezone.utc)
        hour, dow = dt.hour + dt.minute / 60.0, dt.weekday()
        rain = weathersync(rng, state)
        state["rain_mm_h"] = rain

        t = traffic_round(cat, hour, dow, rng, state, args.interval)
        d = drain_round(cat, rain, rng, state, api, args.interval)
        e = energy_round(cat, hour, dow, rng, state, api)
        print(f"[seed] round {round_no:04d} {dt:%H:%M:%S} rain={rain:5.1f} mm/h "
              f"→ traffic {t}, drainage {d}, energy {e}", flush=True)

        if args.iterations and round_no >= args.iterations:
            break
        time.sleep(args.interval)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
