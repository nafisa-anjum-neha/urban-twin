# UrbanTwin — API Reference

Base URL: `http://localhost:8080` (dev/compose). Everything also documented by
OpenAPI at `/swagger-ui.html`. ML service: `http://localhost:8000` (OpenAPI at
`/docs`). List endpoints page as `{content, total, page, size}` unless marked
otherwise. Response markers (`backend`) tell you which inference tier served
the answer (`tensorflow`, `sklearn` or `heuristic`).

## City / zones

- `GET /api/v1/cities`
- `GET /api/v1/zones?cityId=`

## Traffic

- `GET /api/v1/traffic/sensors?zoneId=&status=` — sensors + latest reading
- `GET /api/v1/traffic/readings?sensorId=&from=&to=&page=&size=` — paged
- `POST /api/v1/traffic/readings` — `{sensorId, vehicleCount, avgSpeedKph, occupancyPct, recordedAt}`
- `GET /api/v1/traffic/congestion/firehose` — current congestion per zone
- `GET /api/v1/traffic/congestion/prediction?zoneId=&horizonMinutes=30` — 5-min
  prediction steps (`backend` = tf / sdk / heuristic)

## Roads & maintenance

- `GET /api/v1/roads?zoneId=` / `GET /api/v1/roads/{id}`
- `GET /api/v1/maintenance/requests?status=&page=&size=`
- `POST /api/v1/maintenance/requests` — `{roadId?, assetId?, title, description?, priority, reportedBy}`
- `PATCH /api/v1/maintenance/requests/{id}/status` — `{status}` (`scheduled|in_progress|resolved`)
- `GET /api/v1/maintenance/summary`

## Drainage

- `GET /api/v1/drainage/networks?zoneId=`
- `GET /api/v1/drainage/sensors?networkId=&status=`
- `GET /api/v1/drainage/readings?sensorId=&from=&to=`
- `POST /api/v1/drainage/readings` — `{sensorId, waterLevelCm, flowRateLps, rainfallMm, recordedAt}`
- `GET /api/v1/drainage/waterlogging/predictions?zoneId=` — risk bands + affected roads

## Energy

- `GET /api/v1/energy/grids?zoneId=`
- `GET /api/v1/energy/readings?gridId=&from=&to=`
- `POST /api/v1/energy/readings` — `{gridId, consumptionKw, voltage, outageReported, recordedAt}`
- `GET /api/v1/energy/summary` — per-zone consumption & outages
- `GET /api/v1/energy/assets?zoneId=&type=`

## Waste

- `GET /api/v1/waste/bins?zoneId=&minFill=`
- `POST /api/v1/waste/bins/{id}/collect` — empties a bin
- `POST /api/v1/waste/routes/optimize` — `{zoneId, vehicleId?}` → ordered stops, distanceKm, durationMin, geojson line
- `GET /api/v1/waste/routes?status=`

## Emergency

- `GET /api/v1/emergency/incidents?status=&kind=` / `POST /api/v1/emergency/incidents` — create (seeded incidents exist)
- `POST /api/v1/emergency/incidents/{id}/dispatch` — dispatches best vehicle by ETA → `Dispatch{etaSeconds,…}`
- `POST /api/v1/emergency/dispatch/{id}/resolve`
- `GET /api/v1/emergency/vehicles?status=`
- `GET /api/v1/emergency/route/safest?incidentId=` — safest corridor geojson

## GIS

- `GET /api/v1/gis/layers/{layer}?zoneId=` — FeatureCollections for
  `zones, roads, traffic-sensors, drainage-sensors, bins, assets, incidents, emergency-vehicles, energy-grids`
- `GET /api/v1/gis/heatmap` — congestion heatmap FeatureCollection

## Digital twin (live)

- `GET /api/v1/twin/state` — KPI snapshot (counts + freshness)
- `GET /api/v1/twin/live` — **SSE**, snapshot every 2 s (`text/event-stream`)
- `STOMP /ws` — topic `/topic/twin`; simulation completion streams to `/topic/simulation/{runId}`
- `GET /api/v1/twin/ping` — health/liveness used by seeders & CI smoke

## Ingest bridge (no Kafka needed)

- `POST /api/v1/ingest/traffic` / `drainage` / `energy` — same payloads as the
  corresponding Kafka topics; used by `scripts/seed.py`

## Simulation

- `POST /api/v1/simulation/run` — `{name?, scenario, params?}`; scenarios:
  `rainfall`, `substation_outage`, `water_main_burst`, `sensor_failure`
  (params: `zoneId`, `criticality` `low|medium|high`) — see
  [`SIMULATION.md`](SIMULATION.md)
- `GET /api/v1/simulation/runs?scenario=&limit=`
- `GET /api/v1/simulation/runs/{id}`

## Citizen reports

- `GET /api/v1/reports?status=&category=`
- `POST /api/v1/reports` — `{category: pothole|leak|lighting|garbage|flooding|other, description, lat, lon, reporterName, zoneId?}`
- `POST /api/v1/reports/{id}/upvote`
- `PATCH /api/v1/reports/{id}/status` — `{status}: submitted|acknowledged|in_progress|resolved`

## ML service (`/ml/v1`)

- `GET /ml/v1/health` → `{status, backend, version, models}`
- `POST /ml/v1/predict/congestion`

```json
{"zoneId":"…","history":[[8,1,0.62],[8,1,0.71]],"horizonMinutes":30}
// history rows: [hourOfDay 0-23, dayOfWeek 1-7 (Mon=1, Java), congestion]
{"zoneId":"…","backend":"sklearn","predictions":[
  {"horizonMinute":5,"congestion":0.63,"confidence":0.78},
  {"horizonMinute":10,"congestion":0.65,"confidence":0.78}]}
```

- `POST /ml/v1/predict/waterlogging`

```json
{"zoneId":"…","stationReadings":[[55,240,30],[38,180,18]],
 "networkCapacityM3h":1200,"horizonMinutes":60}
// rows: [waterLevelCm, flowRateLps, rainfallMm] — newest first
{"zoneId":"…","backend":"heuristic","risk":"high","maxLevelCm":61.4,
 "confidence":0.72,"details":{"currentLevelCm":55.0,"capacityM3h":1200.0,"horizonMinutes":60}}
```

Risk bands: `low < 25 cm ≤ moderate < 50 cm ≤ high < 90 cm ≤ severe`.

## Errors

Non-2xx responses are uniform (`ProblemDetail`-ish):

```json
{"status":404,"error":"sensor","message":"sensor 00000000… not found"}
```

`409` reserved for state-machine violations (e.g. resolving an un-dispatched
incident).
