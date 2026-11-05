package com.urbantwin.emergency;

import org.locationtech.jts.geom.Point;
import java.time.Instant;
import java.util.UUID;

/** DTO factory for the emergency module (contract §7.7). */
public final class EmergencyDtos {
    private EmergencyDtos() {}

    public record CreateIncident(String name, String type, int severity, double lat, double lon,
                                 String description, UUID zoneId) {}

    public record IncidentDto(UUID id, UUID zoneId, String zoneName, String name, String incidentType,
                              int severity, double[] location, String description, String status,
                              Instant reportedAt, Instant resolvedAt) {
        public static IncidentDto from(EmergencyIncident i) {
            Point p = i.getLocation();
            double[] loc = p == null ? null : new double[]{p.getX(), p.getY()};
            return new IncidentDto(i.getId(), i.getZone() != null ? i.getZone().getId() : null,
                    i.getZone() != null ? i.getZone().getName() : null, i.getName(), i.getIncidentType(),
                    i.getSeverity(), loc, i.getDescription(), i.getStatus(), i.getReportedAt(), i.getResolvedAt());
        }
    }

    public record VehicleDto(UUID id, String name, String vehicleType, String status, double[] station,
                             double headingDeg) {
        public static VehicleDto from(EmergencyVehicle v) {
            Point p = v.getStation();
            double[] loc = p == null ? null : new double[]{p.getX(), p.getY()};
            return new VehicleDto(v.getId(), v.getName(), v.getVehicleType(), v.getStatus(), loc, v.getHeadingDeg());
        }
    }

    public record DispatchDto(UUID id, UUID incidentId, UUID vehicleId, String routeGeojson,
                              double distanceKm, double etaMin, String status, Instant dispatchedAt) {
        public static DispatchDto from(Dispatch d) {
            return new DispatchDto(d.getId(), d.getIncident() != null ? d.getIncident().getId() : null,
                    d.getVehicle() != null ? d.getVehicle().getId() : null, d.getRouteGeojson(),
                    d.getDistanceKm(), d.getEtaMin(), d.getStatus(), d.getDispatchedAt());
        }
    }
}
