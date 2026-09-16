package com.integrityengine.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.integrityengine.domain.AIAuthorshipResult;
import com.integrityengine.domain.CodeSubmission;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The detector is tested against a stubbed {@link LlmJudge} rather than a live model.
 *
 * <p>Stated plainly: this is the coverage limit of the stage. Everything about how the
 * detector *reacts* to a judge -- success, failure, silence, misbehaviour -- is tested
 * here deterministically. What is not tested anywhere is the real network round trip
 * against Anthropic's servers, because that is non-deterministic, costs money, and
 * would make the build depend on an external service being up.
 */
class AIAuthorshipDetectorTest {

    private static final String KEY = "sk-ant-test-not-a-real-key";

    private static CodeSubmission submission() {
        return new CodeSubmission("sub-1", "student-1", "assignment-1", "A.java", tidySource());
    }

    /** Records what it was asked, so BYOK handling can be asserted. */
    private static final class StubJudge implements LlmJudge {
        private final Optional<LlmVerdict> answer;
        private final RuntimeException failure;
        final List<String> keysSeen = new ArrayList<>();
        int calls;

        StubJudge(Optional<LlmVerdict> answer) {
            this(answer, null);
        }

        StubJudge(Optional<LlmVerdict> answer, RuntimeException failure) {
            this.answer = answer;
            this.failure = failure;
        }

        @Override
        public Optional<LlmVerdict> judge(String apiKey, CodeSubmission submission) {
            calls++;
            keysSeen.add(apiKey);
            if (failure != null) {
                throw failure;
            }
            return answer;
        }
    }

    private static AIAuthorshipDetector detectorWith(LlmJudge judge) {
        return new AIAuthorshipDetector(
                new StylometricExtractor(), new AiAuthorshipModel(), judge);
    }

    // ------------------------------------------------------------------ skippable

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    @DisplayName("Without a key the local model still answers, and no network call is made")
    void withoutAKeyTheLocalModelStillAnswers(String key) {
        StubJudge judge = new StubJudge(Optional.empty());

        AIAuthorshipResult result = detectorWith(judge).analyze(key, submission()).orElseThrow();

        assertEquals(AiAuthorshipModel.MODEL_NAME, result.getModelUsed());
        assertEquals(0, judge.calls, "the judge must not be contacted without a key");
        assertEquals(result.getStylometricAnomalyScore(), result.getAiLikelihood(), 1e-9);
    }

    @Test
    @DisplayName("A file too short to have a style is left unjudged rather than guessed at")
    void anUnmeasurableFileReturnsNothing() {
        CodeSubmission tiny =
                new CodeSubmission("s", "stu", "asg", "Tiny.java", "class A {}\n");

        assertEquals(Optional.empty(),
                detectorWith(new StubJudge(Optional.empty())).analyze(KEY, tiny));
    }

    @Test
    @DisplayName("A language with no tokenizer is left unjudged")
    void anUnknownLanguageReturnsNothing() {
        CodeSubmission other =
                new CodeSubmission("s", "stu", "asg", "notes.txt", tidySource());

        assertEquals(Optional.empty(),
                detectorWith(new StubJudge(Optional.empty())).analyze(KEY, other));
    }

    @Test
    @DisplayName("Every rationale states what the score does not prove")
    void theRationaleNeverReadsAsAnAccusation() {
        AIAuthorshipResult result =
                detectorWith(new StubJudge(Optional.empty())).analyze(null, submission()).orElseThrow();

        String text = result.getRationale().toLowerCase();
        assertTrue(text.contains("student") || text.contains("unclassified")
                        || text.contains("coin toss") || text.contains("irregularity"),
                "rationale must qualify itself: " + result.getRationale());
    }

    // ------------------------------------------------------------- the verdict band

    @Test
    @DisplayName("A split forest is reported as unsure, not rounded into a verdict")
    void theMiddleOfTheRangeIsUnsure() {
        assertEquals(AiAuthorshipModel.Verdict.UNSURE, AiAuthorshipModel.verdict(0.50));
        assertEquals(AiAuthorshipModel.Verdict.UNSURE, AiAuthorshipModel.verdict(0.55));
        assertEquals(AiAuthorshipModel.Verdict.UNSURE, AiAuthorshipModel.verdict(0.45));
        assertEquals(AiAuthorshipModel.Verdict.LIKELY_GENERATED, AiAuthorshipModel.verdict(0.61));
        assertEquals(AiAuthorshipModel.Verdict.LIKELY_HUMAN, AiAuthorshipModel.verdict(0.39));
    }

    @Test
    @DisplayName("The band is symmetric: neither class is easier to be accused of")
    void theBandIsSymmetric() {
        for (double d = 0.0; d <= 0.5; d += 0.01) {
            AiAuthorshipModel.Verdict low = AiAuthorshipModel.verdict(0.5 - d);
            AiAuthorshipModel.Verdict high = AiAuthorshipModel.verdict(0.5 + d);
            assertEquals(low == AiAuthorshipModel.Verdict.UNSURE,
                    high == AiAuthorshipModel.Verdict.UNSURE,
                    "asymmetric at offset " + d);
        }
    }

    // ------------------------------------------------------------- the full path

    @Test
    void withAKeyAndAWorkingJudgeBothSignalsAreCombined() {
        LlmVerdict verdict = new LlmVerdict(0.80, "Very uniform structure.", "claude-opus-5");
        AIAuthorshipDetector detector = detectorWith(new StubJudge(Optional.of(verdict)));

        AIAuthorshipResult result = detector.analyze(KEY, submission()).orElseThrow();
        double heuristic = new AiAuthorshipModel().score(submission()).orElseThrow();

        assertEquals(AIAuthorshipDetector.HEURISTIC_WEIGHT * heuristic
                + AIAuthorshipDetector.JUDGE_WEIGHT * 0.80, result.getAiLikelihood(), 1e-9);
        assertEquals(heuristic, result.getStylometricAnomalyScore(), 1e-9);
        assertEquals("claude-opus-5", result.getModelUsed());
        assertEquals("Very uniform structure.", result.getRationale());
    }

    @Test
    void theKeyIsPassedThroughToTheJudgeUnchanged() {
        StubJudge judge = new StubJudge(Optional.of(new LlmVerdict(0.5, "x", "m")));

        detectorWith(judge).analyze(KEY, submission());

        assertEquals(List.of(KEY), judge.keysSeen);
    }

    @Test
    @DisplayName("BYOK: the key never reaches the result object")
    void theKeyNeverAppearsInTheResult() {
        AIAuthorshipDetector detector = detectorWith(
                new StubJudge(Optional.of(new LlmVerdict(0.5, "fine", "claude-opus-5"))));

        AIAuthorshipResult result = detector.analyze(KEY, submission()).orElseThrow();

        assertFalse(result.toString().contains(KEY));
        assertFalse(result.getRationale().contains(KEY));
        assertFalse(result.getModelUsed().contains(KEY));
        assertFalse(result.getSubmissionId().contains(KEY));
    }

    // -------------------------------------------------------------- degradation

    @Test
    @DisplayName("A judge that returns nothing degrades to heuristic-only, marked as such")
    void anUnavailableJudgeDegradesGracefully() {
        AIAuthorshipDetector detector = detectorWith(new StubJudge(Optional.empty()));

        AIAuthorshipResult result = detector.analyze(KEY, submission()).orElseThrow();
        double heuristic = new AiAuthorshipModel().score(submission()).orElseThrow();

        assertEquals(AIAuthorshipDetector.HEURISTIC_ONLY, result.getModelUsed());
        assertEquals(heuristic, result.getAiLikelihood(), 1e-9);
        assertEquals(heuristic, result.getStylometricAnomalyScore(), 1e-9);
        assertTrue(result.getRationale().toLowerCase().contains("unavailable"),
                "a degraded result must say so: " + result.getRationale());
    }

    @Test
    @DisplayName("A judge that throws must not take the batch down with it")
    void aThrowingJudgeIsContained() {
        // The LlmJudge contract says implementations never throw. The detector does not
        // trust that -- a future or third-party implementation breaking the contract
        // should degrade one submission, not fail the whole run.
        AIAuthorshipDetector detector = detectorWith(
                new StubJudge(null, new IllegalStateException("connection reset")));

        AIAuthorshipResult result = detector.analyze(KEY, submission()).orElseThrow();

        assertEquals(AIAuthorshipDetector.HEURISTIC_ONLY, result.getModelUsed());
    }

    @Test
    @DisplayName("A judge returning null rather than Optional.empty is also contained")
    void aNullReturningJudgeIsContained() {
        AIAuthorshipDetector detector = detectorWith(new LlmJudge() {
            @Override
            public Optional<LlmVerdict> judge(String apiKey, CodeSubmission submission) {
                return null;
            }
        });

        assertEquals(AIAuthorshipDetector.HEURISTIC_ONLY,
                detector.analyze(KEY, submission()).orElseThrow().getModelUsed());
    }

    // ------------------------------------------------------------------ bounds

    @Test
    void combinedScoresStayWithinBounds() {
        for (double likelihood : new double[] {0.0, 0.25, 0.5, 0.75, 1.0}) {
            AIAuthorshipDetector detector = detectorWith(
                    new StubJudge(Optional.of(new LlmVerdict(likelihood, "x", "m"))));

            double score = detector.analyze(KEY, submission()).orElseThrow().getAiLikelihood();
            assertTrue(score >= 0.0 && score <= 1.0, "score out of bounds: " + score);
        }
    }

    @Test
    void weightsSumToOne() {
        assertEquals(1.0, AIAuthorshipDetector.JUDGE_WEIGHT + AIAuthorshipDetector.HEURISTIC_WEIGHT, 1e-9);
    }

    @Test
    void nullSubmissionIsRejected() {
        assertThrows(NullPointerException.class,
                () -> detectorWith(new StubJudge(Optional.empty())).analyze(KEY, null));
        assertThrows(NullPointerException.class, () -> new AIAuthorshipDetector(
                null, new AiAuthorshipModel(), new StubJudge(Optional.empty())));
        assertThrows(NullPointerException.class, () -> new AIAuthorshipDetector(
                new StylometricExtractor(), null, new StubJudge(Optional.empty())));
        assertThrows(NullPointerException.class, () -> new AIAuthorshipDetector(
                new StylometricExtractor(), new AiAuthorshipModel(), null));
    }

    private static String tidySource() {
        return """
                public class OrderProcessor {
                    private final List<Order> orders;

                    /**
                     * Creates a new order processor.
                     */
                    public OrderProcessor(List<Order> orders) {
                        this.orders = orders;
                    }

                    /**
                     * Calculates the total value of all orders.
                     */
                    public double calculateTotal() {
                        double total = 0.0;
                        for (Order order : orders) {
                            total += order.getAmount();
                        }
                        return total;
                    }
                }
                """;
    }
}
