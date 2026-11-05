package com.urbantwin.waste;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Waste bins + collection route optimisation (contract §7.6). */
@RestController
@RequestMapping("/api/v1/waste")
@Tag(name = "waste")
public class WasteController {

    public record OptimizeRequest(UUID zoneId, String vehicleId) {}

    private final WasteService service;

    WasteController(WasteService service) {
        this.service = service;
    }

    @GetMapping("/bins")
    public List<WasteDtos.BinDto> bins(@RequestParam(required = false) UUID zoneId,
                                       @RequestParam(required = false) Double minFill) {
        return service.listBins(zoneId, minFill);
    }

    @PostMapping("/bins/{id}/collect")
    public WasteDtos.BinDto collect(@PathVariable UUID id) {
        return service.collect(id);
    }

    @PostMapping("/routes/optimize")
    public WasteDtos.RouteDto optimize(@RequestBody OptimizeRequest req) {
        return service.optimize(req.zoneId(), req.vehicleId());
    }

    @GetMapping("/routes")
    public List<WasteDtos.RouteDto> routes(@RequestParam(required = false) String status) {
        return service.listRoutes(status);
    }
}
