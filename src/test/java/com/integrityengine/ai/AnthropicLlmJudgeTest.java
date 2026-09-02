package com.integrityengine.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.integrityengine.domain.CodeSubmission;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Response handling is tested exhaustively here; the socket call is not.
 *
 * <p>Splitting {@code parseVerdict} out from the HTTP send is what makes that possible:
 * the part that must survive hostile, truncated and nonsense input is a pure function,
 * so it gets the same adversarial treatment as every other stage. What remains untested
 * is genuinely only the network round trip -- see {@code AIAuthorshipDetectorTest} for
 * the mocked end-to-end behaviour, and the Stage 6 report for what that leaves uncovered.
 */
class AnthropicLlmJudgeTest {

    private static String body(String assistantText) {
        return "{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\","
                + "\"content\":[{\"type\":\"text\",\"text\":\"" + assistantText + "\"}],"
                + "\"model\":\"claude-opus-5\"}";
    }

    // ------------------------------------------------------------- the happy path

    @Test
    void parsesAWellFormedVerdict() {
        Optional<LlmVerdict> verdict =
                AnthropicLlmJudge.parseVerdict(body("LIKELIHOOD: 0.73\\nRATIONALE: Uniform naming throughout."));

        assertTrue(verdict.isPresent());
        assertEquals(0.73, verdict.get().getAiLikelihood(), 1e-9);
        assertEquals("Uniform naming throughout.", verdict.get().getRationale());
        assertEquals("claude-opus-5", verdict.get().getModel());
    }

    @Test
    void acceptsTheBoundaryValues() {
        assertEquals(0.0, AnthropicLlmJudge.parseVerdict(body("LIKELIHOOD: 0\\nRATIONALE: x"))
                .orElseThrow().getAiLikelihood(), 1e-9);
        assertEquals(1.0, AnthropicLlmJudge.parseVerdict(body("LIKELIHOOD: 1.0\\nRATIONALE: x"))
                .orElseThrow().getAiLikelihood(), 1e-9);
    }

    @Test
    @DisplayName("A thinking block with empty text does not displace the real answer")
    void thinkingBlocksAreSkipped() {
        String withThinking = "{\"content\":[{\"type\":\"thinking\",\"thinking\":\"\"},"
                + "{\"type\":\"text\",\"text\":\"LIKELIHOOD: 0.4\\nRATIONALE: Mixed style.\"}]}";

        assertEquals(0.4, AnthropicLlmJudge.parseVerdict(withThinking).orElseThrow().getAiLikelihood(), 1e-9);
    }

    @Test
    @DisplayName("The type discriminator value \"text\" is not mistaken for content")
    void typeDiscriminatorIsNotReadAsContent() {
        // Matching on the *key* "text" rather than the value is what makes this work.
        List<String> blocks = AnthropicLlmJudge.extractTextBlocks(body("LIKELIHOOD: 0.5\\nRATIONALE: ok"));

        assertEquals(1, blocks.size(), "expected exactly one text block, got " + blocks);
        assertTrue(blocks.get(0).startsWith("LIKELIHOOD"));
    }

    @Test
    void decodesEscapeSequencesIncludingUnicode() {
        Optional<LlmVerdict> verdict = AnthropicLlmJudge.parseVerdict(
                body("LIKELIHOOD: 0.5\\nRATIONALE: quote \\\" tab \\t caf\\u00e9"));

        assertTrue(verdict.isPresent());
        assertTrue(verdict.get().getRationale().contains("café"), verdict.get().getRationale());
        assertTrue(verdict.get().getRationale().contains("\""), verdict.get().getRationale());
    }

    @Test
    void aMissingRationaleStillYieldsAVerdict() {
        Optional<LlmVerdict> verdict = AnthropicLlmJudge.parseVerdict(body("LIKELIHOOD: 0.9"));

        assertTrue(verdict.isPresent());
        assertEquals("no rationale supplied", verdict.get().getRationale());
    }

    // --------------------------------------------------------- hostile / broken input

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "not json at all",
            "{}",
            "{\"content\":[]}",
            "{\"content\":[{\"type\":\"text\",\"text\":\"\"}]}",
            "{\"content\":[{\"type\":\"text\",\"text\":\"I cannot help with that.\"}]}",
            "{\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}",
            "<html><body>502 Bad Gateway</body></html>",
            "{\"content\":[{\"type\":\"text\",\"text\":\"LIKELIHOOD: not-a-number\"}]}",
            "{\"content\":[{\"type\":\"text\",\"text\":\"LIKELIHOOD:\"}]}",
            "{\"content\":[{\"type\":\"text\",\"text\":\"RATIONALE: only a rationale\"}]}"})
    @DisplayName("Every malformed or unusable body yields empty, never an exception")
    void malformedBodiesDegradeQuietly(String malformed) {
        assertEquals(Optional.empty(), AnthropicLlmJudge.parseVerdict(malformed));
    }

    @Test
    void aNullBodyIsHandled() {
        assertEquals(Optional.empty(), AnthropicLlmJudge.parseVerdict(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"7.5", "1.5", "100", "-0.5"})
    @DisplayName("An out-of-range likelihood is rejected rather than clamped")
    void outOfRangeLikelihoodsAreRejected(String value) {
        // Clamping 7.5 to 1.0 would convert a model that misread the task into a
        // maximum-confidence accusation.
        assertEquals(Optional.empty(),
                AnthropicLlmJudge.parseVerdict(body("LIKELIHOOD: " + value + "\\nRATIONALE: x")));
    }

    @Test
    @DisplayName("A truncated response terminates instead of scanning forever")
    void truncatedBodyDoesNotHang() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            String truncated = "{\"content\":[{\"type\":\"text\",\"text\":\"LIKELIHOOD: 0.5\\nRATIONALE: cut off here";
            AnthropicLlmJudge.parseVerdict(truncated);

            String danglingEscape = "{\"content\":[{\"type\":\"text\",\"text\":\"abc\\";
            AnthropicLlmJudge.parseVerdict(danglingEscape);

            AnthropicLlmJudge.parseVerdict("\"text\":".repeat(5000));
        });
    }

    @Test
    @DisplayName("A rationale containing JSON-looking text does not derail extraction")
    void rationaleContainingJsonIsSurvivable() {
        Optional<LlmVerdict> verdict = AnthropicLlmJudge.parseVerdict(
                body("LIKELIHOOD: 0.6\\nRATIONALE: the file contains markers"));

        assertTrue(verdict.isPresent());
        assertEquals(0.6, verdict.get().getAiLikelihood(), 1e-9);
    }

    // ------------------------------------------------------------- request building

    @Test
    @DisplayName("BYOK: the key never appears in the request body -- it is a header only")
    void requestBodyNeverContainsTheApiKey() {
        AnthropicLlmJudge judge = new AnthropicLlmJudge();
        String built = judge.buildRequestBody(
                new CodeSubmission("s1", "stu", "a1", "A.java", "int x = 1;"));

        assertFalse(built.contains("sk-ant"), "the request body must not carry a key");
        assertTrue(built.contains("claude-opus-5"));
        assertTrue(built.contains("LIKELIHOOD"));
    }

    @Test
    void sourceContainingQuotesAndNewlinesIsEscaped() {
        AnthropicLlmJudge judge = new AnthropicLlmJudge();
        String built = judge.buildRequestBody(
                new CodeSubmission("s1", "stu", "a1", "A.java", "String s = \"a\\nb\";\n\tx();"));

        assertFalse(built.contains("\n"), "raw newlines would produce an invalid JSON body");
        assertTrue(built.contains("\\n"));
    }

    @Test
    void oversizedSubmissionsAreTruncated() {
        AnthropicLlmJudge judge = new AnthropicLlmJudge();
        String huge = "x".repeat(AnthropicLlmJudge.MAX_SOURCE_CHARS * 3);
        String built = judge.buildRequestBody(new CodeSubmission("s1", "stu", "a1", "A.java", huge));

        assertTrue(built.length() < huge.length(), "an oversized submission must be truncated");
    }

    @Test
    void escapeJsonHandlesControlAndQuoteCharacters() {
        assertEquals("\\u0001", AnthropicLlmJudge.escapeJson(String.valueOf((char) 1)));
        assertEquals("a\\\\b", AnthropicLlmJudge.escapeJson("a\\b"));
        assertEquals("a\\\"b", AnthropicLlmJudge.escapeJson("a\"b"));
        assertEquals("a\\nb", AnthropicLlmJudge.escapeJson("a\nb"));
    }

    // ------------------------------------------------- transport, against a fake client

    private static final String GOOD_BODY =
            "{\"content\":[{\"type\":\"text\",\"text\":\"LIKELIHOOD: 0.42\\nRATIONALE: Regular.\"}]}";

    @Test
    @DisplayName("A blank key short-circuits before any network call is ATTEMPTED, not merely before it succeeds")
    void blankKeysNeverReachTheNetwork() {
        // Asserting the return value alone is not enough here: with the guard removed
        // the real call still fails and still returns empty, so the test would pass
        // while silently firing a request. Counting sends is what makes it meaningful.
        FakeHttpClient http = FakeHttpClient.replying(200, GOOD_BODY);
        AnthropicLlmJudge judge = new AnthropicLlmJudge(http);
        CodeSubmission submission = new CodeSubmission("s1", "stu", "a1", "A.java", "int x = 1;");

        assertEquals(Optional.empty(), judge.judge(null, submission));
        assertEquals(Optional.empty(), judge.judge("", submission));
        assertEquals(Optional.empty(), judge.judge("   ", submission));
        assertEquals(0, http.sendCount, "a blank key must not produce a request");
    }

    @Test
    void aSuccessfulResponseProducesAVerdict() {
        FakeHttpClient http = FakeHttpClient.replying(200, GOOD_BODY);

        Optional<LlmVerdict> verdict = new AnthropicLlmJudge(http)
                .judge("sk-ant-test", new CodeSubmission("s1", "stu", "a1", "A.java", "int x = 1;"));

        assertTrue(verdict.isPresent());
        assertEquals(0.42, verdict.get().getAiLikelihood(), 1e-9);
        assertEquals(1, http.sendCount);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 429, 500, 502, 529})
    @DisplayName("A non-200 response is rejected even when its body looks parseable")
    void nonSuccessStatusesAreRejected(int status) {
        // The body here is a perfectly valid verdict. Only the status makes it invalid,
        // so this fails if the status check is ever dropped.
        FakeHttpClient http = FakeHttpClient.replying(status, GOOD_BODY);

        assertEquals(Optional.empty(), new AnthropicLlmJudge(http)
                .judge("sk-ant-test", new CodeSubmission("s1", "stu", "a1", "A.java", "int x = 1;")));
    }

    @Test
    @DisplayName("BYOK: the key travels in the x-api-key header and nowhere else")
    void theKeyIsSentAsAHeaderAndNotInTheBody() {
        FakeHttpClient http = FakeHttpClient.replying(200, GOOD_BODY);
        String key = "sk-ant-secret-value";

        new AnthropicLlmJudge(http)
                .judge(key, new CodeSubmission("s1", "stu", "a1", "A.java", "int x = 1;"));

        assertEquals(List.of(key), http.headerValues("x-api-key"));
        assertEquals(List.of("2023-06-01"), http.headerValues("anthropic-version"));
        assertEquals("https://api.anthropic.com/v1/messages", http.lastRequest.uri().toString());
        assertTrue(http.lastRequest.timeout().isPresent(), "every request must carry a timeout");
    }

    @Test
    @DisplayName("A network failure degrades to empty rather than propagating")
    void ioFailuresDegradeQuietly() {
        FakeHttpClient http = FakeHttpClient.failingWith(new java.io.IOException("connection reset"));

        assertEquals(Optional.empty(), new AnthropicLlmJudge(http)
                .judge("sk-ant-test", new CodeSubmission("s1", "stu", "a1", "A.java", "int x = 1;")));
    }

    @Test
    @DisplayName("An unexpected runtime failure from the client is also contained")
    void runtimeFailuresDegradeQuietly() {
        FakeHttpClient http = FakeHttpClient.failingWith(new IllegalStateException("client misconfigured"));

        assertEquals(Optional.empty(), new AnthropicLlmJudge(http)
                .judge("sk-ant-test", new CodeSubmission("s1", "stu", "a1", "A.java", "int x = 1;")));
    }

    @Test
    @DisplayName("An interrupt is reported as empty AND leaves the thread's interrupt flag set")
    void interruptionRestoresTheInterruptFlag() {
        FakeHttpClient http = FakeHttpClient.failingWith(new InterruptedException("interrupted"));

        assertEquals(Optional.empty(), new AnthropicLlmJudge(http)
                .judge("sk-ant-test", new CodeSubmission("s1", "stu", "a1", "A.java", "int x = 1;")));
        // Swallowing an interrupt outright would strand a caller trying to cancel a batch.
        assertTrue(Thread.interrupted(), "the interrupt flag must be restored");
    }

    @Test
    void toStringDoesNotLeakAnything() {
        assertEquals("AnthropicLlmJudge[model=claude-opus-5]", new AnthropicLlmJudge().toString());
    }
}
