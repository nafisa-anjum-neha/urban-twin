package com.urbantwin.energy;

import com.urbantwin.city.Zone;
import com.urbantwin.city.ZoneRepository;
import com.urbantwin.common.GlobalExceptionHandler.NotFoundException;
import com.urbantwin.ingest.TwinPublisher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Electricity grid telemetry, per-zone energy summary and asset inventory. */
@Service
public class EnergyService {

    private final ElectricityGridRepository grids;
    private final GridReadingRepository readings;
    private final InfrastructureAssetRepository assets;
    private final ZoneRepository zones;
    private final TwinPublisher publisher;

    public EnergyService(ElectricityGridRepository grids, GridReadingRepository readings,
                         InfrastructureAssetRepository assets, ZoneRepository zones,
                         TwinPublisher publisher) {
        this.grids = grids;
        this.readings = readings;
        this.assets = assets;
        this.zones = zones;
        this.publisher = publisher;
    }

    /* ------------------------------ ingest ------------------------------ */

    public EnergyDtos.GridReadingDto record(EnergyDtos.EnergyPayload payload) {
        UUID gridId = parse(payload.gridId(), "gridId");
        ElectricityGrid grid = grids.findById(gridId)
                .orElseThrow(() -> new NotFoundException("grid", gridId));
        Instant when = payload.recordedAt() == null ? Instant.now() : payload.recordedAt();

        GridReading r = new GridReading();
        r.setGrid(grid);
        r.setConsumptionKw(payload.consumptionKw());
        r.setVoltage(payload.voltage());
        r.setOutageReported(payload.outageReported());
        r.setRecordedAt(when);
        readings.save(r);

        Map<String, Object> meta = grid.getZone() != null
                ? Map.of("zoneId", grid.getZone().getId().toString(), "zoneName", grid.getZone().getName())
                : Map.of();
        publisher.publish("ENERGY", meta, Map.of(
                "grid", grid.getName(),
                "consumptionKw", payload.consumptionKw(),
                "outage", payload.outageReported()));
        return EnergyDtos.GridReadingDto.from(r);
    }

    /* ----------------------------- queries ------------------------------ */

    public List<EnergyDtos.GridDto> listGrids(UUID zoneId) {
        List<ElectricityGrid> all = grids.findAll().stream()
                .filter(g -> zoneId == null || (g.getZone() != null && g.getZone().getId().equals(zoneId)))
                .toList();
        List<EnergyDtos.GridDto> out = new ArrayList<>(all.size());
        for (ElectricityGrid g : all) {
            EnergyDtos.GridReadingDto latest = readings.findTopByGridOrderByRecordedAtDesc(g)
                    .map(EnergyDtos.GridReadingDto::from).orElse(null);
            out.add(EnergyDtos.GridDto.from(g, latest));
        }
        return out;
    }

    public List<EnergyDtos.GridReadingDto> listReadings(UUID gridId, Instant from, Instant to) {
        if (gridId == null) return List.of();
        Instant safeFrom = from == null ? Instant.now().minusSeconds(86_400) : from;
        Instant safeTo = to == null ? Instant.now() : to;
        return readings.findByGridIdAndRecordedAtBetweenOrderByRecordedAtAsc(gridId, safeFrom, safeTo)
                .stream().map(EnergyDtos.GridReadingDto::from).toList();
    }

    public List<EnergyDtos.ZoneSummaryDto> summary() {
        Instant dayAgo = Instant.now().minusSeconds(86_400);
        List<EnergyDtos.ZoneSummaryDto> out = new ArrayList<>();
        for (Zone z : zones.findAll()) {
            double current = 0, peak = 0;
            long outages = 0;
            for (ElectricityGrid g : grids.findByZoneId(z.getId())) {
                var latest = readings.findTopByGridOrderByRecordedAtDesc(g);
                if (latest.isPresent()) current += latest.get().getConsumptionKw();
                peak = Math.max(peak, maxConsumption(g, dayAgo));
                outages += readings.countOutages(g.getId(), dayAgo, Instant.now());
            }
            // trend: compare the last hour average with the daily average
            double recent = recentHourKw(gIds(z.getId()));
            String trend = recent > current * 1.05 ? "up" : recent < current * 0.95 ? "down" : "stable";
            out.add(new EnergyDtos.ZoneSummaryDto(z.getId(), z.getName(), current, peak, outages, trend));
        }
        return out;
    }

    /** electricityState of a zone for the twin: outage / watch (>85 % of capacity) / ok. */
    public String zonePowerState(UUID zoneId) {
        double capacity = capacityOf(zoneId);
        List<GridReading> latest = grids.findByZoneId(zoneId).stream()
                .map(readings::findTopByGridOrderByRecordedAtDesc)
                .flatMap(o -> o.stream())
                .toList();
        if (latest.isEmpty()) return "ok";
        boolean outage = latest.stream().anyMatch(GridReading::isOutageReported);
        if (outage) return "outage";
        double kw = latest.stream().mapToDouble(GridReading::getConsumptionKw).sum();
        if (capacity > 0 && kw > capacity * 0.85) return "watch";
        return "ok";
    }

    private double capacityOf(UUID zoneId) {
        return grids.findByZoneId(zoneId).stream().mapToDouble(ElectricityGrid::getCapacityKw).sum();
    }

    public List<EnergyDtos.AssetDto> listAssets(String type, String status) {
        return assets.search(type, status).stream().map(EnergyDtos.AssetDto::from).toList();
    }

    public double avgAssetHealth(UUID zoneId) {
        var list = assets.findByZoneId(zoneId);
        if (list.isEmpty()) return 80;
        return list.stream().mapToInt(InfrastructureAsset::getConditionScore).average().orElse(80);
    }

    /* ----------------------------- helpers ------------------------------ */

    private List<UUID> gIds(UUID zoneId) {
        return grids.findByZoneId(zoneId).stream().map(ElectricityGrid::getId).toList();
    }

    private double maxConsumption(ElectricityGrid g, Instant since) {
        return readings.findByGridIdAndRecordedAtBetweenOrderByRecordedAtAsc(g.getId(), since, Instant.now())
                .stream().mapToDouble(GridReading::getConsumptionKw).max().orElse(0);
    }

    private double recentHourKw(List<UUID> gridIds) {
        Instant hourAgo = Instant.now().minusSeconds(3600);
        return gridIds.stream()
                .flatMap(id -> readings.findByGridIdAndRecordedAtBetweenOrderByRecordedAtAsc(id, hourAgo, Instant.now()).stream())
                .mapToDouble(GridReading::getConsumptionKw).sum();
    }

    private static UUID parse(String raw, String field) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new NotFoundException(field + " must be a UUID");
        }
    }
}
