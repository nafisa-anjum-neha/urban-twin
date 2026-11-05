package com.urbantwin.roads;

import com.urbantwin.maintenance.MaintenanceRequest;
import com.urbantwin.maintenance.MaintenanceRequestRepository;
import com.urbantwin.common.GlobalExceptionHandler.BadRequestException;
import com.urbantwin.common.GlobalExceptionHandler.NotFoundException;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Road inventory + maintenance workflow (contract §7.3). */
@RestController
@Tag(name = "roads-maintenance")
public class RoadController {

    record RoadDto(UUID id, UUID zoneId, String zoneName, String name, String roadClass,
                   int conditionScore, String geojson, long openMaintenanceRequests) {
        static RoadDto from(Road r, long open) {
            return new RoadDto(r.getId(), r.getZone() != null ? r.getZone().getId() : null,
                    r.getZone() != null ? r.getZone().getName() : null, r.getName(), r.getRoadClass(),
                    r.getConditionScore(), r.getGeojson(), open);
        }
    }

    record MaintenanceDto(UUID id, String title, String description, String priority, String status,
                          String reportedBy, Instant scheduledDate, Instant resolvedAt,
                          UUID roadId, UUID assetId, String roadName, Instant createdAt) {
        static MaintenanceDto from(MaintenanceRequest m) {
            return new MaintenanceDto(m.getId(), m.getTitle(), m.getDescription(), m.getPriority(),
                    m.getStatus(), m.getReportedBy(), m.getScheduledDate(), m.getResolvedAt(),
                    m.getRoad() != null ? m.getRoad().getId() : null,
                    m.getAsset() != null ? m.getAsset().getId() : null,
                    m.getRoad() != null ? m.getRoad().getName() : null, m.getCreatedAt());
        }
    }

    record CreateRequest(String title, String description, String priority, UUID roadId, UUID assetId, String reportedBy) {}
    record StatusRequest(String status) {}

    private static final Set<String> PRIORITIES = Set.of("p1", "p2", "p3", "p4");
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "open", Set.of("assigned", "closed"),
            "assigned", Set.of("in_progress", "closed"),
            "in_progress", Set.of("resolved", "closed"),
            "resolved", Set.of("closed"),
            "closed", Set.of());

    private final RoadRepository roads;
    private final MaintenanceRequestRepository requests;

    RoadController(RoadRepository roads, MaintenanceRequestRepository requests) {
        this.roads = roads;
        this.requests = requests;
    }

    @GetMapping("/api/v1/roads")
    public List<RoadDto> roads(@RequestParam(required = false) UUID zoneId,
                               @RequestParam(required = false) Integer maxCondition) {
        return roads.findAll().stream()
                .filter(r -> zoneId == null || (r.getZone() != null && r.getZone().getId().equals(zoneId)))
                .filter(r -> maxCondition == null || r.getConditionScore() <= maxCondition)
                .map(r -> RoadDto.from(r, requests.findByRoadId(r.getId()).stream()
                        .filter(m -> Set.of("open", "assigned", "in_progress").contains(m.getStatus()))
                        .count()))
                .toList();
    }

    @GetMapping("/api/v1/roads/{id}")
    public RoadDto road(@PathVariable UUID id) {
        Road r = roads.findById(id).orElseThrow(() -> new NotFoundException("road", id));
        return RoadDto.from(r, requests.findByRoadId(id).size());
    }

    @GetMapping("/api/v1/maintenance/requests")
    public List<MaintenanceDto> requests(@RequestParam(required = false) String status,
                                         @RequestParam(required = false) String priority,
                                         @RequestParam(required = false) UUID roadId) {
        return requests.findAllByOrderByCreatedAtDesc().stream()
                .filter(m -> status == null || status.equals(m.getStatus()))
                .filter(m -> priority == null || priority.equals(m.getPriority()))
                .filter(m -> roadId == null || (m.getRoad() != null && m.getRoad().getId().equals(roadId)))
                .map(MaintenanceDto::from).toList();
    }

    @PostMapping("/api/v1/maintenance/requests")
    @Transactional
    public MaintenanceDto create(@RequestBody CreateRequest req) {
        if (req.title() == null || req.title().isBlank()) {
            throw new BadRequestException("title is required");
        }
        if (req.priority() != null && !PRIORITIES.contains(req.priority())) {
            throw new BadRequestException("priority must be p1..p4");
        }
        MaintenanceRequest m = new MaintenanceRequest();
        m.setTitle(req.title());
        m.setDescription(req.description());
        m.setPriority(req.priority() == null ? "p3" : req.priority());
        m.setStatus("open");
        m.setReportedBy(req.reportedBy() == null ? "citizen" : req.reportedBy());
        if (req.roadId() != null) m.setRoad(roads.findById(req.roadId()).orElseThrow());
        if (req.assetId() != null) {
            throw new BadRequestException("assetId linkage is not exposed in this API — pass roadId");
        }
        return MaintenanceDto.from(requests.save(m));
    }

    @PatchMapping("/api/v1/maintenance/requests/{id}/status")
    @Transactional
    public MaintenanceDto patch(@PathVariable UUID id, @RequestBody StatusRequest req) {
        MaintenanceRequest m = requests.findById(id).orElseThrow(() -> new NotFoundException("request", id));
        String next = req.status();
        if (next == null || !TRANSITIONS.containsKey(m.getStatus()) || m.getStatus().equals(next)) {
            throw new BadRequestException("unknown status transition");
        }
        if (!TRANSITIONS.get(m.getStatus()).contains(next)) {
            throw new BadRequestException("cannot move from %s to %s".formatted(m.getStatus(), next));
        }
        m.setStatus(next);
        if ("resolved".equals(next)) m.setResolvedAt(Instant.now());
        return MaintenanceDto.from(requests.save(m));
    }

    @GetMapping("/api/v1/maintenance/summary")
    public List<Map<String, Object>> summary() {
        Map<String, Map<String, Long>> byState = new LinkedHashMap<>();
        requests.findAllByOrderByCreatedAtDesc().forEach(m -> {
            byState.computeIfAbsent(m.getStatus(), k -> new LinkedHashMap<>())
                    .merge(m.getPriority(), 1L, Long::sum);
        });
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        byState.forEach((status, prio) -> prio.forEach((priority, count) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("priority", priority);
            row.put("status", status);
            row.put("count", count);
            out.add(row);
        }));
        return out;
    }
}
