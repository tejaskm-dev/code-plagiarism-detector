package com.integrityengine.domain;

import java.util.Objects;

/**
 * The outcome of comparing one pair of submissions. Immutable.
 */
public final class SimilarityResult {

    private final String leftSubmissionId;
    private final String rightSubmissionId;
    private final double similarityScore;
    private final String comparatorName;

    public SimilarityResult(String leftSubmissionId,
                            String rightSubmissionId,
                            double similarityScore,
                            String comparatorName) {
        this.leftSubmissionId = Objects.requireNonNull(leftSubmissionId, "leftSubmissionId");
        this.rightSubmissionId = Objects.requireNonNull(rightSubmissionId, "rightSubmissionId");
        this.similarityScore = similarityScore;
        this.comparatorName = Objects.requireNonNull(comparatorName, "comparatorName");
    }

    public String getLeftSubmissionId() {
        return leftSubmissionId;
    }

    public String getRightSubmissionId() {
        return rightSubmissionId;
    }

    public double getSimilarityScore() {
        return similarityScore;
    }

    public String getComparatorName() {
        return comparatorName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SimilarityResult other)) {
            return false;
        }
        return Double.compare(similarityScore, other.similarityScore) == 0
                && leftSubmissionId.equals(other.leftSubmissionId)
                && rightSubmissionId.equals(other.rightSubmissionId)
                && comparatorName.equals(other.comparatorName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(leftSubmissionId, rightSubmissionId, similarityScore, comparatorName);
    }

    @Override
    public String toString() {
        return "SimilarityResult[" + leftSubmissionId + " vs " + rightSubmissionId
                + " = " + similarityScore + " (" + comparatorName + ")]";
    }
}
