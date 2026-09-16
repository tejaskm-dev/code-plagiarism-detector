package com.integrityengine.statistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StatisticalAnalyzerTest {

    private static final double EPSILON = 1e-9;

    private final StatisticalAnalyzer analyzer = new StatisticalAnalyzer();

    /** Evenly spaced values, so every expected statistic is derivable by hand. */
    private static List<Double> evenlySpaced(double start, double step, int count) {
        List<Double> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(Math.round((start + i * step) * 1e6) / 1e6);
        }
        return values;
    }

    private static List<Double> repeated(double value, int count) {
        List<Double> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(value);
        }
        return values;
    }

    // ------------------------------------------------------------------- median

    @Nested
    class Median {

        @Test
        @DisplayName("Odd sample takes the middle value; even sample averages the two middle values")
        void handComputedMedians() {
            assertEquals(3.0, analyzer.computeMedian(List.of(1.0, 2.0, 3.0, 4.0, 5.0)), EPSILON);
            assertEquals(2.5, analyzer.computeMedian(List.of(1.0, 2.0, 3.0, 4.0)), EPSILON);
            assertEquals(7.0, analyzer.computeMedian(List.of(7.0)), EPSILON);
            assertEquals(-2.0, analyzer.computeMedian(List.of(-5.0, -2.0, 1.0)), EPSILON);
        }

        @Test
        void unsortedInputIsHandled() {
            assertEquals(3.0, analyzer.computeMedian(List.of(5.0, 1.0, 4.0, 2.0, 3.0)), EPSILON);
        }

        @Test
        @DisplayName("The caller's list is never reordered")
        void inputIsNotMutated() {
            List<Double> values = new ArrayList<>(List.of(5.0, 1.0, 4.0, 2.0, 3.0));
            List<Double> before = List.copyOf(values);

            analyzer.computeMedian(values);

            assertEquals(before, values, "computeMedian sorted the caller's list in place");
        }

        @Test
        @DisplayName("A NaN would sort to the end and drag the median with it, so it is rejected")
        void nonFiniteAndEmptyInputIsRejected() {
            assertThrows(IllegalArgumentException.class, () -> analyzer.computeMedian(List.of()));
            assertThrows(IllegalArgumentException.class,
                    () -> analyzer.computeMedian(Arrays.asList(1.0, Double.NaN, 3.0)));
            assertThrows(IllegalArgumentException.class,
                    () -> analyzer.computeMedian(Arrays.asList(1.0, Double.POSITIVE_INFINITY)));
            assertThrows(NullPointerException.class, () -> analyzer.computeMedian(null));
            assertThrows(NullPointerException.class,
                    () -> analyzer.computeMedian(Arrays.asList(1.0, null, 3.0)));
        }
    }

    // ---------------------------------------------------------------- MAD / MAE

    @Nested
    class Dispersion {

        @Test
        @DisplayName("MAD of [1,2,3,4,5]: median 3, deviations [2,1,0,1,2], median of those is 1")
        void handComputedMad() {
            assertEquals(1.0, analyzer.computeMAD(List.of(1.0, 2.0, 3.0, 4.0, 5.0)), EPSILON);
        }

        @Test
        @DisplayName("MAE of [1,2,3,4,5]: deviations sum to 6 over 5 points = 1.2")
        void handComputedMae() {
            assertEquals(1.2, analyzer.computeMAE(List.of(1.0, 2.0, 3.0, 4.0, 5.0)), EPSILON);
        }

        @Test
        @DisplayName("An extreme value barely moves the MAD, which is the entire point")
        void madResistsAnExtremeValue() {
            // [1,2,3,4,100]: median 3, deviations [2,1,0,1,97], median of those is still 1.
            assertEquals(3.0, analyzer.computeMedian(List.of(1.0, 2.0, 3.0, 4.0, 100.0)), EPSILON);
            assertEquals(1.0, analyzer.computeMAD(List.of(1.0, 2.0, 3.0, 4.0, 100.0)), EPSILON);
            // The MAE, by contrast, is dragged upward by the same point.
            assertEquals(20.2, analyzer.computeMAE(List.of(1.0, 2.0, 3.0, 4.0, 100.0)), EPSILON);
        }

        @Test
        void madIsZeroWhenMoreThanHalfTheSampleSitsOnTheMedian() {
            assertEquals(0.0, analyzer.computeMAD(List.of(5.0, 5.0, 5.0, 5.0, 7.0)), EPSILON);
            assertEquals(0.4, analyzer.computeMAE(List.of(5.0, 5.0, 5.0, 5.0, 7.0)), EPSILON);
        }

        @Test
        void bothAreZeroForAConstantSample() {
            assertEquals(0.0, analyzer.computeMAD(repeated(0.42, 8)), EPSILON);
            assertEquals(0.0, analyzer.computeMAE(repeated(0.42, 8)), EPSILON);
        }
    }

    // ------------------------------------------------------------ z-score tiers

    @Nested
    class ModifiedZScore {

        @Test
        @DisplayName("Tier 1: 0.6745 * (100 - 3) / 1 = 65.4265")
        void tierOneUsesTheMad() {
            assertEquals(65.4265, analyzer.computeModifiedZScore(100.0, 3.0, 1.0, 20.2), 1e-4);
            assertEquals(0.6745, analyzer.computeModifiedZScore(4.0, 3.0, 1.0, 20.2), 1e-4);
            assertEquals(0.0, analyzer.computeModifiedZScore(3.0, 3.0, 1.0, 20.2), EPSILON);
        }

        @Test
        @DisplayName("Tier 2: MAD is 0, so (7 - 5) / (1.253314 * 0.4) = 3.98942")
        void tierTwoFallsBackToTheMae() {
            assertEquals(3.98942, analyzer.computeModifiedZScore(7.0, 5.0, 0.0, 0.4), 1e-5);
            assertEquals(0.0, analyzer.computeModifiedZScore(5.0, 5.0, 0.0, 0.4), EPSILON);
        }

        @Test
        @DisplayName("Tier 3: with no dispersion, anything off the median is infinitely far away")
        void tierThreeIsInfiniteOffTheMedian() {
            assertEquals(0.0, analyzer.computeModifiedZScore(5.0, 5.0, 0.0, 0.0), EPSILON);
            assertEquals(Double.POSITIVE_INFINITY, analyzer.computeModifiedZScore(9.0, 5.0, 0.0, 0.0));
            assertEquals(Double.NEGATIVE_INFINITY, analyzer.computeModifiedZScore(1.0, 5.0, 0.0, 0.0));
        }

        @Test
        @DisplayName("Sign is preserved: a suspiciously LOW score is not a plagiarism finding")
        void signIsPreserved() {
            assertTrue(analyzer.computeModifiedZScore(1.0, 3.0, 1.0, 1.0) < 0);
            assertTrue(analyzer.computeModifiedZScore(5.0, 3.0, 1.0, 1.0) > 0);
        }
    }

    // ----------------------------------------------------- the scenarios that matter

    @Test
    @DisplayName("Requirement 2: a trivial assignment where every pair scores ~0.95 flags nobody")
    void trivialAssignmentFlagsNothing() {
        // "Write a function that adds two numbers": every honest submission looks alike.
        // Scores run 0.93 to 0.97, so in ABSOLUTE terms every single pair looks damning.
        // Relative to its own cohort, no pair stands out -- which is the whole thesis.
        List<Double> batch = evenlySpaced(0.93, 0.001, 41);

        assertEquals(0.95, analyzer.computeMedian(batch), EPSILON);
        assertEquals(0.01, analyzer.computeMAD(batch), EPSILON);

        for (double score : batch) {
            assertFalse(analyzer.isOutlier(score, batch),
                    "trivial-assignment score " + score + " was flagged (z="
                            + analyzer.computeModifiedZScore(score, analyzer.computeMedian(batch),
                                    analyzer.computeMAD(batch), analyzer.computeMAE(batch)) + ")");
        }
    }

    @Test
    @DisplayName("Requirement 3: one planted outlier in a normal cohort, and only that pair is flagged")
    void singlePlantedOutlierIsTheOnlyThingFlagged() {
        List<Double> honest = evenlySpaced(0.10, 0.01, 26);
        List<Double> batch = new ArrayList<>(honest);
        batch.add(0.92);

        List<Double> flagged = new ArrayList<>();
        for (double score : batch) {
            if (analyzer.isOutlier(score, batch)) {
                flagged.add(score);
            }
        }

        assertEquals(List.of(0.92), flagged, "expected exactly the planted pair to be flagged");
        assertTrue(analyzer.computeModifiedZScore(0.92, analyzer.computeMedian(batch),
                analyzer.computeMAD(batch), analyzer.computeMAE(batch)) > 6.0);
    }

    @Test
    @DisplayName("Why median/MAD and not mean/SD: at 23% contamination, mean+3SD exceeds 1.0 and can never fire")
    void meanAndStandardDeviationWouldHideTheColluders() {
        List<Double> batch = new ArrayList<>(evenlySpaced(0.10, 0.01, 20));
        batch.addAll(repeated(0.95, 6));

        double mean = batch.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double variance = batch.stream().mapToDouble(v -> (v - mean) * (v - mean)).average().orElseThrow();
        double meanPlusThreeSigma = mean + 3 * Math.sqrt(variance);

        // The colluders inflate the very statistic meant to catch them, so far that the
        // threshold lands above the maximum possible similarity score.
        assertTrue(meanPlusThreeSigma > 1.0,
                "expected the mean-based threshold to be unreachable, got " + meanPlusThreeSigma);
        assertFalse(0.95 >= meanPlusThreeSigma, "mean+3SD would not flag the colluders");

        // The robust statistic is unmoved and flags every one of them.
        assertTrue(analyzer.isOutlier(0.95, batch), "modified z-score must flag the colluders");
        for (double honest : evenlySpaced(0.10, 0.01, 20)) {
            assertFalse(analyzer.isOutlier(honest, batch), "honest pair " + honest + " was flagged");
        }
    }

    // ------------------------------------------------------------ forced fallbacks

    @Test
    @DisplayName("Fallback 1 forced: MAD = 0 but real spread remains, so the MAE tier does the work")
    void madZeroFallsBackToMaeAndStillFlags() {
        // Five of eight pairs scored identically, collapsing the MAD to zero. A tier-1
        // only implementation divides by zero here and reports Infinity or NaN for
        // every pair in the cohort.
        List<Double> batch = new ArrayList<>(repeated(0.20, 5));
        batch.addAll(List.of(0.21, 0.22, 0.95));

        assertEquals(0.0, analyzer.computeMAD(batch), EPSILON, "this batch must have MAD = 0");
        assertTrue(analyzer.computeMAE(batch) > 0.0, "but it must still have spread");

        assertTrue(analyzer.isOutlier(0.95, batch), "the outlier must survive the MAD collapse");
        assertFalse(analyzer.isOutlier(0.20, batch));
        assertFalse(analyzer.isOutlier(0.22, batch));
    }

    @Test
    @DisplayName("Fallback 2 forced: MAD = MAE = 0, so the absolute guardrail decides")
    void maeZeroFallsBackToTheAbsoluteGuardrail() {
        // Constructible after all: every pair in the cohort scoring exactly alike is
        // what a handed-out template or a wholly duplicated cohort produces.
        List<Double> allIdentical = repeated(0.98, 6);
        assertEquals(0.0, analyzer.computeMAD(allIdentical), EPSILON);
        assertEquals(0.0, analyzer.computeMAE(allIdentical), EPSILON);

        // Uniformly high: no pair is unusual, but the level itself is alarming.
        assertTrue(analyzer.isOutlier(0.98, allIdentical),
                "a cohort identical at 0.98 must be surfaced by the guardrail");

        // Uniformly low: equally devoid of dispersion, and correctly silent.
        List<Double> allLow = repeated(0.05, 6);
        assertFalse(analyzer.isOutlier(0.05, allLow),
                "a cohort identical at 0.05 must not be flagged");

        // A value far off a zero-dispersion cohort still answers to the guardrail, not
        // to its infinite z-score: 0.30 is unusual here but not alarming on its own.
        assertFalse(analyzer.isOutlier(0.30, allLow));
        assertTrue(analyzer.isOutlier(0.99, allLow));
    }

    @Test
    @DisplayName("The guardrail boundary is inclusive")
    void guardrailBoundaryIsExact() {
        List<Double> flat = repeated(0.50, 6);

        assertTrue(analyzer.isOutlier(StatisticalAnalyzer.ABSOLUTE_GUARDRAIL, flat));
        assertFalse(analyzer.isOutlier(Math.nextDown(StatisticalAnalyzer.ABSOLUTE_GUARDRAIL), flat));
    }

    @Test
    @DisplayName("The outlier threshold boundary is inclusive")
    void outlierThresholdBoundaryIsExact() {
        // median 0, MAD 1 -> z = 0.6745 * value, so value = 3.5 / 0.6745 sits exactly on it.
        List<Double> batch = List.of(-2.0, -1.0, 0.0, 1.0, 2.0);
        assertEquals(0.0, analyzer.computeMedian(batch), EPSILON);
        assertEquals(1.0, analyzer.computeMAD(batch), EPSILON);

        double onTheLine = StatisticalAnalyzer.OUTLIER_THRESHOLD / StatisticalAnalyzer.MAD_CONSISTENCY;
        assertTrue(analyzer.isOutlier(onTheLine, batch));
        assertFalse(analyzer.isOutlier(onTheLine * 0.999, batch));
    }
}
