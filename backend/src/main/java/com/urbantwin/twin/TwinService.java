package com.urbantwin.twin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbantwin.city.City;
import com.urbantwin.city.CityRepository;
import com.urbantwin.city.Zone;
import com.urbantwin.city.ZoneRepository;
import com.urbantwin.drainage.DrainageService;
import com.urbantwin.emergency.EmergencyService;
import com.urbantwin.energy.EnergyService;
import com.urbantwin.maintenance.MaintenanceRequestRepository;
import com.urbantwin.traffic.TrafficService;
import com.urbantwin.waste.WasteBin;
import com.urbantwin.waste.WasteBinRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Digital twin state assembly: joins every infrastructure domain into one
 * per-zone snapshot (§7.11). The snapshot is cached in Redis for 30 s; a
 * missing/failed Redis degrades silently to a fresh computation.
 */
@Service
public class TwinService {
    private static final Logger log = LoggerFactory.getLogger(TwinService.class);
    private static final String CACHE_KEY = "twin:state:v1";
    private static final Duration TTL = Duration.ofSeconds(30);

    private final CityRepository cities;
    private final ZoneRepository zones;
    private final TrafficService traffic;
    private final DrainageService drainage;
    private final EnergyService energy;
    private final EmergencyService emergency;
    private final MaintenanceRequestRepository maintenance;
    private final WasteBinRepository bins;
    private final com.urbantwin.reports.ReportService reports;
    private final ObjectMapper json;
    private final StringRedisTemplate redis;
    private final boolean redisConfigured;

    public TwinService(CityRepository cities, ZoneRepository zones, TrafficService traffic,
                       DrainageService drainage, EnergyService energy, EmergencyService emergency,
                       MaintenanceRequestRepository maintenance, WasteBinRepository bins,
                       com.urbantwin.reports.ReportService reports, ObjectMapper json,
                       StringRedisTemplate redis, @Value("${app.redis-enabled:true}") boolean redisConfigured) {
        this.cities = cities;
        this.zones = zones;
        this.traffic = traffic;
        this.drainage = drainage;
        this.energy = energy;
        this.emergency = emergency;
        this.maintenance = maintenance;
        this.bins = bins;
        this.reports = reports;
        this.json = json;
        this.redis = redis;
        this.redisConfigured = redisConfigured;
    }

    public Map<String, Object> state() {
        // 1. attempt Redis read
        Optional<Map<String, Object>> cached = fromCache();
        if (cached.isPresent()) return cached.get();

        Map<String, Object> snapshot = buildSnapshot();

        // 2. best-effort write-back
        toCache(snapshot);
        return snapshot;
    }

    /* ---------------------------- internals ----------------------------- */

    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> fromCache() {
        if (!redisConfigured) return Optional.empty();
        try {
            String raw = redis.opsForValue().get(CACHE_KEY);
            if (raw == null) return Optional.empty();
            return Optional.of((Map<String, Object>) json.readValue(raw, Map.class));
        } catch (Exception e) {
            log.debug("redis cache read skipped: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private void toCache(Map<String, Object> snapshot) {
        if (!redisConfigured) return;
        try {
            redis.opsForValue().set(CACHE_KEY, json.writeValueAsString(snapshot), TTL);
        } catch (Exception e) {
            log.debug("redis cache write skipped: {}", e.getMessage());
        }
    }

    Map<String, Object> buildSnapshot() {
        // refresh flood scores once per snapshot so all consumers see the same numbers
        drainage.waterloggingPredictions();

        List<WasteBin> allBins = bins.findAll();
        City city = cities.findAll().stream().findFirst().orElse(null);
        Map<String, Object> state = new java.util.LinkedHashMap<>();
        state.put("city", city == null
                ? Map.of("id", "", "name", "unseeded")
                : Map.of("id", city.getId().toString(), "name", city.getName()));

        List<Map<String, Object>> zoneStates = new ArrayList<>();
        double congestionSum = 0;
        int worstZones = 0;
        for (Zone z : zones.findAll()) {
            double congestion = traffic.zoneCongestion(z.getId());
            double flood = drainage.zoneFloodScore(z.getId());
            String power = energy.zonePowerState(z.getId());
            long overfull = allBins.stream()
                    .filter(b -> b.getZone() != null && b.getZone().getId().equals(z.getId()) && b.getFillLevelPct() >= 85)
                    .count();
            int assetHealth = (int) Math.round(energy.avgAssetHealth(z.getId()));
            Map<String, Object> zs = new java.util.LinkedHashMap<>();
            zs.put("zoneId", z.getId().toString());
            zs.put("name", z.getName());
            zs.put("congestion", round3(congestion));
            zs.put("floodRisk", round3(flood));
            zs.put("powerState", power);
            zs.put("binsOverfull", overfull);
            zs.put("assetHealth", assetHealth);
            zoneStates.add(zs);

            congestionSum += congestion;
            if (congestion >= 0.66 || flood >= 0.5) worstZones++;
        }
        state.put("zones", zoneStates);

        Map<String, Object> totals = new java.util.LinkedHashMap<>();
        totals.put("activeIncidents", emergency.activeIncidentCount());
        totals.put("openMaintenance", maintenance.countByStatus("open")
                + maintenance.countByStatus("assigned") + maintenance.countByStatus("in_progress"));
        totals.put("binsOverfull", allBins.stream().filter(b -> b.getFillLevelPct() >= 85).count());
        double avgCongestion = zoneStates.isEmpty() ? 0 : congestionSum / zoneStates.size();
        totals.put("avgCongestion", round3(avgCongestion));
        String networkStatus = zoneStates.size() <= 1 ? "ok" : worstZones > zoneStates.size() / 2 ? "critical" : worstZones > 0 ? "degraded" : "ok";
        totals.put("networkStatus", networkStatus);
        totals.put("dataFreshness", "30s");
        state.put("totals", totals);
        state.put("updatedAt", Instant.now().toString());
        return state;
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    /** exposed for tests */
    public static String cacheKey() { return CACHE_KEY; }
}
