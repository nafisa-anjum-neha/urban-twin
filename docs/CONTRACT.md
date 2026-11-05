# Urban Twin — Cross-Service Contract (v1.0)

> **Read this fully before writing any code.** All services implement this contract exactly.
> Any change must be documented here. Ports and names below are authoritative.

---

## 1. Project identity

- Product name: **UrbanTwin — Smart Urban Infrastructure & Digital Twin Platform**
- Repo root: `urban-twin/`
- Tagline: "A digital twin platform that unifies traffic, drainage, energy, waste and emergency
  systems for a city and simulates cross-infrastructure failure cascades."

## 2. Repo layout

```
urban-twin/
├── README.md
├── LICENSE                      (MIT)
├── .gitignore
├── docker-compose.yml
├── docs/
│   ├── CONTRACT.md              (this file)
│   ├── ARCHITECTURE.md
│   ├── DATA_MODEL.md            (ERD in mermaid)
│   ├── API.md                   (generated examples)
│   └── SIMULATION.md            (cascade engine design)
├── backend/                     (Spring Boot 3, Java 17, Maven)
├── ml-service/                  (Python 3.11, FastAPI, TensorFlow)
├── frontend/                    (React 18, Vite, Leaflet)
├── deploy/
│   ├── k8s/                     (flat YAML manifests incl. README)
│   └── seed/                    (data generation notes)
├── scripts/
│   └── seed.py                  (synthetic city data generator → REST API)
└── .github/workflows/ci.yml
```

## 3. Ports & runtimes

| Service     | Dev port | Container port | Notes                          |
|-------------|----------|----------------|--------------------------------|
| api         | 8080     | 8080           | Spring Boot (`/api/v1`, `/ws`) |
| web         | 5173     | 3000           | Vite dev / nginx prod          |
| ml-service  | 8000     | 8000           | FastAPI (`/ml/v1`)             |
| postgres    | 5432     | 5432           | PostGIS 3.4 image              |
| redis       | 6379     | 6379           |                                |
| kafka       | 9092     | 9092           | Confluent KRaft (no zookeeper) |

- Postgres creds: `postgres / urbantwin`, db `urbantwin`
- Kafka auto-create topics enabled.
- Redis is optional for local dev: API must degrade gracefully (cache-miss = direct query).

## 4. Backend module layout (Spring Boot)

- Artifact: `backend`, base package **`com.urbantwin`**, Spring Boot **3.3.x**, Java **17**, Maven.
- Dependencies: `spring-boot-starter-web`, `data-jpa`, `validation`, `data-redis`, `kafka`,
  `springdoc-openapi-starter-webmvc-ui` (2.x), `hibernate-spatial` + `org.hibernate.orm:hibernate-core`,
  `org.postgresql:postgresql`, `org.locationtech.jts:jts-core`, `lombok`, test starter.
- Single Maven module, feature packages under `com.urbantwin.<module>`:

  `traffic`, `roads`, `drainage`, `energy`, `waste`, `emergency`, `gis`,
  `twin` (digital-twin dashboard/state), `simulation`, `ingest` (Kafka listeners & REST
  ingest), `common` (config, exceptions, DTO mapping via static factories, seed
  `DataSeeder` implementing `CommandLineRunner`, enabled by `app.seed.enabled=true`).

- Every entity: UUID id (`@UuidGenerator`), `createdAt`, `updatedAt`.
- Spatial columns stored as `geography(Point,4326)` / `geography(LineString,4326)` via
  Hibernate Spatial; DTOs expose simple `[lon, lat]` arrays or GeoJSON strings.
- Controllers under `/api/v1/**`, JSON only. OpenAPI UI at `/swagger-ui.html`.
- Global error format (ALL validation & runtime errors):

```json
{ "timestamp": "...", "status": 400, "error": "Bad Request", "message": "...", "path": "/api/v1/..." }
```

## 5. Kafka topics

| Topic                | Producer            | Consumer       | Payload DTO (JSON)                                                                                  |
|----------------------|---------------------|----------------|------------------------------------------------------------------------------------------------------|
| `traffic.readings`   | ingest + simulator  | traffic, twin  | `{sensorId, vehicleCount, avgSpeedKph, occupancyPct, recordedAt}`                                    |
| `drainage.readings`  | ingest + simulator  | drainage, twin | `{sensorId, waterLevelCm, flowRateLps, rainfallMm, recordedAt}`                                      |
| `energy.readings`    | ingest + simulator  | energy, twin   | `{gridId, zoneId, consumptionKw, voltage, outageReported, recordedAt}`                               |
| `twin.updates`       | simulation + others | twin SSE/WS    | `{type, zoneId, payload:{...}, occurredAt}` — `type` ∈ `TRAFFIC`, `DRAINAGE`, `ENERGY`, `INCIDENT`, `SIMULATION` |
| `simulation.events`  | simulation          | twin           | `{simulationId, stepIndex, nodeType, description, payload, occurredAt}`                              |

- Listeners log + persist to the right tables; publish `twin.updates` for each.
- WebSocket: STOMP endpoint `/ws`, broker prefix `/topic` (e.g. `/topic/twin/updates`,
  `/topic/simulation/{id}`).

## 6. Data model (tables, key columns)

All tables snake_case. Columns beyond these are allowed but never rename these.

1. `city` — name, slug (unique), population
2. `zone` — city FK, name, zone_type(residential|commercial|industrial|mixed), polygon `geography(Polygon,4326)`, geojson (text)
3. `road` — zone FK, name, road_class(hw|ar|co|lo), condition_score(0..100), lanes, speed_limit_kph, path `geography(LineString,4326)`, geojson(text)
4. `intersection` — zone FK, name, location point, traffic_light(boolean), signal_cycle_seconds
5. `traffic_sensor` — road FK, name, location point, status(active|inactive|faulty)
6. `traffic_reading` — sensor FK, vehicle_count, avg_speed_kph, occupancy_pct, congestion_level(0..1), recorded_at
7. `drainage_network` — zone FK, name, capacity_m3_per_hour, network_class(primary|secondary)
8. `drainage_sensor` — network FK, name, location point, status(normal|warning|critical|faulty)
9. `water_level_reading` — sensor FK, water_level_cm, flow_rate_lps, rainfall_mm, risk_level(low|moderate|high|severe), recorded_at
10. `electricity_grid` — zone FK, name, capacity_kw, loss_pct
11. `grid_reading` — grid FK, consumption_kw, voltage, outage_reported, recorded_at
12. `infrastructure_asset` — zone FK, asset_type(streetlight|water_main|pump|transformer|signal), name, location point, condition_score, installed_year, status(operational|degraded|failed)
13. `maintenance_request` — asset FK nullable, road FK nullable, title, description, priority(p1..p4), status(open|assigned|in_progress|resolved|closed), reported_by, scheduled_date, resolved_at
14. `waste_bin` — zone FK, name, location point, fill_level_pct, last_collected_at
15. `waste_collection_route` — name, zone FK, vehicle_id, stops(JSONB int order), total_distance_km, estimated_duration_min, status(planned|active|completed)
16. `emergency_incident` — zone FK, name, incident_type(medical|fire|crime|hazard|flood), severity(1..5), location point, description, status(active|dispatched|resolved), reported_at, resolved_at
17. `emergency_vehicle` — name, vehicle_type(ambulance|fire_engine|police|rescue), status(available|dispatched|offline), station point, heading_deg
18. `dispatch` — incident FK, vehicle FK, route_geojson(text, nullable), distance_km, eta_min, status(dispatched|en_route|arrived|cancelled), dispatched_at
19. `citizen_report` — zone FK, category(pothole|leak|lighting|garbage|flooding|other), description, location point, status(submitted|acknowledged|in_progress|resolved), upvotes, reporter_name
20. `simulation_run` — name, scenario(rainfall|substation_outage|water_main_burst|sensor_failure), input_params(JSONB), result(JSONB cascade graph), status(running|completed|failed), started_at, completed_at

## 7. REST API contract (`/api/v1`)

Response shapes: **list endpoints** return `{ "content": [...], "total": n, "page": p, "size": s }`
for pageables; single-resource endpoints return the object; map endpoints return GeoJSON
`FeatureCollection`.

### 7.1 City / Zone
- `GET /api/v1/cities` → `[{id,name,slug,population,zoneCount}]`
- `GET /api/v1/zones?cityId=...` → list of `{id,name,zoneType,cityId,geojson}`

### 7.2 Traffic
- `GET /api/v1/traffic/sensors?zoneId=&status=` → sensors with `latestReading`
- `GET /api/v1/traffic/readings?sensorId=&from=&to=` (ISO datetimes)
- `POST /api/v1/traffic/readings` (body: topic payload DTO)
- `GET /api/v1/traffic/congestion/firehose` → per-zone live congestion:
  `[{zoneId, name, congestionLevel(0..1), avgSpeedKph, trend(up|down|stable), updatedAt}]`
- `GET /api/v1/traffic/congestion/prediction?zoneId=&horizonMinutes=30` →
  `[{zoneId, horizonMinute, predictedCongestion, confidence}]` (calls ML service; falls back to heuristic)
- `GET /api/v1/traffic/hotspots` → top 10 congested intersections GeoJSON FC

### 7.3 Roads & maintenance
- `GET /api/v1/roads?zoneId=&maxCondition=` → `{id,name,roadClass,conditionScore,geojson,openMaintenanceRequests}`
- `GET /api/v1/roads/{id}`
- `GET /api/v1/maintenance/requests?status=&priority=&roadId=`
- `POST /api/v1/maintenance/requests` `{title,description,priority,assetId?,roadId?,reportedBy}`
- `PATCH /api/v1/maintenance/requests/{id}/status` `{status}` (validates transitions)
- `GET /api/v1/maintenance/summary` → `[{roadClass|priority,status,count}]` grouped

### 7.4 Drainage
- `GET /api/v1/drainage/networks?zoneId=`
- `GET /api/v1/drainage/sensors?networkId=&status=`
- `GET /api/v1/drainage/readings?sensorId=&from=&to=`
- `POST /api/v1/drainage/readings`
- `GET /api/v1/drainage/waterlogging/predictions` →
  `[{zoneId, risk(low|moderate|high|severe), maxLevelCm, affectedRoads:[roadIds], confidence}]`

### 7.5 Energy
- `GET /api/v1/energy/grids?zoneId=`
- `GET /api/v1/energy/readings?gridId=&from=&to=`
- `POST /api/v1/energy/readings`
- `GET /api/v1/energy/summary` → per zone `{zoneId,name,currentKw,peakKw24h,outages24h,trend}`
- `GET /api/v1/energy/assets?type=&status=` (infrastructure assets shared)

### 7.6 Waste
- `GET /api/v1/waste/bins?zoneId=&minFill=70`
- `POST /api/v1/waste/bins/{id}/collect` (reset fill level 0, set lastCollectedAt)
- `POST /api/v1/waste/routes/optimize` `{zoneId, vehicleId}` → optimized route object
  `{id,name,zoneId,vehicleId,stops:[{binId,name,lat,lon,fillLevel,order}],totalDistanceKm,estimatedDurationMin,status}` — nearest-neighbor 2-opt over bins filled ≥ 60 %
- `GET /api/v1/waste/routes?status=`

### 7.7 Emergency
- `GET /api/v1/emergency/incidents?status=&type=&minSeverity=`
- `POST /api/v1/emergency/incidents` `{name,type,severity,lat,lon,description,zoneId}`
- `POST /api/v1/emergency/incidents/{id}/dispatch` `{vehicleId?}` → picks nearest `available`
  vehicle (or ignores vehicleId check when null), creates Dispatch with straight-line route
  GeoJSON, distance_km, eta_min (by severity speed), sets vehicle DISPATCHED
- `POST /api/v1/emergency/dispatch/{id}/resolve`
- `GET /api/v1/emergency/vehicles?status=`
- `GET /api/v1/emergency/route/safest?incidentId=` → route avoiding waterlogged roads from
  drainage predictions: GeoJSON LineString candidate fallback list

### 7.8 GIS
- `GET /api/v1/gis/layers/{layer}` → GeoJSON `FeatureCollection`; `layer` ∈
  `zones|roads|traffic-sensors|drainage-sensors|bins|assets|incidents|emergency-vehicles|energy-grids`
  (points when applicable, polygons for zones, linestrings for roads). Properties include id/name/status.
- `GET /api/v1/gis/heatmap?layer=traffic|drainage|energy` → `[{lat,lon,weight}]`

### 7.9 Citizen reports
- `GET /api/v1/reports?status=&category=`
- `POST /api/v1/reports` `{category,description,lat,lon,reporterName,zoneId?}` (zone auto-detected from point if null)
- `POST /api/v1/reports/{id}/upvote`
- `PATCH /api/v1/reports/{id}/status` `{status}`

### 7.10 Simulation engine (the flagship)
- `POST /api/v1/simulation/run` body:

```json
{ "scenario": "rainfall" | "substation_outage" | "water_main_burst" | "sensor_failure",
  "name": "string",
  "params": { "intensityMm": 90, "durationMinutes": 120, "zoneId": "uuid?", "gridId?": "uuid", "criticality": "high" } }
```

  Synchronous response (also persisted in `simulation_run` and streamed to `/topic/simulation/{id}`):

```json
{ "simulationId": "uuid", "scenario": "rainfall", "status": "completed",
  "timelineSeconds": 0,
  "cascade": {
    "nodes": [ { "id":"seed:rainfall","type":"TRIGGER","label":"Rainfall 90 mm/h",
                 "zoneId":null,"payload":{"intensityMm":90},
                 "startOffsetSec":0, "severity":0.9 } ],
    "edges": [ { "from":"seed:rainfall","to":"drainage:zone-x","relation":"overloads","delaySec":600,
                 "probability":0.86 } ]
  },
  "impactSummary": { "zonesAffected":4, "roadsImpacted":23, "roadsWaterlogged":7,
                     "incidentsRerouted":3, "citizenReportsEstimate":41, "totalNodes":14 }
}
```

- Node `type` ∈ `TRIGGER|DRAINAGE|ROAD|TRAFFIC|ENERGY|INCIDENT|REPORT|MITIGATION`
- `GET /api/v1/simulation/runs?scenario=&limit=20`
- `GET /api/v1/simulation/runs/{id}` → full result
- Rules file `simulation/CascadeRulesEngine` encodes the 4 scenarios as weighted rules;
  each rule = (queried system state, condition, generated nodes/edges with delays & probabilities).
  Rainfall → drainage sensors rising → probability of exceeding network capacity → waterlogged
  road segments (roads intersecting flood-prone zones) → rerouted traffic loads on alternate
  roads → emergency vehicle detours → estimated citizen flood reports. Outage → traffic lights
  off at intersections → congestion spike → traffic readings; grids offline. Burst → same but
  localized. Sensor_failure → stale readings → twin heartbeat alarms.

### 7.11 Digital twin core
- `GET /api/v1/twin/state` → the dashboard snapshot:

```json
{ "city": {"id","name"}, "zones": [{"zoneId","name","congestion","floodRisk","powerState(ok|watch|outage)",
     "binsOverfull","assetHealth"}], "totals": {"activeIncidents","openMaintenance","binsOverfull",
     "avgCongestion","networkStatus(ok|degraded|critical)","dataFreshness": "2s"},
  "updatedAt": "..." }
```

- `GET /api/v1/twin/live` (SSE, `text/event-stream`, 2 s tick) → same snapshot delta or
  `event: ping`. Implemented with Kafka `twin.updates` where available, rebuilt from DB otherwise.

### 7.12 Ingest
- `POST /api/v1/ingest/traffic` / `drainage` / `energy` — same payload as Kafka topics; useful
  for scripts/seed.py pushing live data without Kafka.

## 8. ML service (FastAPI, `/ml/v1`)

- Endpoints (all JSON):
  - `POST /ml/v1/predict/congestion` `{zoneId, history:[[hourOfDay,dayOfWeek,congestion]...], horizonMinutes:30}`
    → `{"predictions":[{"horizonMinute": h, "congestion": 0..1}], "modelVersion":"..."}`
  - `POST /ml/v1/predict/waterlogging` `{z readings history} {stationReadings:[{waterLevelCm,rainfallMm,minAgo}],networkCapacityM3h}`
    → `{"risk":"low|moderate|high|severe","maxLevelCm":x,"confidence":0..1}`
  - `GET /ml/v1/health` → `{"status":"ok","models":{"congestion":true,"waterlogging":true}}`
- Models: TensorFlow/Keras small dense nets OR `sklearn GradientBoostingRegressor` compiled
  equivalently; **service must expose identical API even without TF installed** — implement
  heuristic/statistical fallback + gradient boosting when sklearn available.
- Training: `train.py` generates synthetic labeled history and saves models to `ml-service/models/`.
- Backend integration: `MlClient` (RestTemplate/WebClient) with 1500 ms timeout; on failure falls
  back to the same heuristics in Java, and response includes `"model":"heuristic"`.

## 9. Frontend (React 18 + Vite)

- Stack: Vite, TypeScript, `react-router-dom`, `@tanstack/react-query`, `react-leaflet`,
  `leaflet`, `recharts`, Tailwind (dark theme, slate/zinc palette, emerald/amber/red accents),
  `axios`, `@stomp/stompjs` for `/ws` + fallback polling `/twin/live`.
- Base URL from `VITE_API_URL` (default `http://localhost:8080/api/v1`), `VITE_WS_URL`, `VITE_ML_URL` unused directly.
- Pages (route → name):
  1. `/` → **Digital Twin Overview**: KPI cards (incidents, maintenance, overfull bins, avg congestion), city map with zone polygons colored by congestion + risk dots, live status ticker (SSE).
  2. `/traffic` → congestion list per zone, hotspots on map, congestion-over-time chart (recharts), prediction toggle (current vs +30 min).
  3. `/roads` → roads colored by condition_score w/ legend, maintenance request table + create dialog + status PATCH.
  4. `/drainage` → sensor map colored by risk, water level gauges, waterlogging predictions panel with affected roads.
  5. `/energy` → per-zone consumption bars (recharts), grids table with outages, 24 h consumption chart.
  6. `/waste` → bins map colored by fill level, optimize-route button (calls `/waste/routes/optimize`, draws route line, shows distance/duration).
  7. `/emergency` → active incidents + available vehicles on map, dispatch button (shows ETA), dispatches list.
  8. `/simulation` → **cascade simulator**: form to run the 4 scenarios with params; when it completes, render an animated cascade timeline (stacked nodes appearing along delays) + force-ish layout of nodes/edges (simple layered layout, SVG) + impact summary + link to map highlights.
  9. `/citizen` → citizen report form + table with upvote buttons.
- Shared components: `Sidebar` (nav), `Topbar` (live status, data freshness), `MapCard`
  (Leaflet + OSM tiles `https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png`), `KpiCard`,
  `StatusBadge`, `CascadeGraph` (SVG), `RiskGauge`.
- Map must remain lean: single shared `useMapData(layers)` hook, no plugin beyond leaflet core.

## 10. Deployment

- `docker-compose.yml` services: postgres(postgis/postgis:16-3.4), redis, kafka
  (confluentinc/cp-kafka:7.6.x, KRaft, auto-create topics), api (build ./backend),
  web (build ./frontend), ml (build ./ml-service). Healthchecks for db; api depends_on healthy db.
- Backend Dockerfile: maven:3.9-eclipse-temurin-17 multi-stage → temurin-17-jre-alpine run image.
- Frontend Dockerfile: node:20-alpine build stage, nginx:alpine serve, nginx.conf proxies `/api` and `/ml` to api/ml services.
- `deploy/k8s/`: ns, postgres statefulset, redis + kafka deployments, api/web/ml deployments with probes, services, configmaps for env, ingress. README with `kubectl apply -f deploy/k8s`.
- CI (`.github/workflows/ci.yml`): jobs `backend` (mvn -B verify), `frontend` (npm ci && npm run build), `ml` (pip install -r requirements.txt && pytest -q), each on paths filters.

## 11. Seed data

- `scripts/seed.py` (Python, stdlib + requests): optionally Kafka or REST ingest; creates city,
  8 zones, ~40 roads, ~30 intersections, ~60 traffic sensors + 2 days of readings every 15 min
  (~10k), ~20 drainage sensors + readings, 4 grids + readings, ~50 assets, maintenance requests,
  ~120 waste bins, 3 incidents, 6 vehicles, reports, then continuously POSTs new readings
  every cycle when `--live`.
- Backend `DataSeeder` also seeds a minimal city when `app.seed.enabled=true` so the app is
  never empty on first run.
