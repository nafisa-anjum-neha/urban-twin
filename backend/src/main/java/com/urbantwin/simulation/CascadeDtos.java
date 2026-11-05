package com.urbantwin.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Immutable cascade result structures (contract §7.10). */
public final class CascadeDtos {
    private CascadeDtos() {}

    public record Node(String id, String type, String label, String zoneId,
                       Map<String, Object> payload, int startOffsetSec, double severity) {}

    public record Edge(String from, String to, String relation, int delaySec, double probability) {}

    public record Cascade(List<Node> nodes, List<Edge> edges) {}

    public record Impact(long zonesAffected, long roadsImpacted, long roadsWaterlogged,
                         long incidentsRerouted, long citizenReportsEstimate, int totalNodes) {}

    public record RunResult(UUID simulationId, String scenario, String status, int timelineSeconds,
                            Cascade cascade, Impact impactSummary, String name,
                            String startedAt, String completedAt) {}
}
