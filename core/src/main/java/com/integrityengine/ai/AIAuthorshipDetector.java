package com.integrityengine.ai;

import com.integrityengine.domain.AIAuthorshipResult;
import com.integrityengine.domain.CodeSubmission;
import java.util.Objects;
import java.util.Optional;

/**
 * Combines the local stylometric signal with an optional LLM judgement.
 *
 * <p><b>BYOK by construction.</b> The API key is a per-call parameter — never a field,
 * never a constructor argument, never written to disk or a log, and never placed in the
 * returned result. Nothing in this class can retain it past the call that supplied it.
 *
 * <p><b>The local model is the default, not the fallback.</b> {@link AiAuthorshipModel}
 * is a trained classifier that needs no key, no network and no per-file cost, and it is
 * what runs when nobody supplies credentials. Supplying a key adds a second opinion on
 * top; it does not switch the module on. This reverses the earlier arrangement, in which
 * no key meant no result at all.
 *
 * <p><b>Four outcomes.</b>
 * <ul>
 *   <li><b>No key</b> — the local model's verdict alone, recorded under
 *       {@link AiAuthorshipModel#MODEL_NAME}.</li>
 *   <li><b>Key, judge answered</b> — a result combining both signals.</li>
 *   <li><b>Key, judge failed</b> — the local verdict alone, marked
 *       {@link #HEURISTIC_ONLY} so a reader can tell a degraded analysis from a full
 *       one. An unreachable or misbehaving model degrades the analysis; it never fails
 *       the batch.</li>
 *   <li><b>Unmeasurable file</b> — {@link Optional#empty()}, for a language with no
 *       tokenizer or a file too short to have a style at all. Staying silent is the
 *       honest answer there; a score computed from nine tokens would not be one.</li>
 * </ul>
 */
public final class AIAuthorshipDetector {

    /** Recorded as the model when the LLM call did not produce a usable verdict. */
    public static final String HEURISTIC_ONLY = "heuristic-only";

    /**
     * Votes within this distance of 0.5 mean the model is split and should be presented
     * as undecided rather than rounded into a verdict. Re-exported here so callers
     * outside this package can render the same three outcomes without reaching into
     * the model itself.
     */
    public static final double UNSURE_BAND = AiAuthorshipModel.UNSURE_BAND;

    /** @return one of {@code generated}, {@code human} or {@code unsure} */
    public static String verdictLabel(double score) {
        return switch (AiAuthorshipModel.verdict(score)) {
            case LIKELY_GENERATED -> "generated";
            case LIKELY_HUMAN -> "human";
            case UNSURE -> "unsure";
        };
    }

    /**
     * How much the external judgement counts relative to the local heuristic. The judge
     * is weighted higher because the heuristic cannot distinguish machine-written code
     * from a tidy student; neither weight is calibrated.
     */
    public static final double JUDGE_WEIGHT = 0.65;
    public static final double HEURISTIC_WEIGHT = 0.35;
    @SuppressWarnings("unused")
    private final StylometricExtractor extractor;
    private final AiAuthorshipModel model;
    private final LlmJudge judge;

    public AIAuthorshipDetector() {
        this(new StylometricExtractor(), new AiAuthorshipModel(), new AnthropicLlmJudge());
    }

    public AIAuthorshipDetector(LlmJudge judge) {
        this(new StylometricExtractor(), new AiAuthorshipModel(), judge);
    }

    AIAuthorshipDetector(StylometricExtractor extractor, AiAuthorshipModel model, LlmJudge judge) {
        this.extractor = Objects.requireNonNull(extractor, "extractor");
        this.model = Objects.requireNonNull(model, "model");
        this.judge = Objects.requireNonNull(judge, "judge");
    }

    /**
     * @param apiKey     caller-supplied key; null or blank runs the local model only
     * @param submission the submission to judge
     * @return the verdict, or empty when the file cannot be measured at all
     */
    public Optional<AIAuthorshipResult> analyze(String apiKey, CodeSubmission submission) {
        Objects.requireNonNull(submission, "submission");

        Optional<Double> local = model.score(submission);
        if (local.isEmpty()) {
            return Optional.empty();
        }
        double stylometric = local.get();

        if (apiKey == null || apiKey.isBlank()) {
            return Optional.of(new AIAuthorshipResult(
                    submission.getSubmissionId(),
                    stylometric,
                    stylometric,
                    AiAuthorshipModel.MODEL_NAME,
                    explain(stylometric)));
        }

        Optional<LlmVerdict> verdict = askJudge(apiKey, submission);

        if (verdict.isEmpty()) {
            return Optional.of(new AIAuthorshipResult(
                    submission.getSubmissionId(),
                    stylometric,
                    stylometric,
                    HEURISTIC_ONLY,
                    "External judgement unavailable. " + explain(stylometric)));
        }

        LlmVerdict answer = verdict.get();
        double combined = HEURISTIC_WEIGHT * stylometric + JUDGE_WEIGHT * answer.getAiLikelihood();

        return Optional.of(new AIAuthorshipResult(
                submission.getSubmissionId(),
                Math.max(0.0, Math.min(1.0, combined)),
                stylometric,
                answer.getModel(),
                answer.getRationale()));
    }

    /**
     * Says what the vote means in words, including saying that it means nothing much.
     * A number near the middle is a split forest, and a reader who is handed "0.51"
     * without that sentence will read it as a finding.
     */
    private String explain(double score) {
        return switch (AiAuthorshipModel.verdict(score)) {
            case LIKELY_GENERATED -> String.format(
                    "%.0f%% of the trained model's decision trees read this file as generated. "
                    + "Regular naming, even structure and uniform commenting are what drives "
                    + "that. A meticulous student produces the same pattern, so this is a "
                    + "reason to ask, not a finding.", score * 100);
            case LIKELY_HUMAN -> String.format(
                    "%.0f%% of the trained model's decision trees read this file as generated, "
                    + "which is low. The file carries the irregularity of someone editing as "
                    + "they went.", score * 100);
            case UNSURE -> String.format(
                    "The model is split — %.0f%% of its decision trees say generated, which is "
                    + "close enough to a coin toss that it is not evidence in either "
                    + "direction. Treat this file as unclassified.", score * 100);
        };
    }

    /**
     * The contract says an {@link LlmJudge} never throws. This does not trust it: a
     * third-party or future implementation that breaks that promise degrades the
     * analysis here rather than propagating out and killing the batch.
     */
    private Optional<LlmVerdict> askJudge(String apiKey, CodeSubmission submission) {
        try {
            Optional<LlmVerdict> verdict = judge.judge(apiKey, submission);
            return verdict == null ? Optional.empty() : verdict;
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
