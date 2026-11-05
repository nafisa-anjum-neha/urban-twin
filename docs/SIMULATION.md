# UrbanTwin — Cascade Simulation

The flagship of the platform: a **cross-system failure cascade engine** that
answers *"if X happens, what breaks next — and when, and how badly?"*

Run it from the **Simulation** page of the SPA (animated, node-by-node along
the timeline), or with curl:

```bash
curl -X POST http://localhost:8080/api/v1/simulation/run \
  -H 'Content-Type: application/json' \
  -d '{"name":"bolt-storm-demo","scenario":"rainfall","params":{"criticality":"high"}}'
```

## Model

Each run builds a **rooted directed acyclic cascade graph**:

- **Nodes** — discrete infrastructure state changes with `type`
  (`TRIGGER | DRAINAGE | ROAD | TRAFFIC | ENERGY | REPORT | MITIGATION`),
  a label like *"Waterlogging score 0.72 — Zone Central"*, zone id, payload
  map, `startOffsetSec` and a severity in `[0,1]`.
- **Edges** — `(relation, delaySec, probability)`. `delaySec` is when the
  downstream effect *starts* after its parent; `probability` is Bernoulli-sampled
  with the run's seeded RNG, so identical inputs produce identical runs.
- **timelineSeconds** — the last node's `startOffsetSec` (drives the SPA
  animation clock).
- **impactSummary** — user-facing totals: e.g. averaged congestion, count of
  waterlogged zones, outage flag, citizen-report wave size, mitigation ETA.

The graph is layered in the SPA by topological depth (simple layered SVG
layout) and colors nodes by severity (emerald → amber → red).

## Scenario catalogue

Four scenario families are built in (`SimulationController` whitelist):

### 1. `rainfall`

Params: `zoneId` *(optional uuid — default: first two zones)*,
`criticality` (`low|medium|high` → 0.6 / 0.8 / 0.9 severity ceiling).

Storm over the zone: rain intensity → waterlogging score climbs past
threshold (drainage pump capacity exceeded) → road segments flood with
condition/traffic impacts → traffic reroutes (congestion spike on arterials)
→ citizen report wave → field crews dispatched as mitigation nodes.

### 2. `substation_outage`

Params: `zoneId`, `criticality`.

Feeder loss decimates the electricity grid node; the drainage pumps wired to
that zone black out after ~2 min (`blacks_out`, p=0.95) — their waterlogging
risk climbs; streetlight clusters trigger a citizen report wave (~15 min);
the mitigation branch dispatches field crews after ~5 min (p=0.85).

### 3. `water_main_burst`

A burst at a distribution valve (~60 l/s) overloads the drainage network
within ~5 min → a specific road corridor is waterlogged within ~10 min →
traffic detours around it (~13 min, p=0.75) with congestion payload — then
the valve crew closes in (~3 min, p=0.9). This is the canonical
*water → infrastructure → traffic* chain demo.

### 4. `sensor_failure`

A zone's sensors (traffic + drainage) go silent; the twin degrades to
interpolated values (staleness nodes show the freshness gap), and
`MlClient`-style fallback nodes demonstrate that heuristics take over —
mirroring the real degradation chain built into the stack.

## API

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/simulation/run` | `{name?, scenario, params?}` → full `RunResult` |
| `GET` | `/api/v1/simulation/runs?scenario=&limit=` | history summaries (no cascade body) |
| `GET` | `/api/v1/simulation/runs/{id}` | replay a stored run (full cascade) |

Shape of a run (contract §7.10 — abbreviated):

```json
{
  "id": "3f1a…",
  "scenario": "water_main_burst",
  "status": "completed",
  "timelineSeconds": 900,
  "cascade": {
    "nodes": [
      {"id": "seed:burst", "type": "TRIGGER", "label": "Water main burst (60 l/s)",
       "zoneId": "b1…", "payload": {}, "startOffsetSec": 0, "severity": 0.9},
      {"id": "drainage:…", "type": "DRAINAGE", "startOffsetSec": 300, "severity": 0.72, "…": "…"}
    ],
    "edges": [
      {"from": "seed:burst", "to": "drainage:…", "relation": "overloads", "delaySec": 300, "probability": 0.85}
    ]
  },
  "impactSummary": {"waterloggedZones": 1, "peakCongestion": 0.68, "citizenReports": 12, "…": "…"}
}
```

Completion is published to Kafka `simulation.events` and STOMP
`/topic/simulation/{id}`; runs persist (`simulation_runs`) and are replayable
indefinitely.
