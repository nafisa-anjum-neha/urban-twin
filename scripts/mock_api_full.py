"""UrbanTwin local demo backend (mock, stdlib only) — MULTI-CITY edition.

Serves the frontend's full API surface for 5 cities with live, diurnal
telemetry on http://localhost:8080, so the Vite dev server renders every
page without Docker/Postgres/Kafka.

Cities: Dhaka 🇧🇩 · Kolkata 🇮🇳 · Mumbai 🇮🇳 · Singapore 🇸🇬 · Tokyo 🇯🇵
Switch via  ?cityId=<slug>  on any endpoint (the UI passes it everywhere).

Run:  python mock_api_full.py            (server on :8080, Ctrl+C to stop)
"""
import json
import math
import random
import time
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit, parse_qs

RNG = random.Random(7)
BASE = "/api/v1"
NS = "6c9f2a52-9c63-49a6-bd8f-9d51a3d34e11"

def uid(slug, n) -> str:
    """Stable UUID per city entity: uuid5 of 'slug:n'."""
    return __import__("uuid").uuid5(__import__("uuid").UUID(NS), f"{slug}:{n}").hex[:8] + \
           "-0000-4000-8000-" + f"{n:012d}"[-12:]

def iso(dt=None) -> str:
    return (dt or datetime.now(timezone.utc)).isoformat().replace("+00:00", "Z")


class CityBuilder:
    """Builds one city's whole dataset from a compact preset."""

    def __init__(self, preset):
        slug = preset["slug"]
        self.slug = slug
        self.city = {"id": uid(slug, 1), "name": preset["name"], "slug": slug,
                     "country": preset["country"], "flag": preset["flag"],
                     "population": preset["population"]}
        zones = []
        for i, (name, ztype, lat, lon, r, cong) in enumerate(preset["zones"], start=1):
            pts = []
            for k in range(14):
                a = 2 * math.pi * k / 14
                noise = 0.82 + 0.18 * ((k * 7 + i * 13) % 5) / 4
                pts.append([round(lon + r * noise * math.sin(a) / 100 * 0.95, 6),
                            round(lat + r * noise * math.cos(a) / 110, 6)])
            pts.append(pts[0])
            zones.append({"id": uid(slug, 100 + i), "name": name, "zoneType": ztype,
                          "cityId": self.city["id"],
                          "geojson": json.dumps({"type": "Polygon", "coordinates": [pts]}),
                          "_lat": lat, "_lon": lon, "_r": r, "_cong": cong})
        self.zones = zones
        by_name = {z["name"]: z for z in zones}
        roads = []
        for j, (name, cls, zname, cond) in enumerate(preset["roads"], start=1):
            z = by_name[zname]
            n = 6
            line = []
            for k in range(n):
                t = k / (n - 1)
                line.append([round(z["_lon"] - z["_r"] * 0.03 + t * z["_r"] * 0.055, 6),
                             round(z["_lat"] - z["_r"] * 0.045 + t * z["_r"] * 0.09, 6)])
            roads.append({"id": uid(slug, 200 + j), "zoneId": z["id"], "name": name,
                          "roadClass": cls, "conditionScore": cond,
                          "geojson": json.dumps({"type": "LineString", "coordinates": line}),
                          "openMaintenanceRequests": 1 if cond < 50 else 0})
        self.roads = roads
        # sensors
        self.traffic_sensors = [
            {"id": uid(slug, 400 + i), "roadId": roads[i % len(roads)]["id"],
             "name": f"{z['name']} corridor sensor", "location": self._pt(z),
             "status": "active",
             "latestReading": {"sensorId": uid(slug, 400 + i), "vehicleCount": 320,
                               "avgSpeedKph": 34, "occupancyPct": 42,
                               "congestionLevel": z["_cong"], "recordedAt": iso()}}
            for i, z in enumerate(zones, start=1)]
        self.drain_sensors = [
            {"id": uid(slug, 410 + i), "networkId": uid(slug, 500 + i),
             "name": f"{z['name']} storm gauge", "location": self._pt(z),
             "status": "normal" if i % 4 else "warning",
             "latestReading": {"sensorId": uid(slug, 410 + i),
                               "waterLevelCm": round(RNG.uniform(8, 38), 1),
                               "flowRateLps": round(RNG.uniform(40, 160), 1),
                               "rainfallMm": round(RNG.uniform(0, 6), 1),
                               "riskLevel": "low", "recordedAt": iso()}}
            for i, z in enumerate(zones, start=1)]
        self.networks = [{"id": uid(slug, 500 + i), "zoneId": z["id"],
                          "name": f"{z['name']} trunk drain",
                          "capacityM3PerHour": round(250 + 70 * (i % 5)),
                          "networkClass": "primary" if i % 2 else "secondary"}
                         for i, z in enumerate(zones, start=1)]
        self.grids = [{"id": uid(slug, 600 + i), "zoneId": z["id"],
                       "name": f"{z['name']} substation",
                       "capacityKw": 3800 - 90 * i, "lossPct": round(3.5 + z["_cong"], 2),
                       "zoneName": z["name"],
                       "latestReading": {"gridId": uid(slug, 600 + i),
                                         "consumptionKw": round((3800 - 90 * i) * z["_cong"]),
                                         "voltage": 24500, "outageReported": i == 6,
                                         "recordedAt": iso()}}
                      for i, z in enumerate(zones, start=1)]
        binnames = preset.get("binnames", ["Bazar", "School gate", "Bus terminal", "Mosque lane"])
        self.bins = []
        for i, z in enumerate(zones, start=1):
            for b in range(3):
                self.bins.append({
                    "id": uid(slug, 700 + 3 * (i - 1) + b), "zoneId": z["id"],
                    "name": f"{z['name']} {binnames[b % len(binnames)]} bin",
                    "location": self._pt(z, 1.0),
                    "fillLevelPct": min(99, int(28 + 9 * ((i * 3 + b) % 8) + (17 if (i + b) % 3 == 0 else 0))),
                    "lastCollectedAt": iso(datetime.now(timezone.utc) - timedelta(hours=6 + b * 7))})
        self.assets = [
            {"id": uid(slug, 800 + i), "zoneId": zones[i % len(zones)]["id"],
             "assetType": ["streetlight", "water_main", "pump", "transformer", "signal"][i % 5],
             "name": f"{zones[i % len(zones)]['name']} asset #{i}",
             "location": self._pt(zones[i % len(zones)], 1.0),
             "conditionScore": 55 + (i * 7) % 40, "installedYear": 2005 + (i % 18),
             "status": "operational" if i % 7 else "degraded"}
            for i in range(1, 21)]
        inc_names = preset["incidents"]
        self.incidents = []
        for i, (n, t, s, d) in enumerate(inc_names, start=1):
            z = zones[(i * 3) % len(zones)]
            self.incidents.append({
                "id": uid(slug, 900 + i), "zoneId": z["id"], "name": n,
                "incidentType": t, "severity": s, "location": self._pt(z),
                "description": d, "status": "dispatched" if i == 1 else "active",
                "reportedAt": iso(datetime.now(timezone.utc) - timedelta(minutes=25 * i)),
                "resolvedAt": None})
        self.vehicles = [
            {"id": uid(slug, 950 + i), "name": n, "vehicleType": t, "status": st,
             "station": stn, "headingDeg": h}
            for i, (n, t, st, stn, h) in enumerate(preset["vehicles"], start=1)]
        self.reports = [{"id": uid(slug, 1000 + i), "zoneId": zones[(i * 2) % len(zones)]["id"],
                         "category": cat, "description": desc,
                         "location": self._pt(zones[(i * 2) % len(zones)]),
                         "status": {"pothole": "in_progress", "flooding": "submitted",
                                    "lighting": "acknowledged", "garbage": "in_progress",
                                    "leak": "resolved"}[cat],
                         "upvotes": (i * 5) % 34,
                         "reporterName": ["Aminul", "Sadia", "Rafiq", "Tahmid"][i % 4],
                         "createdAt": iso(datetime.now(timezone.utc) - timedelta(hours=5 * i))}
                        for i, (cat, desc) in enumerate(preset["reports"] * 2, start=1)]
        maint = []
        pri = ["p1", "p2", "p3", "p4"]
        k = 0
        for st, n in {"open": 6, "assigned": 3, "in_progress": 4, "resolved": 9, "closed": 12}.items():
            for _ in range(n):
                k += 1
                road = roads[k % len(roads)]
                maint.append({"id": uid(slug, 1100 + k), "title": f"Repair crew — {road['name']} section",
                              "description": "Asphalt patch + drain clearing", "priority": pri[k % 4],
                              "status": st, "reportedBy": "ops console", "roadId": road["id"],
                              "assetId": None, "roadName": road["name"],
                              "scheduledDate": iso(datetime.now(timezone.utc) + timedelta(days=1 + k % 5))[:10],
                              "resolvedAt": iso() if st == "resolved" else None,
                              "createdAt": iso(datetime.now(timezone.utc) - timedelta(days=k % 6))})
        self.maint = maint
        self.runs = []

    def _pt(self, z, drift=0.6):
        return [round(z["_lon"] + RNG.uniform(-drift, drift) * z["_r"] / 90, 6),
                round(z["_lat"] + RNG.uniform(-drift, drift) * z["_r"] / 90, 6)]

    # ---------- live computations ----------
    def drift(self, base, amp=0.22):
        h = time.localtime().tm_hour + time.localtime().tm_min / 60.0
        diurnal = amp * math.sin((h - 3) / 24 * 2 * math.pi)
        return max(0.06, min(0.97, base + diurnal + RNG.uniform(-0.07, 0.12)))

    def twin_state(self):
        zs = []
        for z in self.zones:
            cong = self.drift(z["_cong"])
            zs.append({"zoneId": z["id"], "name": z["name"], "congestion": round(cong, 3),
                       "floodRisk": round(self.drift(0.25 + 0.1 * z["_cong"], 0.2), 3),
                       "powerState": "watch" if z is self.zones[min(5, len(self.zones) - 1)] else "ok",
                       "binsOverfull": sum(1 for b in self.bins if b["zoneId"] == z["id"] and b["fillLevelPct"] >= 85),
                       "assetHealth": 99 - ord(z["name"][0]) % 11})
        total = sum(z["congestion"] for z in zs) / max(1, len(zs))
        return {
            "city": {"id": self.city["id"], "name": self.city["name"],
                     "flag": self.city["flag"], "country": self.city["country"]},
            "zones": zs,
            "totals": {
                "activeIncidents": sum(1 for i in self.incidents if i["status"] != "resolved"),
                "openMaintenance": sum(1 for m in self.maint if m["status"] in ("open", "assigned", "in_progress")),
                "binsOverfull": sum(1 for b in self.bins if b["fillLevelPct"] >= 85),
                "avgCongestion": round(total, 3),
                "networkStatus": "critical" if total > 0.8 else ("degraded" if total > 0.6 else "ok"),
                "dataFreshness": "sims 2 s",
            },
            "updatedAt": iso(),
        }

    def firehose(self):
        out = []
        for z in self.zones:
            c = self.drift(z["_cong"])
            out.append({"zoneId": z["id"], "name": z["name"], "congestionLevel": round(c, 3),
                        "avgSpeedKph": round(62 - 45 * c, 1),
                        "trend": "up" if c > z["_cong"] else ("down" if c < z["_cong"] - 0.05 else "stable"),
                        "updatedAt": iso()})
        return out

    def predictions(self):
        out = []
        for zf in self.firehose():
            for m in (5, 10, 15, 20, 25, 30):
                pred = max(0.05, min(0.98, zf["congestionLevel"] + m * 0.004 if zf["trend"] == "up"
                                     else zf["congestionLevel"] - m * 0.003))
                out.append({"zoneId": zf["zoneId"], "horizonMinute": m,
                            "predictedCongestion": round(pred, 3), "confidence": 0.78})
        return out

    def water_predictions(self):
        out = []
        for z in self.zones:
            lvl = self.drift(0.3 + 0.5 * z["_cong"], 0.3)
            risk = "severe" if lvl > 0.8 else ("high" if lvl > 0.62 else ("moderate" if lvl > 0.4 else "low"))
            affected = [r["id"] for r in self.roads if r["zoneId"] == z["id"] and r["conditionScore"] < 60]
            out.append({"zoneId": z["id"], "risk": risk, "maxLevelCm": round(lvl * 95, 1),
                        "affectedRoads": affected, "confidence": round(0.72 + RNG.uniform(0, 0.2), 2)})
        return out

    def energy_summary(self):
        return [{"zoneId": g["zoneId"], "name": g["zoneName"],
                 "currentKw": g["latestReading"]["consumptionKw"],
                 "peakKw24h": round(g["capacityKw"] * 0.92),
                 "outages24h": 3 if g["zoneName"] == self.zones[min(5, len(self.zones) - 1)]["name"] else 0,
                 "trend": "up" if g["latestReading"]["consumptionKw"] > g["capacityKw"] / 2 else "down"}
                for g in self.grids]

    def readings_history(self, maker):
        out = []
        for step in range(24, -1, -1):
            t = datetime.now(timezone.utc) - timedelta(hours=step)
            h = t.hour + t.minute / 60
            frac = 0.55 + 0.4 * math.sin((h - 3) / 24 * 2 * math.pi)
            out.append({"recordedAt": iso(t), **maker(frac)})
        return out

    def heading(self):
        return "Heritage Arcade roof leak after the storm."


CITY_PRESETS = [
    {
        "slug": "dhaka", "name": "Dhaka", "country": "Bangladesh", "flag": "🇧🇩",
        "population": 22_500_000,
        "zones": [
            ("Gulshan", "commercial", 23.7925, 90.4125, 1.4, 0.72),
            ("Banani", "commercial", 23.7898, 90.4012, 1.3, 0.66),
            ("Dhanmondi", "residential", 23.7461, 90.3760, 1.8, 0.58),
            ("Mirpur", "mixed", 23.8069, 90.3667, 2.2, 0.49),
            ("Uttara", "mixed", 23.8759, 90.3795, 2.4, 0.41),
            ("Motijheel", "commercial", 23.7333, 90.4127, 1.5, 0.77),
            ("Rampura", "mixed", 23.7679, 90.4248, 1.7, 0.55),
            ("Mohammadpur", "residential", 23.7640, 90.3596, 1.9, 0.52),
        ],
        "roads": [
            ("Progoti Sarani", "ar", "Rampura", 62), ("Airport Road", "hw", "Uttara", 78),
            ("Mirpur Road", "ar", "Mirpur", 44), ("Sonargaon Road", "ar", "Dhanmondi", 55),
            ("Kazi Nazrul Islam Avenue", "ar", "Gulshan", 51), ("Bangla Motor Road", "co", "Motijheel", 67),
            ("Begum Rokeya Sarani", "ar", "Mirpur", 63), ("Shahid Tajuddin Ahmed Sarani", "hw", "Uttara", 48),
        ],
        "binnames": ["Bazar", "School gate", "Bus terminal", "Mosque lane"],
        "incidents": [
            ("Waterlogging in Tejgaon link road", "flood", 4, "Drains overflowing after cloudburst; two lanes impassable."),
            ("Feeder trip at Motijheel substation", "power_outage", 4, "11 kV feeder down; partial area blackout."),
            ("Building collapse warning (evacuated)", "hazard", 5, "Cracks widened overnight, the Fire Service cordoned the block."),
            ("Garbage truck stranded on Rampura bridge", "traffic", 2, "Broken-down compactor Blocking an outbound lane."),
            ("Sidewalk railing damaged near Banani 11", "hazard", 3, "Loose railing after a bus scraped the footpath."),
        ],
        "reports": [
            ("pothole", "Deep potholes on the rickshaw lane after last night's rain."),
            ("flooding", "Underpass waterlogged again in the downpour."),
            ("lighting", "Streetlight pole leaning and dark near the bazar crossing."),
            ("garbage", "Van missed pickup for the second day beside the mosque."),
            ("leak", "Water main leak pooling at the corner near Banani 11."),
        ],
        "vehicles": [
            ("Dhaka Central Ambulance 1", "ambulance", "available", [90.4125, 23.7755], 45),
            ("Mirpur Fire Engine 2", "fire_engine", "dispatched", [90.3667, 23.8069], -70),
            ("Uttara Ambulance 3", "ambulance", "available", [90.3795, 23.8759], 120),
            ("Motijheel Rescue 1", "rescue", "available", [90.4127, 23.7333], 0),
        ],
    },
    {
        "slug": "kolkata", "name": "Kolkata", "country": "India", "flag": "🇮🇳",
        "population": 15_100_000,
        "zones": [
            ("Park Street", "commercial", 22.5535, 88.3523, 1.3, 0.68),
            ("Salt Lake", "commercial", 22.5868, 88.4339, 1.9, 0.54),
            ("Howrah", "industrial", 22.5958, 88.2636, 2.0, 0.63),
            ("Behala", "residential", 22.4967, 88.3107, 1.9, 0.47),
            ("Dum Dum", "mixed", 22.6420, 88.4311, 1.8, 0.52),
            ("New Town", "commercial", 22.5800, 88.4640, 2.1, 0.44),
            ("Jadavpur", "residential", 22.4990, 88.3670, 1.7, 0.51),
            ("Sealdah", "mixed", 22.5670, 88.3700, 1.3, 0.71),
        ],
        "roads": [
            ("Chowringhee Road", "ar", "Park Street", 58), ("VIP Road", "hw", "Dum Dum", 71),
            ("EM Bypass", "hw", "Jadavpur", 53), ("AJC Bose Road", "ar", "Sealdah", 47),
            ("BT Road", "ar", "Howrah", 60), ("Strand Road", "co", "Howrah", 64),
            ("MG Road", "co", "Sealdah", 49), ("New Town Link", "hw", "New Town", 75),
        ],
        "reports": [
            ("pothole", "Craters on the tram lines remnant near Esplanade."),
            ("flooding", "Underpass at AJC Bose submerged within minutes of rain."),
            ("garbage", "Vat overflowing near the Howrah station approach."),
            ("leak", "Colonial-era water pipe leaking into the lane."),
            ("lighting", "Lamp post out across from the park."),
        ],
        "incidents": [
            ("Tidal flooding in Howrah approach", "flood", 3, "High tide pushed water over the strand embankment."),
            ("Tram wire snap at Sealdah", "hazard", 3, "Overhead wire down across the carriageway."),
            ("Transformer blast in Salt Lake", "power_outage", 4, "Sector V feeder down; office blocks on backup."),
            ("Bridge girder alarm (Howrah)", "hazard", 5, "Sensors flagged vibration beyond limits; traffic diverted."),
            ("Waterlogging at AJC Bose underpass", "flood", 3, "Pumps overwhelmed by the evening shower."),
        ],
        "vehicles": [
            ("Kolkata Central Ambulance 1", "ambulance", "available", [88.3523, 22.5671], 40),
            ("Howrah Fire Engine 2", "fire_engine", "dispatched", [88.2636, 22.5958], -60),
            ("Salt Lake Ambulance 3", "ambulance", "available", [88.4339, 22.5868], 110),
            ("Sealdah Rescue 1", "rescue", "available", [88.3700, 22.5670], 15),
        ],
    },
    {
        "slug": "mumbai", "name": "Mumbai", "country": "India", "flag": "🇮🇳",
        "population": 21_300_000,
        "zones": [
            ("Bandra", "commercial", 19.0596, 72.8295, 1.5, 0.74),
            ("Andheri", "commercial", 19.1136, 72.8697, 2.0, 0.69),
            ("Colaba", "mixed", 18.9067, 72.8147, 1.4, 0.57),
            ("Dadar", "mixed", 19.0178, 72.8478, 1.6, 0.66),
            ("Powai", "industrial", 19.1176, 72.9060, 1.8, 0.48),
            ("Worli", "commercial", 19.0070, 72.8180, 1.3, 0.7),
            ("Chembur", "mixed", 19.0520, 72.8970, 1.7, 0.56),
            ("Malad", "residential", 19.1870, 72.8480, 1.9, 0.5),
        ],
        "roads": [
            ("Marine Drive", "hw", "Colaba", 81), ("SV Road", "hw", "Bandra", 52),
            ("Western Express Highway", "hw", "Andheri", 57), ("Eastern Express Highway", "hw", "Chembur", 60),
            ("Link Road", "ar", "Malad", 55), ("LBS Marg", "ar", "Powai", 46),
            ("Carter Road", "co", "Bandra", 70), ("Worli Sea Link", "hw", "Worli", 84),
        ],
        "reports": [
            ("flooding", "Subway pump room filling up near the station."),
            ("pothole", "Potholes on the sea-facing promenade stretch."),
            ("garbage", "Overflow near the chawl compound for two days."),
            ("leak", "Pipeline leak near the water tank."),
            ("lighting", "Coastal road lights off since the outage."),
        ],
        "incidents": [
            ("High-tide flooding Worli sea face", "flood", 4, "Tide overtopped the parapet; northbound lane closed."),
            ("Signal failure Andheri junction", "traffic", 3, "Adaptive lights offline; wardens deployed."),
            ("Outage in Chembur feeder", "power_outage", 3, "Load-shedding window extended after fault."),
            ("Pothole cluster Dadar TT", "hazard", 2, "Fresh craters reported on the flyover approach."),
            ("Pump room flooding at the metro", "flood", 3, "Sump pumps lagging behind the downpour."),
        ],
        "vehicles": [
            ("Mumbai Central Ambulance 1", "ambulance", "available", [72.8295, 19.0596], 30),
            ("Powai Fire Engine 2", "fire_engine", "dispatched", [72.9060, 19.1176], -80),
            ("Colaba Ambulance 3", "ambulance", "available", [72.8147, 18.9067], 100),
            ("Dadar Rescue 1", "rescue", "available", [72.8478, 19.0178], 10),
        ],
    },
    {
        "slug": "singapore", "name": "Singapore", "country": "Singapore", "flag": "🇸🇬",
        "population": 5_900_000,
        "zones": [
            ("Orchard", "commercial", 1.3049, 103.8318, 1.1, 0.6),
            ("Marina Bay", "commercial", 1.2830, 103.8600, 1.2, 0.55),
            ("Jurong East", "industrial", 1.3329, 103.7436, 2.0, 0.43),
            ("Tampines", "mixed", 1.3496, 103.9568, 1.9, 0.47),
            ("Woodlands", "mixed", 1.4382, 103.7890, 2.2, 0.38),
            ("Punggol", "residential", 1.4050, 103.9020, 2.0, 0.44),
            ("Bugis", "mixed", 1.3506, 103.8560, 1.2, 0.63),
            ("Sentosa", "mixed", 1.2494, 103.8174, 1.5, 0.35),
        ],
        "roads": [
            ("Orchard Boulevard", "ar", "Orchard", 88), ("Nicoll Highway", "hw", "Bugis", 79),
            ("Pan Island Expressway", "hw", "Jurong East", 66), ("Bukit Timah Road", "ar", "Woodlands", 74),
            ("Ayer Rajah Expressway", "hw", "Sentosa", 87), ("East Coast Parkway", "hw", "Marina Bay", 85),
            ("Thomson Road", "ar", "Punggol", 71), ("Serangoon Road", "co", "Bugis", 68),
        ],
        "reports": [
            ("flooding", "Flash flood underpass at the expressway exit after the storm."),
            ("pothole", "Road shoulder crumbled near the construction hoarding."),
            ("garbage", "Chute blockage the second HDB block in a row."),
            ("lighting", "Pedestrian crossing lights flickering at night."),
            ("leak", "Construction site dewatering into the monsoon drain."),
        ],
        "incidents": [
            ("Flash flood Jurong East underpass", "flood", 3, "Ponding after the record morning squall."),
            ("Lift breakdown Tampines block", "hazard", 2, "Lift stuck between floors; technician en route."),
            ("Grid voltage dip Woodlands", "power_outage", 2, "Brief brownout reported by smart meters."),
            ("Roadworks overrun PIE exit", "traffic", 2, "Works overran; residual lane closure."),
            ("Pedestrian crossing fault Orchard", "hazard", 1, "Crossing timer stuck; manual override."),
        ],
        "vehicles": [
            ("SCDF Ambulance 1", "ambulance", "available", [103.8318, 1.3049], 60),
            ("Jurong Fire Engine 2", "fire_engine", "dispatched", [103.7436, 1.3329], -75),
            ("Tampines Ambulance 3", "ambulance", "available", [103.9568, 1.3496], 130),
            ("Marina Rescue 1", "rescue", "available", [103.8600, 1.2830], 5),
        ],
    },
    {
        "slug": "tokyo", "name": "Tokyo", "country": "Japan", "flag": "🇯🇵",
        "population": 37_400_000,
        "zones": [
            ("Shibuya", "commercial", 35.6595, 139.7005, 1.2, 0.65),
            ("Shinjuku", "commercial", 35.6938, 139.7035, 1.4, 0.71),
            ("Ginza", "commercial", 35.6717, 139.7650, 1.2, 0.6),
            ("Ueno", "mixed", 35.7156, 139.7745, 1.4, 0.52),
            ("Ikebukuro", "mixed", 35.7295, 139.7109, 1.5, 0.57),
            ("Asakusa", "mixed", 35.7118, 139.7967, 1.4, 0.49),
            ("Akihabara", "commercial", 35.6989, 139.7731, 1.0, 0.63),
            ("Ota", "industrial", 35.5670, 139.7240, 2.3, 0.45),
        ],
        "roads": [
            ("Meiji-dori", "ar", "Shibuya", 78), ("Yasukuni-dori", "co", "Shinjuku", 74),
            ("Chuo-dori", "co", "Ginza", 82), ("Kasu-ga-dori", "ar", "Ueno", 70),
            ("Metropolitan Route 417", "hw", "Ikebukuro", 72), ("Kototoi-dori", "ar", "Asakusa", 76),
            ("Shshow-dori", "co", "Akihabara", 80), ("Kanpachi-dori", "hw", "Ota", 68),
        ],
        "reports": [
            ("pothole", "Road dip formed after the last typhoon near the crossing."),
            ("flooding", "Underpass pump backup delays traffic at the expressway ramp."),
            ("garbage", "Collection point overflowing outside the station exit."),
            ("leak", "Water supply pipe seeping by the vending machine corner."),
            ("lighting", "Streetlights on the pedestrian bridge staying dark."),
        ],
        "incidents": [
            ("Typhoon drainage backlog Asakusa", "flood", 3, "Pumps running after the typhoon band."),
            ("Train delay ripple Shinjuku", "traffic", 3, "Signalling fault backing up the interchange."),
            ("Blackout block Ikebukuro", "power_outage", 2, "Distribution fault affecting two blocks."),
            ("Sidewalk crack Ueno", "hazard", 2, "Ground settlement after utility work."),
            ("Elevator fault Ota depot", "hazard", 1, "Freight lift inspection triggered."),
        ],
        "vehicles": [
            ("Tokyo Ambulance 1", "ambulance", "available", [139.7005, 35.6595], 55),
            ("Shinjuku Fire Engine 2", "fire_engine", "dispatched", [139.7035, 35.6938], -65),
            ("Ota Ambulance 3", "ambulance", "available", [139.7240, 35.5670], 115),
            ("Ueno Rescue 1", "rescue", "available", [139.7745, 35.7156], 20),
        ],
    },
]

CITIES = {p["slug"]: CityBuilder(p) for p in CITY_PRESETS}


def resolve_city(qs, default="dhaka"):
    """cityId may be a slug or uuid. Falls back to the default city."""
    cid = (qs.get("cityId") or [None])[0]
    if not cid or cid == "null":
        return CITIES[default]
    for c in CITIES.values():
        if cid in (c.slug, c.city["id"]):
            return c
    return CITIES[default]


def route_optimize(city, body):
    bins = [b for b in city.bins if b["zoneId"] == body.get("zoneId")] or city.bins[:6]
    bins.sort(key=lambda b: -b["fillLevelPct"])
    stops = [{"binId": b["id"], "name": b["name"], "lat": b["location"][1],
              "lon": b["location"][0], "fillLevel": b["fillLevelPct"], "order": i + 1}
             for i, b in enumerate(bins[:8])]
    return {"id": uid(city.slug, 1230), "name": "AM-shift collection route",
            "zoneId": body.get("zoneId"), "vehicleId": None, "stops": stops,
            "totalDistanceKm": round(RNG.uniform(9, 17), 1),
            "estimatedDurationMin": 5 * len(stops) + 22, "status": "planned"}


def safest_route(city):
    z = city.zones[0]
    line = [[z["_lon"], z["_lat"] - .02], [z["_lon"] + .01, z["_lat"]], [z["_lon"] + .02, z["_lat"] + .01]]
    return {"distanceKm": 5.4, "etaMin": 16, "riskScore": 0.18, "roads": 3,
            "geojson": json.dumps({"type": "LineString", "coordinates": line})}


def cascade(city, body):
    scenario = body.get("scenario", "rainfall")
    zone_id = body.get("params", {}).get("zoneId")
    zone = next((z for z in city.zones if z["id"] == zone_id), city.zones[0])
    intensity = body.get("params", {}).get("intensityMm", 85)
    sev = min(5, max(2, round(intensity / 22)))
    N = zone["name"]
    if scenario == "substation_outage":
        nodes = [
            {"id": "t1", "type": "TRIGGER", "label": f"{N} feeder trip", "zoneId": zone["id"], "startOffsetSec": 0, "severity": sev},
            {"id": "g1", "type": "ENERGY", "label": f"{N} substation offline", "zoneId": zone["id"], "startOffsetSec": 45, "severity": 3},
            {"id": "p1", "type": "DRAINAGE", "label": "Lift pumps stop", "zoneId": zone["id"], "startOffsetSec": 180, "severity": 3},
            {"id": "r1", "type": "ROAD", "label": "Signal loss on key road", "zoneId": zone["id"], "startOffsetSec": 240, "severity": 2},
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
            {"id": "g1", "type": "TRAFFIC", "label": "Prediction degraded", "zoneId": zone["id"], "startOffsetSec": 30, "severity": 2},
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
    return {"simulationId": uid(city.slug, 1300), "scenario": scenario, "status": "completed",
            "timelineSeconds": max(n["startOffsetSec"] for n in nodes) + 60,
            "cascade": {"nodes": nodes,
                        "edges": [{"from": a, "to": b, "relation": rel, "delaySec": d, "probability": p}
                                  for a, b, rel, d, p in edges]},
            "impactSummary": impact, "name": body.get("name", "Demo run"),
            "startedAt": iso(), "completedAt": iso()}


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
        split = urlsplit(self.path)
        p = split.path[len(BASE):] if split.path.startswith(BASE) else split.path
        qs = parse_qs(split.query)
        cid = qs.get("cityId") or (body.get("cityId") if isinstance(body, dict) else None)
        city = resolve_city({"cityId": [cid]} if cid else {})

        if p.startswith("/simulation/run"):
            res = cascade(city, body)
            city.runs.insert(0, {"id": res["simulationId"], "simulationId": res["simulationId"],
                                 "name": res["name"], "scenario": res["scenario"], "status": "completed",
                                 "startedAt": res["startedAt"], "completedAt": res["completedAt"]})
            return self._json(res)
        if p.startswith("/waste/routes/optimize"):
            return self._json(route_optimize(city, body))
        if p.startswith("/waste/bins/") and p.endswith("/collect"):
            bid = p.split("/")[2]
            for b in city.bins:
                if b["id"] == bid:
                    b["fillLevelPct"] = 0
                    b["lastCollectedAt"] = iso()
                    return self._json(b)
            return self._json({"error": "not found"}, 404)
        if p.startswith("/emergency/incidents/"):
            if "/dispatch" in p:
                return self._json({"id": uid(city.slug, 1400), "incidentId": body.get("incidentId", ""),
                                   "vehicleId": body.get("vehicleId", ""), "routeGeojson": None,
                                   "distanceKm": round(RNG.uniform(3, 9), 1), "etaMin": RNG.randint(6, 25),
                                   "status": "en_route", "dispatchedAt": iso()})
            return self._json({"id": body.get("id", ""), "status": "resolved"})
        if p.startswith("/emergency/dispatch/") and p.endswith("/resolve"):
            return self._json({"status": "arrived", "resolvedAt": iso()})
        if p.startswith("/emergency/incidents"):
            z = city.zones[RNG.randrange(len(city.zones))]
            inc = {"id": uid(city.slug, 1500), "zoneId": z["id"], "name": body.get("name", "New incident"),
                   "incidentType": body.get("incidentType", "hazard"), "severity": body.get("severity", 2),
                   "location": city._pt(z), "description": body.get("description"),
                   "status": "active", "reportedAt": iso(), "resolvedAt": None}
            city.incidents.append(inc)
            return self._json(inc)
        if p.startswith("/maintenance/requests"):
            z = city.zones[0]
            req = {"id": uid(city.slug, 1600), "title": body.get("title", "Work order"),
                   "description": body.get("description"), "priority": body.get("priority", "p3"),
                   "status": "open", "reportedBy": body.get("reportedBy", "citizen"),
                   "roadId": body.get("roadId"), "assetId": None,
                   "roadName": next((r["name"] for r in city.roads if r["id"] == body.get("roadId")), None),
                   "scheduledDate": None, "resolvedAt": None, "createdAt": iso()}
            city.maint.insert(0, req)
            return self._json(req)
        if p.startswith("/reports") and p.endswith("/upvote"):
            rid = p.split("/")[2]
            for r in city.reports:
                if r["id"] == rid:
                    r["upvotes"] += 1
                    return self._json(r)
            return self._json({"error": "not found"}, 404)
        if p.startswith("/reports"):
            z = city.zones[RNG.randrange(len(city.zones))]
            rep = {"id": uid(city.slug, 1700), "zoneId": z["id"],
                   "category": body.get("category", "other"),
                   "description": body.get("description", ""),
                   "location": body.get("location") or city._pt(z),
                   "status": "submitted", "upvotes": 0,
                   "reporterName": body.get("reporterName", "you"),
                   "createdAt": iso()}
            city.reports.insert(0, rep)
            return self._json(rep)
        return self._json({"ok": True})

    def do_GET(self):
        split = urlsplit(self.path)
        p = split.path[len(BASE):] if split.path.startswith(BASE) else split.path
        qs = parse_qs(split.query)
        city = resolve_city(qs)
        strip = lambda x: p.startswith(x)
        starts = p.startswith

        if strip("/twin/live"):
            return self._sse(city)
        if p == "/cities":
            return self._json([c.city for c in CITIES.values()])
        if p == "/zones":
            return self._json([{k: z[k] for k in ("id", "name", "zoneType", "cityId", "geojson")} for z in city.zones])
        if p == "/twin/state":
            return self._json(city.twin_state())
        if p == "/twin/compare":
            rows = []
            for c in CITIES.values():
                s = c.twin_state()
                rows.append({"cityId": c.city["id"], "slug": c.slug, "name": c.city["name"], "flag": c.city["flag"],
                             "country": c.city["country"], "zones": len(c.zones),
                             "avgCongestion": s["totals"]["avgCongestion"],
                             "activeIncidents": s["totals"]["activeIncidents"],
                             "openMaintenance": s["totals"]["openMaintenance"],
                             "binsOverfull": s["totals"]["binsOverfull"]})
            return self._json(rows)
        if starts("/gis/layers/"):
            return self._json(self._layer(city, p.rsplit("/", 1)[1]))
        if p == "/gis/heatmap":
            return self._json([{"lat": z["_lat"], "lon": z["_lon"],
                                "weight": round(city.drift(z["_cong"]), 2)} for z in city.zones])
        if p == "/traffic/sensors":
            return self._json(city.traffic_sensors)
        if p == "/traffic/readings":
            return self._json(city.readings_history(
                lambda f: {"vehicleCount": round(300 + 260 * f), "avgSpeedKph": round(64 - 40 * f, 1),
                           "occupancyPct": round(28 + 40 * f), "congestionLevel": round(city.drift(0.5), 3)}))
        if p == "/traffic/congestion/firehose":
            return self._json(city.firehose())
        if p == "/traffic/congestion/prediction":
            return self._json(city.predictions())
        if p == "/roads":
            return self._json(city.roads)
        if p == "/maintenance/requests":
            return self._json(city.maint)
        if p == "/maintenance/summary":
            rows, n = [], 0
            for st, k in {"open": 6, "assigned": 3, "in_progress": 4, "resolved": 9, "closed": 12}.items():
                rows.insert(0, {"roadClass": None, "status": st, "count": k})
                n += k
            for rc in ("hw", "ar", "co", "pw"):
                c = sum(1 for r in city.roads if r["roadClass"] == rc)
                if c:
                    rows.insert(n % len(rows), {"roadClass": rc, "status": "open", "count": c})
            return self._json(rows)
        if p == "/drainage/networks":
            return self._json(city.networks)
        if p == "/drainage/sensors":
            return self._json(city.drain_sensors)
        if p == "/drainage/readings":
            return self._json(city.readings_history(lambda f: {
                "waterLevelCm": round(18 + 46 * f, 1), "flowRateLps": round(60 + 120 * f, 1),
                "rainfallMm": round(max(0.0, 8 * f - 2), 1),
                "riskLevel": "low" if f < 0.5 else ("moderate" if f < 0.72 else "high")}))
        if p == "/drainage/waterlogging/predictions":
            return self._json(city.water_predictions())
        if p == "/energy/grids":
            return self._json(city.grids)
        if p == "/energy/readings":
            return self._json(city.readings_history(lambda f: {
                "consumptionKw": round(1500 + 1600 * f), "voltage": 24500, "outageReported": False}))
        if p == "/energy/summary":
            return self._json(city.energy_summary())
        if p == "/energy/assets":
            return self._json(city.assets)
        if p == "/waste/bins":
            return self._json(city.bins)
        if p == "/waste/routes":
            return self._json([])
        if p == "/emergency/incidents":
            return self._json(city.incidents)
        if p == "/emergency/vehicles":
            return self._json(city.vehicles)
        if starts("/emergency/route/safest"):
            return self._json(safest_route(city))
        if p == "/reports":
            return self._json(city.reports)
        if p == "/simulation/runs":
            return self._json(city.runs)
        return self._json({"error": "not found", "path": p}, 404)

    def _sse(self, city):
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        try:
            while True:
                payload = json.dumps(city.twin_state()).encode()
                self.wfile.write(b"data: " + payload + b"\n\n")
                self.wfile.flush()
                time.sleep(2)
        except (ConnectionAbortedError, ConnectionResetError, OSError):
            pass

    def _layer(self, city, layer):
        def feat(geom, props):
            return {"type": "Feature", "geometry": geom, "properties": props}
        if layer == "zones":
            return {"type": "FeatureCollection", "features": [
                feat(json.loads(z["geojson"]), {"id": z["id"], "name": z["name"], "zoneType": z["zoneType"]})
                for z in city.zones]}
        if layer == "roads":
            return {"type": "FeatureCollection", "features": [
                feat(json.loads(r["geojson"]), {"id": r["id"], "name": r["name"],
                                                "roadClass": r["roadClass"],
                                                "conditionScore": r["conditionScore"]})
                for r in city.roads]}
        if layer == "traffic-sensors":
            return {"type": "FeatureCollection", "features": [
                feat({"type": "Point", "coordinates": s["location"]},
                     {"id": s["id"], "name": s["name"], "status": s["status"]})
                for s in city.traffic_sensors]}
        if layer == "drainage-sensors":
            return {"type": "FeatureCollection", "features": [
                feat({"type": "Point", "coordinates": s["location"]},
                     {"id": s["id"], "name": s["name"], "status": s["status"]})
                for s in city.drain_sensors]}
        if layer == "bins":
            return {"type": "FeatureCollection", "features": [
                feat({"type": "Point", "coordinates": b["location"]},
                     {"id": b["id"], "name": b["name"], "fillLevelPct": b["fillLevelPct"]})
                for b in city.bins]}
        if layer == "assets":
            return {"type": "FeatureCollection", "features": [
                feat({"type": "Point", "coordinates": a["location"]},
                     {"id": a["id"], "name": a["name"], "status": a["status"]})
                for a in city.assets]}
        if layer == "incidents":
            return {"type": "FeatureCollection", "features": [
                feat({"type": "Point", "coordinates": i["location"]},
                     {"id": i["id"], "name": i["name"], "status": i["status"], "severity": i["severity"]})
                for i in city.incidents]}
        if layer == "emergency-vehicles":
            return {"type": "FeatureCollection", "features": [
                feat({"type": "Point", "coordinates": v["station"]},
                     {"id": v["id"], "name": v["name"], "status": v["status"]})
                for v in city.vehicles]}
        if layer == "energy-grids":
            return {"type": "FeatureCollection", "features": [
                feat({"type": "Point", "coordinates": [z["_lon"], z["_lat"]]},
                     {"id": g["id"], "name": g["name"], "status": "operational"})
                for g, z in zip(city.grids, city.zones)]}
        return {"type": "FeatureCollection", "features": []}


class MockServer(ThreadingHTTPServer):
    # request_queue_size only takes effect BEFORE server_bind (TCPServer.__init__),
    # so declare it on the class. The page opens ~10 parallel connections at boot.
    request_queue_size = 128
    daemon_threads = True
    allow_reuse_address = True


if __name__ == "__main__":
    names = ", ".join(c.city["name"] + " (" + c.city["country"] + ")" for c in CITIES.values())
    print("UrbanTwin mock API on http://localhost:8080/api/v1 - cities: " + names)
    print("Switch with ?cityId=<slug> · Ctrl+C to stop")
    MockServer(("0.0.0.0", 8080), H).serve_forever()
