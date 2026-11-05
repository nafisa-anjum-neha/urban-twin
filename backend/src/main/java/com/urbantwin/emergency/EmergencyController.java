package com.urbantwin.emergency;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Emergency incidents, nearest-vehicle dispatch, safe route advisory (contract §7.7). */
@RestController
@RequestMapping("/api/v1/emergency")
@Tag(name = "emergency")
public class EmergencyController {

    public record DispatchRequest(UUID vehicleId) {}

    private final EmergencyService service;

    EmergencyController(EmergencyService service) {
        this.service = service;
    }

    @GetMapping("/incidents")
    public List<EmergencyDtos.IncidentDto> incidents(@RequestParam(required = false) String status,
                                                     @RequestParam(required = false) String type,
                                                     @RequestParam(required = false) Integer minSeverity) {
        return service.listIncidents(status, type, minSeverity);
    }

    @PostMapping("/incidents")
    public EmergencyDtos.IncidentDto create(@RequestBody EmergencyDtos.CreateIncident req) {
        return service.create(req);
    }

    @PostMapping("/incidents/{id}/dispatch")
    public EmergencyDtos.DispatchDto dispatch(@PathVariable UUID id,
                                              @RequestBody(required = false) DispatchRequest req) {
        return service.dispatch(id, req == null ? null : req.vehicleId());
    }

    @PostMapping("/dispatch/{id}/resolve")
    public EmergencyDtos.DispatchDto resolve(@PathVariable UUID id) {
        return service.resolve(id);
    }

    @GetMapping("/vehicles")
    public List<EmergencyDtos.VehicleDto> vehicles(@RequestParam(required = false) String status) {
        return service.listVehicles(status);
    }

    @GetMapping(value = "/route/safest", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> safest(@RequestParam UUID incidentId) {
        return ResponseEntity.ok(service.safestRoute(incidentId));
    }
}
