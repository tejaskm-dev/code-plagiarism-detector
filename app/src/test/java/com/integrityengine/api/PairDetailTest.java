package com.integrityengine.api;

import static com.integrityengine.api.HttpSupport.field;
import static com.integrityengine.api.HttpSupport.files;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.javalin.Javalin;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The pair-detail endpoint exists so a reviewer can open ANY comparison, not only the
 * ones the statistics flagged. A tool that claims a pair is merely "unusual for this
 * class" invites the reader to check that judgement, and they cannot check it if the
 * pairs just below the line are unopenable.
 */
class PairDetailTest {

    @TempDir
    Path workspace;

    private Javalin app;
    private HttpSupport http;

    private static final String ALICE = """
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

    private static final String DANA = """
            public class MarkRegister {
                private final int[] values;
                public MarkRegister(int[] values) { this.values = values; }
                public int peak() {
                    int top = values[0];
                    for (int k = 1; k < values.length; k++) {
                        if (values[k] > top) { top = values[k]; }
                    }
                    return top;
                }
                public double mean() {
                    int sum = 0;
                    for (int value : values) { sum += value; }
                    return (double) sum / values.length;
                }
            }
            """;

    private static final String QUEUE = """
            public class TaskQueue {
                private final java.util.List<String> items = new java.util.ArrayList<>();
                public void enqueue(String label) { items.add(label); }
                public String dequeue() {
                    if (items.isEmpty()) { throw new IllegalStateException("empty"); }
                    return items.remove(0);
                }
                public boolean isEmpty() { return items.isEmpty(); }
            }
            """;

    @BeforeEach
    void startServer() {
        app = new IntegrityApi(workspace.resolve("pair.db")).server();
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
        String id = field(http.postForm("/api/v1/assignments", "name=pairs").body(), "id");
        http.postFiles("/api/v1/assignments/" + id + "/submissions",
                files("alice.java", ALICE, "dana.java", DANA, "queue.java", QUEUE));
        http.postEmpty("/api/v1/assignments/" + id + "/analyze");
        return id;
    }

    private String submissionId(String assignmentId, String filename) throws Exception {
        String body = http.get("/api/v1/assignments/" + assignmentId + "/submissions").body();
        int at = body.indexOf("\"filename\":\"" + filename + "\"");
        String before = body.substring(0, at);
        int idAt = before.lastIndexOf("\"submissionId\":\"");
        int start = idAt + "\"submissionId\":\"".length();
        return body.substring(start, body.indexOf('"', start));
    }

    private HttpResponse<String> pair(String assignmentId, String left, String right) throws Exception {
        return http.get("/api/v1/assignments/" + assignmentId + "/pair"
                + "?left=" + URLEncoder.encode(left, StandardCharsets.UTF_8)
                + "&right=" + URLEncoder.encode(right, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("An UNFLAGGED pair can be opened — the whole point of this endpoint")
    void unflaggedPairsAreReviewable() throws Exception {
        String id = analysedAssignment();
        // alice vs queue solve different problems, so this pair will not be flagged.
        HttpResponse<String> response = pair(id,
                submissionId(id, "alice.java"), submissionId(id, "queue.java"));

        assertEquals(200, response.statusCode(), response.body());
        assertEquals("false", field(response.body(), "flagged"), response.body());
        assertTrue(response.body().contains("\"source\""), "both sources must be returned");
        assertTrue(response.body().contains("TaskQueue"), response.body());
    }

    @Test
    void aFlaggedPairReturnsItsMatchingRegions() throws Exception {
        String id = analysedAssignment();
        HttpResponse<String> response = pair(id,
                submissionId(id, "alice.java"), submissionId(id, "dana.java"));

        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"spans\""), response.body());
        assertTrue(response.body().contains("\"startLine\""), response.body());
        assertTrue(Integer.parseInt(field(response.body(), "sharedFingerprints")) > 0);
    }

    @Test
    @DisplayName("The payload carries the cohort context a score is meaningless without")
    void cohortContextTravelsWithThePair() throws Exception {
        String id = analysedAssignment();
        String body = pair(id, submissionId(id, "alice.java"), submissionId(id, "dana.java")).body();

        assertTrue(body.contains("\"cohortMedian\""), body);
        assertTrue(body.contains("\"cohortMad\""), body);
        assertTrue(body.contains("\"reviewThreshold\""), body);
        assertTrue(body.contains("\"modifiedZ\""), body);
        assertTrue(body.contains("\"containmentLeftInRight\""), body);
    }

    @Test
    void matchedLineCountsAreReported() throws Exception {
        String id = analysedAssignment();
        String body = pair(id, submissionId(id, "alice.java"), submissionId(id, "dana.java")).body();

        assertTrue(body.contains("\"matchedLines\""), body);
        assertTrue(body.contains("\"lineCount\""), body);
    }

    @Test
    @DisplayName("Order does not matter: either direction returns the same pair")
    void theEndpointIsSymmetricOnFlagging() throws Exception {
        String id = analysedAssignment();
        String a = submissionId(id, "alice.java");
        String b = submissionId(id, "dana.java");

        assertEquals(field(pair(id, a, b).body(), "flagged"),
                     field(pair(id, b, a).body(), "flagged"));
    }

    @Test
    void requestingAPairBeforeAnalysisIs409() throws Exception {
        String id = field(http.postForm("/api/v1/assignments", "name=x").body(), "id");
        http.postFiles("/api/v1/assignments/" + id + "/submissions",
                files("alice.java", ALICE, "dana.java", DANA));

        assertEquals(409, pair(id, submissionId(id, "alice.java"),
                submissionId(id, "dana.java")).statusCode());
    }

    @Test
    void missingOrUnknownSubmissionsAreRejected() throws Exception {
        String id = analysedAssignment();

        assertEquals(400, http.get("/api/v1/assignments/" + id + "/pair").statusCode());
        assertEquals(400, http.get("/api/v1/assignments/" + id + "/pair?left=a").statusCode());
        assertEquals(404, pair(id, "ghost", submissionId(id, "alice.java")).statusCode());
        assertEquals(404, http.get("/api/v1/assignments/nope/pair?left=a&right=b").statusCode());
    }

    @Test
    @DisplayName("Editing the batch invalidates the cached analysis behind this endpoint")
    void theCachedAnalysisIsDroppedWhenTheBatchChanges() throws Exception {
        // The analysis is cached to avoid re-tokenising on every click. That cache would
        // be worse than no cache if it outlived the batch it describes.
        String id = analysedAssignment();
        String a = submissionId(id, "alice.java");
        assertEquals(200, pair(id, a, submissionId(id, "dana.java")).statusCode());

        http.delete("/api/v1/assignments/" + id + "/submissions/" + submissionId(id, "queue.java"));

        assertEquals(409, pair(id, a, submissionId(id, "dana.java")).statusCode(),
                "results and pair detail must go stale together");
    }
}
