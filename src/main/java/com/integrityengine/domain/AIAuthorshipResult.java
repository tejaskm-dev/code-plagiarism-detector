package com.integrityengine.domain;

import java.util.Objects;

/**
 * Verdict from the AI-authorship module for a single submission. Immutable.
 *
 * <p>Carries the model that produced it so a report can state provenance — under BYOK
 * the caller's key decides which model ran.
 */
public final class AIAuthorshipResult {

    private final String submissionId;
    private final double aiLikelihood;
    private final double stylometricAnomalyScore;
    private final String modelUsed;
    private final String rationale;

    public AIAuthorshipResult(String submissionId,
                              double aiLikelihood,
                              double stylometricAnomalyScore,
                              String modelUsed,
                              String rationale) {
        this.submissionId = Objects.requireNonNull(submissionId, "submissionId");
        this.aiLikelihood = aiLikelihood;
        this.stylometricAnomalyScore = stylometricAnomalyScore;
        this.modelUsed = Objects.requireNonNull(modelUsed, "modelUsed");
        this.rationale = Objects.requireNonNull(rationale, "rationale");
    }

    public String getSubmissionId() {
        return submissionId;
    }

    public double getAiLikelihood() {
        return aiLikelihood;
    }

    public double getStylometricAnomalyScore() {
        return stylometricAnomalyScore;
    }

    public String getModelUsed() {
        return modelUsed;
    }

    public String getRationale() {
        return rationale;
    }

    @Override
    public String toString() {
        return "AIAuthorshipResult[" + submissionId + " likelihood=" + aiLikelihood
                + " model=" + modelUsed + "]";
    }
}
