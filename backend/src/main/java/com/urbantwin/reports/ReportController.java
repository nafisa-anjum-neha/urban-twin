package com.urbantwin.reports;

import com.urbantwin.common.GlobalExceptionHandler.BadRequestException;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Citizen reports (contract §7.9). */
@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "reports")
public class ReportController {

    public record CreateReport(String category, String description, double lat, double lon,
                               String reporterName, UUID zoneId) {}

    public record StatusRequest(String status) {}

    private static final List<String> CATEGORIES = List.of(
            "pothole", "leak", "lighting", "garbage", "flooding", "other");
    private static final List<String> STATUSES = List.of(
            "submitted", "acknowledged", "in_progress", "resolved");

    private final ReportService service;

    ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping
    public List<CitizenReport> list(@RequestParam(required = false) String status,
                                    @RequestParam(required = false) String category) {
        return service.list(status, category);
    }

    @PostMapping
    public CitizenReport create(@RequestBody CreateReport req) {
        if (!CATEGORIES.contains(req.category())) {
            throw new BadRequestException("category must be one of " + CATEGORIES);
        }
        if (req.description() == null || req.description().isBlank()) {
            throw new BadRequestException("description is required");
        }
        return service.create(req.category(), req.description(), req.lat(), req.lon(),
                req.reporterName(), req.zoneId());
    }

    @PostMapping("/{id}/upvote")
    public CitizenReport upvote(@PathVariable UUID id) {
        return service.upvote(id);
    }

    @PatchMapping("/{id}/status")
    public CitizenReport patch(@PathVariable UUID id, @RequestBody StatusRequest req) {
        if (req.status() == null || !STATUSES.contains(req.status())) {
            throw new BadRequestException("status must be one of " + STATUSES);
        }
        return service.patchStatus(id, req.status());
    }
}
