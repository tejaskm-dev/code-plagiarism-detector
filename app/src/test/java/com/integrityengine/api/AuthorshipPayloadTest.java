package com.integrityengine.api;

import static com.integrityengine.api.HttpSupport.field;
import static com.integrityengine.api.HttpSupport.files;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.javalin.Javalin;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PERMANENT REGRESSION TESTS for two failures that made working features invisible.
 *
 * <p><b>One.</b> The authorship classifier ran, produced a verdict for every submission
 * and recorded it in the report — and the HTTP layer published only
 * {@code aiResultCount}, a bare integer. The browser therefore had nothing to draw and
 * the whole feature looked unimplemented from the outside. Everything worked except
 * the one line that would have let anyone see it.
 *
 * <p><b>Two.</b> Every "Compare" button in the UI carried a {@code data-open-pair}
 * attribute and no listener was ever bound to it, so {@code openDiff()} was unreachable
 * and the buttons were inert markup.
 *
 * <p>Neither failed a test, because nothing asserted that the work reached the surface.
 * Both are the same class of bug — a finished feature with a severed last connection —
 * and these tests exist to make that class fail loudly.
 */
class AuthorshipPayloadTest {

    @TempDir
    Path workspace;

    private Javalin app;
    private HttpSupport http;

    /** Long enough to clear the model's minimum token count. */
    private static final String SOURCE = """
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
                public int count() { return scores.length; }
            }
            """;

    @BeforeEach
    void startServer() {
        app = new IntegrityApi(workspace.resolve("api.db")).server();
        app.start(0);
        http = new HttpSupport(app.port());
    }

    @AfterEach
    void stopServer() {
        if (app != null) {
            app.stop();
        }
    }

    private String analysedAssignment() throws Exception {
        String id = field(http.postForm("/api/v1/assignments", "name=test").body(), "id");
        assertEquals(200, http.postFiles("/api/v1/assignments/" + id + "/submissions",
                files("a.java", SOURCE, "b.java", SOURCE.replace("GradeBook", "MarkBook")))
                .statusCode());
        assertEquals(200, http.postEmpty("/api/v1/assignments/" + id + "/analyze").statusCode());
        return id;
    }

    @Test
    @DisplayName("Authorship reaches the browser as a score and a verdict, not just a count")
    void everyFileCarriesItsAuthorshipVerdict() throws Exception {
        String body = http.get("/api/v1/assignments/" + analysedAssignment() + "/results").body();

        assertTrue(body.contains("\"aiScore\""), "results must publish a per-file score: " + body);
        assertTrue(body.contains("\"aiVerdict\""), "results must publish a per-file verdict");
        assertTrue(body.contains("\"aiRationale\""), "results must publish the reasoning");
    }

    @Test
    @DisplayName("The cohort authorship summary is published")
    void theCohortSummaryIsPublished() throws Exception {
        String body = http.get("/api/v1/assignments/" + analysedAssignment() + "/results").body();

        assertTrue(body.contains("\"ai\""), "results must carry an ai summary block: " + body);
        assertTrue(body.contains("\"measured\""), "the summary must say how many files it judged");
        assertTrue(body.contains("\"unsure\""),
                "the summary must report undecided files rather than folding them into a verdict");
    }

    @Test
    @DisplayName("A verdict is one of the three the model is allowed to give")
    void verdictsAreConstrained() throws Exception {
        String body = http.get("/api/v1/assignments/" + analysedAssignment() + "/results").body();

        int at = body.indexOf("\"aiVerdict\":\"");
        assertTrue(at >= 0, "no verdict in payload");
        String verdict = body.substring(at + 13, body.indexOf('"', at + 13));
        assertTrue(List.of("generated", "human", "unsure", "unmeasured").contains(verdict),
                "unexpected verdict: " + verdict);
    }

    // ------------------------------------------------------- the severed handler

    @Test
    @DisplayName("Every data-open-pair button has a listener bound to it")
    void theCompareButtonsAreWiredToSomething() throws Exception {
        String app = asset("/app.js");

        assertTrue(app.contains("data-open-pair"), "the buttons should still exist");
        assertTrue(app.contains("[data-open-pair]"),
                "nothing selects data-open-pair, so every Compare button is inert markup");
        assertTrue(app.contains("openDiff("),
                "the selector must actually lead to openDiff");
    }

    @Test
    @DisplayName("The charting library is served locally, so the UI works with no internet")
    void chartsAreSelfHosted() throws Exception {
        assertEquals(200, http.get("/vendor/chart.min.js").statusCode(),
                "Chart.js must be vendored, not loaded from a CDN");
        assertEquals(200, http.get("/charts.js").statusCode());

        String page = asset("/");
        assertFalseContains(page, "https://cdn.");
        assertFalseContains(page, "http://cdn.");
        assertFalseContains(page, "unpkg.com");
        assertFalseContains(page, "jsdelivr");
    }

    private static void assertFalseContains(String haystack, String needle) {
        assertTrue(!haystack.contains(needle),
                "the page must not reference " + needle + " -- it has to render offline");
    }

    private String asset(String path) throws Exception {
        HttpResponse<String> response = http.get(path);
        assertEquals(200, response.statusCode(), path);
        return response.body();
    }
}
