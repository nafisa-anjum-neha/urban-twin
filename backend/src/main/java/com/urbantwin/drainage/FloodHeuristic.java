package com.urbantwin.drainage;

/**
 * Pure waterlogging flood maths — service-limit heuristics shared by ingest
 * labelling, prediction fallback and the cascade simulator.
 */
public final class FloodHeuristic {
    /** Service depth thresholds (cm) aligning with the UI gauge (RiskGauge). */
    public static final double WARN_CM = 50;
    public static final double HIGH_CM = 100;
    public static final double SEVERE_CM = 150;

    private FloodHeuristic() {}

    /** Risk band label for a water level. */
    public static String riskForCm(double cm) {
        if (cm >= SEVERE_CM) return "severe";
        if (cm >= HIGH_CM) return "high";
        if (cm >= WARN_CM) return "moderate";
        return "low";
    }

    /** Numeric risk score 0..1 for the digital twin (maps the same thresholds). */
    public static double scoreForCm(double cm) {
        return Math.max(0, Math.min(1, cm / SEVERE_CM));
    }

    /**
     * Projected peak water level (cm) for a network: current level plus the rainfall
     * run-off accumulated over the duration, divided by network capacity headroom.
     * Run-off coefficient k converts mm of rain to cm of level for an urban basin.
     */
    public static double projectedLevelCm(double currentLevelCm, double rainfallMm, double durationMinutes,
                                          double capacityM3PerHour, double inflowLps) {
        double hours = Math.max(0.25, durationMinutes / 60.0);
        double directRunoffCm = rainfallMm * 0.04 * hours;          // ~4 % becomes standing level
        double inflowCmS = inflowLps * 0.0004;                     // inflow-limited contribution
        double inflowSurplus = Math.max(0, inflowCmS * hours - capacityM3PerHour * hours / 36_000.0);
        double projection = currentLevelCm + directRunoffCm + inflowSurplus;
        return Math.max(currentLevelCm, projection);
    }

    /** Confidence of a projection — decays with the distance travelled beyond observations. */
    public static double confidence(double projectedDelta) {
        return Math.max(0.35, 1.0 - projectedDelta / 120.0);
    }
}
