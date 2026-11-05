package com.urbantwin.waste;

import java.util.List;
import java.util.UUID;

/**
 * Pure waste-collection route optimisation: nearest-neighbour construction followed
 * by a bounded 2-opt improvement pass. Deterministic and unit-testable with no
 * Spring involvement.
 */
public final class WasteRouteOptimizer {
    private WasteRouteOptimizer() {}

    /** A candidate stop (bin) in [lon, lat]. */
    public record Stop(UUID binId, String name, double lon, double lat, double fillLevelPct) {}

    public record Result(List<Stop> orderedStops, double totalDistanceKm, double durationMinutes) {}

    /**
     * Orders `stops` (all bins to service) starting and ending at `depot`.
     * Rule of thumb: urban collection averages 18 km/h moving and ~2 min dwell per stop.
     */
    public static Result plan(List<Stop> stops, Stop depot) {
        if (stops.isEmpty()) return new Result(List.of(), 0, 0);
        var tour = nearestNeighbour(stops, depot);
        tour = twoOpt(tour, depot);
        double km = tourDistance(tour, depot);
        double minutes = km / 18.0 * 60.0 + 2.0 * tour.size();
        return new Result(tour, Math.round(km * 100.0) / 100.0, Math.round(minutes));
    }

    /* -------------------------- internals ------------------------------- */

    private static List<Stop> nearestNeighbour(List<Stop> stops, Stop depot) {
        List<Stop> remaining = new java.util.ArrayList<>(stops);
        List<Stop> tour = new java.util.ArrayList<>(stops.size());
        double[] cursor = new double[]{depot.lon(), depot.lat()};
        while (!remaining.isEmpty()) {
            Stop best = null;
            double bestD = Double.MAX_VALUE;
            for (Stop s : remaining) {
                double d = haversine(cursor, new double[]{s.lon(), s.lat()});
                if (d < bestD) { bestD = d; best = s; }
            }
            tour.add(best);
            remaining.remove(best);
            cursor = new double[]{best.lon(), best.lat()};
        }
        return tour;
    }

    /** Classic 2-opt on a closed tour until no improving move exists (bounded passes). */
    private static List<Stop> twoOpt(List<Stop> tour, Stop depot) {
        int n = tour.size();
        if (n < 3) return tour;
        boolean improved = true;
        int passes = 0;
        while (improved && passes++ < 20) {
            improved = false;
            for (int i = 0; i < n - 1; i++) {
                for (int k = i + 1; k < n; k++) {
                    double before = edge(tour, i, depot) + edge(tour, k, depot);
                    List<Stop> swapped = reverse(tour, i, k);
                    double after = edge(swapped, i, depot) + edge(swapped, k, depot);
                    if (after < before - 1e-9) {
                        tour = swapped;
                        improved = true;
                    }
                }
            }
        }
        return tour;
    }

    /** distance from node i-1 (or depot) to node i */
    private static double edge(List<Stop> t, int i, Stop depot) {
        if (i == 0) return haversine(new double[]{depot.lon(), depot.lat()}, new double[]{t.get(0).lon(), t.get(0).lat()});
        return haversine(new double[]{t.get(i - 1).lon(), t.get(i - 1).lat()}, new double[]{t.get(i).lon(), t.get(i).lat()});
    }

    private static List<Stop> reverse(List<Stop> t, int i, int k) {
        List<Stop> copy = new java.util.ArrayList<>(t);
        while (i < k) {
            java.util.Collections.swap(copy, i, k);
            i++; k--;
        }
        return copy;
    }

    private static double tourDistance(List<Stop> t, Stop depot) {
        double total = 0;
        double[] cur = new double[]{depot.lon(), depot.lat()};
        for (Stop s : t) { total += haversine(cur, new double[]{s.lon(), s.lat()}); cur = new double[]{s.lon(), s.lat()}; }
        return total + haversine(cur, new double[]{depot.lon(), depot.lat()}); // return to depot
    }

    /** haversine distance (km) between two [lon, lat] points. */
    static double haversine(double[] a, double[] b) {
        double lat1 = Math.toRadians(a[1]), lat2 = Math.toRadians(b[1]);
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b[0] - a[0]);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }
}
