package com.urbantwin.ml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Thin client for the Python ML microservice (contract §8). Every call has a hard
 * 1.5 s timeout and degrades to Optional.empty() so the pure-Java heuristics
 * (CongestionHeuristic / FloodHeuristic) take over transparently.
 */
@Component
public class MlClient {
    private static final Logger log = LoggerFactory.getLogger(MlClient.class);
    private static final Duration TIMEOUT = Duration.ofMillis(1500);

    private final WebClient web;
    private final boolean enabled;

    public MlClient(@Value("${app.ml.base-url}") String baseUrl) {
        this.web = WebClient.builder().baseUrl(baseUrl).build();
        this.enabled = baseUrl != null && !baseUrl.isBlank();
    }

    /** One prediction phase per 15-minute step of the horizon, per zone. */
    public Optional<List<com.urbantwin.traffic.TrafficDtos.PredictionDto>> predictCongestion(
            UUID zoneId, List<double[]> history, int horizonMinutes) {
        if (!enabled) return Optional.empty();
        Map<String, Object> body = Map.of(
                "zoneId", zoneId.toString(),
                "history", history,
                "horizonMinutes", horizonMinutes);
        return tryPost("/ml/v1/predict/congestion", body, json -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> predictions =
                    (List<Map<String, Object>>) json.get("predictions");
            return predictions.stream()
                    .map(p -> new com.urbantwin.traffic.TrafficDtos.PredictionDto(
                            zoneId,
                            (int) num(p.get("horizonMinute")),
                            num(p.get("congestion")),
                            num(p.getOrDefault("confidence", 0.75))))
                    .toList();
        });
    }

    /**
     * Returns a single coarse drainage prediction: risk band, projected peak water
     * level (cm) and model confidence — DrainageService enriches impacted roads.
     */
    public Optional<List<com.urbantwin.drainage.DrainageDtos.PredictionDto>> predictWaterlogging(
            UUID zoneId, List<double[]> stationReadings, double networkCapacity) {
        if (!enabled) return Optional.empty();
        Map<String, Object> body = Map.of(
                "zoneId", zoneId.toString(),
                "stationReadings", stationReadings,
                "networkCapacityM3h", networkCapacity);
        return tryPost("/ml/v1/predict/waterlogging", body, json -> {
            String risk = String.valueOf(json.getOrDefault("risk", "low"));
            double maxLevel = num(json.getOrDefault("maxLevelCm", 0));
            double confidence = num(json.getOrDefault("confidence", 0.5));
            return List.of(new com.urbantwin.drainage.DrainageDtos.PredictionDto(
                    zoneId, risk, maxLevel, List.of(), confidence));
        });
    }

    private <T> Optional<T> tryPost(String path, Object body,
                                    Function<Map<String, Object>, T> mapper) {
        try {
            Map<String, Object> json = web.post().uri(path)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(TIMEOUT)
                    .block();
            if (json == null) return Optional.empty();
            return Optional.of(mapper.apply(json));
        } catch (Exception e) {
            log.debug("ML service unavailable at {} — falling back to heuristics: {}",
                    path, e.getMessage());
            return Optional.empty();
        }
    }

    private static double num(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return 0; }
    }
}
