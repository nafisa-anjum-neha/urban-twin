package com.urbantwin.simulation;

import com.urbantwin.common.GlobalExceptionHandler;
import com.urbantwin.common.PageEnvelope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Cascade simulator API — the flagship cross-system model (contract §7.10). */
@RestController
@RequestMapping("/api/v1/simulation")
@Tag(name = "simulation", description = "Cross-infrastructure cascade simulator")
public class SimulationController {

    private static final List<String> SCENARIOS = List.of(
            "rainfall", "substation_outage", "water_main_burst", "sensor_failure");

    private final SimulationService service;

    public SimulationController(SimulationService service) {
        this.service = service;
    }

    public record RunRequest(String scenario, String name, Map<String, Object> params) {}

    @PostMapping("/run")
    @Operation(summary = "Run a cascade simulation")
    public ResponseEntity<CascadeDtos.RunResult> run(@RequestBody RunRequest req) {
        if (req.scenario() == null || !SCENARIOS.contains(req.scenario())) {
            throw new GlobalExceptionHandler.BadRequestException("scenario must be one of " + SCENARIOS);
        }
        return ResponseEntity.ok(service.run(req.name(), req.scenario(), req.params()));
    }

    @GetMapping("/runs")
    @Operation(summary = "Recent simulation runs")
    public List<CascadeDtos.RunResult> runs(@RequestParam(required = false) String scenario,
                                            @RequestParam(defaultValue = "20") int limit) {
        return service.history(scenario, limit);
    }

    @GetMapping("/runs/{id}")
    @Operation(summary = "Full result of one run")
    public CascadeDtos.RunResult run(@PathVariable java.util.UUID id) {
        return service.get(id);
    }
}
