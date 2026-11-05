package com.urbantwin.traffic;

import org.locationtech.jts.geom.Point;
import java.time.Instant;
import java.util.UUID;

/** DTO factory + payload records for the traffic module (contract §7.2, §5). */
public final class TrafficDtos {
    private TrafficDtos() {}

    /** Kafka `traffic.readings` payload and REST ingest body. */
    public record TrafficPayload(String sensorId, int vehicleCount, double avgSpeedKph,
                                 double occupancyPct, Instant recordedAt) {}

    public record ReadingDto(UUID id, UUID sensorId, int vehicleCount, double avgSpeedKph,
                             double occupancyPct, double congestionLevel, Instant recordedAt) {
        public static ReadingDto from(TrafficReading r) {
            return new ReadingDto(r.getId(), r.getSensor().getId(), r.getVehicleCount(),
                    r.getAvgSpeedKph(), r.getOccupancyPct(), r.getCongestionLevel(), r.getRecordedAt());
        }
    }

    public record SensorDto(UUID id, UUID roadId, String name, double[] location, String status,
                            ReadingDto latestReading) {
        public static SensorDto from(TrafficSensor s, ReadingDto latest) {
            Point p = s.getLocation();
            double[] loc = p == null ? null : new double[]{p.getX(), p.getY()};
            return new SensorDto(s.getId(), s.getRoad() != null ? s.getRoad().getId() : null,
                    s.getName(), loc, s.getStatus(), latest);
        }
    }

    public record ZoneCongestionDto(UUID zoneId, String name, double congestionLevel,
                                    double avgSpeedKph, String trend, Instant updatedAt) {}

    public record PredictionDto(UUID zoneId, int horizonMinute, double predictedCongestion, double confidence) {}
}
