package com.integrityengine.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The single object {@code IntegrityEngine.analyzeBatch} hands back. Immutable:
 * both collections are defensively copied on the way in and are already unmodifiable.
 */
public final class Report {

    private final String assignmentId;
    private final Instant generatedAt;
    private final List<SimilarityResult> similarityResults;
    private final List<SimilarityResult> flaggedResults;
    private final List<AIAuthorshipResult> aiResults;
    private final double cohortMedian;
    private final double cohortMad;
    private final double cohortMae;

    public Report(String assignmentId,
                  Instant generatedAt,
                  List<SimilarityResult> similarityResults,
                  List<SimilarityResult> flaggedResults,
                  List<AIAuthorshipResult> aiResults,
                  double cohortMedian,
                  double cohortMad,
                  double cohortMae) {
        this.assignmentId = Objects.requireNonNull(assignmentId, "assignmentId");
        this.generatedAt = Objects.requireNonNull(generatedAt, "generatedAt");
        this.similarityResults = List.copyOf(similarityResults);
        this.flaggedResults = List.copyOf(flaggedResults);
        this.aiResults = List.copyOf(aiResults);
        this.cohortMedian = cohortMedian;
        this.cohortMad = cohortMad;
        this.cohortMae = cohortMae;
    }

    public String getAssignmentId() {
        return assignmentId;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public List<SimilarityResult> getSimilarityResults() {
        return similarityResults;
    }

    /**
     * The subset of {@link #getSimilarityResults()} whose modified z-score crossed the
     * outlier threshold for this cohort. This, not the raw scores, is what a reviewer
     * should look at first.
     */
    public List<SimilarityResult> getFlaggedResults() {
        return flaggedResults;
    }

    public List<AIAuthorshipResult> getAiResults() {
        return aiResults;
    }

    /** The cohort's median similarity — reported so a flag can be explained, not just asserted. */
    public double getCohortMedian() {
        return cohortMedian;
    }

    /** The cohort's median absolute deviation. */
    public double getCohortMad() {
        return cohortMad;
    }

    /**
     * The cohort's mean absolute deviation.
     *
     * <p>Reported alongside the MAD because it is what the outlier test falls back to
     * when the MAD collapses to zero — which happens whenever half the cohort scored
     * identically. Without it a reader cannot reconstruct how a pair was judged.
     */
    public double getCohortMae() {
        return cohortMae;
    }

    @Override
    public String toString() {
        return "Report[" + assignmentId + " pairs=" + similarityResults.size()
                + " flagged=" + flaggedResults.size()
                + " aiVerdicts=" + aiResults.size() + "]";
    }
}
