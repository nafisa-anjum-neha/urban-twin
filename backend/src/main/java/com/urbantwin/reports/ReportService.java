package com.urbantwin.reports;

import com.urbantwin.city.Zone;
import com.urbantwin.city.ZoneRepository;
import com.urbantwin.common.GeoSupport;
import com.urbantwin.drainage.DrainageService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;

/** Citizen report intake, upvotes and municipal triage (§7.9). */
@Service
public class ReportService {

    private final CitizenReportRepository reports;
    private final ZoneRepository zones;
    private final DrainageService drainage;

    public ReportService(CitizenReportRepository reports, ZoneRepository zones, DrainageService drainage) {
        this.reports = reports;
        this.zones = zones;
        this.drainage = drainage;
    }

    public List<CitizenReport> list(String status, String category) {
        return reports.findAll().stream()
                .sorted((a, b) -> Integer.compare(b.getUpvotes(), a.getUpvotes()))
                .filter(r -> status == null || status.equals(r.getStatus()))
                .filter(r -> category == null || category.equals(r.getCategory()))
                .toList();
    }

    /** Creates a report; the zone is auto-detected from the point when not supplied. */
    public CitizenReport create(String category, String description, double lat, double lon,
                                String reporterName, UUID zoneId) {
        CitizenReport r = new CitizenReport();
        r.setCategory(category);
        r.setDescription(description);
        r.setReporterName(reporterName == null || reporterName.isBlank() ? "citizen" : reporterName);
        r.setStatus("submitted");
        r.setUpvotes(0);
        r.setLocation(GeoSupport.point(lat, lon));
        Zone zone = zoneId == null ? nearestZone(lat, lon)
                : zones.findById(zoneId).orElseThrow();
        r.setZone(zone);
        CitizenReport saved = reports.save(r);

        // auto-escalate flooding reports in high-risk zones to the twin feed
        if ("flooding".equals(category) && zone != null && drainage.zoneFloodScore(zone.getId()) > 0.45) {
            r.setStatus("acknowledged");
            saved = reports.save(r);
        }
        return saved;
    }

    public CitizenReport upvote(UUID id) {
        CitizenReport r = reports.findById(id).orElseThrow();
        r.setUpvotes(r.getUpvotes() + 1);
        return reports.save(r);
    }

    public CitizenReport patchStatus(UUID id, String status) {
        CitizenReport r = reports.findById(id).orElseThrow();
        r.setStatus(status);
        return reports.save(r);
    }

    public long countByStatus(String status) {
        return reports.count() == 0 ? 0 : reports.findAll().stream()
                .filter(r -> status.equals(r.getStatus())).count();
    }

    private Zone nearestZone(double lat, double lon) {
        Point p = GeoSupport.point(lat, lon);
        List<Zone> all = zones.findAll();
        if (all.isEmpty()) return null;
        return all.stream().min((a, b) -> Double.compare(
                a.getBoundary() == null ? Double.MAX_VALUE : a.getBoundary().distance(p),
                b.getBoundary() == null ? Double.MAX_VALUE : b.getBoundary().distance(p))).orElse(null);
    }

    /** twin helper */
    public Map<UUID, Long> zoneReportCounts() {
        Map<UUID, Long> m = new java.util.HashMap<>();
        reports.findAll().forEach(r -> {
            if (r.getZone() != null) m.merge(r.getZone().getId(), 1L, Long::sum);
        });
        return m;
    }
}
