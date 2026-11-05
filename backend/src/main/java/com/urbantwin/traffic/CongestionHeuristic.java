package com.urbantwin.traffic;

/**
 * Pure, dependency-free congestion maths so the same logic can be unit-tested and
 * reused by the DataSeeder and the prediction fallback.
 */
public final class CongestionHeuristic {
    private CongestionHeuristic() {}

    /** Congestion index from a sensor sample: occupancy plus speed deficit vs 45 km/h free flow. */
    public static double indexOf(int vehicleCount, double avgSpeedKph, double occupancyPct) {
        double occ = clamp01(occupancyPct);
        double speedGap = 1.0 - clamp01(avgSpeedKph / 45.0);
        double volume = clamp01(vehicleCount / 900.0);
        return clamp01(0.55 * occ + 0.30 * speedGap + 0.15 * volume);
    }

    /**
     * Diurnal baseline congestion (0..1) for a given hour and weekday. Peaks at the
     * 8-10 and 17-20 commute windows, quiet overnight, busier midweek days.
     */
    public static double diurnalBaseline(int hourOfDay, int dayOfWeek) {
        double morning = gaussian(hourOfDay, 8.5, 1.7);
        double evening = gaussian(hourOfDay, 18.0, 2.2);
        double midday = 0.22 * gaussian(hourOfDay, 13.0, 3.5);
        double base = 0.10 + 0.58 * morning + 0.62 * evening + midday;
        double dayFactor = switch (dayOfWeek) {
            case 1, 5 -> 1.12;
            case 2, 3, 4 -> 1.0;
            case 6 -> 0.82;
            default -> 0.62; // Sunday
        };
        return clamp01(base * dayFactor);
    }

    /**
     * Heuristic short-horizon prediction: baseline drift plus inertia of the current
     * level, damped as the horizon grows. Used when the ML service is unavailable.
     */
    public static double predicted(double currentLevel, int hourOfDay, int dayOfWeek, int horizonMinute) {
        double baseline = diurnalBaseline(hourOfDay, dayOfWeek);
        double remaining = Math.max(0, horizonMinute) / 30.0; // 0..1 over a 30 min class horizon
        double inertia = (currentLevel - baseline) * Math.exp(-1.6 * remaining);
        return clamp01(baseline + inertia);
    }

    private static double gaussian(double x, double mu, double sigma) {
        return Math.exp(-Math.pow(x - mu, 2) / (2 * sigma * sigma));
    }

    static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
