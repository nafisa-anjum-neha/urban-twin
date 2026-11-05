package com.urbantwin.gis;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GeoJSON layer catalogue and heat-map weights (contract §7.8). */
@RestController
@RequestMapping("/api/v1/gis")
@Tag(name = "gis")
public class GisController {

    private static final List<String> LAYERS = Arrays.asList(
            "zones", "roads", "traffic-sensors", "drainage-sensors", "bins", "assets",
            "incidents", "emergency-vehicles", "energy-grids");

    private final GisService service;

    GisController(GisService service) {
        this.service = service;
    }

    @GetMapping("/layers/{layer}")
    public ResponseEntity<String> layer(@PathVariable String layer) {
        if (!LAYERS.contains(layer)) {
            return ResponseEntity.badRequest().body(
                    "{\"timestamp\":\"\"," + "\"status\":400,\"error\":\"Bad Request\",\"message\":\"unknown layer " + layer
                            + "\",\"path\":\"/api/v1/gis/layers/" + layer + "\"}");
        }
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(service.layer(layer));
    }

    @GetMapping("/heatmap")
    public List<Map<String, Object>> heatmap(@RequestParam String layer) {
        return service.heatmap(layer);
    }
}
