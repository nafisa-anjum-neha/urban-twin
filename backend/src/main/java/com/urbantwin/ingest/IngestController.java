package com.urbantwin.ingest;

import com.urbantwin.drainage.DrainageDtos.WaterPayload;
import com.urbantwin.drainage.DrainageService;
import com.urbantwin.energy.EnergyDtos;
import com.urbantwin.energy.EnergyDtos.EnergyPayload;
import com.urbantwin.energy.EnergyService;
import com.urbantwin.traffic.TrafficDtos;
import com.urbantwin.traffic.TrafficDtos.TrafficPayload;
import com.urbantwin.traffic.TrafficService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST ingest bridge (contract §7.12): runs the same path as the Kafka topics so
 * scripts/seed.py and quick demos can push live telemetry without Kafka.
 */
@RestController
@RequestMapping("/api/v1/ingest")
@Tag(name = "ingest")
public class IngestController {

    private final TrafficService traffic;
    private final DrainageService drainage;
    private final EnergyService energy;

    IngestController(TrafficService traffic, DrainageService drainage, EnergyService energy) {
        this.traffic = traffic;
        this.drainage = drainage;
        this.energy = energy;
    }

    @PostMapping("/traffic")
    public TrafficDtos.ReadingDto traffic(@RequestBody TrafficPayload payload) {
        return traffic.record(payload);
    }

    @PostMapping("/drainage")
    public com.urbantwin.drainage.DrainageDtos.WaterReadingDto drainage(@RequestBody WaterPayload payload) {
        return drainage.record(payload);
    }

    @PostMapping("/energy")
    public EnergyDtos.GridReadingDto energy(@RequestBody EnergyPayload payload) {
        return energy.record(payload);
    }
}
