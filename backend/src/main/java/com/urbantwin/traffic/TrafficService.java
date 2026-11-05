package com.urbantwin.traffic;

import com.urbantwin.city.Zone;
import com.urbantwin.city.ZoneRepository;
import com.urbantwin.common.GlobalExceptionHandler.NotFoundException;
import com.urbantwin.ingest.TwinPublisher;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.urbantwin.traffic.CongestionHeuristic.indexOf;

/**
 * Traffic telemetry ingest, per-zone live firehose and short-horizon congestion
 * prediction (ML service first, CongestionHeuristic fallback).
 */
@Service
@Transactional(readOnly = true)
public class TrafficService {

    private final TrafficSensorRepository sensors;
    private final TrafficReadingRepository readings;
    private final ZoneRepository zones;
    private final TwinPublisher publisher;
    private final com.urbantwin.ml.MlClient ml;

    public TrafficService(TrafficSensorRepository sensors, TrafficReadingRepository readings,
                          ZoneRepository zones, TwinPublisher publisher, com.urbantwin.ml.MlClient ml) {
        this.sensors = sensors;
        this.readings = readings;
        this.zones = zones;
        this.publisher = publisher;
        this.ml = ml;
    }

    /* ------------------------------ ingest ------------------------------ */

    @Transactional
    public TrafficDtos.ReadingDto record(TrafficDtos.TrafficPayload payload) {
        UUID sensorId = parseId(payload.sensorId(), "sensorId");
        TrafficSensor sensor = sensors.findById(sensorId)
                .orElseThrow(() -> new NotFoundException("sensor", sensorId));
        Instant when = payload.recordedAt() == null ? Instant.now() : payload.recordedAt();

        TrafficReading r = new TrafficReading();
        r.setSensor(sensor);
        r.setVehicleCount(payload.vehicleCount());
        r.setAvgSpeedKph(payload.avgSpeedKph());
        r.setOccupancyPct(clamp01(payload.occupancyPct()));
        r.setCongestionLevel(indexOf(payload.vehicleCount(), payload.avgSpeedKph(), payload.occupancyPct()));
        r.setRecordedAt(when);
        readings.save(r);

        publisher.publish("TRAFFIC", zoneMeta(sensor), Map.of(
                "sensor", sensor.getName(),
                "speedKph", payload.avgSpeedKph(),
                "congestion", r.getCongestionLevel()));
        return TrafficDtos.ReadingDto.from(r);
    }

    /* ----------------------------- queries ------------------------------ */

    public List<TrafficDtos.SensorDto> listSensors(UUID zoneId, String status) {
        List<TrafficSensor> all = sensors.findAll();
        List<TrafficDtos.SensorDto> out = new ArrayList<>();
        for (TrafficSensor s : all) {
            Map<String, Object> meta = zoneMeta(s);
            if (zoneId != null && !zoneId.toString().equals(meta.get("zoneId"))) continue;
            if (status != null && !status.equals(s.getStatus())) continue;
            TrafficDtos.ReadingDto latest = readings.findFirstBySensorIdOrderByRecordedAtDesc(s.getId())
                    .map(TrafficDtos.ReadingDto::from).orElse(null);
            out.add(TrafficDtos.SensorDto.from(s, latest));
        }
        return out;
    }

    public List<TrafficDtos.ReadingDto> listReadings(UUID sensorId, Instant from, Instant to) {
        Instant safeFrom = from == null ? Instant.now().minusSeconds(86_400) : from;
        Instant safeTo = to == null ? Instant.now() : to;
        if (sensorId == null) return List.of();
        return readings.findBySensorIdAndRecordedAtBetweenOrderByRecordedAtAsc(sensorId, safeFrom, safeTo)
                .stream().map(TrafficDtos.ReadingDto::from).toList();
    }

    /** Per-zone congestion computed from the newest reading of each zone sensor. */
    public List<TrafficDtos.ZoneCongestionDto> firehose() {
        List<TrafficDtos.ZoneCongestionDto> out = new ArrayList<>();
        for (Zone z : zones.findAll()) {
            ZoneStats now = zoneStats(z.getId(), 15 * 60);
            ZoneStats earlier = zoneStats(z.getId(), 60 * 60);
            String trend = now.congestion > earlier.congestion + 0.03 ? "up"
                    : now.congestion < earlier.congestion - 0.03 ? "down" : "stable";
            out.add(new TrafficDtos.ZoneCongestionDto(z.getId(), z.getName(), now.congestion,
                    now.avgSpeed, trend, Instant.now()));
        }
        return out;
    }

    /** Plain (unfiltered) latest congestion of a zone for twin/simulation reuse. */
    public double zoneCongestion(UUID zoneId) {
        return zoneStats(zoneId, 15 * 60).congestion;
    }

    /**
     * Congestion prediction per zone. Calls the ML service with the last-24h history
     * (hourOfDay, dayOfWeek, congestion samples); falls back to the diurnal heuristic.
     * Produces a phase every 15 minutes up to horizonMinutes.
     */
    public List<TrafficDtos.PredictionDto> predict(UUID zoneId, int horizonMinutes) {
        int horizon = Math.max(15, Math.min(180, horizonMinutes));
        List<Zone> targets = zones.findAll().stream()
                .filter(z -> zoneId == null || z.getId().equals(zoneId)).toList();
        List<TrafficDtos.PredictionDto> out = new ArrayList<>();
        for (Zone z : targets) {
            List<double[]> history = history24h(z.getId());
            var mlPreds = ml.predictCongestion(z.getId(), history, horizon);
            if (mlPreds.isPresent() && !mlPreds.get().isEmpty()) {
                for (TrafficDtos.PredictionDto p : mlPreds.get()) {
                    out.add(new TrafficDtos.PredictionDto(z.getId(), p.horizonMinute(),
                            clamp01(p.predictedCongestion()), p.confidence()));
                }
            } else {
                double current = history.isEmpty() ? 0.3 : clamp01(history.get(history.size() - 1)[2]);
                var now = Instant.now().atZone(ZoneOffset.UTC);
                for (int m = 15; m <= horizon; m += 15) {
                    out.add(new TrafficDtos.PredictionDto(
                            z.getId(), m,
                            CongestionHeuristic.predicted(current, now.getHour(), now.getDayOfWeek().getValue(), m),
                            0.72));
                }
            }
        }
        return out;
    }

    /* ------------------------------ helpers ----------------------------- */

    private record ZoneStats(double congestion, double avgSpeed) {}

    private ZoneStats zoneStats(UUID zoneId, int windowSeconds) {
        double congestionAcc = 0, speedAcc = 0;
        int n = 0;
        Instant cutoff = Instant.now().minusSeconds(Math.max(windowSeconds, 90));
        for (TrafficSensor s : sensors.findByRoadZoneId(zoneId)) {
            var latest = readings.findFirstBySensorIdOrderByRecordedAtDesc(s.getId());
            if (latest.isEmpty()) continue;
            TrafficReading r = latest.get();
            if (r.getRecordedAt().isAfter(cutoff)) {
                congestionAcc += r.getCongestionLevel();
                speedAcc += r.getAvgSpeedKph();
                n++;
            }
        }
        return n == 0 ? new ZoneStats(0, 45) : new ZoneStats(clamp01(congestionAcc / n), speedAcc / n);
    }

    /** Last 24 h of zone congestion as [hourOfDay, dayOfWeek, level] samples for the ML model. */
    List<double[]> history24h(UUID zoneId) {
        Instant from = Instant.now().minusSeconds(24 * 3600);
        List<double[]> out = new ArrayList<>();
        for (TrafficSensor s : sensors.findByRoadZoneId(zoneId)) {
            for (TrafficReading r : readings.findBySensorIdAndRecordedAtBetweenOrderByRecordedAtAsc(s.getId(), from, Instant.now())) {
                var t = r.getRecordedAt().atZone(ZoneOffset.UTC);
                out.add(new double[]{t.getHour(), t.getDayOfWeek().getValue(), r.getCongestionLevel()});
            }
        }
        // subsample to at most ~400 points for a snappy ML round-trip
        if (out.size() > 400) {
            List<double[]> sub = new ArrayList<>(400);
            int step = out.size() / 400;
            for (int i = 0; i < 400; i++) sub.add(out.get(i * step));
            return sub;
        }
        return out;
    }

    /** zone metadata for the twin publisher (empty map → city-wide event). */
    Map<String, Object> zoneMeta(TrafficSensor s) {
        Map<String, Object> m = new HashMap<>();
        if (s.getRoad() != null && s.getRoad().getZone() != null) {
            m.put("zoneId", s.getRoad().getZone().getId().toString());
            m.put("zoneName", s.getRoad().getZone().getName());
        }
        return m;
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static UUID parseId(String raw, String field) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new NotFoundException(field + " must be a UUID");
        }
    }
}
