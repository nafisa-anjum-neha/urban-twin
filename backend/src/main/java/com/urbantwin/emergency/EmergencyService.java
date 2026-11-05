package com.urbantwin.emergency;

import com.urbantwin.city.Zone;
import com.urbantwin.city.ZoneRepository;
import com.urbantwin.common.GeoSupport;
import com.urbantwin.common.GlobalExceptionHandler.BadRequestException;
import com.urbantwin.common.GlobalExceptionHandler.NotFoundException;
import com.urbantwin.drainage.DrainageService;
import com.urbantwin.roads.Road;
import com.urbantwin.roads.RoadRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;

/** Incident intake, nearest-vehicle dispatch and flood-aware route advisory. */
@Service
public class EmergencyService {

    /** fleet cruise + response speeds (km/h) indexed by incident severity 1..5 */
    private static final double[] SPEED_BY_SEVERITY = {0, 70, 60, 50, 40, 32};

    private final EmergencyIncidentRepository incidents;
    private final EmergencyVehicleRepository vehicles;
    private final DispatchRepository dispatches;
    private final ZoneRepository zones;
    private final RoadRepository roads;
    private final DrainageService drainage;
    private final com.urbantwin.ingest.TwinPublisher publisher;

    public EmergencyService(EmergencyIncidentRepository incidents, EmergencyVehicleRepository vehicles,
                            DispatchRepository dispatches, ZoneRepository zones, RoadRepository roads,
                            DrainageService drainage, com.urbantwin.ingest.TwinPublisher publisher) {
        this.incidents = incidents;
        this.vehicles = vehicles;
        this.dispatches = dispatches;
        this.zones = zones;
        this.roads = roads;
        this.drainage = drainage;
        this.publisher = publisher;
    }

    /* --------------------------- incidents ------------------------------ */

    public EmergencyDtos.IncidentDto create(EmergencyDtos.CreateIncident dto) {
        if (dto.severity() < 1 || dto.severity() > 5) {
            throw new BadRequestException("severity must be 1..5");
        }
        EmergencyIncident i = new EmergencyIncident();
        i.setName(dto.name());
        i.setIncidentType(dto.type());
        i.setSeverity(dto.severity());
        i.setDescription(dto.description());
        i.setStatus("active");
        i.setReportedAt(Instant.now());
        i.setLocation(GeoSupport.point(dto.lat(), dto.lon()));
        Zone zone;
        if (dto.zoneId() != null) {
            zone = zones.findById(dto.zoneId()).orElseThrow(() -> new NotFoundException("zone", dto.zoneId()));
        } else {
            // nearest zone centroid
            final var point = i.getLocation();
            zone = nearestZone(point);
        }
        i.setZone(zone);
        EmergencyIncident saved = incidents.save(i);

        publisher.publish("INCIDENT", zoneMeta(zone), Map.of(
                "incident", saved.getName(),
                "type", saved.getIncidentType(),
                "severity", saved.getSeverity()));
        return EmergencyDtos.IncidentDto.from(saved);
    }

    public List<EmergencyDtos.IncidentDto> listIncidents(String status, String type, Integer minSeverity) {
        return incidents.findAll().stream()
                .filter(i -> status == null || status.equals(i.getStatus()))
                .filter(i -> type == null || type.equals(i.getIncidentType()))
                .filter(i -> minSeverity == null || i.getSeverity() >= minSeverity)
                .sorted(Comparator.comparing(EmergencyIncident::getReportedAt).reversed())
                .map(EmergencyDtos.IncidentDto::from).toList();
    }

    /* --------------------------- dispatching ---------------------------- */

    /**
     * Picks the nearest `available` vehicle (or forces `vehicleId` when provided),
     * computes a straight-ish response route and ETA by severity speed, and moves
     * vehicle + incident into DISPATCHED state.
     */
    public EmergencyDtos.DispatchDto dispatch(UUID incidentId, UUID vehicleId) {
        EmergencyIncident incident = incidents.findById(incidentId)
                .orElseThrow(() -> new NotFoundException("incident", incidentId));
        if ("resolved".equals(incident.getStatus())) {
            throw new BadRequestException("incident already resolved");
        }

        EmergencyVehicle vehicle;
        if (vehicleId != null) {
            vehicle = vehicles.findById(vehicleId).orElseThrow(() -> new NotFoundException("vehicle", vehicleId));
            if (!"available".equals(vehicle.getStatus()) && !"offline".equals(vehicle.getStatus())) {
                throw new BadRequestException("vehicle is not available");
            }
        } else {
            final Point loc = incident.getLocation();
            vehicle = vehicles.findByStatus("available").stream()
                    .filter(v -> v.getStation() != null && incident.getLocation() != null)
                    .min(Comparator.comparingDouble(v ->
                            GeoSupport.distanceKm(new double[]{v.getStation().getX(), v.getStation().getY()},
                                    new double[]{loc.getX(), loc.getY()})))
                    .orElseThrow(() -> new BadRequestException("no available vehicles — try again shortly"));
        }

        double km = responseDistanceKm(vehicle, incident);
        double speed = SPEED_BY_SEVERITY[Math.max(1, Math.min(5, incident.getSeverity()))];
        double eta = km / speed * 60.0;

        Dispatch d = new Dispatch();
        d.setIncident(incident);
        d.setVehicle(vehicle);
        d.setDistanceKm(Math.round(km * 10.0) / 10.0);
        d.setEtaMin(Math.round(eta));
        d.setStatus("dispatched");
        d.setDispatchedAt(Instant.now());
        d.setRouteGeojson(routeGeojson(vehicle, incident));

        vehicle.setStatus("dispatched");
        incident.setStatus("dispatched");
        vehicles.save(vehicle);
        incidents.save(incident);
        Dispatch saved = dispatches.save(d);

        publisher.publish("INCIDENT", zoneMeta(incident.getZone()), Map.of(
                "dispatch", saved.getId().toString(),
                "vehicle", vehicle.getName(),
                "etaMin", saved.getEtaMin()));
        return EmergencyDtos.DispatchDto.from(saved);
    }

    /** Marks the dispatch arrived, frees the vehicle and closes the incident. */
    public EmergencyDtos.DispatchDto resolve(UUID dispatchId) {
        Dispatch d = dispatches.findById(dispatchId).orElseThrow(() -> new NotFoundException("dispatch", dispatchId));
        d.setStatus("arrived");
        d.getVehicle().setStatus("available");
        EmergencyIncident i = d.getIncident();
        i.setStatus("resolved");
        i.setResolvedAt(Instant.now());
        vehicles.save(d.getVehicle());
        incidents.save(i);
        publisher.publish("INCIDENT", zoneMeta(i.getZone()), Map.of(
                "dispatch", d.getId().toString(),
                "resolved", i.getName()));
        return EmergencyDtos.DispatchDto.from(dispatches.save(d));
    }

    public List<EmergencyDtos.VehicleDto> listVehicles(String status) {
        return vehicles.findAll().stream()
                .filter(v -> status == null || status.equals(v.getStatus()))
                .map(EmergencyDtos.VehicleDto::from).toList();
    }

    /* --------------------- flood-aware route advisory -------------------- */

    /**
     * Candidate safe routes for reaching an incident: the primary line is the direct
     * polyline; when the zone's flood score is elevated, a detour variant that hugs
     * higher-condition roads is included and flagged in its properties. Route selection
     * is advisory (city control room) — the tank/vehicle nav integration is out of scope.
     */
    public String safestRoute(UUID incidentId) {
        EmergencyIncident incident = incidents.findById(incidentId)
                .orElseThrow(() -> new NotFoundException("incident", incidentId));
        Point loc = incident.getLocation();
        if (loc == null) throw new BadRequestException("incident has no location");
        double lat = loc.getY(), lon = loc.getX();

        double floodScore = incident.getZone() != null ? drainage.zoneFloodScore(incident.getZone().getId()) : 0;
        boolean detourNeeded = floodScore > 0.45;

        List<double[]> primary = List.of(
                new double[]{lon - 0.01, lat - 0.01},
                new double[]{lon, lat - 0.004},
                new double[]{lon, lat});
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        sb.append("{\"type\":\"Feature\",\"geometry\":").append(GeoSupport.lineGeoJson(primary))
          .append(",\"properties\":{\"variant\":\"direct\",\"floodScore\":").append(round2(floodScore)).append("}}");
        if (detourNeeded) {
            List<Road> wideRoads = roads.findByZoneId(incident.getZone() != null ? incident.getZone().getId() : null)
                    .stream().filter(r -> r.getConditionScore() >= 70).limit(4).toList();
            for (int k = 0; k < wideRoads.size(); k++) {
                Road r = wideRoads.get(k);
                if (r.getPath() == null) continue;
                LineString path = r.getPath();
                var coords = List.of(path.getCoordinates());
                List<double[]> detour = new ArrayList<>();
                for (var c : coords) detour.add(new double[]{c.getX(), c.getY()});
                sb.append(",{\"type\":\"Feature\",\"geometry\":").append(GeoSupport.lineGeoJson(detour))
                  .append(",\"properties\":{\"variant\":\"detour-").append(k + 1)
                  .append("\",\"road\":\"").append(r.getName().replace("\"", ""))
                  .append("\",\"floodScore\":").append(round2(floodScore)).append("}}");
            }
        } else {
            // always offer one high-condition detour alternative for ops planning
            Optional<Road> alt = roads.findByZoneId(incident.getZone() != null ? incident.getZone().getId() : null)
                    .stream().filter(r -> r.getConditionScore() >= 70).findFirst();
            alt.ifPresent(r -> {
                if (r.getPath() == null) return;
                List<double[]> detour = new ArrayList<>();
                for (var c : List.of(r.getPath().getCoordinates())) detour.add(new double[]{c.getX(), c.getY()});
                sb.append(",{\"type\":\"Feature\",\"geometry\":").append(GeoSupport.lineGeoJson(detour))
                  .append(",\"properties\":{\"variant\":\"wellness\",\"road\":\"")
                  .append(r.getName().replace("\"", "")).append("\"}}");
            });
        }
        sb.append(']');
        return "{\"type\":\"FeatureCollection\",\"features\":" + sb + "}";
    }

    /* ----------------------------- helpers ------------------------------ */

    private double responseDistanceKm(EmergencyVehicle v, EmergencyIncident i) {
        Point station = v.getStation();
        Point loc = i.getLocation();
        if (station == null || loc == null) return 3.0; // pessimistic default
        return GeoSupport.distanceKm(new double[]{station.getX(), station.getY()},
                new double[]{loc.getX(), loc.getY()});
    }

    /** simplified 2-leg polyline station → incident, snapped straight for demo map display */
    private String routeGeojson(EmergencyVehicle v, EmergencyIncident i) {
        Point station = v.getStation();
        Point loc = i.getLocation();
        if (station == null || loc == null) return null;
        double midLat = (station.getY() + loc.getY()) / 2.0;
        double midLon = (station.getX() + loc.getX()) / 2.0;
        List<double[]> points = List.of(
                new double[]{station.getX(), station.getY()},
                new double[]{midLon + 0.0015, midLat + 0.0015},
                new double[]{loc.getX(), loc.getY()});
        return GeoSupport.lineGeoJson(points);
    }

    private Zone nearestZone(Point point) {
        List<Zone> all = zones.findAll();
        if (all.isEmpty()) {
            throw new NotFoundException("Configure at least one city zone before reporting incidents");
        }
        return all.stream()
                .min(Comparator.comparingDouble(z -> {
                    if (z.getBoundary() == null) return Double.MAX_VALUE;
                    return z.getBoundary().distance(point);
                }))
                .orElseThrow();
    }

    private Map<String, Object> zoneMeta(Zone zone) {
        return zone == null ? Map.of() : Map.of(
                "zoneId", String.valueOf(zone.getId()),
                "zoneName", zone.getName());
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** exposed for the twin (used by DigitalTwinService totals). */
    public long activeIncidentCount() {
        return incidents.findAll().stream()
                .filter(i -> Set.of("active", "dispatched").contains(i.getStatus()))
                .count();
    }
}
