package com.urbantwin.drainage;

import com.urbantwin.city.ZoneRepository;
import com.urbantwin.roads.Road;
import com.urbantwin.roads.RoadRepository;
import com.urbantwin.city.Zone;
import com.urbantwin.common.GlobalExceptionHandler.NotFoundException;
import com.urbantwin.ingest.TwinPublisher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.urbantwin.drainage.FloodHeuristic.projectedLevelCm;
import static com.urbantwin.drainage.FloodHeuristic.riskForCm;
import static com.urbantwin.drainage.FloodHeuristic.scoreForCm;

/** Drainage telemetry ingest and waterlogging prediction (ML first, heuristic fallback). */
@Service
@Transactional(readOnly = true)
public class DrainageService {

    private final DrainageNetworkRepository networks;
    private final DrainageSensorRepository sensors;
    private final WaterLevelReadingRepository readings;
    private final RoadRepository roads;
    private final ZoneRepository zones;
    private final TwinPublisher publisher;
    private final com.urbantwin.ml.MlClient ml;

    public DrainageService(DrainageNetworkRepository networks, DrainageSensorRepository sensors,
                           WaterLevelReadingRepository readings, RoadRepository roads,
                           ZoneRepository zones, TwinPublisher publisher, com.urbantwin.ml.MlClient ml) {
        this.networks = networks;
        this.sensors = sensors;
        this.readings = readings;
        this.roads = roads;
        this.zones = zones;
        this.publisher = publisher;
        this.ml = ml;
    }

    /* ------------------------------ ingest ------------------------------ */

    @Transactional
    public DrainageDtos.WaterReadingDto record(DrainageDtos.WaterPayload payload) {
        UUID sensorId = parse(payload.sensorId(), "sensorId");
        DrainageSensor sensor = sensors.findById(sensorId)
                .orElseThrow(() -> new NotFoundException("sensor", sensorId));
        Instant when = payload.recordedAt() == null ? Instant.now() : payload.recordedAt();

        WaterLevelReading r = new WaterLevelReading();
        r.setSensor(sensor);
        r.setWaterLevelCm(payload.waterLevelCm());
        r.setFlowRateLps(payload.flowRateLps());
        r.setRainfallMm(payload.rainfallMm());
        r.setRiskLevel(riskForCm(payload.waterLevelCm()));
        r.setRecordedAt(when);
        readings.save(r);

        // keep the sensor status in sync with the water level
        String newStatus = switch (r.getRiskLevel()) {
            case "severe" -> "critical";
            case "high" -> "warning";
            default -> "normal";
        };
        if (!sensor.getStatus().equals(newStatus) && !"faulty".equals(sensor.getStatus())) {
            sensor.setStatus(newStatus);
            sensors.save(sensor);
        }

        Map<String, Object> meta = sensor.getNetwork() != null && sensor.getNetwork().getZone() != null
                ? Map.of("zoneId", sensor.getNetwork().getZone().getId().toString(),
                         "zoneName", sensor.getNetwork().getZone().getName())
                : Map.of();
        publisher.publish("DRAINAGE", meta, Map.of(
                "sensor", sensor.getName(),
                "waterLevelCm", r.getWaterLevelCm(),
                "rainfallMm", r.getRainfallMm(),
                "risk", r.getRiskLevel()));
        return DrainageDtos.WaterReadingDto.from(r);
    }

    /* ----------------------------- queries ------------------------------ */

    public List<DrainageDtos.NetworkDto> listNetworks(UUID zoneId) {
        return networks.findAll().stream()
                .filter(n -> zoneId == null || (n.getZone() != null && n.getZone().getId().equals(zoneId)))
                .map(DrainageDtos.NetworkDto::from).toList();
    }

    public List<DrainageDtos.SensorDto> listSensors(UUID networkId, String status) {
        return sensors.findAll().stream()
                .filter(s -> networkId == null || (s.getNetwork() != null && s.getNetwork().getId().equals(networkId)))
                .filter(s -> status == null || status.equals(s.getStatus()))
                .map(s -> DrainageDtos.SensorDto.from(s, readings.findTopBySensorOrderByRecordedAtDesc(s)
                        .map(DrainageDtos.WaterReadingDto::from).orElse(null)))
                .toList();
    }

    public List<DrainageDtos.WaterReadingDto> listReadings(UUID sensorId, Instant from, Instant to) {
        if (sensorId == null) return List.of();
        Instant safeFrom = from == null ? Instant.now().minusSeconds(86_400) : from;
        Instant safeTo = to == null ? Instant.now() : to;
        return readings.findBySensorIdAndRecordedAtBetweenOrderByRecordedAtAsc(sensorId, safeFrom, safeTo)
                .stream().map(DrainageDtos.WaterReadingDto::from).toList();
    }

    /**
     * Waterlogging prediction per zone: latest station readings go to the ML service;
     * on failure a physics-style fallback projects the peak level with FloodHeuristic
     * and lists roads likely to flood (worn roads are the most at-risk corridors).
     */
    public List<DrainageDtos.PredictionDto> waterloggingPredictions() {
        List<DrainageDtos.PredictionDto> out = new ArrayList<>();
        for (Zone z : zones.findAll()) {
            var zSensors = sensors.findByNetworkZoneId(z.getId());
            if (zSensors.isEmpty()) continue;

            double maxNow = 0, rainfall = 0, inflow = 0;
            int capacity = 1500;
            List<double[]> series = new ArrayList<>();
            for (DrainageSensor s : zSensors) {
                var latest = readings.findTopBySensorOrderByRecordedAtDesc(s);
                if (latest.isEmpty()) continue;
                WaterLevelReading r = latest.get();
                maxNow = Math.max(maxNow, r.getWaterLevelCm());
                rainfall = Math.max(rainfall, r.getRainfallMm());
                inflow += r.getFlowRateLps();
                series.add(new double[]{r.getWaterLevelCm(), r.getFlowRateLps(), r.getRainfallMm()});
                if (s.getNetwork() != null) capacity = s.getNetwork().getCapacityM3PerHour();
            }

            // ML first, heuristic fallback — both produce (maxLevelCm, confidence)
            double maxLevel = maxNow, confidence = 0.7;
            var mlPred = ml.predictWaterlogging(z.getId(), series, capacity);
            if (mlPred.isPresent() && !mlPred.get().isEmpty()) {
                var p = mlPred.get().get(0);
                maxLevel = Math.max(maxLevel, p.maxLevelCm());
                confidence = p.confidence();
            } else {
                var projected = projectedLevelCm(maxNow, rainfall, 60, capacity, inflow);
                maxLevel = projected;
                confidence = 0.7;
            }

            String risk = riskForCm(maxLevel);
            List<UUID> affected = roads.findByZoneId(z.getId()).stream()
                    .filter(r -> r.getConditionScore() < 60)
                    .limit(6).map(Road::getId).toList();
            if ("low".equals(risk)) affected = List.of();
            out.add(new DrainageDtos.PredictionDto(z.getId(), risk, maxLevel, affected, confidence));
            // expose the numeric score for the twin
            scoreByZone.put(z.getId(), scoreForCm(maxLevel));
        }
        return out;
    }

    /** Latest zone flood score 0..1, refreshed by each waterloggingPredictions() call. */
    public double zoneFloodScore(UUID zoneId) {
        Double s = scoreByZone.get(zoneId);
        return s == null ? 0 : s;
    }

    private final Map<UUID, Double> scoreByZone = new java.util.concurrent.ConcurrentHashMap<>();

    private static UUID parse(String raw, String field) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new NotFoundException(field + " must be a UUID");
        }
    }
}
