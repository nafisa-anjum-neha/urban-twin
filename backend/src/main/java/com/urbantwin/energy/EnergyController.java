package com.urbantwin.energy;

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

/** Electricity grids, meters and infrastructure assets (contract §7.5). */
@RestController
@RequestMapping("/api/v1/energy")
@Tag(name = "energy")
public class EnergyController {

    private final EnergyService service;

    EnergyController(EnergyService service) {
        this.service = service;
    }

    @GetMapping("/grids")
    public List<EnergyDtos.GridDto> grids(@RequestParam(required = false) UUID zoneId) {
        return service.listGrids(zoneId);
    }

    @GetMapping("/readings")
    public List<EnergyDtos.GridReadingDto> readings(@RequestParam UUID gridId,
                                                    @RequestParam(required = false) Instant from,
                                                    @RequestParam(required = false) Instant to) {
        return service.listReadings(gridId, from, to);
    }

    @PostMapping("/readings")
    public EnergyDtos.GridReadingDto record(@RequestBody EnergyDtos.EnergyPayload payload) {
        return service.record(payload);
    }

    @GetMapping("/summary")
    public List<EnergyDtos.ZoneSummaryDto> summary() {
        return service.summary();
    }

    @GetMapping("/assets")
    public List<EnergyDtos.AssetDto> assets(@RequestParam(required = false) String type,
                                            @RequestParam(required = false) String status) {
        return service.listAssets(type, status);
    }
}
