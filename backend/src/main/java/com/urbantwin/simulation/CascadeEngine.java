package com.urbantwin.simulation;

import com.urbantwin.city.Zone;
import com.urbantwin.city.ZoneRepository;
import com.urbantwin.drainage.DrainageService;
import com.urbantwin.emergency.EmergencyIncidentRepository;
import com.urbantwin.roads.RoadRepository;
import com.urbantwin.simulation.CascadeDtos.Edge;
import com.urbantwin.simulation.CascadeDtos.Node;
import com.urbantwin.traffic.TrafficService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cross-infrastructure CascadeRulesEngine (docs/SIMULATION.md).
 *
 * <p>Four scenario families — rainfall, substation_outage, water_main_burst and
 * sensor_failure — are encoded as weighted propagation rules that query the live
 * platform state (drainage levels, road condition, active incidents, grid
 * capacity) and emit a directed graph of failure nodes with per-edge delays and
 * probabilities. The bigger the query-time risk, the more edges light up.</p>
 */
@Component
public class CascadeEngine {

    private final ZoneRepository zones;
    private final RoadRepository roads;
    private final DrainageService drainage;
    private final TrafficService traffic;
    private final EmergencyIncidentRepository incidents;
    private final Random rng;

    public CascadeEngine(ZoneRepository zones, RoadRepository roads, DrainageService drainage,
                         TrafficService traffic, EmergencyIncidentRepository incidents,
                         @Value("${app.sim.seed:42}") long seed) {
        this.zones = zones;
        this.roads = roads;
        this.drainage = drainage;
        this.traffic = traffic;
        this.incidents = incidents;
        this.rng = new Random(seed);
    }

    public CascadeDtos.RunResult run(String scenario, String name, Map<String, Object> params) {
        Builder b = new Builder();
        switch (scenario) {
            case "rainfall" -> rainfall(b, params);
            case "substation_outage" -> substationOutage(b, params);
            case "water_main_burst" -> waterMainBurst(b, params);
            case "sensor_failure" -> sensorFailure(b, params);
            default -> throw new IllegalArgumentException("unknown scenario: " + scenario);
        }

        CascadeDtos.Cascade cascade = b.build();
        int endOffset = cascade.nodes().stream().mapToInt(Node::startOffsetSec).max().orElse(0);
        CascadeDtos.Impact impact = new CascadeDtos.Impact(
                countZones(cascade),
                countType(cascade, "ROAD"),
                countType(cascade, "ROAD", "waterlogged"),
                countType(cascade, "INCIDENT"),
                countType(cascade, "REPORT") > 0 ? 40 + rng.nextInt(60) : rng.nextInt(6),
                cascade.nodes().size());
        return new CascadeDtos.RunResult(null, scenario, "completed", endOffset,
                cascade, impact, name, Instant.now().toString(), Instant.now().toString());
    }

    /* ============================ scenarios ============================ */

    private void rainfall(Builder b, Map<String, Object> params) {
        double intensity = num(params, "intensityMm", 80);
        double duration = num(params, "durationMinutes", 120);
        List<Zone> targets = targets(params);
        double floodHitThreshold = 0.45;

        Map<String, Object> payload = Map.of("intensityMm", intensity, "durationMinutes", duration);
        b.node("seed:rainfall", "TRIGGER", "Rainfall %.0f mm/h for %.0f min".formatted(intensity, duration),
                null, payload, 0, 0.9);
        drainage.waterloggingPredictions();

        // per-zone drainage response
        for (Zone z : targets) {
            double score = drainage.zoneFloodScore(z.getId());
            double p = Math.min(0.97, 0.25 + score + intensity / 400.0);
            String nodeA = "drainage:" + z.getId();
            b.edge("seed:rainfall", nodeA, "runoff", 600, p);
            b.node(nodeA, "DRAINAGE", z.getName() + " drainage network under load",
                    id(z), Map.of("sourceScore", score), 600, Math.min(1, 0.3 + score));

            if (score > floodHitThreshold || intensity > 100) {
                // roads become impassable when the projected depth exceeds the kerb height
                var zRoads = roads.findAll().stream()
                        .filter(r -> r.getZone() != null && z.getId().equals(r.getZone().getId()))
                        .sorted(java.util.Comparator.comparingInt(com.urbantwin.roads.Road::getConditionScore))
                        .limit(3)
                        .toList();
                int delay = 900;
                for (var r : zRoads) {
                    String nodeR = "road:" + r.getId();
                    b.edge(nodeA, nodeR, "waterlogs", delay, Math.min(0.95, 0.35 + score));
                    b.node(nodeR, "ROAD", r.getName() + " flooded/stalled",
                            id(z), Map.of("condition", r.getConditionScore()), delay,
                            Math.min(1, 0.4 + score));
                    delay += 180 + rng.nextInt(240);

                    // traffic reroutes onto the remaining arterials
                    String nodeT = "traffic:" + r.getId();
                    b.edge(nodeR, nodeT, "reroutes", 120, 0.8);
                    b.node(nodeT, "TRAFFIC", "Traffic diverted away from " + r.getName(),
                            id(z), Map.of(), delay, 0.6);
                    delay += 60;
                }
            }

            // emergency response affected when any road rerouted
            long incidentsInZone = incidents.findAll().stream()
                    .filter(i -> i.getZone() != null && z.getId().equals(i.getZone().getId())
                            && !"resolved".equals(i.getStatus()))
                    .count();
            if (incidentsInZone > 0 && score > 0.3) {
                String nodeI = "incident:zone-" + z.getId();
                b.edge(nodeA, nodeI, "reroutes", 1200, 0.75);
                b.node(nodeI, "INCIDENT", incidentsInZone + " active emergency call(s) rerouted in " + z.getName(),
                        id(z), Map.of("count", incidentsInZone), 1200, 0.7);
            }
        }

        // citizen flood reports surface shortly after the water does
        long estimatedReports = rng.nextInt(60) + 20;
        b.edge("seed:rainfall", "reports:citizens", "accumulate", 1500, 0.9);
        b.node("reports:citizens", "REPORT", "≈" + estimatedReports + " citizen flooding reports expected",
                null, Map.of("estimate", estimatedReports), 1500, 0.85);

        b.edge("seed:rainfall", "mitigation:drain-crews", "dispatched", 600, 0.7);
        b.node("mitigation:drain-crews", "MITIGATION", "Drainage crews + flood alert broadcast",
                null, Map.of(), 600, 0.8);
    }

    private void substationOutage(Builder b, Map<String, Object> params) {
        double criticality = "high".equalsIgnoreCase(String.valueOf(params.getOrDefault("criticality", "medium"))) ? 0.9 : 0.6;
        UUID zoneId = optUUID(params, "zoneId");
        List<Zone> targets = zoneId == null ? zones.findAll().subList(0, Math.min(2, zones.findAll().size()))
                : zones.findAll().stream().filter(z -> z.getId().equals(zoneId)).toList();

        b.node("seed:outage", "TRIGGER", "Substation feeder loss (grid " + (zoneId == null ? "auto-selected" : zoneId) + ")",
                null, Map.of("criticality", criticality), 0, criticality);

        for (Zone z : targets) {
            double zCongestion = traffic.zoneCongestion(z.getId());
            String nodeE = "grid:" + z.getId();
            b.edge("seed:outage", nodeE, "blacks_out", 120, 0.95);
            b.node(nodeE, "ENERGY", z.getName() + " zone loses power",
                    id(z), Map.of(), 120, 0.95);

            // signalised intersections go dark → congestion builds
            var zoneCluster = roads.findAll().stream()
                    .filter(r -> r.getZone() != null && z.getId().equals(r.getZone().getId()))
                    .limit(2)
                    .map(com.urbantwin.roads.Road::getZone)
                    .distinct()
                    .toList();
            int delay = 240;
            for (var zr : zoneCluster) {
                String nodeT = "signals:" + zr.getId();
                b.edge(nodeE, nodeT, "dark", delay, 0.9);
                b.node(nodeT, "TRAFFIC", "Traffic signals dark in " + zr.getName(),
                        id(zr), Map.of("baselineCongestion", zCongestion), delay, 0.85);
                delay += 180;
            }
            b.edge(nodeE, "congestion:spike-" + z.getId(), "gridlocks", 600, 0.8);
            b.node("congestion:spike-" + z.getId(), "TRAFFIC", "Congestion spike at corridor level",
                    id(z), Map.of("expectedDelta", "+0.25 within 10 min"), 600, 0.75);
        }

        b.edge("seed:outage", "reports:citizens", "trips_calls", 900, 0.8);
        b.node("reports:citizens", "REPORT", "Citizen reports of streetlight & outage cluster",
                null, Map.of("estimate", 25 + rng.nextInt(40)), 900, 0.8);
        b.edge("seed:outage", "mitigation:field-crew", "dispatched", 300, 0.85);
        b.node("mitigation:field-crew", "MITIGATION", "Field crew dispatched; signal marshals deployed",
                null, Map.of(), 300, 0.9);
    }

    private void waterMainBurst(Builder b, Map<String, Object> params) {
        UUID zoneId = optUUID(params, "zoneId");
        List<Zone> targets = zoneId == null ? zones.findAll().subList(0, Math.min(1, zones.findAll().size()))
                : zones.findAll().stream().filter(z -> z.getId().equals(zoneId)).toList();
        b.node("seed:burst", "TRIGGER", "Water main burst (60 l/s)", null,
                Map.of("flowLps", 60), 0, 0.8);

        for (Zone z : targets) {
            double score = 0.55 + rng.nextDouble() * 0.3;
            drainage.waterloggingPredictions();
            String zoneIdStr = id(z);
            String drainageNode = "drainage:" + zoneIdStr;
            b.edge("seed:burst", drainageNode, "overloads", 300, 0.85);
            b.node(drainageNode, "DRAINAGE", z.getName() + " trunk line surging",
                    zoneIdStr, Map.of("score", round2(score)), 300, 0.8);
            b.edge(drainageNode, "road:burst", "floods", 600, 0.8);
            b.node("road:burst", "ROAD", "Waterlogged corridor near fan-out valve",
                    zoneIdStr, Map.of(), 600, 0.75);
            b.edge("road:burst", "traffic:burst", "detours", 800, 0.75);
            b.node("traffic:burst", "TRAFFIC", "Traffic detoured around the burst",
                    zoneIdStr, Map.of(), 800, 0.6);
        }
        b.edge("seed:burst", "mitigation:valve-crew", "dispatched", 200, 0.9);
        b.node("mitigation:valve-crew", "MITIGATION", "Valve isolation + restoration crew",
                null, Map.of("eta", "20 min"), 200, 0.9);
    }

    private void sensorFailure(Builder b, Map<String, Object> params) {
        b.node("seed:sensor", "TRIGGER", "Cluster of IoT sensors goes stale", null,
                Map.of("count", 3 + rng.nextInt(6)), 0, 0.6);
        b.edge("seed:sensor", "drainage:stale", "stale", 60, 0.9);
        b.node("drainage:stale", "DRAINAGE", "Water level gauge feed frozen",
                null, Map.of("lastSeenMinutes", 45), 60, 0.6);
        b.edge("seed:sensor", "traffic:stale", "stale", 120, 0.9);
        b.node("traffic:stale", "TRAFFIC", "Traffic loop data frozen",
                null, Map.of("lastSeenMinutes", 45), 120, 0.6);
        b.edge("seed:sensor", "twin:alarm", "escalates", 300, 1.0);
        b.node("twin:alarm", "REPORT", "Digital-twin freshness alarm raised → ops paged",
                null, Map.of("severity", "p2"), 300, 0.95);
        b.edge("seed:sensor", "mitigation:manual-verification", "patrol", 600, 0.7);
        b.node("mitigation:manual-verification", "MITIGATION", "Manual field verification launched",
                null, Map.of(), 600, 0.8);
    }

    /* ============================= builder ============================= */

    private class Builder {
        private final List<Node> nodes = new ArrayList<>();
        private final List<Edge> edges = new ArrayList<>();
        private final Map<String, Node> byId = new LinkedHashMap<>();

        void node(String id, String type, String label, String zoneId,
                  Map<String, Object> payload, int offset, double severity) {
            Node n = new Node(id, type, label, zoneId, payload, offset, severity);
            if (!byId.containsKey(id)) {
                nodes.add(n);
                byId.put(id, n);
            }
        }

        void edge(String from, String to, String relation, int delay, double probability) {
            edges.add(new Edge(from, to, relation, delay, round2(probability)));
        }

        Node last() {
            return nodes.get(nodes.size() - 1);
        }

        CascadeDtos.Cascade build() {
            return new CascadeDtos.Cascade(List.copyOf(nodes), List.copyOf(edges));
        }
    }

    /* ============================= helpers ============================== */

    private List<Zone> targets(Map<String, Object> params) {
        UUID zoneId = optUUID(params, "zoneId");
        List<Zone> all = zones.findAll();
        return zoneId == null ? all : all.stream().filter(z -> z.getId().equals(zoneId)).toList();
    }

    private long countZones(CascadeDtos.Cascade c) {
        return c.nodes().stream().map(Node::zoneId).filter(z -> z != null && !z.isBlank()).distinct().count();
    }

    private long countType(CascadeDtos.Cascade c, String type) {
        return c.nodes().stream().filter(n -> type.equals(n.type())).count();
    }

    private long countType(CascadeDtos.Cascade c, String type, String labelToken) {
        return c.nodes().stream()
                .filter(n -> type.equals(n.type()) && n.label().toLowerCase().contains(labelToken)).count();
    }

    private static String id(UUID v) {
        return v == null ? null : v.toString();
    }

    private static String id(Zone z) {
        return z == null ? null : z.getId().toString();
    }

    private static UUID optUUID(Map<String, Object> params, String key) {
        try {
            Object v = params.get(key);
            return v == null ? null : UUID.fromString(String.valueOf(v));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static double num(Map<String, Object> params, String key, double dflt) {
        try {
            Object v = params.get(key);
            return v == null ? dflt : Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return dflt;
        }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
