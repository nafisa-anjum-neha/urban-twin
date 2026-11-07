package com.urbantwin.common;

import com.urbantwin.city.City;
import com.urbantwin.city.CityRepository;
import com.urbantwin.city.Zone;
import com.urbantwin.city.ZoneRepository;
import com.urbantwin.drainage.DrainageNetwork;
import com.urbantwin.drainage.DrainageNetworkRepository;
import com.urbantwin.drainage.DrainageSensor;
import com.urbantwin.drainage.DrainageSensorRepository;
import com.urbantwin.drainage.FloodHeuristic;
import com.urbantwin.drainage.WaterLevelReading;
import com.urbantwin.drainage.WaterLevelReadingRepository;
import com.urbantwin.emergency.EmergencyIncident;
import com.urbantwin.emergency.EmergencyIncidentRepository;
import com.urbantwin.emergency.EmergencyVehicle;
import com.urbantwin.emergency.EmergencyVehicleRepository;
import com.urbantwin.energy.ElectricityGrid;
import com.urbantwin.energy.ElectricityGridRepository;
import com.urbantwin.energy.GridReading;
import com.urbantwin.energy.GridReadingRepository;
import com.urbantwin.energy.InfrastructureAsset;
import com.urbantwin.energy.InfrastructureAssetRepository;
import com.urbantwin.maintenance.MaintenanceRequest;
import com.urbantwin.maintenance.MaintenanceRequestRepository;
import com.urbantwin.reports.CitizenReport;
import com.urbantwin.reports.CitizenReportRepository;
import com.urbantwin.roads.Intersection;
import com.urbantwin.roads.IntersectionRepository;
import com.urbantwin.roads.Road;
import com.urbantwin.roads.RoadRepository;
import com.urbantwin.traffic.TrafficSensor;
import com.urbantwin.traffic.TrafficSensorRepository;
import com.urbantwin.traffic.TrafficReading;
import com.urbantwin.traffic.TrafficReadingRepository;
import static com.urbantwin.traffic.CongestionHeuristic.diurnalBaseline;
import static com.urbantwin.traffic.CongestionHeuristic.indexOf;
import com.urbantwin.waste.WasteBin;
import com.urbantwin.waste.WasteBinRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the demo city (Dhaka, Bangladesh) with zones, roads, sensors, grids,
 * bins, vehicles and 48 h of diurnal telemetry so the dashboard and the
 * cascade simulator have meaningful data on first boot. Enable with
 * app.seed.enabled.
 */
@Component
public class DataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    record ZoneSeed(String name, String type, double lat, double lon, double radiusKm) {}

    /** Dhaka neighborhoods — the twin's default constellation. */
    private static final List<ZoneSeed> ZONE_SEEDS = List.of(
            new ZoneSeed("Gulshan", "commercial", 23.7925, 90.4125, 1.4),
            new ZoneSeed("Banani", "commercial", 23.7898, 90.4012, 1.3),
            new ZoneSeed("Dhanmondi", "residential", 23.7461, 90.3760, 1.8),
            new ZoneSeed("Mirpur", "mixed", 23.8069, 90.3667, 2.2),
            new ZoneSeed("Uttara", "mixed", 23.8759, 90.3795, 2.4),
            new ZoneSeed("Motijheel", "commercial", 23.7333, 90.4127, 1.5),
            new ZoneSeed("Rampura", "mixed", 23.7679, 90.4248, 1.7),
            new ZoneSeed("Mohammadpur", "residential", 23.7640, 90.3596, 1.9),
            new ZoneSeed("Tejgaon", "industrial", 23.7568, 90.3972, 1.8),
            new ZoneSeed("Kurmitola", "mixed", 23.8550, 90.4010, 1.9));

    private static final String[] ROAD_NAMES = {
            "Progoti Sarani", "Airport Road", "Mirpur Road", "Sonargaon Road",
            "Kazi Nazrul Islam Avenue", "Bangla Motor Road", "Begum Rokeya Sarani",
            "Shahid Tajuddin Ahmed Sarani"};

    private static final String[] ASSET_TYPES = {"streetlight", "water_main", "pump", "transformer", "signal"};
    private static final String[] REPORT_TEXTS = {
            "Streetlight pole leaning after the last evening storm.",
            "Deep pothole cluster on the rickshaw lane after rain.",
            "Open drainage cover overflowing during the downpour.",
            "Garbage van missed pickup for the second day near the bazar.",
            "Waterlogged underpass at Airport Road during the monsoon.",
    };

    private final CityRepository cities;
    private final ZoneRepository zones;
    private final RoadRepository roads;
    private final IntersectionRepository intersections;
    private final TrafficSensorRepository trafficSensors;
    private final TrafficReadingRepository trafficReadings;
    private final DrainageNetworkRepository drainageNetworks;
    private final DrainageSensorRepository drainageSensors;
    private final WaterLevelReadingRepository waterReadings;
    private final ElectricityGridRepository grids;
    private final GridReadingRepository gridReadings;
    private final InfrastructureAssetRepository assets;
    private final MaintenanceRequestRepository maintenance;
    private final WasteBinRepository bins;
    private final EmergencyIncidentRepository incidents;
    private final EmergencyVehicleRepository vehicles;
    private final CitizenReportRepository reports;
    private final boolean enabled;

    public DataSeeder(CityRepository cities, ZoneRepository zones, RoadRepository roads,
                      IntersectionRepository intersections, TrafficSensorRepository trafficSensors,
                      TrafficReadingRepository trafficReadings, DrainageNetworkRepository drainageNetworks,
                      DrainageSensorRepository drainageSensors, WaterLevelReadingRepository waterReadings,
                      ElectricityGridRepository grids, GridReadingRepository gridReadings,
                      InfrastructureAssetRepository assets, MaintenanceRequestRepository maintenance,
                      WasteBinRepository bins, EmergencyIncidentRepository incidents,
                      EmergencyVehicleRepository vehicles, CitizenReportRepository reports,
                      @Value("${app.seed.enabled}") boolean enabled) {
        this.cities = cities;
        this.zones = zones;
        this.roads = roads;
        this.intersections = intersections;
        this.trafficSensors = trafficSensors;
        this.trafficReadings = trafficReadings;
        this.drainageNetworks = drainageNetworks;
        this.drainageSensors = drainageSensors;
        this.waterReadings = waterReadings;
        this.grids = grids;
        this.gridReadings = gridReadings;
        this.assets = assets;
        this.maintenance = maintenance;
        this.bins = bins;
        this.incidents = incidents;
        this.vehicles = vehicles;
        this.reports = reports;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            log.info("DataSeeder disabled (app.seed.enabled=false)");
            return;
        }
        if (cities.count() > 0) {
            log.info("Data seeded already — skipping ({} cities)", cities.count());
            return;
        }
        log.info("Seeding UrbanTwin demo city…");
        City dhaka = new City();
        dhaka.setName("Dhaka");
        dhaka.setSlug("dhaka");
        dhaka.setPopulation(22_500_000);
        cities.save(dhaka);

        Random rng = new Random(7L);
        List<Zone> created = seedZones(dhaka);
        List<Road> createdRoads = seedRoads(created, rng);
        seedIntersections(created, rng);
        seedTraffic(createdRoads, rng);
        seedDrainage(created, rng);
        seedGrids(created, rng);
        seedAssets(created, rng);
        seedMaintenance(createdRoads, rng);
        seedBins(created, rng);
        seedEmergency(created, rng);
        seedReports(created, rng);
        log.info("Seed complete: {} zones, {} roads, traffic/drainage/energy history for 48 h",
                created.size(), createdRoads.size());
    }

    /* --------------------------- seeding steps --------------------------- */

    private List<Zone> seedZones(City city) {
        List<Zone> out = new ArrayList<>();
        for (ZoneSeed s : ZONE_SEEDS) {
            Zone z = new Zone();
            z.setCity(city);
            z.setName(s.name());
            z.setZoneType(s.type());
            var polygon = GeoSupport.boxPolygon(s.lat(), s.lon(), s.radiusKm());
            z.setBoundary(polygon);
            z.setGeojson(polygonGeoJson(polygon));
            out.add(zones.save(z));
        }
        return out;
    }

    private List<Road> seedRoads(List<Zone> zoneList, Random rng) {
        List<Road> out = new ArrayList<>();
        for (Zone z : zoneList) {
            int count = 4 + rng.nextInt(2);
            for (int i = 0; i < count; i++) {
                Road r = new Road();
                r.setZone(z);
                r.setName(z.getName() + " " + ROAD_NAMES[i % ROAD_NAMES.length]);
                r.setRoadClass(i == 0 ? "ar" : i == 1 ? "co" : rng.nextBoolean() ? "lo" : "hw");
                r.setConditionScore(35 + rng.nextInt(60));
                r.setLanes(2 + rng.nextInt(4));
                r.setSpeedLimitKph(30 + rng.nextInt(4) * 10);
                var path = roadPath(z, i, rng);
                r.setPath(path);
                r.setGeojson(lineGeoJson(path));
                out.add(roads.save(r));
            }
        }
        return out;
    }

    private void seedIntersections(List<Zone> zoneList, Random rng) {
        for (Zone z : zoneList) {
            var c = centroid(z);
            for (int i = 0; i < 3; i++) {
                Intersection x = new Intersection();
                x.setZone(z);
                x.setName(z.getName() + " Junction " + (i + 1));
                double lat = c[0] + (rng.nextDouble() - 0.5) * 0.02;
                double lon = c[1] + (rng.nextDouble() - 0.5) * 0.02;
                x.setLocation(GeoSupport.point(lat, lon));
                x.setTrafficLight(rng.nextBoolean());
                x.setSignalCycleSeconds(40 + rng.nextInt(60));
                intersections.save(x);
            }
        }
    }

    private void seedTraffic(List<Road> roadList, Random rng) {
        List<TrafficSensor> sensors = new ArrayList<>();
        for (Road r : roadList) {
            TrafficSensor s = new TrafficSensor();
            s.setRoad(r);
            s.setName(r.getName() + " Sensor");
            s.setStatus(rng.nextInt(20) == 0 ? "faulty" : "active");
            if (r.getPath() != null) {
                var mid = r.getPath().getCentroid();
                s.setLocation(GeoSupport.point(mid.getY(), mid.getX()));
            }
            sensors.add(trafficSensors.save(s));
        }

        Instant start = Instant.now().minusSeconds(48 * 3600).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        List<TrafficReading> batch = new ArrayList<>(1024);
        for (TrafficSensor s : sensors) {
            if ("faulty".equals(s.getStatus())) continue;
            double jitter = 0.85 + rng.nextDouble() * 0.3;
            for (int step = 0; step < 48 * 4; step++) {
                Instant at = start.plusSeconds(step * 900L);
                var t = at.atZone(java.time.ZoneOffset.UTC);
                double baseline = diurnalBaseline(t.getHour(), t.getDayOfWeek().getValue());
                // rain events inject extra congestion around hour 30-36
                double rainBoost = step % 192 >= 118 && step % 192 <= 136 ? 0.15 : 0;
                double level = Math.min(1, Math.max(0.03, baseline * jitter + rainBoost + (rng.nextDouble() - 0.5) * 0.08));
                double speed = Math.max(6, 52 - level * 42 + (rng.nextDouble() - 0.5) * 6);
                TrafficReading r = new TrafficReading();
                r.setSensor(s);
                r.setVehicleCount((int) (150 + level * 800 * jitter));
                r.setAvgSpeedKph(Math.round(speed));
                r.setOccupancyPct(Math.min(1, level * 0.9));
                r.setCongestionLevel(indexOf(r.getVehicleCount(), speed, r.getOccupancyPct()));
                r.setRecordedAt(at);
                batch.add(r);
                if (batch.size() >= 1000) {
                    trafficReadings.saveAll(batch);
                    batch.clear();
                }
            }
        }
        trafficReadings.saveAll(batch);
    }

    private void seedDrainage(List<Zone> zoneList, Random rng) {
        List<DrainageSensor> sensors = new ArrayList<>();
        for (Zone z : zoneList) {
            int networksPerZone = 2;
            for (int n = 0; n < networksPerZone; n++) {
                DrainageNetwork net = new DrainageNetwork();
                net.setZone(z);
                net.setName(z.getName() + (n == 0 ? " Primary" : " Secondary") + " Storm Line");
                net.setCapacityM3PerHour(n == 0 ? 1800 + rng.nextInt(1800) : 900 + rng.nextInt(700));
                net.setNetworkClass(n == 0 ? "primary" : "secondary");
                var savedNet = drainageNetworks.save(net);
                DrainageSensor s = new DrainageSensor();
                s.setNetwork(savedNet);
                s.setName(net.getName() + " Gauge");
                s.setStatus("normal");
                var c = centroid(z);
                s.setLocation(GeoSupport.point(c[0] + (rng.nextDouble() - 0.5) * 0.02, c[1] + (rng.nextDouble() - 0.5) * 0.02));
                sensors.add(drainageSensors.save(s));
            }
        }

        Instant start = Instant.now().minusSeconds(48 * 3600).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        List<WaterLevelReading> batch = new ArrayList<>();
        for (DrainageSensor s : sensors) {
            double base = 18 + rng.nextDouble() * 14;
            for (int step = 0; step < 48 * 4; step++) {
                Instant at = start.plusSeconds(step * 900L);
                boolean rainWindow = step % 192 >= 116 && step % 192 <= 140;
                double rain = rainWindow ? 18 + rng.nextDouble() * 30 : rng.nextDouble() * 2;
                double lagged = rainWindow && step % 192 > 120 ? (step % 192 - 120) / 20.0 : 0;
                double level = base + lagged * 34 + (rainWindow ? rain * 0.7 : 0) * (1 + rng.nextDouble() / 6);
                double flow = 40 + level * 12 + (rainWindow ? 180 : 0);
                WaterLevelReading r = new WaterLevelReading();
                r.setSensor(s);
                r.setWaterLevelCm(Math.round(level * 10.0) / 10.0);
                r.setFlowRateLps(Math.round(flow));
                r.setRainfallMm(Math.round(rain * 10.0) / 10.0);
                r.setRiskLevel(FloodHeuristic.riskForCm(r.getWaterLevelCm()));
                r.setRecordedAt(at);
                batch.add(r);
                if (batch.size() >= 1000) {
                    waterReadings.saveAll(batch);
                    batch.clear();
                }
            }
        }
        waterReadings.saveAll(batch);
    }

    private void seedGrids(List<Zone> zoneList, Random rng) {
        Instant start = Instant.now().minusSeconds(48 * 3600).truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        for (Zone z : zoneList) {
            ElectricityGrid g = new ElectricityGrid();
            g.setZone(z);
            g.setName(z.getName() + " 66/11 kV Substation");
            g.setCapacityKw(40_000 + rng.nextInt(30_000));
            g.setLossPct(3 + rng.nextDouble() * 6);
            var savedGrid = grids.save(g);

            List<GridReading> batch = new ArrayList<>();
            for (int step = 0; step < 48; step++) {
                Instant at = start.plusSeconds(step * 3600L);
                var t = at.atZone(java.time.ZoneOffset.UTC);
                double load = diurnalBaseline(t.getHour(), t.getDayOfWeek().getValue());
                boolean outage = step == 30 && rng.nextBoolean();
                GridReading r = new GridReading();
                r.setGrid(savedGrid);
                r.setConsumptionKw(Math.round(savedGrid.getCapacityKw() * (0.35 + load * 0.5)));
                r.setVoltage(218 + rng.nextDouble() * 16);
                r.setOutageReported(outage);
                r.setRecordedAt(at);
                batch.add(r);
                if (batch.size() >= 1000) {
                    gridReadings.saveAll(batch);
                    batch.clear();
                }
            }
            gridReadings.saveAll(batch);
        }
    }

    private void seedAssets(List<Zone> zoneList, Random rng) {
        for (Zone z : zoneList) {
            var c = centroid(z);
            for (int i = 0; i < 6; i++) {
                InfrastructureAsset a = new InfrastructureAsset();
                a.setZone(z);
                a.setAssetType(ASSET_TYPES[i % ASSET_TYPES.length]);
                a.setName(z.getName() + " " + a.getAssetType() + " #" + (i + 1));
                a.setLocation(GeoSupport.point(c[0] + (rng.nextDouble() - 0.5) * 0.03, c[1] + (rng.nextDouble() - 0.5) * 0.03));
                a.setConditionScore(40 + rng.nextInt(55));
                a.setInstalledYear(2004 + rng.nextInt(20));
                a.setStatus(a.getConditionScore() < 50 ? "degraded" : "operational");
                assets.save(a);
            }
        }
    }

    private void seedMaintenance(List<Road> roadList, Random rng) {
        String[] titles = {"Resurfacing after monsoon damage", "Kerb reconstruction", "Pothole repair",
                "Storm drain regrading", "Streetlight pulling", "Culvert desilting"};
        for (int i = 0; i < 14; i++) {
            Road r = roadList.get(rng.nextInt(roadList.size()));
            MaintenanceRequest m = new MaintenanceRequest();
            m.setRoad(r);
            m.setTitle(titles[i % titles.length] + " — " + r.getName());
            m.setDescription("Auto-seeded work order from last inspection round.");
            m.setPriority(List.of("p1", "p2", "p3", "p4").get(rng.nextInt(4)));
            m.setStatus(List.of("open", "assigned", "in_progress", "resolved", "closed").get(rng.nextInt(4)));
            m.setReportedBy("inspector-bot");
            if ("resolved".equals(m.getStatus())) m.setResolvedAt(Instant.now().minusSeconds(86_400 * (2 + i)));
            maintenance.save(m);
        }
    }

    private void seedBins(List<Zone> zoneList, Random rng) {
        for (int i = 0; i < 120; i++) {
            Zone z = zoneList.get(rng.nextInt(zoneList.size()));
            var c = centroid(z);
            WasteBin b = new WasteBin();
            b.setZone(z);
            b.setName(z.getName() + " Bin " + String.format(Locale.ROOT, "%02d", i + 1));
            b.setLocation(GeoSupport.point(c[0] + (rng.nextDouble() - 0.5) * 0.03, c[1] + (rng.nextDouble() - 0.5) * 0.03));
            b.setFillLevelPct(rng.nextDouble() * 100);
            b.setLastCollectedAt(Instant.now().minusSeconds(3600L * (2 + rng.nextInt(48))));
            bins.save(b);
        }
    }

    private void seedEmergency(List<Zone> zoneList, Random rng) {
        String[][] fleet = {
                {"AMB-01", "ambulance", "BMRC Station Point"},
                {"AMB-02", "ambulance", "PHC North"},
                {"FIRE-01", "fire_engine", "Central Fire Depot"},
                {"POL-01", "police", "Division HQ"},
                {"POL-02", "police", "North Precinct"},
                {"RSQ-01", "rescue", "Relief Base Camp"}
        };
        for (String[] v : fleet) {
            var z = zoneList.get(rng.nextInt(zoneList.size()));
            var c = centroid(z);
            EmergencyVehicle ev = new EmergencyVehicle();
            ev.setName(v[0]);
            ev.setVehicleType(v[1]);
            ev.setStatus("available");
            ev.setStation(GeoSupport.point(c[0] + (rng.nextDouble() - 0.5) * 0.02, c[1] + (rng.nextDouble() - 0.5) * 0.02));
            ev.setHeadingDeg(rng.nextInt(360));
            vehicles.save(ev);
        }

        String[][] offs = {
                {"Medical emergency — CPR in progress", "medical", "4"},
                {"Warehouse fire, smoke visible", "fire", "5"},
                {null, "hazard", "2"}
        };
        for (String[] o : offs) {
            Zone z = zoneList.get(rng.nextInt(zoneList.size()));
            var c = centroid(z);
            EmergencyIncident i = new EmergencyIncident();
            i.setZone(z);
            i.setName(o[0] != null ? o[0] : z.getName() + " – chemical spill alarm");
            i.setIncidentType(o[1]);
            i.setSeverity(Integer.parseInt(o[2]));
            i.setStatus("active");
            i.setReportedAt(Instant.now().minusSeconds(600L * (1 + rng.nextInt(10))));
            i.setLocation(GeoSupport.point(c[0] + (rng.nextDouble() - 0.5) * 0.01, c[1] + (rng.nextDouble() - 0.5) * 0.01));
            i.setDescription("Seeded during demo bootstrap.");
            incidents.save(i);
        }
    }

    private void seedReports(List<Zone> zoneList, Random rng) {
        for (int i = 0; i < 10; i++) {
            Zone z = zoneList.get(rng.nextInt(zoneList.size()));
            var c = centroid(z);
            CitizenReport r = new CitizenReport();
            r.setZone(z);
            r.setCategory(List.of("pothole", "leak", "lighting", "garbage", "flooding").get(i % 5));
            r.setDescription(REPORT_TEXTS[i % REPORT_TEXTS.length]);
            r.setLocation(GeoSupport.point(c[0] + (rng.nextDouble() - 0.5) * 0.02, c[1] + (rng.nextDouble() - 0.5) * 0.02));
            r.setStatus(List.of("submitted", "acknowledged", "in_progress", "resolved").get(rng.nextInt(4)));
            r.setUpvotes(rng.nextInt(25));
            r.setReporterName("citizen" + (i + 1));
            reports.save(r);
        }
    }

    /* ------------------------------- utils ------------------------------- */

    private static double[] centroid(Zone z) {
        if (z.getBoundary() != null) {
            var c = z.getBoundary().getCentroid();
            return new double[]{c.getY(), c.getX()};
        }
        return new double[]{23.8103, 90.4125}; // Dhaka center fallback
    }

    private static org.locationtech.jts.geom.LineString roadPath(Zone z, int i, Random rng) {
        var c = centroid(z);
        double spread = 0.012;
        double lat0 = c[0] + (rng.nextDouble() - 0.5) * spread;
        double lon0 = c[1] + (rng.nextDouble() - 0.5) * spread;
        double lat1 = c[0] + (rng.nextDouble() - 0.5) * spread * 2;
        double lon1 = c[1] + (rng.nextDouble() - 0.5) * spread * 2;
        double bias = (i % 2 == 0) ? 0.004 : -0.004;
        return GeoSupport.line(List.of(
                new double[]{lon0, lat0},
                new double[]{(lon0 + lon1) / 2 + bias, (lat0 + lat1) / 2},
                new double[]{lon1, lat1}));
    }

    /** minimal GeoJSON writers (avoid heavier deps) */
    private static String polygonGeoJson(org.locationtech.jts.geom.Polygon p) {
        StringBuilder sb = new StringBuilder("[[");
        var coords = p.getCoordinates();
        for (int i = 0; i < coords.length; i++) {
            if (i > 0) sb.append(',');
            sb.append('[').append(coords[i].getX()).append(',').append(coords[i].getY()).append(']');
        }
        sb.append("]]");
        return "{\"type\":\"Polygon\",\"coordinates\":" + sb + "}";
    }

    private static String lineGeoJson(org.locationtech.jts.geom.LineString l) {
        StringBuilder sb = new StringBuilder("[");
        var coords = l.getCoordinates();
        for (int i = 0; i < coords.length; i++) {
            if (i > 0) sb.append(',');
            sb.append('[').append(coords[i].getX()).append(',').append(coords[i].getY()).append(']');
        }
        sb.append(']');
        return "{\"type\":\"LineString\",\"coordinates\":" + sb + "}";
    }
}
