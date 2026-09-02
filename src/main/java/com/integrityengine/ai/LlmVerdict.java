package com.integrityengine.ai;

import java.util.Objects;

/** A judgement returned by an {@link LlmJudge}. Immutable. */
public final class LlmVerdict {

    private final double aiLikelihood;
    private final String rationale;
    private final String model;

    public LlmVerdict(double aiLikelihood, String rationale, String model) {
        this.aiLikelihood = aiLikelihood;
        this.rationale = Objects.requireNonNull(rationale, "rationale");
        this.model = Objects.requireNonNull(model, "model");
    }

    public double getAiLikelihood() {
        return aiLikelihood;
    }

    public String getRationale() {
        return rationale;
    }

    public String getModel() {
        return model;
    }
}
