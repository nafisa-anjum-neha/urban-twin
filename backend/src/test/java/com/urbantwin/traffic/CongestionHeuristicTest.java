package com.urbantwin.traffic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Pure tests of the congestion maths (no Spring context, no DB). */
class CongestionHeuristicTest {

    @Test
    void heavyOccupiedSlowTrafficIsGridlocked() {
        double level = CongestionHeuristic.indexOf(850, 12, 0.95);
        assertTrue(level > 0.8, "expected severe congestion, got " + level);
    }

    @Test
    void freeFlowingLightTrafficIsCalm() {
        double level = CongestionHeuristic.indexOf(120, 55, 0.05);
        assertTrue(level < 0.25, "expected free flow, got " + level);
    }

    @Test
    void morningPeakBeatsMidnightBaseline() {
        double morning = CongestionHeuristic.diurnalBaseline(8, 3);
        double midnight = CongestionHeuristic.diurnalBaseline(2, 3);
        assertTrue(morning > midnight * 2, "morning " + morning + " vs midnight " + midnight);
    }

    @Test
    void predictionPullsTowardsBaselineOverTime() {
        // stuck traffic (0.9) shortly after the peak should relax toward the baseline
        double now = CongestionHeuristic.predicted(0.9, 19, 4, 5);
        double soon = CongestionHeuristic.predicted(0.9, 19, 4, 30);
        double baseline = CongestionHeuristic.diurnalBaseline(19, 4);
        assertTrue(Math.abs(soon - baseline) <= Math.abs(now - baseline),
                "prediction should converge to baseline");
        assertTrue(soon >= 0 && soon <= 1, "prediction must stay within [0,1]");
    }
}
