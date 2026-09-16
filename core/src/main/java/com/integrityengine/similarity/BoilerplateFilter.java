package com.integrityengine.similarity;

import com.integrityengine.domain.Fingerprint;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Suppresses fingerprints that appear across most of the cohort.
 *
 * <p>Provided starter code, import blocks and required method signatures otherwise give
 * every pair a high floor score and drown the real signal.
 *
 * <p><b>The hazard this class carries.</b> Suppression is defined relative to the
 * cohort, so a colluding group is itself part of the statistic. If 2 students out of 4
 * copy each other, their shared fingerprints have a document frequency of 0.5 and a
 * threshold of 0.5 deletes precisely the evidence that identifies them. The filter is
 * therefore inert below {@link #MINIMUM_COHORT_SIZE}: on a small batch, no fingerprint
 * can be distinguished from boilerplate without also being indistinguishable from
 * collusion, and suppressing nothing is the safe failure. Choosing a threshold well
 * above the largest plausible collusion cluster remains the caller's responsibility.
 */
public final class BoilerplateFilter {

    /**
     * Below this many submissions the filter suppresses nothing.
     *
     * <p>With four submissions a colluding pair is half the cohort; there is no
     * threshold that removes shared boilerplate without also removing them.
     */
    public static final int MINIMUM_COHORT_SIZE = 5;

    /** Present in half the cohort or more is treated as boilerplate. */
    public static final double DEFAULT_THRESHOLD = 0.5;

    /**
     * Identify the fingerprints common enough to be treated as boilerplate.
     *
     * @param batch     one fingerprint set per submission
     * @param threshold fraction of submissions a fingerprint must appear in to count as
     *                  boilerplate, in (0.0, 1.0]
     * @return the fingerprints to exclude from all comparisons; empty if the cohort is
     *         too small to judge
     * @throws IllegalArgumentException if the threshold is not in (0.0, 1.0]
     */
    public Set<Fingerprint> suppress(List<Set<Fingerprint>> batch, double threshold) {
        Objects.requireNonNull(batch, "batch");
        if (Double.isNaN(threshold) || threshold <= 0.0 || threshold > 1.0) {
            // A threshold of 0 would suppress every fingerprint in the batch and leave
            // nothing to compare, which is never what a caller means.
            throw new IllegalArgumentException(
                    "threshold must be in (0.0, 1.0], got " + threshold);
        }

        if (batch.size() < MINIMUM_COHORT_SIZE) {
            return Set.of();
        }

        Map<Fingerprint, Integer> documentFrequency = new HashMap<>();
        for (Set<Fingerprint> submission : batch) {
            Objects.requireNonNull(submission, "batch must not contain null sets");
            // A Set already holds each fingerprint once, so this counts documents, not
            // occurrences -- a file repeating one idiom fifty times must not on its own
            // make that idiom look like cohort-wide boilerplate.
            for (Fingerprint fingerprint : submission) {
                documentFrequency.merge(fingerprint, 1, Integer::sum);
            }
        }

        // Compare in integer space to keep the boundary exact: at threshold 0.5 over 10
        // submissions, "appears in 5" must suppress and "appears in 4" must not.
        int required = (int) Math.ceil(threshold * batch.size() - 1e-9);

        Set<Fingerprint> suppressed = new HashSet<>();
        for (Map.Entry<Fingerprint, Integer> entry : documentFrequency.entrySet()) {
            if (entry.getValue() >= required) {
                suppressed.add(entry.getKey());
            }
        }
        return Set.copyOf(suppressed);
    }
}
