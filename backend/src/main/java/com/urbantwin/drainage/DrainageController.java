package com.urbantwin.drainage;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Drainage telemetry + waterlogging predictions (contract §7.4). */
@RestController
@RequestMapping("/api/v1/drainage")
@Tag(name = "drainage")
public class DrainageController {

    private final DrainageService service;

    DrainageController(DrainageService service) {
        this.service = service;
    }

    @GetMapping("/networks")
    public List<DrainageDtos.NetworkDto> networks(@RequestParam(required = false) UUID zoneId) {
        return service.listNetworks(zoneId);
    }

    @GetMapping("/sensors")
    public List<DrainageDtos.SensorDto> sensors(@RequestParam(required = false) UUID networkId,
                                                @RequestParam(required = false) String status) {
        return service.listSensors(networkId, status);
    }

    @GetMapping("/readings")
    public List<DrainageDtos.WaterReadingDto> readings(@RequestParam UUID sensorId,
                                                       @RequestParam(required = false) Instant from,
                                                       @RequestParam(required = false) Instant to) {
        return service.listReadings(sensorId, from, to);
    }

    @PostMapping("/readings")
    public DrainageDtos.WaterReadingDto record(@RequestBody DrainageDtos.WaterPayload payload) {
        return service.record(payload);
    }

    @GetMapping("/waterlogging/predictions")
    public List<DrainageDtos.PredictionDto> predictions() {
        return service.waterloggingPredictions();
    }
}
