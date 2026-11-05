package com.urbantwin.drainage;

import org.locationtech.jts.geom.Point;
import java.time.Instant;
import java.util.UUID;

/** DTO factory + payload records for the drainage module (contract §7.4, §5). */
public final class DrainageDtos {
    private DrainageDtos() {}

    /** Kafka `drainage.readings` payload and REST ingest body. */
    public record WaterPayload(String sensorId, double waterLevelCm, double flowRateLps,
                               double rainfallMm, Instant recordedAt) {}

    public record NetworkDto(UUID id, UUID zoneId, String zoneName, String name,
                             int capacityM3PerHour, String networkClass) {
        public static NetworkDto from(DrainageNetwork n) {
            return new NetworkDto(n.getId(), n.getZone() != null ? n.getZone().getId() : null,
                    n.getZone() != null ? n.getZone().getName() : null,
                    n.getName(), n.getCapacityM3PerHour(), n.getNetworkClass());
        }
    }

    public record SensorDto(UUID id, UUID networkId, String name, double[] location, String status,
                            WaterReadingDto latestReading) {
        public static SensorDto from(DrainageSensor s, WaterReadingDto latest) {
            Point p = s.getLocation();
            double[] loc = p == null ? null : new double[]{p.getX(), p.getY()};
            return new SensorDto(s.getId(), s.getNetwork() != null ? s.getNetwork().getId() : null,
                    s.getName(), loc, s.getStatus(), latest);
        }
    }

    public record WaterReadingDto(UUID id, UUID sensorId, double waterLevelCm, double flowRateLps,
                                  double rainfallMm, String riskLevel, Instant recordedAt) {
        public static WaterReadingDto from(WaterLevelReading r) {
            return new WaterReadingDto(r.getId(), r.getSensor().getId(), r.getWaterLevelCm(),
                    r.getFlowRateLps(), r.getRainfallMm(), r.getRiskLevel(), r.getRecordedAt());
        }
    }

    /** zoneId + risk + peak level + impacted roads (contract §7.4). */
    public record PredictionDto(UUID zoneId, String risk, double maxLevelCm,
                                java.util.List<UUID> affectedRoads, double confidence) {}
}
