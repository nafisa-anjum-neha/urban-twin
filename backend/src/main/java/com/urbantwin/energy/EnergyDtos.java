package com.urbantwin.energy;

import org.locationtech.jts.geom.Point;
import java.time.Instant;
import java.util.UUID;

/** DTO factory + payload records for the energy module (contract §7.5, §5). */
public final class EnergyDtos {
    private EnergyDtos() {}

    /** Kafka `energy.readings` payload and REST ingest body. */
    public record EnergyPayload(String gridId, double consumptionKw, double voltage,
                                boolean outageReported, Instant recordedAt) {}

    public record GridDto(UUID id, UUID zoneId, String zoneName, String name,
                          double capacityKw, double lossPct, GridReadingDto latestReading) {
        public static GridDto from(ElectricityGrid g, GridReadingDto latest) {
            return new GridDto(g.getId(), g.getZone() != null ? g.getZone().getId() : null,
                    g.getZone() != null ? g.getZone().getName() : null,
                    g.getName(), g.getCapacityKw(), g.getLossPct(), latest);
        }
    }

    public record GridReadingDto(UUID id, UUID gridId, double consumptionKw, double voltage,
                                 boolean outageReported, Instant recordedAt) {
        public static GridReadingDto from(GridReading r) {
            return new GridReadingDto(r.getId(), r.getGrid().getId(), r.getConsumptionKw(),
                    r.getVoltage(), r.isOutageReported(), r.getRecordedAt());
        }
    }

    public record ZoneSummaryDto(UUID zoneId, String name, double currentKw, double peakKw24h,
                                 long outages24h, String trend) {}

    public record AssetDto(UUID id, UUID zoneId, String zoneName, String assetType, String name,
                           double[] location, int conditionScore, int installedYear, String status) {
        public static AssetDto from(InfrastructureAsset a) {
            Point p = a.getLocation();
            double[] loc = p == null ? null : new double[]{p.getX(), p.getY()};
            return new AssetDto(a.getId(), a.getZone() != null ? a.getZone().getId() : null,
                    a.getZone() != null ? a.getZone().getName() : null, a.getAssetType(), a.getName(),
                    loc, a.getConditionScore(), a.getInstalledYear(), a.getStatus());
        }
    }
}
