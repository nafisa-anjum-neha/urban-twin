package com.urbantwin.waste;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbantwin.city.Zone;
import com.urbantwin.city.ZoneRepository;
import com.urbantwin.common.GlobalExceptionHandler.BadRequestException;
import com.urbantwin.common.GlobalExceptionHandler.NotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Waste bin management and collection-route optimisation (§7.6). */
@Service
public class WasteService {

    private static final double MIN_FILL_FOR_ROUTE = 60.0;

    private final WasteBinRepository bins;
    private final WasteCollectionRouteRepository routes;
    private final ZoneRepository zones;
    private final ObjectMapper json;

    public WasteService(WasteBinRepository bins, WasteCollectionRouteRepository routes,
                        ZoneRepository zones, ObjectMapper json) {
        this.bins = bins;
        this.routes = routes;
        this.zones = zones;
        this.json = json;
    }

    public List<WasteDtos.BinDto> listBins(UUID zoneId, Double minFill) {
        return bins.findAll().stream()
                .filter(b -> zoneId == null || (b.getZone() != null && b.getZone().getId().equals(zoneId)))
                .filter(b -> minFill == null || b.getFillLevelPct() >= minFill)
                .map(WasteDtos.BinDto::from).toList();
    }

    /** Resets a bin to empty after a pickup run. */
    public WasteDtos.BinDto collect(UUID binId) {
        WasteBin b = bins.findById(binId).orElseThrow(() -> new NotFoundException("bin", binId));
        b.setFillLevelPct(0);
        b.setLastCollectedAt(Instant.now());
        return WasteDtos.BinDto.from(bins.save(b));
    }

    /**
     * Builds an optimised route for bins filled ≥ 60 % in the zone (or the first
     * zone when omitted), persists it as `planned`, and returns the full DTO.
     */
    public WasteDtos.RouteDto optimize(UUID zoneId, String vehicleId) {
        UUID target = zoneId;
        Zone zone = zones.findById(target).orElseThrow(() -> new NotFoundException("zone", target));

        List<WasteBin> candidates = bins.findByZoneIdAndFillLevelPctGreaterThanEqual(zone.getId(), MIN_FILL_FOR_ROUTE);
        if (candidates.isEmpty()) {
            throw new BadRequestException("No bins above 60 % fill in this zone — nothing to collect");
        }

        // depot = zone centroid-ish office (average of bin positions)
        double depotLon = candidates.stream().mapToDouble(b -> b.getLocation().getX()).average().orElse(0);
        double depotLat = candidates.stream().mapToDouble(b -> b.getLocation().getY()).average().orElse(0);

        List<WasteRouteOptimizer.Stop> stops = candidates.stream()
                .map(b -> new WasteRouteOptimizer.Stop(b.getId(), b.getName(),
                        b.getLocation().getX(), b.getLocation().getY(), b.getFillLevelPct()))
                .toList();

        WasteRouteOptimizer.Result plan = WasteRouteOptimizer.plan(
                stops, new WasteRouteOptimizer.Stop(null, "depot", depotLon, depotLat, 0));

        // persist with stop-order JSON
        WasteCollectionRoute route = new WasteCollectionRoute();
        route.setName("Sweep " + zone.getName() + " " + Instant.now());
        route.setZone(zone);
        route.setVehicleId(vehicleId == null ? "" : vehicleId);
        route.setStopsJson(toStopsJson(plan));
        route.setTotalDistanceKm(plan.totalDistanceKm());
        route.setEstimatedDurationMin(plan.durationMinutes());
        route.setStatus("planned");
        return WasteDtos.RouteDto.from(routes.save(route), toStopDtos(plan));
    }

    public List<WasteDtos.RouteDto> listRoutes(String status) {
        return routes.findAllByOrderByCreatedAtDesc().stream()
                .filter(r -> status == null || status.equals(r.getStatus()))
                .map(r -> WasteDtos.RouteDto.from(r, persistedStops(r.getStopsJson())))
                .toList();
    }

    /* ----------------------------- helpers ------------------------------ */

    private String toStopsJson(WasteRouteOptimizer.Result plan) {
        try {
            List<WasteDtos.StopDto> list = toStopDtos(plan);
            return json.writeValueAsString(list);
        } catch (Exception e) {
            throw new IllegalStateException("failed to persist route stops", e);
        }
    }

    private List<WasteDtos.StopDto> toStopDtos(WasteRouteOptimizer.Result plan) {
        List<WasteDtos.StopDto> out = new ArrayList<>(plan.orderedStops().size());
        for (int i = 0; i < plan.orderedStops().size(); i++) {
            var s = plan.orderedStops().get(i);
            out.add(new WasteDtos.StopDto(s.binId(), s.name(), s.lat(), s.lon(), s.fillLevelPct(), i + 1));
        }
        return out;
    }

    private List<WasteDtos.StopDto> persistedStops(String stopsJson) {
        if (stopsJson == null || stopsJson.isBlank()) return List.of();
        try {
            return json.readValue(stopsJson,
                    json.getTypeFactory().constructCollectionType(List.class, WasteDtos.StopDto.class));
        } catch (Exception e) {
            return List.of();
        }
    }
}
