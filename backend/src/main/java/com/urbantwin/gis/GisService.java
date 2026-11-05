package com.urbantwin.gis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbantwin.city.Zone;
import com.urbantwin.city.ZoneRepository;
import com.urbantwin.drainage.DrainageSensorRepository;
import com.urbantwin.drainage.DrainageSensor;
import com.urbantwin.drainage.WaterLevelReadingRepository;
import com.urbantwin.emergency.EmergencyIncidentRepository;
import com.urbantwin.emergency.EmergencyVehicleRepository;
import com.urbantwin.energy.ElectricityGridRepository;
import com.urbantwin.roads.RoadRepository;
import com.urbantwin.traffic.TrafficReadingRepository;
import com.urbantwin.traffic.TrafficSensorRepository;
import com.urbantwin.waste.WasteBinRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GeoJSON FeatureCollection builder for every map layer (contract §7.8) plus the
 * /gis/heatmap endpoint. Coordinates are serialised [lon, lat] (GeoJSON order);
 * the frontend parses these strings directly.
 */
@Service
public class GisService {

    private final ZoneRepository zones;
    private final RoadRepository roads;
    private final TrafficSensorRepository trafficSensors;
    private final TrafficReadingRepository trafficReadings;
    private final DrainageSensorRepository drainageSensors;
    private final WaterLevelReadingRepository waterReadings;
    private final WasteBinRepository bins;
    private final com.urbantwin.energy.InfrastructureAssetRepository assets;
    private final EmergencyIncidentRepository incidents;
    private final EmergencyVehicleRepository vehicles;
    private final ElectricityGridRepository grids;
    private final ObjectMapper json;

    public GisService(ZoneRepository zones, RoadRepository roads,
                      TrafficSensorRepository trafficSensors, TrafficReadingRepository trafficReadings,
                      DrainageSensorRepository drainageSensors, WaterLevelReadingRepository waterReadings,
                      WasteBinRepository bins,
                      com.urbantwin.energy.InfrastructureAssetRepository assets,
                      EmergencyIncidentRepository incidents, EmergencyVehicleRepository vehicles,
                      ElectricityGridRepository grids, ObjectMapper json) {
        this.zones = zones;
        this.roads = roads;
        this.trafficSensors = trafficSensors;
        this.trafficReadings = trafficReadings;
        this.drainageSensors = drainageSensors;
        this.waterReadings = waterReadings;
        this.bins = bins;
        this.assets = assets;
        this.incidents = incidents;
        this.vehicles = vehicles;
        this.grids = grids;
        this.json = json;
    }

    public String layer(String name) {
        return switch (name) {
            case "zones" -> pointFeatureCollection(zoneFeatures());
            case "roads" -> pointFeatureCollection(roadFeatures());
            case "traffic-sensors" -> pointFeatureCollection(trafficPointFeatures());
            case "drainage-sensors" -> pointFeatureCollection(drainagePointFeatures());
            case "bins" -> pointFeatureCollection(binPointFeatures());
            case "assets" -> pointFeatureCollection(assetPointFeatures());
            case "incidents" -> pointFeatureCollection(incidentPointFeatures());
            case "emergency-vehicles" -> pointFeatureCollection(vehiclePointFeatures());
            case "energy-grids" -> gridFeatureCollection();
            default -> throw new IllegalArgumentException("unknown layer: " + name);
        };
    }

    public List<Map<String, Object>> heatmap(String layer) {
        List<Map<String, Object>> out = new ArrayList<>();
        switch (layer == null ? "" : layer) {
            case "traffic" -> {
                for (var s : trafficSensors.findAll()) {
                    if (s.getLocation() == null) continue;
                    double weight = trafficReadings.findFirstBySensorIdOrderByRecordedAtDesc(s.getId())
                            .map(r -> Math.round(r.getCongestionLevel() * 100.0) / 100.0).orElse(0.15);
                    out.add(point(s.getLocation(), weight));
                }
            }
            case "drainage" -> {
                for (DrainageSensor s : drainageSensors.findAll()) {
                    if (s.getLocation() == null) continue;
                    double weight = waterReadings.findTopBySensorOrderByRecordedAtDesc(s)
                            .map(r -> Math.round(Math.min(1, r.getWaterLevelCm() / 150.0) * 100.0) / 100.0)
                            .orElse(0.1);
                    out.add(point(s.getLocation(), weight));
                }
            }
            case "energy" -> {
                for (var g : grids.findAll()) {
                    if (g.getZone() == null || g.getZone().getBoundary() == null) continue;
                    Point c = g.getZone().getBoundary().getCentroid();
                    out.add(point(c, Math.max(0.2, Math.min(1, g.getCapacityKw() / 50_000.0))));
                }
            }
            default -> { /* empty list */ }
        }
        return out;
    }

    /* ----------------------------- layers ------------------------------- */

    private List<Map<String, Object>> zoneFeatures() {
        List<Map<String, Object>> features = new ArrayList<>();
        for (Zone z : zones.findAll()) {
            features.add(feature(z.getGeojson() != null ? z.getGeojson() : geoJson(z.getBoundary()),
                    kv("id", z.getId(), "name", z.getName(), "zoneType", z.getZoneType())));
        }
        return features;
    }

    private List<Map<String, Object>> roadFeatures() {
        List<Map<String, Object>> features = new ArrayList<>();
        for (var r : roads.findAll()) {
            String status = r.getConditionScore() >= 70 ? "good" : r.getConditionScore() >= 45 ? "fair" : "poor";
            features.add(feature(r.getGeojson() != null ? r.getGeojson() : geoJson(r.getPath()),
                    kv("id", r.getId(), "name", r.getName(), "roadClass", r.getRoadClass(),
                            "status", status, "conditionScore", r.getConditionScore())));
        }
        return features;
    }

    private List<Map<String, Object>> trafficPointFeatures() {
        List<Map<String, Object>> features = new ArrayList<>();
        for (var s : trafficSensors.findAll()) {
            features.add(feature(pointJson(s.getLocation()),
                    kv("id", s.getId(), "name", s.getName(), "status", s.getStatus())));
        }
        return features;
    }

    private List<Map<String, Object>> drainagePointFeatures() {
        List<Map<String, Object>> features = new ArrayList<>();
        for (DrainageSensor s : drainageSensors.findAll()) {
            String status = s.getStatus();
            features.add(feature(pointJson(s.getLocation()),
                    kv("id", s.getId(), "name", s.getName(), "status",
                            "faulty".equals(status) ? "faulty" : status)));
        }
        return features;
    }

    private List<Map<String, Object>> binPointFeatures() {
        List<Map<String, Object>> features = new ArrayList<>();
        int fill = 0;
        for (var b : bins.findAll()) {
            fill = (int) Math.round(b.getFillLevelPct());
            features.add(feature(pointJson(b.getLocation()),
                    kv("id", b.getId(), "name", b.getName(),
                            "status", fill >= 85 ? "critical" : fill >= 60 ? "warning" : "normal",
                            "fillLevelPct", (Object) fill)));
        }
        return features;
    }

    private List<Map<String, Object>> assetPointFeatures() {
        List<Map<String, Object>> features = new ArrayList<>();
        for (var a : assets.findAll()) {
            features.add(feature(pointJson(a.getLocation()),
                    kv("id", a.getId(), "name", a.getName(), "status", a.getStatus(),
                            "assetType", a.getAssetType())));
        }
        return features;
    }

    private List<Map<String, Object>> incidentPointFeatures() {
        List<Map<String, Object>> features = new ArrayList<>();
        for (var i : incidents.findAll()) {
            features.add(feature(pointJson(i.getLocation()),
                    kv("id", i.getId(), "name", i.getName(), "status", i.getStatus(),
                            "incidentType", i.getIncidentType(), "severity", i.getSeverity())));
        }
        return features;
    }

    private List<Map<String, Object>> vehiclePointFeatures() {
        List<Map<String, Object>> features = new ArrayList<>();
        for (var v : vehicles.findAll()) {
            features.add(feature(pointJson(v.getStation()),
                    kv("id", v.getId(), "name", v.getName(), "status", v.getStatus(),
                            "vehicleType", v.getVehicleType())));
        }
        return features;
    }

    private String gridFeatureCollection() {
        List<Map<String, Object>> features = new ArrayList<>();
        for (var g : grids.findAll()) {
            Point c = g.getZone() != null && g.getZone().getBoundary() != null
                    ? g.getZone().getBoundary().getCentroid() : null;
            features.add(feature(pointJson(c),
                    kv("id", g.getId(), "name", g.getName(), "status", "substation",
                            "capacityKw", (Object) g.getCapacityKw())));
        }
        return pointFeatureCollection(features);
    }

    /* ------------------------- serialisation ---------------------------- */

    private Map<String, Object> feature(String geometryJson, Map<String, Object> props) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("type", "Feature");
        f.put("geometry", geometryJson);
        f.put("properties", props);
        return f;
    }

    private Map<String, Object> kv(Object... pairs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) m.put((String) pairs[i], pairs[i + 1]);
        return m;
    }

    private String pointFeatureCollection(List<Map<String, Object>> features) {
        return serialise(Map.of("type", "FeatureCollection", "features", features));
    }

    private String serialise(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (Exception e) {
            return "{\"type\":\"FeatureCollection\",\"features\":[]}";
        }
    }

    private static String pointJson(Point p) {
        if (p == null) return null;
        return "{\"type\":\"Point\",\"coordinates\":[" + p.getX() + "," + p.getY() + "]}";
    }

    private static Map<String, Object> point(Point p, double weight) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lat", round(p.getY()));
        m.put("lon", round(p.getX()));
        m.put("weight", weight);
        return m;
    }

    /** Point / LineString / Polygon → GeoJSON fragment string ([lon, lat] order). */
    static String geoJson(Geometry g) {
        if (g == null) return null;
        if (g instanceof Point p) return pointJson(p);
        StringBuilder sb = new StringBuilder();
        Coordinate[] coords = g.getCoordinates();
        switch (g.getGeometryType()) {
            case "LineString" -> {
                sb.append("{\"type\":\"LineString\",\"coordinates\":[");
                appendLooped(sb, coords);
                sb.append("]}");
            }
            case "Polygon" -> {
                sb.append("{\"type\":\"Polygon\",\"coordinates\":[[");
                appendLooped(sb, coords);
                sb.append("]]}");
            }
            default -> sb.append("{\"type\":\"GeometryCollection\",\"geometries\":[]}");
        }
        return sb.toString();
    }

    private static void appendLooped(StringBuilder sb, Coordinate[] coords) {
        for (int i = 0; i < coords.length; i++) {
            if (i > 0) sb.append(',');
            sb.append('[').append(coords[i].getX()).append(',').append(coords[i].getY()).append(']');
        }
    }

    private static double round(double v) {
        return Math.round(v * 10_000.0) / 10_000.0;
    }
}
