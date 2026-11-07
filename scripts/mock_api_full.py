"""UrbanTwin local demo backend (mock, stdlib only).

Serves the full frontend API surface with a live, Dhaka-based dataset on
http://localhost:8080 — so the Vite dev server (VITE_API_URL default) renders
every page with real-looking moving data without Docker/Postgres/Kafka.

Run:  python mock_api_full.py            (server on :8080, Ctrl+C to stop)
"""
import json
import math
import random
import time
import uuid
import threading
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

RNG = random.Random(7)
BASE = "/api/v1"
CITY_ID = "c0000000-0000-0000-0000-000000000001"
CITY = {"id": CITY_ID, "name": "Dhaka", "slug": "dhaka", "population": 22_500_000, "zoneCount": 10}

def _uid(n) -> str:
    return f"70000000-0000-0000-0000-{n:012d}"

# ---------------------------------------------------------------- zones
ZONE_DEFS = [
    ("Gulshan", "commercial", 23.7925, 90.4125, 1.4, 0.72),
    ("Banani", "commercial", 23.7898, 90.4012, 1.3, 0.66),
    ("Dhanmondi", "residential", 23.7461, 90.3760, 1.8, 0.58),
    ("Mirpur", "mixed", 23.8069, 90.3667, 2.2, 0.49),
    ("Uttara", "mixed", 23.8759, 90.3795, 2.4, 0.41),
    ("Motijheel", "commercial", 23.7333, 90.4127, 1.5, 0.77),
    ("Rampura", "mixed", 23.7679, 90.4248, 1.7, 0.55),
    ("Mohammadpur", "residential", 23.7640, 90.3596, 1.9, 0.52),
    ("Tejgaon", "industrial", 23.7568, 90.3972, 1.8, 0.61),
    ("Kurmitola", "mixed", 23.8550, 90.4010, 1.9, 0.38),
]
ZONES = []
for i, (name, ztype, lat, lon, r, base_cong) in enumerate(ZONE_DEFS, start=1):
    pts = []
    for k in range(14):
        a = 2 * math.pi * k / 14
        noise = 0.82 + 0.18 * ((k * 7 + i * 13) % 5) / 4
        pts.append([round(lon + (r * noise * math.sin(a)) / 100.0 * 0.95, 6),
                    round(lat + (r * noise * math.cos(a)) / 110.0, 6)])
    pts.append(pts[0])
    ZONES.append({
        "id": _uid(100 + i), "name": name, "zoneType": ztype, "cityId": CITY_ID,
        "geojson": json.dumps({"type": "Polygon", "coordinates": [pts]}),
        "_lat": lat, "_lon": lon, "_r": r, "_cong": base_cong,
    })
ZN = {z["name"]: z for z in ZONES}

ROAD_DEFS = [
    ("Progoti Sarani", "ar", "Rampura", 62), ("Airport Road", "hw", "Kurmitola", 78),
    ("Mirpur Road", "ar", "Mirpur", 44), ("Sonargaon Road", "ar", "Tejgaon", 55),
    ("Kazi Nazrul Islam Avenue", "ar", "Banani", 51), ("Bangla Motor Road", "co", "Motijheel", 67),
    ("Begum Rokeya Sarani", "ar", "Uttara", 63), ("Shahid Tajuddin Ahmed Sarani", "hy", "Tejgaon", 48),
]
def _hex(v) -> str:
    return f"{v:02x}"
def _uid_str(i) -> str:
    return f"70000000-0000-0000-0000-{i:012d}"

ROADS = []
for j, (name, cls, zname, cond) in enumerate(ROAD_DEFS, start=1):
    z = ZN[zname]
    n = 6
    line = []
    for k in range(n):
        t = k / (n - 1)
        line.append([round(z["_lon"] - z["_r"] * 0.03 + t * z["_r"] * 0.055, 6),
                     round(z["_lat"] - z["_r"] * 0.045 + t * z["_r"] * 0.09, 6)])
    ROADS.append({
        "id": _uid_str(200 + j), "zoneId": z["id"], "name": name, "roadClass": cls,
        "conditionScore": cond,
        "geojson": json.dumps({"type": "LineString", "coordinates": line}),
        "openMaintenanceRequests": 1 if cond < 50 else 0,
    })

STATION = {"lat": 23.8103, "lon": 90.4125}

def _lonlat(z, drift=0.6):
    return [round(z["_lon"] + RNG.uniform(-drift, drift) * z["_r"] / 90.0, 6),
            round(z["_lat"] + RNG.uniform(-drift, drift) * z["_r"] / 90.0, 6)]

def _iso(dt=None) -> str:
    return (dt or datetime.now(timezone.utc)).isoformat().replace("+00:00", "Z")

TRAFFIC_SENSORS, DRAIN_SENSORS = [], []
for i, z in enumerate(ZONES, start=1):
    TRAFFIC_SENSORS.append({
        "id": _uid_str(400 + i), "roadId": ROADS[i % len(ROADS)]["id"],
        "name": f"{z['name']} corridor sensor", "location": _lonlat(z),
        "status": "active",
        "latestReading": {"sensorId": _uid_str(400 + i), "vehicleCount": 320, "avgSpeedKph": 34,
                          "occupancyPct": 42, "congestionLevel": z["_cong"], "recordedAt": _iso()},
    })
    DRAIN_SENSORS.append({
        "id": _uid_str(410 + i), "networkId": _uid_str(500 + i), "name": f"{z['name']} storm gauge",
        "location": _lonlat(z), "status": "normal" if i % 4 else "warning",
        "latestReading": {"sensorId": _uid_str(410 + i), "waterLevelCm": round(RNG.uniform(8, 38), 1),
                          "flowRateLps": round(RNG.uniform(40, 160), 1), "rainfallMm": round(RNG.uniform(0, 6), 1),
                          "riskLevel": "low", "recordedAt": _iso()},
    })

NETWORKS = [{"id": _uid_str(500 + i), "zoneId": z["id"], "name": f"{z['name']} trunk drain",
             "capacityM3PerHour": round(250 + 70 * (i % 5)), "networkClass": "primary" if i % 2 else "secondary"}
            for i, z in enumerate(ZONES, start=1)]

GRIDS = [{"id": _uid_str(600 + i), "zoneId": z["id"], "name": f"{z['name']} 33/11kV substation",
          "capacityKw": 3800 - 90 * i, "lossPct": round(3.5 + z["_cong"], 2), "zoneName": z["name"],
          "latestReading": {"gridId": _uid_str(600 + i), "consumptionKw": round((3800 - 90 * i) * z["_cong"]),
                            "voltage": 24500, "outageReported": i == 6, "recordedAt": _iso()}}
         for i, z in enumerate(ZONES, start=1)]

BIN_NAMES = ["Bazar", "School gate", "Bus terminal", "Mosque lane", "Market road", "Park corner"]
BINS = []
for i, z in enumerate(ZONES, start=1):
    for b in range(3):
        BINS.append({
            "id": _uid_str(700 + 3 * (i - 1) + b), "zoneId": z["id"],
            "name": f"{z['name']} {BIN_NAMES[b]} bin", "location": _lonlat(z, 1.0),
            "fillLevelPct": min(99, int(28 + 9 * ((i * 3 + b) % 8) + (17 if (i + b) % 3 == 0 else 0))),
            "lastCollectedAt": _iso(datetime.now(timezone.utc) - timedelta(hours=6 + b * 7)),
        })

ASSET_TYPES = ["streetlight", "water_main", "pump", "transformer", "signal"]
ASSETS = [{"id": _uid_str(800 + i), "zoneId": ZONES[i % 10]["id"], "assetType": ASSET_TYPES[i % 5],
           "name": f"{ZONES[i % 10]['name']} {ASSET_TYPES[i % 5]} #{i}", "location": _lonlat(ZONES[i % 10], 1.0),
           "conditionScore": 55 + (i * 7) % 40, "installedYear": 2005 + (i % 18),
           "status": "operational" if i % 7 else "degraded"}
          for i in range(1, 22)]

INCIDENTS = [
    {"id": _uid_str(900 + i), "zoneId": ZONES[(i * 3) % 10]["id"], "name": n,
     "incidentType": t, "severity": s, "location": _lonlat(ZONES[(i * 3) % 10]),
     "description": d, "status": "dispatched" if i == 1 else "active",
     "reportedAt": _iso(datetime.now(timezone.utc) - timedelta(minutes=25 * i)), "resolvedAt": None}
    for i, (n, t, s, d) in enumerate([
        ("Waterlogged underpass car stuck", "flood", 3, "Monsoon surge trapped a car at the old ramp."),
        ("Pump house fire alarm", "fire", 4, "Smoke reported at the Tejgaon pump station."),
        ("Medical emergency at Uttara depot", "medical", 2, "Worker fainted, ambulance en route."),
        ("Fallen electric pole hazard", "hazard", 3, "Pole leaning over the service lane after rain."),
    ], start=1) if i <= 4]

VEHICLES = [
    {"id": _uid_str(950 + i), "name": n, "vehicleType": t, "status": st, "station": stn, "headingDeg": h}
    for i, (n, t, st, stn, h) in enumerate([
        ("Dhaka Central Ambulance 1", "ambulance", "available", [90.4125, 23.7755], 45),
        ("Mirpur Fire Engine 2", "fire_engine", "dispatched", [90.3667, 23.8069], -70),
        ("Uttara Ambulance 3", "ambulance", "available", [90.3795, 23.8759], 120),
        ("Motijheel Rescue 1", "rescue", "available", [90.4127, 23.7333], 0),
    ], start=1)]

REPORT_TEXTS = [
    ("pothole", "Deep potholes on the rickshaw lane after last night's rain."),
    ("flooding", "Underpass at Airport Road waterlogged again in the downpour."),
    ("lighting", "Streetlight pole leaning and dark near the bazar crossing."),
    ("garbage", "Van missed pickup for the second day beside the mosque."),
    ("leak", "Water main leak pooling at the corner near Banani 11."),
]
REPORTS = []
CAT = {"pothole": "in_progress", "flooding": "submitted", "lighting": "acknowledged",
       "garbage": "in_progress", "leak": "resolved"}
MAINT_STATUS = {"open": 6, "assigned": 3, "in_progress": 4, "resolved": 9, "closed": 12}
for i, (cat, desc) in enumerate(REPORT_TEXTS * 2, start=1):
    z = ZONES[(i * 2) % 10]
    REPORTS.append({"id": _uid_str(1000 + i), "zoneId": z["id"], "category": cat,
                    "description": desc, "location": _lonlat(z), "status": CAT[cat],
                    "upvotes": (i * 5) % 34, "reporterName": ["Aminul", "Sadia", "Rafiq", "Tahmid"][i % 4],
                    "createdAt": _iso(datetime.now(timezone.utc) - timedelta(hours=5 * i)),
                    "zoneName": z["name"]})

MAINT = []
PRI = ["p1", "p2", "p3", "p4"]
k = 0
for st, n in MAINT_STATUS.items():
    for _ in range(n):
        k += 1
        road = ROADS[k % len(ROADS)]
        MAINT.append({"id": _uid_str(1100 + k), "title": f"Repair crew — {road['name']} section",
                      "description": "Asphalt patch + drain clearing", "priority": PRI[k % 4],
                      "status": st, "reportedBy": "ops console", "roadId": road["id"], "assetId": None,
                      "roadName": road["name"],
                      "scheduledDate": _iso(datetime.now(timezone.utc) + timedelta(days=1 + k % 5))[:10],
                      "resolvedAt": _iso() if st == "resolved" else None,
                      "createdAt": _iso(datetime.now(timezone.utc) - timedelta(days=k % 6))})
    if st == "resolved":
        pass

def _drift(base, amp=0.22):
    """Diurnal + live jitter so numbers visibly move between polls."""
    h = time.localtime().tm_hour + time.localtime().tm_min / 60.0
    diurnal = amp * math.sin((h - 3) / 24 * 2 * math.pi)
    return max(0.06, min(0.97, base + diurnal + RNG.uniform(-0.07, 0.12)))

def _twin_state():
    zs = []
    for z in ZONES:
        cong = _drift(z["_cong"])
        flood = _drift(0.25 + 0.1 * (z["_cong"]), 0.2)
        zs.append({"zoneId": z["id"], "name": z["name"], "congestion": round(cong, 3),
                   "floodRisk": round(flood, 3),
                   "powerState": "watch" if z["name"] == "Tejgaon" else "ok",
                   "binsOverfull": sum(1 for b in BINS if b["zoneId"] == z["id"] and b["fillLevelPct"] >= 85),
                   "assetHealth": 99 - 9 * (z["id"] and ord(z["name"][0]) % 11)})
    total_cong = sum(z["congestion"] for z in zs) / len(zs)
    net = "critical" if total_cong > 0.8 else ("degraded" if total_cong > 0.6 else "ok")
    return {
        "city": {"id": CITY_ID, "name": "Dhaka"},
        "zones": zs,
        "totals": {
            "activeIncidents": sum(1 for i in INCIDENTS if i["status"] != "resolved"),
            "openMaintenance": MAINT_STATUS["open"] + MAINT_STATUS["assigned"] + MAINT_STATUS["in_progress"],
            "binsOverfull": sum(1 for b in BINS if b["fillLevelPct"] >= 85),
            "avgCongestion": round(total_cong, 3),
            "networkStatus": net,
            "dataFreshness": "sims 2 s",
        },
        "updatedAt": _iso(),
    }

def _firehose():
    out = []
    for z in ZONES:
        c = _drift(z["_cong"])
        speed = round(62 - 45 * c, 1)
        out.append({"zoneId": z["id"], "name": z["name"], "congestionLevel": round(c, 3),
                    "avgSpeedKph": speed,
                    "trend": "up" if c > z["_cong"] else ("down" if c < z["_cong"] - 0.05 else "stable"),
                    "updatedAt": _iso()})
    return out

def _readings_history(sensor_id, maker):
    out = []
    for step in range(24, -1, -1):
        t = datetime.now(timezone.utc) - timedelta(hours=step)
        h = t.hour + t.minute / 60
        frac = 0.55 + 0.4 * math.sin((h - 3) / 24 * 2 * math.pi)
        out.append({"sensorId": sensor_id, "recordedAt": _iso(t), **maker(frac, step)})
    return out

def _water_predictions():
    out = []
    for z in ZONES:
        lvl = _drift(0.3 + 0.5 * z["_cong"], 0.3)
        risk = "severe" if lvl > 0.8 else ("high" if lvl > 0.62 else ("moderate" if lvl > 0.4 else "low"))
        affected = [r["id"] for r in ROADS if r["zoneId"] == z["id"] and r["conditionScore"] < 60]
        out.append({"zoneId": z["id"], "risk": risk, "maxLevelCm": round(lvl * 95, 1),
                    "affectedRoads": affected, "confidence": round(0.72 + RNG.uniform(0, 0.2), 2)})
    return out

def _energy_summary():
    return [{"zoneId": g["zoneId"], "name": g["zoneName"], "currentKw": g["latestReading"]["consumptionKw"],
             "peakKw24h": round(g["capacityKw"] * 0.92), "outages24h": 3 if g["zoneName"] == "Tejgaon" else 0,
             "trend": "up" if g["latestReading"]["consumptionKw"] > g["capacityKw"] / 2 else "down"}
            for g in GRIDS]

def _hex_via(v) -> str:
    return uuid.UUID(v)

def _route_optimize(body):
    bins = [b for b in BINS if b["zoneId"] == body.get("zoneId")] or BINS[:6]
    bins.sort(key=lambda b: -b["fillLevelPct"])
    stops = [{"binId": b["id"], "name": b["name"], "lat": b["location"][1], "lon": b["location"][0],
              "fillLevel": b["fillLevelPct"], "order": i + 1} for i, b in enumerate(bins[:8])]
    return {"id": _uid_str(1234), "name": "AM-shift collection route", "zoneId": body.get("zoneId"),
            "vehicleId": None, "stops": stops, "totalDistanceKm": round(RNG.uniform(9, 17), 1),
            "estimatedDurationMin": 5 * len(stops) + 22, "status": "planned"}

def _safest_route(params_path):
    z = ZONES[3]
    line = [[z["_lon"], z["_lat"] - .02], [z["_lon"] + .01, z["_lat"]], [z["_lon"] + .02, z["_lat"] + .01]]
    return {"distanceKm": 5.4, "etaMin": 16, "riskScore": 0.18, "roads": 3,
            "geojson": json.dumps({"type": "LineString", "coordinates": line})}

SCN = json.loads if False else None

def _cascade(body):
    scenario = body.get("scenario", "rainfall")
    z = body.get("params", {}).get("zoneId")
    zone = next((zz for zz in ZONES if zz["id"] == z), ZONES[5])  # Motijheel default
    intensity = body.get("params", {}).get("intensityMm", 85)
    sev = min(5, max(2, round(intensity / 22)))
    N = zone["name"]
    if scenario == "substation_outage":
        nodes = [
            {"id": "t1", "type": "TRIGGER", "label": f"{N} feeder trip", "zoneId": zone["id"], "startOffsetSec": 0, "severity": sev},
            {"id": "g1", "type": "ENERGY", "label": f"{N} substation offline", "zoneId": zone["id"], "startOffsetSec": 45, "severity": 3},
            {"id": "p1", "type": "DRAINAGE", "label": "Lift pumps stop", "zoneId": zone["id"], "startOffsetSec": 180, "severity": 3},
            {"id": "r1", "type": "ROAD", "label": f"Signal loss on {N} road", "zoneId": zone["id"], "startOffsetSec": 240, "severity": 2},
            {"id": "k1", "type": "TRAFFIC", "label": "Gridlock builds", "zoneId": zone["id"], "startOffsetSec": 420, "severity": 3},
            {"id": "c1", "type": "REPORT", "label": "Citizen outage reports spike", "zoneId": zone["id"], "startOffsetSec": 480, "severity": 2},
            {"id": "m1", "type": "MITIGATION", "label": "Mobile genset dispatched", "zoneId": zone["id"], "startOffsetSec": 600, "severity": 1},
        ]
        edges = [("t1", "g1", "causes", 45, 0.9), ("g1", "p1", "knock_on", 135, 0.8),
                 ("g1", "r1", "signal_off", 195, 0.7), ("r1", "k1", "congests", 180, 0.85),
                 ("k1", "c1", "notified", 60, 0.6), ("c1", "m1", "escalates", 120, 0.75)]
        impact = {"zonesAffected": 2, "roadsImpacted": 4, "roadsWaterlogged": 0, "incidentsRerouted": 2,
                  "citizenReportsEstimate": 40, "totalNodes": 7}
    elif scenario == "water_main_burst":
        nodes = [
            {"id": "t1", "type": "TRIGGER", "label": "48-inch main burst", "zoneId": zone["id"], "startOffsetSec": 0, "severity": sev},
            {"id": "w1", "type": "DRAINAGE", "label": f"{N} flooding spreads", "zoneId": zone["id"], "startOffsetSec": 60, "severity": 4},
            {"id": "r1", "type": "ROAD", "label": "Roadbed undermined", "zoneId": zone["id"], "startOffsetSec": 300, "severity": 4},
            {"id": "k1", "type": "TRAFFIC", "label": "Detour congestion", "zoneId": zone["id"], "startOffsetSec": 480, "severity": 3},
            {"id": "i1", "type": "INCIDENT", "label": "Hazard cordon raised", "zoneId": zone["id"], "startOffsetSec": 540, "severity": 3},
            {"id": "m1", "type": "MITIGATION", "label": "Valve isolation crew", "zoneId": zone["id"], "startOffsetSec": 720, "severity": 1},
        ]
        edges = [("t1", "w1", "floods", 60, 0.95), ("w1", "r1", "undermines", 240, 0.7),
                 ("r1", "k1", "reroutes", 180, 0.8), ("w1", "i1", "hazards", 60, 0.65),
                 ("i1", "m1", "dispatches", 180, 0.85)]
        impact = {"zonesAffected": 1, "roadsImpacted": 5, "roadsWaterlogged": 5, "incidentsRerouted": 3,
                  "citizenReportsEstimate": 55, "totalNodes": 6}
    elif scenario == "sensor_failure":
        nodes = [
            {"id": "t1", "type": "TRIGGER", "label": "Corridor sensor drops out", "zoneId": zone["id"], "startOffsetSec": 0, "severity": 2},
            {"id": "g1", "type": "TRAFFIC", "label": " prediction degraded", "zoneId": zone["id"], "startOffsetSec": 30, "severity": 2},
            {"id": "r1", "type": "ROAD", "label": "Adaptive lights revert", "zoneId": zone["id"], "startOffsetSec": 90, "severity": 2},
            {"id": "m1", "type": "MITIGATION", "label": "Heuristic fallback engaged", "zoneId": zone["id"], "startOffsetSec": 150, "severity": 1},
        ]
        edges = [("t1", "g1", "blinds", 30, 0.9), ("t1", "r1", "degrades", 90, 0.5),
                 ("g1", "m1", "fails_over", 60, 0.95)]
        impact = {"zonesAffected": 1, "roadsImpacted": 1, "roadsWaterlogged": 0, "incidentsRerouted": 0,
                  "citizenReportsEstimate": 0, "totalNodes": 4}
    else:  # rainfall
        nodes = [
            {"id": "t1", "type": "TRIGGER", "label": f"Cloudburst {intensity} mm over {N}", "zoneId": zone["id"], "startOffsetSec": 0, "severity": sev},
            {"id": "w1", "type": "DRAINAGE", "label": "Trunk drain overtops", "zoneId": zone["id"], "startOffsetSec": 120, "severity": sev},
            {"id": "r1", "type": "ROAD", "label": "Corridor waterlogged", "zoneId": zone["id"], "startOffsetSec": 420, "severity": sev},
            {"id": "k1", "type": "TRAFFIC", "label": "Rush-hour gridlock", "zoneId": zone["id"], "startOffsetSec": 660, "severity": sev},
            {"id": "e1", "type": "ENERGY", "label": "Pump load spikes feeder", "zoneId": zone["id"], "startOffsetSec": 720, "severity": 2},
            {"id": "i1", "type": "INCIDENT", "label": "Stranded vehicle", "zoneId": zone["id"], "startOffsetSec": 780, "severity": sev - 1},
            {"id": "c1", "type": "REPORT", "label": "Citizen flood reports", "zoneId": zone["id"], "startOffsetSec": 900, "severity": 2},
            {"id": "m1", "type": "MITIGATION", "label": "Pumps + traffic wardens", "zoneId": zone["id"], "startOffsetSec": 1080, "severity": 1},
        ]
        edges = [("t1", "w1", "saturates", 120, 0.92), ("w1", "r1", "floods", 300, 0.85),
                 ("r1", "k1", "congests", 240, 0.8), ("w1", "e1", "loads", 300, 0.55),
                 ("k1", "i1", "strands", 120, 0.6), ("e1", "c1", "outages", 180, 0.5),
                 ("c1", "m1", "escalates", 180, 0.8)]
        impact = {"zonesAffected": 3, "roadsImpacted": 6, "roadsWaterlogged": 4, "incidentsRerouted": 2,
                  "citizenReportsEstimate": 70, "totalNodes": 8}
    return {"simulationId": _uid_str(1300), "scenario": scenario, "status": "completed",
            "timelineSeconds": max(n["startOffsetSec"] for n in nodes) + 60,
            "cascade": {"nodes": nodes, "edges": [{"from": a, "to": b, "relation": rel, "delaySec": d, "probability": p}
                                                  for a, b, rel, d, p in edges]},
            "impactSummary": impact, "name": body.get("name", "Demo run"),
            "startedAt": _iso(), "completedAt": _iso()}

RUNS = []

# ------------------------------------------------------------ server
class H(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *a):
        pass

    def _hdrs(self, code=200, ctype="application/json", length=None):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET,POST,OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        if length is not None:
            self.send_header("Content-Length", str(length))
        self.end_headers()

    def _json(self, obj, code=200):
        body = json.dumps(obj).encode()
        self._hdrs(code, length=len(body))
        self.wfile.write(body)

    def do_OPTIONS(self):
        self._hdrs(204, length=0)

    def do_POST(self):
        n = int(self.headers.get("Content-Length", 0) or 0)
        raw = self.rfile.read(n) if n else b"{}"
        try:
            body = json.loads(raw or b"{}")
        except json.JSONDecodeError:
            body = {}
        p = self.path[len(BASE):] if self.path.startswith(BASE) else self.path

        if p.startswith("/simulation/run"):
            res = _cascade(body)
            RUNS.insert(0, {"id": res["simulationId"], "simulationId": res["simulationId"],
                            "name": res["name"], "scenario": res["scenario"], "status": "completed",
                            "startedAt": res["startedAt"], "completedAt": res["completedAt"]})
            return self._json(res)
        if p.startswith("/waste/routes/optimize"):
            return self._json(_route_optimize(body))
        if p.startswith("/emergency/incidents/"):
            if "/dispatch" in p:
                return self._json({"id": _uid_str(1400), "incidentId": body.get("incidentId", ""),
                                   "vehicleId": body.get("vehicleId", ""), "routeGeojson": None,
                                   "distanceKm": round(RNG.uniform(3, 9), 1), "etaMin": RNG.randint(6, 25),
                                   "status": "en_route", "dispatchedAt": _iso()})
            return self._json({"id": body.get("id", ""), "status": "resolved"})
        if p.startswith("/emergency/dispatch/") and p.endswith("/resolve"):
            return self._json({"status": "arrived", "resolvedAt": _iso()})
        if p.startswith("/emergency/incidents"):
            z = ZONES[RNG.randrange(10)]
            inc = {"id": _uid_str(1500), "zoneId": z["id"], "name": body.get("name", "New incident"),
                   "incidentType": body.get("incidentType", "hazard"), "severity": body.get("severity", 2),
                   "location": _lonlat(z), "description": body.get("description"),
                   "status": "active", "reportedAt": _iso(), "resolvedAt": None}
            INCIDENTS.append(inc)
            return self._json(inc)
        if p.startswith("/maintenance/requests"):
            req = {"id": _uid_str(1600), "title": body.get("title", "Work order"), "description": body.get("description"),
                   "priority": body.get("priority", "p3"), "status": "open",
                   "reportedBy": body.get("reportedBy", "citizen"), "roadId": body.get("roadId"), "assetId": None,
                   "roadName": None, "scheduledDate": None, "resolvedAt": None, "createdAt": _iso()}
            MAINT.insert(0, req)
            return self._json(req)
        if p.startswith("/reports") and p.endswith("/upvote"):
            rid = p.split("/")[2]
            for r in REPORTS:
                if r["id"] == rid:
                    r["upvotes"] += 1
                    return self._json(r)
            return self._json({"error": "not found"}, 404)
        if p.startswith("/reports"):
            z = ZONES[RNG.randrange(10)]
            rep = {"id": _uid_str(1700), "zoneId": z["id"], "category": body.get("category", "other"),
                   "description": body.get("description", ""), "location": body.get("location") or _lonlat(z),
                   "status": "submitted", "upvotes": 0, "reporterName": body.get("reporterName", "you"),
                   "createdAt": _iso(), "zoneName": z["name"]}
            REPORTS.insert(0, rep)
            return self._json(rep)
        if p.startswith("/drainage/simulate") or p.startswith("/"):
            return self._json({"ok": True})
        return self._json({"error": "not found"}, 404)

    def do_GET(self):
        p = self.path[len(BASE):].split("?")[0] if self.path.startswith(BASE) else self.path.split("?")[0]
        if p.startswith("/twin/live"):
            return self._sse()
        if p == "/cities":
            return self._json([CITY])
        if p == "/zones":
            return self._json([{k: z[k] for k in ("id", "name", "zoneType", "cityId", "geojson")} for z in ZONES])
        if p == "/twin/state":
            return self._json(_twin_state())
        if p.startswith("/gis/layers/"):
            return self._json(self._layer(p.rsplit("/", 1)[1]))
        if p == "/gis/heatmap":
            return self._json([{"lat": z["_lat"], "lon": z["_lon"], "weight": round(_drift(z["_cong"]), 2)} for z in ZONES])
        if p == "/traffic/sensors":
            return self._json(TRAFFIC_SENSORS)
        if p == "/traffic/readings":
            return self._json(_readings_history(_uid_str(401),
                                                lambda frac, s: {"vehicleCount": round(300 + 260 * frac),
                                                                 "avgSpeedKph": round(64 - 40 * frac, 1),
                                                                 "occupancyPct": round(28 + 40 * frac),
                                                                 "congestionLevel": round(_drift(0.5), 3)}))
        if p == "/traffic/congestion/firehose":
            return self._json(_firehose())
        if p == "/traffic/congestion/prediction":
            out = []
            for zf in _firehose():
                for m in (5, 10, 15, 20, 25, 30):
                    pred = max(0.05, min(0.98, zf["congestionLevel"] + m * 0.004 if zf["trend"] == "up"
                                         else zf["congestionLevel"] - m * 0.003))
                    out.append({"zoneId": zf["zoneId"], "horizonMinute": m,
                                "predictedCongestion": round(pred, 3), "confidence": round(0.78, 2)})
            return self._json(out)
        if p == "/roads":
            return self._json(ROADS)
        if p == "/maintenance/requests":
            return self._json(MAINT)
        if p == "/maintenance/summary":
            rows = []
            for st, n in MAINT_STATUS.items():
                rows.append({"status": st, "count": n})
            for clsname, label in (("hw", "hw"), ("ar", "ar"), ("co", "co"), ("pw", "pw")):
                c = sum(1 for r in ROADS if r["roadClass"] == label)
                if c:
                    rows.insert(0, {"roadClass": label, "status": "open", "count": c})
            return self._json(rows)
        if p == "/drainage/networks":
            return self._json(NETWORKS)
        if p == "/drainage/sensors":
            return self._json(DRAIN_SENSORS)
        if p == "/drainage/readings":
            sid = _uid_str(411)
            return self._json(_readings_history(sid, lambda frac, s: {
                "waterLevelCm": round(18 + 46 * frac, 1), "flowRateLps": round(60 + 120 * frac, 1),
                "rainfallMm": round(max(0.0, 8 * frac - 2), 1),
                "riskLevel": "low" if frac < 0.5 else ("moderate" if frac < 0.72 else "high")}))
        if p == "/drainage/waterlogging/predictions":
            return self._json(_water_predictions())
        if p == "/energy/grids":
            return self._json(GRIDS)
        if p == "/energy/readings":
            return self._json(_readings_history(_uid_str(601), lambda frac, s: {
                "consumptionKw": round(1500 + 1600 * frac), "voltage": 24500, "outageReported": False}))
        if p == "/energy/summary":
            return self._json(_energy_summary())
        if p == "/energy/assets":
            return self._json(ASSETS)
        if p == "/waste/bins":
            return self._json(BINS)
        if p == "/waste/routes":
            return self._json([])
        if p == "/emergency/incidents":
            return self._json(INCIDENTS)
        if p == "/emergency/vehicles":
            return self._json(VEHICLES)
        if p.startswith("/emergency/route/safest"):
            return self._json(_safest_route(p))
        if p == "/reports":
            return self._json(REPORTS)
        if p == "/simulation/runs":
            return self._json(RUNS)
        if p.startswith("/roads/"):
            return self._json(next((r for r in ROADS if r["id"] == p.split("/")[2]), None) or (self._json({}, 404) or {}))
        if p.startswith("/simulation/runs/"):
            return self._json(next((RUNS[0] if RUNS else {"nodes": [], "edges": []}), {}))
        return self._json({"error": "not found", "path": p}, 404)

    def _sse(self):
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        try:
            while True:
                payload = json.dumps(_twin_state()).encode()
                self.wfile.write(b"data: " + payload + b"\n\n")
                self.wfile.flush()
                if not self.rfile.readable() or self.wfile.closed:
                    break
                time.sleep(2)
        except (ConnectionAbortedError, ConnectionResetError, OSError):
            pass

    def _layer(self, layer):
        def feat(geom, props):
            return {"type": "Feature", "geometry": geom, "properties": props}
        if layer == "zones":
            return {"type": "FeatureCollection", "features": [
                feat(json.loads(z["geojson"]), {"id": z["id"], "name": z["name"], "zoneType": z["zoneType"]})
                for z in ZONES]}
        if layer == "roads":
            return {"type": "FeatureCollection", "features": [
                feat(json.loads(r["geojson"]), {"id": r["id"], "name": r["name"], "roadClass": r["roadClass"],
                                                "conditionScore": r["conditionScore"]}) for r in ROADS]}
        if layer == "traffic-sensors":
            return {"type": "FeatureCollection", "features": [
                feat({"type": "Point", "coordinates": s["location"]}, {"id": s["id"], "name": s["name"], "status": s["status"]})
                for s in TRAFFIC_SENSORS]}
        if layer == "drainage-sensors":
            return {"type": "FeatureCollection", "features": [
                feat({"type": "Point", "coordinates": s["location"]}, {"id": s["id"], "name": s["name"], "status": s["status"]})
                for s in DRAIN_SENSORS]}
        if layer == "bins":
            return {"type": "FeatureCollection", "features": [
                feat({"type": "Point", "coordinates": b["location"]}, {"id": b["id"], "name": b["name"],
                                                                       "fillLevelPct": b["fillLevelPct"]}) for b in BINS]}
        if layer == "assets":
            return {"type": "FeatureCollection", "features": [
                feat({"type": "Point", "coordinates": a["location"]}, {"id": a["id"], "name": a["name"],
                                                                       "status": a["status"]}) for a in ASSETS]}
        if layer == "incidents":
            return {"type": "FeatureCollection", "features": [
                feat({"type": "Point", "coordinates": i["location"]}, {"id": i["id"], "name": i["name"],
                                                                       "status": i["status"], "severity": i["severity"]})
                for i in INCIDENTS]}
        if layer == "emergency-vehicles":
            return {"type": "FeatureCollection", "features": [
                feat({"type": "Point", "coordinates": v["station"]}, {"id": v["id"], "name": v["name"], "status": v["status"]})
                for v in VEHICLES]}
        if layer == "energy-grids":
            return {"type": "FeatureCollection", "features": [
                feat({"type": "Point", "coordinates": [ZONES[i % 10]["_lon"], ZONES[i % 10]["_lat"]]},
                     {"id": g["id"], "name": g["name"], "status": "operational"})
                for i, g in enumerate(GRIDS)]}
        return {"type": "FeatureCollection", "features": []}


class MockServer(ThreadingHTTPServer):
    # request_queue_size only takes effect BEFORE server_bind (TCPServer.__init__),
    # so declare it on the class. The page opens ~10 parallel connections at
    # boot; Windows strict backlogs make anything below that hang first loads.
    request_queue_size = 128
    daemon_threads = True
    allow_reuse_address = True


if __name__ == "__main__":
    print("UrbanTwin mock API on http://localhost:8080/api/v1  (Ctrl+C to stop)")
    MockServer(("0.0.0.0", 8080), H).serve_forever()
