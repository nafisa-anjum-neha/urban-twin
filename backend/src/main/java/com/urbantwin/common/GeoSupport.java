package com.urbantwin.common;

import java.util.List;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.linearref.LengthIndexedLine;

/** JTS + GeoJSON helpers. Coordinates are [lon, lat] (GeoJSON order). */
public final class GeoSupport {
    public static final GeometryFactory GF = new GeometryFactory();

    private GeoSupport() {}

    public static Point point(double lat, double lon) {
        Point p = GF.createPoint(new Coordinate(lon, lat));
        p.setSRID(4326);
        return p;
    }

    public static LineString line(List<double[]> lonLat) {
        LineString l = GF.createLineString(lonLat.stream()
                .map(c -> new Coordinate(c[0], c[1])).toArray(Coordinate[]::new));
        l.setSRID(4326);
        return l;
    }

    /** Builds a closed polygon ring around centre (lat, lon) with a simple box of ~halfKm km. */
    public static Polygon boxPolygon(double lat, double lon, double halfKm) {
        double dLat = halfKm / 111.0;
        double dLon = halfKm / (111.0 * Math.cos(Math.toRadians(lat)));
        Polygon p = GF.createPolygon(GF.createLinearRing(new Coordinate[] {
                new Coordinate(lon - dLon, lat - dLat),
                new Coordinate(lon - dLon, lat + dLat),
                new Coordinate(lon + dLon, lat + dLat),
                new Coordinate(lon + dLon, lat - dLat),
                new Coordinate(lon - dLon, lat - dLat),
        }), null);
        p.setSRID(4326);
        return p;
    }

    /** GeoJSON Point string for [lon, lat]. */
    public static String pointGeoJson(double lon, double lat) {
        return "{\"type\":\"Point\",\"coordinates\":[%f,%f]}".formatted(lon, lat);
    }

    /** GeoJSON LineString string for a single [lon, lat] polyline. */
    public static String lineGeoJson(List<double[]> lonLat) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < lonLat.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('[').append(lonLat.get(i)[0]).append(',').append(lonLat.get(i)[1]).append(']');
        }
        return "{\"type\":\"LineString\",\"coordinates\":" + sb + "]}";
    }

    /** GeoJSON FeatureCollection string for point lists. */
    public static String pointFeatureCollection(List<double[]> lonLat) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lonLat.size(); i++) {
            if (i > 0) sb.append(',');
            double lon = lonLat.get(i)[0], lat = lonLat.get(i)[1];
            sb.append("{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[")
              .append(lon).append(',').append(lat).append("]},\"properties\":{}}");
        }
        return "{\"type\":\"FeatureCollection\",\"features\":[" + sb + "]}";
    }

    public static boolean intersect(LineString a, LineString b) {
        try {
            return a.intersects(b);
        } catch (Exception e) {
            return false;
        }
    }

    /** distance in km between two [lon, lat] coords (haversine). */
    public static double distanceKm(double[] a, double[] b) {
        double lat1 = a[1], lat2 = b[1], dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(b[0] - a[0]);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    public static String segment(LineString ls, double fromPct, double toPct) {
        LengthIndexedLine lil = new LengthIndexedLine(ls);
        return substringGeoJson(lil, fromPct * ls.getLength(), toPct * ls.getLength());
    }

    public static String substringGeoJson(LengthIndexedLine lil, double start, double end) {
        org.locationtech.jts.geom.Geometry seg = lil.extractLine(start, end);
        StringBuilder sb = new StringBuilder("[");
        Coordinate[] coords = seg.getCoordinates();
        for (int i = 0; i < coords.length; i++) {
            if (i > 0) sb.append(',');
            sb.append('[').append(coords[i].x).append(',').append(coords[i].y).append(']');
        }
        return "{\"type\":\"LineString\",\"coordinates\":" + sb + "]}";
    }
}
