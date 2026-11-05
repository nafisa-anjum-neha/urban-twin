package com.urbantwin.simulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbantwin.common.GlobalExceptionHandler.NotFoundException;
import com.urbantwin.ingest.TwinPublisher;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/** Runs, persists and replays cascade simulations (contract §7.10). */
@Service
public class SimulationService {

    private final CascadeEngine engine;
    private final SimulationRunRepository runs;
    private final ObjectMapper json;
    private final KafkaTemplate<String, Object> kafka;
    private final SimpMessagingTemplate stomp;

    public SimulationService(CascadeEngine engine, SimulationRunRepository runs, ObjectMapper json,
                             KafkaTemplate<String, Object> kafka, SimpMessagingTemplate stomp) {
        this.engine = engine;
        this.runs = runs;
        this.json = json;
        this.kafka = kafka;
        this.stomp = stomp;
    }

    /** Validates the Scenario, executes the engine, persists and streams the result. */
    public CascadeDtos.RunResult run(String name, String scenario, Map<String, Object> params) {
        Instant started = Instant.now();
        CascadeDtos.RunResult result = engine.run(scenario, name, params == null ? Map.of() : params);
        String resultJson;
        Map<String, Object> paramsJson;
        try {
            resultJson = json.writeValueAsString(result);
            paramsJson = params == null ? Map.of() : params;
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialise simulation result", e);
        }

        SimulationRun persisted = new SimulationRun();
        persisted.setName(name == null || name.isBlank() ? scenario + " run" : name);
        persisted.setScenario(scenario);
        persisted.setInputParamsJson(serializeQuietly(paramsJson));
        persisted.setResultJson(resultJson);
        persisted.setStatus("completed");
        persisted.setStartedAt(started);
        persisted.setCompletedAt(Instant.now());
        SimulationRun saved = runs.save(persisted);

        var completed = new CascadeDtos.RunResult(saved.getId(), result.scenario(), result.status(),
                result.timelineSeconds(), result.cascade(), result.impactSummary(),
                saved.getName(), saved.getStartedAt().toString(), saved.getCompletedAt().toString());

        stream(saved.getId().toString(), completed);
        return completed;
    }

    public List<CascadeDtos.RunResult> history(String scenario, int limit) {
        return runs.findAll().stream()
                .filter(r -> scenario == null || scenario.equals(r.getScenario()))
                .sorted((a, b) -> b.getStartedAt().compareTo(a.getStartedAt()))
                .limit(Math.max(1, Math.min(50, limit)))
                .map(this::summary).collect(Collectors.toList());
    }

    public CascadeDtos.RunResult get(UUID id) {
        SimulationRun r = runs.findById(id).orElseThrow(() -> new NotFoundException("simulation run", id));
        try {
            var full = json.readValue(r.getResultJson(), CascadeDtos.RunResult.class);
            return new CascadeDtos.RunResult(r.getId(), full.scenario(), r.getStatus(),
                    full.timelineSeconds(), full.cascade(), full.impactSummary(),
                    r.getName(), r.getStartedAt().toString(), String.valueOf(r.getCompletedAt()));
        } catch (Exception e) {
            return summary(r);
        }
    }

    /* ----------------------------- helpers ------------------------------ */

    public CascadeDtos.RunResult summary(SimulationRun r) {
        try {
            var full = json.readValue(r.getResultJson(), CascadeDtos.RunResult.class);
            return new CascadeDtos.RunResult(r.getId(), r.getScenario(), r.getStatus(),
                    full.timelineSeconds(), null, full.impactSummary(),
                    r.getName(), r.getStartedAt().toString(), String.valueOf(r.getCompletedAt()));
        } catch (Exception e) {
            return new CascadeDtos.RunResult(r.getId(), r.getScenario(), r.getStatus(), 0,
                    null, null, r.getName(), r.getStartedAt().toString(), String.valueOf(r.getCompletedAt()));
        }
    }

    private void stream(String simId, CascadeDtos.RunResult result) {
        try {
            kafka.send("simulation.events", simId, result);
        } catch (Exception ignored) {
            // best effort streaming
        }
        try {
            stomp.convertAndSend("/topic/simulation/" + simId, result);
        } catch (Exception ignored) {
        }
    }

    private String serializeQuietly(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }
}
