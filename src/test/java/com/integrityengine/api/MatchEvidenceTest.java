package com.integrityengine.api;

import static com.integrityengine.api.HttpSupport.field;
import static com.integrityengine.api.HttpSupport.files;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.integrityengine.domain.Token;
import com.integrityengine.tokenizer.Language;
import com.integrityengine.tokenizer.TokenizerFactory;
import io.javalin.Javalin;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Evidence is the difference between "these files are 98% similar" and "their
 * average() is your mean(), and they renamed total to sum". The second is a finding a
 * marker can act on; the first is a number they have to take on trust.
 */
class MatchEvidenceTest {

    @TempDir
    Path workspace;

    private Javalin app;
    private HttpSupport http;

    private static final String ORIGINAL = """
            public class GradeBook {
                private final int[] scores;
                public GradeBook(int[] scores) { this.scores = scores; }
                public double average() {
                    int total = 0;
                    for (int score : scores) { total += score; }
                    return (double) total / scores.length;
                }
                public int highest() {
                    int best = scores[0];
                    for (int i = 1; i < scores.length; i++) {
                        if (scores[i] > best) { best = scores[i]; }
                    }
                    return best;
                }
            }
            """;

    /** Same program: every identifier renamed, methods reordered. */
    private static final String DISGUISED = """
            public class MarkRegister {
                private final int[] marks;
                public MarkRegister(int[] marks) { this.marks = marks; }
                public int peak() {
                    int top = marks[0];
                    for (int k = 1; k < marks.length; k++) {
                        if (marks[k] > top) { top = marks[k]; }
                    }
                    return top;
                }
                public double mean() {
                    int sum = 0;
                    for (int mark : marks) { sum += mark; }
                    return (double) sum / marks.length;
                }
            }
            """;

    @BeforeEach
    void startServer() {
        app = new IntegrityApi(workspace.resolve("evidence.db")).server();
        app.start(0);
        http = new HttpSupport(app.port());
    }

    @AfterEach
    void stopServer() {
        if (app != null) {
            app.stop();
        }
    }

    private String pairBody() throws Exception {
        String id = field(http.postForm("/api/v1/assignments", "name=evidence").body(), "id");
        http.postFiles("/api/v1/assignments/" + id + "/submissions",
                files("alice.java", ORIGINAL, "dana.java", DISGUISED));
        http.postEmpty("/api/v1/assignments/" + id + "/analyze");

        String listing = http.get("/api/v1/assignments/" + id + "/submissions").body();
        String left = idFor(listing, "alice.java");
        String right = idFor(listing, "dana.java");
        return http.get("/api/v1/assignments/" + id + "/pair"
                + "?left=" + URLEncoder.encode(left, StandardCharsets.UTF_8)
                + "&right=" + URLEncoder.encode(right, StandardCharsets.UTF_8)).body();
    }

    private static String idFor(String listing, String filename) {
        int at = listing.indexOf("\"filename\":\"" + filename + "\"");
        String before = listing.substring(0, at);
        int idAt = before.lastIndexOf("\"submissionId\":\"");
        int start = idAt + "\"submissionId\":\"".length();
        return listing.substring(start, listing.indexOf('"', start));
    }

    // --------------------------------------------------- the alignment it rests on

    @ParameterizedTest
    @EnumSource(Language.class)
    @DisplayName("Normalised and lexical token streams are index-aligned in every language")
    void tokenStreamsStayAligned(Language language) {
        // Everything in MatchEvidence depends on position i meaning the same token in
        // both streams. That holds because generalisation rewrites tokens in place --
        // if a tokenizer ever inserted or dropped one, the evidence would silently
        // start naming the wrong variables.
        var tokenizer = new TokenizerFactory().forLanguage(language);
        String source = language == Language.PYTHON
                ? "def average(values):\n    total = 0\n    for value in values:\n        total += value\n    return total\n"
                : "int average(int[] values){ int total = 0; for (int i=0;i<10;i++){ total += values[i]; } return total; }";

        List<Token> normalised = tokenizer.tokenize(source);
        List<Token> lexical = tokenizer.lexicalTokens(source);

        assertEquals(normalised.size(), lexical.size(), language + " streams differ in length");
        for (int i = 0; i < normalised.size(); i++) {
            assertEquals(normalised.get(i).getKind(), lexical.get(i).getKind(),
                    language + " kinds diverge at index " + i);
            assertEquals(normalised.get(i).getLine(), lexical.get(i).getLine(),
                    language + " line numbers diverge at index " + i);
        }
    }

    // ------------------------------------------------------------------- evidence

    @Test
    @DisplayName("Matching routines are named on both sides, so a finding reads as prose")
    void matchingRoutinesAreNamed() throws Exception {
        String body = pairBody();

        assertTrue(body.contains("\"routines\""), body);
        // average() on one side is mean() on the other; highest() is peak().
        assertTrue(body.contains("average"), "the original routine should be named: " + body);
        assertTrue(body.contains("mean") || body.contains("peak"),
                "the renamed routine should be named: " + body);
        assertTrue(body.contains("\"leftStartLine\""), body);
    }

    @Test
    @DisplayName("The identifiers that were swapped are listed with their counts")
    void renamedIdentifiersAreReported() throws Exception {
        String body = pairBody();

        assertTrue(body.contains("\"renames\""), body);
        // scores -> marks and total -> sum are the substitutions actually made.
        assertTrue(body.contains("scores") && body.contains("marks"),
                "the scores/marks rename should appear: " + body);
        assertTrue(body.contains("\"occurrences\""), body);
        assertTrue(Integer.parseInt(field(body, "renamedTokens")) > 0,
                "a wholly renamed copy must report renamed identifiers");
    }

    @Test
    @DisplayName("A verbatim copy reports no renames at all — the absence is evidence too")
    void anUntouchedCopyShowsNothingRenamed() throws Exception {
        String id = field(http.postForm("/api/v1/assignments", "name=verbatim").body(), "id");
        // Byte-identical content under two filenames: a copy with nothing touched at
        // all. An earlier version of this test renamed the class to tell the files
        // apart, and the evidence correctly reported that rename — the fixture was
        // wrong, not the tool.
        http.postFiles("/api/v1/assignments/" + id + "/submissions",
                files("a.java", ORIGINAL, "b.java", ORIGINAL));
        http.postEmpty("/api/v1/assignments/" + id + "/analyze");

        String listing = http.get("/api/v1/assignments/" + id + "/submissions").body();
        String body = http.get("/api/v1/assignments/" + id + "/pair"
                + "?left=" + URLEncoder.encode(idFor(listing, "a.java"), StandardCharsets.UTF_8)
                + "&right=" + URLEncoder.encode(idFor(listing, "b.java"), StandardCharsets.UTF_8)).body();

        assertEquals(0, Integer.parseInt(field(body, "renamedTokens")),
                "nothing was renamed, so nothing should be reported as renamed: " + body);
        assertTrue(Integer.parseInt(field(body, "identicalTokens")) > 0, body);
    }

    @Test
    void routineNamesAreRecoveredAcrossLanguages() {
        var java = new TokenizerFactory().forLanguage(Language.JAVA);
        List<Token> lexical = java.lexicalTokens(ORIGINAL);
        // A position inside the body of highest() should resolve to that method.
        int inside = -1;
        for (int i = 0; i < lexical.size(); i++) {
            if (lexical.get(i).getValue().equals("best")) { inside = i + 3; break; }
        }
        assertTrue(inside > 0, "fixture should contain the identifier the test looks for");
        assertEquals("highest", MatchEvidence.enclosingRoutine(lexical, inside));
    }

    @Test
    void codeOutsideAnyRoutineIsLabelledHonestly() {
        var java = new TokenizerFactory().forLanguage(Language.JAVA);
        List<Token> lexical = java.lexicalTokens("int x = 1; int y = 2;");

        assertEquals("top level", MatchEvidence.enclosingRoutine(lexical, lexical.size() - 1));
    }
}
