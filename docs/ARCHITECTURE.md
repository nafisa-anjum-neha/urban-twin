# UrbanTwin — Architecture

This document explains *how* the system works end-to-end. For binding shapes
and paths see [`CONTRACT.md`](CONTRACT.md); for endpoint examples see
[`API.md`](API.md).

## 1. Component map

```
┌────────────┐   Kafka topics     ┌──────────────────┐   JDBC   ┌────────────┐
│  seeders / │  traffic.readings  │  Spring Boot API │─────────▶│  PostGIS   │
│  sensors   │  drainage.readings │  (Java 17)       │◀─────────│  (pg 16)   │
└────────────┘  energy.readings   │                  │          └────────────┘
       │                          │                  │          ┌────────────┐
       │  REST ingest bridge      │  • REST /api/v1  │          │   Redis    │
       └─────────────────────────▶│  • STOMP /ws     │◀────────▶│ twin state │
                                  │  • SSE /twin/live│          └────────────┘
┌──────────────┐   REST /ml/v1    │                  │          ┌────────────┐
│  FastAPI ML  │◀────────────────▶│                  │◀────────▶│ Kafka KRaft│
└──────────────┘                  └──────────────────┘          └────────────┘
       ▲                                  │ HTTP
       │  training (train.py)             ▼
   models/                        ┌──────────────────┐
   TF ▸ sklearn ▸ heuristic       │  React SPA :3000 │  Leaflet · Recharts
                                  │  nginx proxies   │
                                  └──────────────────┘
```

## 2. Data ingestion path

1. **Devices (or `scripts/seed.py`)** publish instantaneous telemetry:
   - Kafka: `traffic.readings`, `drainage.readings`, `energy.readings`
     (`String` key = sensor id, JSON value, ErrorHandlingDeserializer on the
     consumer side),
   - or REST: `POST /api/v1/ingest/{traffic|drainage|energy}` — identical
     payloads, so the seeder works without Kafka running.
2. **Ingest services** perform the same pipeline per reading regardless of
   entry point: parse → resolve sensor → **persist the reading** → recompute
   module state (e.g. congestion rolling index, waterlogging score) →
   **publish to Redis** → **fan out to the live layer**.
3. **Live layer** (contract §7.11):
   - `GET /api/v1/twin/live` — Server-Sent Events, a snapshot every 2 s,
   - `STOMP /ws` topic `/topic/twin` — richer event fan-out for the SPA.
   The React Topbar consumes SSE with automatic reconnect, and the Overview
   page subscribes over STOMP; both degrade to React-Query polling when the
   stream is unavailable.

## 3. The ML decision path

For every forecast request the backend:

1. snapshots the last ≤ 400 readings from PostGIS (14-day retention),
2. builds the ML request (contract §8 shapes),
3. calls `MlClient` with a **1.5 s timeout** (non-blocking WebClient),
4. on timeout / connection failure / malformed reply → **Java heuristics**
   (congestion diurnal-blend, waterlogging mass-balance) produce the answer,
5. the response is cached (Redis, 30 s TTL) and streamed.

Inside the ML service the three tiers are detected once at import time and
baked into `BUNDLE`; `/ml/v1/health` reports `backend: tensorflow|sklearn|heuristic`:

| Tier | Artifacts | Notes |
|---|---|---|
| tensorflow | `models/*.keras` | best in-sample MAE, heaviest image |
| sklearn | `models/*.joblib` | GradientBoosting, models ship pre-trained |
| heuristic | none | always works, zero deps |

Both learned tiers enforce the same invariants as the physics (monotonic
level ≥ current level when raining; congestion ∈ [0,1]).

## 4. GIS layer

`GisService` renders database rows to GeoJSON on the fly — `geography(LineString)`
becomes a `LineString` feature, `geography(Point)` a point feature. The
distance/route computations use PostGIS `ST_Distance` (meters on spheroid) and
a Dijkstra pass over road primitives for waste and emergency routing. The
heatmap endpoint buckets zone congestion into a lightweight FeatureCollection
the SPA colors client-side. No vendor tiles beyond the free OSM raster tiles.

## 5. Cascade simulation (flagship)

`CascadeEngine` (see [`SIMULATION.md`](SIMULATION.md)) models a scenario as a
rooted DAG:

- **nodes** — infrastructure state changes (`TRIGGER`, `DRAINAGE`, `ROAD`,
  `TRAFFIC`, `ENERGY`, `REPORT`, `MITIGATION`) with zone, severity and
  payload,
- **edges** — `(relation, delaySec, probability)`; probability draws Bernoulli
  trials with the run's RNG seed so runs are best-effort deterministic,
- timeline — every node carries `startOffsetSec`; the SPA animates nodes
  appearing along the timeline and lays out layers with a simple layered
  (topological) SVG layout.

Runs are persisted (`SimulationRun.result_json`) and replayable via
`GET /api/v1/simulation/runs/{id}`; completion is broadcast on Kafka
`simulation.events` and STOMP `/topic/simulation/{id}`.

## 6. Failure tolerances (the "digital twin never dies" contract)

| Failure | Behaviour |
|---|---|
| ML service down / slow | Java heuristics answer after 1.5 s; responses self-describe via `backend` |
| Redis absent | twin state falls back to in-memory + SSE only |
| Kafka absent | REST ingest bridge keeps the pipeline alive |
| DB seeding disabled | API boots empty but healthy; seeder can push data any time |

## 7. Cross-cutting

- **Seeding**: when `app.seed.enabled=true` (dev default) the API seeds a
  deterministic Bengaluru dataset on first boot (RNG seed 42), so any fresh
  start demos identically.
- **Observability**: SpringDoc OpenAPI at `/swagger-ui.html`,
  `management.endpoint.health` probes used by Docker/K8s healthchecks.
- **Testing**: pure-unit tests for the heuristic + optimizer cores (no DB
  needed), full API pytest suite for the ML service, typed build for the SPA.
