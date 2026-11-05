package com.urbantwin.traffic;

import com.urbantwin.common.PageEnvelope;
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

/** Traffic monitoring + prediction endpoints (contract §7.2). */
@RestController
@RequestMapping("/api/v1/traffic")
@Tag(name = "traffic")
public class TrafficController {

    private final TrafficService service;

    TrafficController(TrafficService service) {
        this.service = service;
    }

    @GetMapping("/sensors")
    public List<TrafficDtos.SensorDto> sensors(@RequestParam(required = false) UUID zoneId,
                                               @RequestParam(required = false) String status) {
        return service.listSensors(zoneId, status);
    }

    @GetMapping("/readings")
    public PageEnvelope<TrafficDtos.ReadingDto> readings(@RequestParam UUID sensorId,
                                                         @RequestParam(required = false) Instant from,
                                                         @RequestParam(required = false) Instant to) {
        List<TrafficDtos.ReadingDto> list = service.listReadings(sensorId, from, to);
        return new PageEnvelope<>(list.stream().limit(500).toList(), list.size(), 0, Math.min(500, list.size()));
    }

    @PostMapping("/readings")
    public TrafficDtos.ReadingDto record(@RequestBody TrafficDtos.TrafficPayload payload) {
        return service.record(payload);
    }

    @GetMapping("/congestion/firehose")
    public List<TrafficDtos.ZoneCongestionDto> firehose() {
        return service.firehose();
    }

    @GetMapping("/congestion/prediction")
    public List<TrafficDtos.PredictionDto> prediction(@RequestParam(required = false) UUID zoneId,
                                                      @RequestParam(defaultValue = "30") int horizonMinutes) {
        return service.predict(zoneId, horizonMinutes);
    }
}
