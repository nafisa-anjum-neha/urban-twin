# UrbanTwin — Data Model

PostgreSQL 16 with **PostGIS 3.4**; Hibernate manages the schema
(`ddl-auto: update`). Geography columns are `geography(*, 4326)` so PostGIS
functions (`ST_Distance`, `ST_DWithin`, `ST_AsGeoJSON`) operate in meters on
the spheroid.

## Entity overview

```
City ──< Zone ──< Road ──< TrafficSensor ──< TrafficReading
 │              │    └──< MaintenanceRequest ──┐
 │              ├──< DrainageNetwork ──< DrainageSensor ──< WaterLevelReading
 │              ├──< ElectricityGrid ──<  GridReading
 │              ├──< InfrastructureAsset ──(MaintenanceRequest)
 │              ├──< WasteBin
 │              └──< EmergencyIncident ──< Dispatch >── EmergencyVehicle
 └── CitizenReport (zone-scoped)        SimulationRun (standalone audit)
```

## City geography

| Entity | Key fields | Notes |
|---|---|---|
| `City` | name, slug, population | one row per demonstration city (Dhaka, Bangladesh) |
| `Zone` | name, zone_type, `boundary geography(Polygon,4326)`, geojson cache | polygons drive map coloring; `geojson` freezes the boundary for fast GIS rendering |

## Roads & maintenance

| Entity | Key fields | Notes |
|---|---|---|
| `Road` | zone, name, roadClass (`hw/ar/co/lo`), **conditionScore** 0–100, lanes, speedLimitKph, `path geography(LineString,4326)` | line geometry feeds routing + GIS layers |
| `MaintenanceRequest` | road or InfrastructureAsset, title, priority, status, reportedBy, scheduledDate, resolvedAt | status machine: `reported → scheduled → in_progress → resolved` |
| `InfrastructureAsset` | zone, type, name, `location geography(Point,4326)`, status | pumps, transformers, streetlights… |

## Sensors & telemetry (time-series)

All readings tables keep only `recorded_at` + measurements and are append-only.
The seeder backfills **48 h at 15-minute cadence** so charts are meaningful on
first boot; the API retains a 14-day rolling window through queries.

| Entity | Parent | Measurements |
|---|---|---|
| `TrafficSensor` | Road | `location geography(Point,4326)`, status (active/inactive/faulty) |
| `TrafficReading` | TrafficSensor | vehicleCount, avgSpeedKph, occupancyPct, derived congestionLevel |
| `DrainageNetwork` | Zone | capacityM3PerHour, networkClass |
| `DrainageSensor` | DrainageNetwork | `location`, status |
| `WaterLevelReading` | DrainageSensor | waterLevelCm, flowRateLps, rainfallMm (tipping-bucket per-interval accumulation) |
| `ElectricityGrid` | Zone | capacityKw, lossPct, name |
| `GridReading` | ElectricityGrid | consumptionKw, voltage, outageReported |

## Waste

| Entity | Key fields |
|---|---|
| `WasteBin` | zone, `location`, capacityL, fillLevelPct, kind, lastCollectedAt |
| `WasteCollectionRoute` | zone, JSON-serialized stop list, distanceKm, durationMin, status |

Optimization is computed in `WasteRouteOptimizer` (nearest-neighbour over fill
level priority + `ST_Distance` matrix), persisted as a route, drawn by the SPA.

## Emergency

| Entity | Key fields |
|---|---|
| `EmergencyIncident` | zone, kind (fire/medical/police), severity, `location`, status, reportedAt |
| `EmergencyVehicle` | vehicleType, status (available/dispatched), `station geography(Point,4326)`, headingDeg |
| `Dispatch` | incident ↔ vehicle, etaSeconds, routeKm, status, dispatchedAt |

`EmergencyService` ranks candidate vehicles by PostGIS distance and road
condition (safest route = avoid low conditionScore segments), then persists a
`Dispatch` with the computed ETA.

## Citizen reports

`CitizenReport` — kind, description, `location`, zone, `upvotes`,
status (`open → acknowledged → resolved`). Powers `/citizen` and doubles as
soft sensor data ("citizens as sensors") shown in cascade scenarios.

## Simulation audit

| Entity | Key fields |
|---|---|
| `SimulationRun` | name, scenario, inputParamsJson, **resultJson** (full cascade DAG), status, startedAt, completedAt |

`resultJson` stores the complete engine output so historical runs replay
byte-identically through `GET /api/v1/simulation/runs/{id}`.

## Kafka topics

| Topic | Key | Value |
|---|---|---|
| `traffic.readings` | sensor uuid | `TrafficPayload{sensorId, vehicleCount, avgSpeedKph, occupancyPct, recordedAt}` |
| `drainage.readings` | sensor uuid | `WaterPayload{sensorId, waterLevelCm, flowRateLps, rainfallMm, recordedAt}` |
| `energy.readings` | grid uuid | `EnergyPayload{gridId, consumptionKw, voltage, outageReported, recordedAt}` |
| `ingest.audit` | entity uuid | raw ingest confirmation |
| `simulation.events` | run uuid | completed cascade `RunResult` |

Topics are declared automatically by `KafkaTopicConfig` on boot
(`auto.create.topics.enable` is also on in the compose image).
