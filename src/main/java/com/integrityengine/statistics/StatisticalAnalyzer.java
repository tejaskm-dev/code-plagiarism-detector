package com.integrityengine.statistics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Turns a raw similarity score into "unusual for this cohort".
 *
 * <p>This is the module the project's central claim rests on. A raw similarity of 0.95
 * means nothing on its own: on "write a function that adds two numbers" every honest
 * pair in the class scores 0.95, and on a large open-ended project 0.95 is damning. The
 * only defensible question is how far a pair sits from its own cohort's distribution.
 *
 * <p>Median and MAD are used rather than mean and standard deviation on purpose: the
 * colluding pairs are themselves the outliers, and they drag a mean-based threshold
 * upward far enough to hide inside it. The median and MAD have a breakdown point of
 * 50%, so up to half the cohort can be extreme before the baseline moves at all.
 *
 * <p><b>Three tiers, in order.</b> Real cohorts routinely produce degenerate
 * distributions, and each tier exists because the one before it divides by zero:
 * <ol>
 *   <li><b>MAD &gt; 0</b> — the standard Iglewicz-Hoaglin modified z-score.</li>
 *   <li><b>MAD = 0, MAE &gt; 0</b> — more than half the cohort scored identically, so
 *       the MAD collapses while real spread remains. Fall back to the mean absolute
 *       deviation, which uses every point rather than only the middle one.</li>
 *   <li><b>MAD = 0 and MAE = 0</b> — every score in the cohort is identical and there
 *       is no dispersion to measure at all. Relative analysis is meaningless here, so
 *       the decision falls back to an absolute similarity bar.</li>
 * </ol>
 */
public class StatisticalAnalyzer {
    // Intentionally not final: IntegrityEngine is required to compute the cohort
    // statistics once per batch rather than once per pair, and the only reliable way to
    // pin that is a test double that counts invocations.

    /**
     * Makes the MAD a consistent estimator of the standard deviation for normally
     * distributed data (the 0.75 quantile of the standard normal).
     */
    public static final double MAD_CONSISTENCY = 0.6745;

    /** The matching constant for the mean absolute deviation: sqrt(pi/2). */
    public static final double MAE_CONSISTENCY = 1.253314;

    /** Iglewicz and Hoaglin's recommended cutoff for the modified z-score. */
    public static final double OUTLIER_THRESHOLD = 3.5;

    /**
     * Absolute similarity bar used only when a cohort has no dispersion whatsoever.
     *
     * <p>Deliberately high: this tier fires when statistics can say nothing, so it
     * should only speak when the raw similarity is alarming on its own terms.
     */
    public static final double ABSOLUTE_GUARDRAIL = 0.90;

    /**
     * @param values the sample, not modified by this call
     * @return the median
     * @throws IllegalArgumentException if empty or containing a non-finite value
     */
    public double computeMedian(List<Double> values) {
        List<Double> sorted = validatedCopy(values);
        Collections.sort(sorted);

        int size = sorted.size();
        int middle = size / 2;
        if (size % 2 == 1) {
            return sorted.get(middle);
        }
        // Averaging the two middle values keeps the median between them rather than
        // arbitrarily favouring the lower one.
        return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }

    /**
     * Median absolute deviation: the median of each point's distance from the median.
     *
     * @return the MAD, which is 0 whenever half or more of the sample sits on the median
     */
    public double computeMAD(List<Double> values) {
        double median = computeMedian(values);

        List<Double> deviations = new ArrayList<>(values.size());
        for (double value : values) {
            deviations.add(Math.abs(value - median));
        }
        return computeMedian(deviations);
    }

    /**
     * Mean absolute deviation about the median — the tier-2 fallback.
     *
     * <p>Taken about the median rather than the mean, so it stays a measure of spread
     * around the same centre the z-score is computed from.
     *
     * @return the MAE, which is 0 only when every value is identical
     */
    public double computeMAE(List<Double> values) {
        double median = computeMedian(values);

        double total = 0.0;
        for (double value : validatedCopy(values)) {
            total += Math.abs(value - median);
        }
        return total / values.size();
    }

    /**
     * The modified z-score of one value against a cohort's centre and spread.
     *
     * <p>Callers should normally use {@link #isOutlier(double, List)}, which applies the
     * absolute guardrail. This method reports the score itself, and in the fully
     * degenerate case (no dispersion, value off the median) that score is infinite —
     * the limit as spread approaches zero.
     *
     * @param value  the score being judged
     * @param median the cohort's median
     * @param mad    the cohort's median absolute deviation
     * @param mae    the cohort's mean absolute deviation, used only when the MAD is 0
     * @return the modified z-score; 0.0 if the value sits on the median with no spread,
     *         otherwise possibly infinite
     */
    public double computeModifiedZScore(double value, double median, double mad, double mae) {
        double deviation = value - median;

        if (mad > 0.0) {
            return MAD_CONSISTENCY * deviation / mad;
        }
        if (mae > 0.0) {
            return deviation / (MAE_CONSISTENCY * mae);
        }
        // No dispersion at all. A value on the median deviates by nothing; anything
        // else is infinitely many (zero-width) deviations away.
        return deviation == 0.0 ? 0.0 : Math.copySign(Double.POSITIVE_INFINITY, deviation);
    }

    /**
     * Decide whether one score is anomalous for its cohort, applying all three tiers.
     *
     * <p>Note the cost: this recomputes the cohort's statistics on every call, which is
     * O(n log n) per query. Callers scoring every pair in a batch should hoist the
     * median/MAD/MAE out and call {@link #computeModifiedZScore} directly.
     *
     * @param value the score being judged
     * @param batch every pairwise score in the cohort, including {@code value}
     * @return true if the score is anomalous enough to warrant review
     */
    public boolean isOutlier(double value, List<Double> batch) {
        return isOutlier(value, computeMedian(batch), computeMAD(batch), computeMAE(batch));
    }

    /**
     * The same decision against statistics the caller has already computed.
     *
     * <p>Exists so a batch scoring every pair can compute median, MAD and MAE once
     * instead of once per pair. The tier logic lives here rather than being duplicated
     * at the call site — {@link #isOutlier(double, List)} delegates to this.
     *
     * @param value  the score being judged
     * @param median the cohort's median
     * @param mad    the cohort's median absolute deviation
     * @param mae    the cohort's mean absolute deviation
     */
    public boolean isOutlier(double value, double median, double mad, double mae) {
        if (mad == 0.0 && mae == 0.0) {
            // Tier 3. Every pair in this cohort scored identically, so no pair stands
            // out from any other; the only question left is whether that shared level
            // is alarming in absolute terms. This deliberately flags a whole cohort at
            // once (mass copying, or a template) and stays silent on a uniformly low one.
            return value >= ABSOLUTE_GUARDRAIL;
        }
        return Math.abs(computeModifiedZScore(value, median, mad, mae)) >= OUTLIER_THRESHOLD;
    }

    /**
     * Reject input that would silently corrupt a median. A NaN sorts to the end of the
     * list and would drag the median with it rather than throwing.
     */
    private static List<Double> validatedCopy(List<Double> values) {
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("cannot compute a statistic of an empty sample");
        }

        List<Double> copy = new ArrayList<>(values.size());
        for (Double value : values) {
            Objects.requireNonNull(value, "values must not contain null");
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("values must be finite, got " + value);
            }
            copy.add(value);
        }
        return copy;
    }
}
