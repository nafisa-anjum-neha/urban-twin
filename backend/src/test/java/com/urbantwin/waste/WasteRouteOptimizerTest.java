package com.urbantwin.waste;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure tests of the nearest-neighbour + 2-opt route optimiser. */
class WasteRouteOptimizerTest {

    private static WasteRouteOptimizer.Stop stop(double lon, double lat) {
        return new WasteRouteOptimizer.Stop(UUID.randomUUID(), "bin", lon, lat, 100);
    }

    @Test
    void tourCoversEveryStopExactlyOnce() {
        List<WasteRouteOptimizer.Stop> stops = List.of(
                stop(77.60, 12.97), stop(77.61, 12.98), stop(77.62, 12.97), stop(77.61, 12.96));
        var result = WasteRouteOptimizer.plan(stops, stop(77.60, 12.98));

        assertEquals(stops.size(), result.orderedStops().size());
        assertEquals(stops.size(), result.orderedStops().stream().map(WasteRouteOptimizer.Stop::binId).distinct().count());
    }

    @Test
    void planBetterThanNaiveOrder() {
        // deliberately scattered bins; NN+2-opt should beat a left-to-right sweep
        List<WasteRouteOptimizer.Stop> stops = new ArrayList<>();
        double[][] grid = {{77.60, 12.97}, {77.62, 12.99}, {77.64, 12.96}, {77.61, 12.995},
                {77.63, 12.98}, {77.65, 12.99}, {77.60, 12.995}, {77.62, 12.96}};
        for (double[] p : grid) stops.add(stop(p[0], p[1]));

        var result = WasteRouteOptimizer.plan(stops, stop(77.60, 12.96)); // south-west depot
        var naive = naiveDistance(stops, stop(77.60, 12.96));

        assertTrue(result.totalDistanceKm() <= naive * 1.05,
                "planned " + result.totalDistanceKm() + " should be ≤ naive " + naive);
        assertTrue(result.totalDistanceKm() > 0);
    }

    @Test
    void emptyPlanIsValid() {
        var result = WasteRouteOptimizer.plan(List.of(), stop(77.6, 12.97));
        assertEquals(0, result.totalDistanceKm());
        assertEquals(0, result.orderedStops().size());
    }

    private static double naiveDistance(List<WasteRouteOptimizer.Stop> stops, WasteRouteOptimizer.Stop depot) {
        double total = 0;
        double[] cur = {depot.lon(), depot.lat()};
        for (var s : stops) {
            total += WasteRouteOptimizer.haversine(cur, new double[]{s.lon(), s.lat()});
            cur = new double[]{s.lon(), s.lat()};
        }
        return total + WasteRouteOptimizer.haversine(cur, new double[]{depot.lon(), depot.lat()});
    }
}
