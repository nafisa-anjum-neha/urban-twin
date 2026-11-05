package com.urbantwin.waste;

import java.time.Instant;
import java.util.UUID;

/** DTO factory for the waste module (contract §7.6). */
public final class WasteDtos {
    private WasteDtos() {}

    public record BinDto(UUID id, UUID zoneId, String zoneName, String name, double[] location,
                         double fillLevelPct, Instant lastCollectedAt) {
        public static BinDto from(WasteBin b) {
            var p = b.getLocation();
            double[] loc = p == null ? null : new double[]{p.getX(), p.getY()};
            return new BinDto(b.getId(), b.getZone() != null ? b.getZone().getId() : null,
                    b.getZone() != null ? b.getZone().getName() : null,
                    b.getName(), loc, b.getFillLevelPct(), b.getLastCollectedAt());
        }
    }

    public record StopDto(UUID binId, String name, double lat, double lon, double fillLevel, int order) {}

    public record RouteDto(UUID id, String name, UUID zoneId, UUID vehicleId, java.util.List<StopDto> stops,
                           double totalDistanceKm, double estimatedDurationMin, String status) {
        public static RouteDto from(WasteCollectionRoute r, java.util.List<StopDto> stops) {
            return new RouteDto(r.getId(), r.getName(), r.getZone() != null ? r.getZone().getId() : null,
                    parse(r.getVehicleId()), stops, r.getTotalDistanceKm(), r.getEstimatedDurationMin(), r.getStatus());
        }
        private static UUID parse(String s) {
            try { return UUID.fromString(s); } catch (Exception e) { return null; }
        }
    }
}
