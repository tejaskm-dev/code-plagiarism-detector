package com.integrityengine.api;

import static com.integrityengine.api.HttpSupport.field;
import static com.integrityengine.api.HttpSupport.files;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * Tests the HTTP layer specifically: routing, validation, multipart handling and
 * response shaping. The engine underneath is already covered by its own suite, so
 * nothing here re-tests detection quality -- only what a client can do to this server.
 */
class IntegrityApiTest {

    @TempDir
    Path workspace;

    private Javalin app;
    private HttpSupport http;

    private static final String GRADEBOOK = """
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

    private static final String RENAMED = """
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

    private static final String UNRELATED = """
            public class Queue {
                private final java.util.List<String> items = new java.util.ArrayList<>();
                public void enqueue(String label) { items.add(label); }
                public String dequeue() {
                    if (items.isEmpty()) { throw new IllegalStateException("empty"); }
                    return items.remove(0);
                }
                public boolean isEmpty() { return items.isEmpty(); }
            }
            """;

    /** Provided starter, left unclosed so each fixture appends its own method. */
    private static final String STARTER_OPEN = """
            public class GradeBook {
                private final int[] scores;
                public GradeBook(int[] scores) { this.scores = scores; }
                public double average() {
                    int total = 0;
                    for (int score : scores) { total += score; }
                    return (double) total / scores.length;
                }
            """;

    private static final String METHOD_A = """
                public String describe(String prefix) {
                    StringBuilder out = new StringBuilder(prefix);
                    for (int i = 0; i < scores.length; i++) { out.append(',').append(scores[i]); }
                    return out.toString();
                }
            }
            """;

    private static final String METHOD_B = """
                public long fib(int n) {
                    if (n < 2) { return n; }
                    long a = 0, b = 1;
                    while (n-- > 1) { long t = a + b; a = b; b = t; }
                    return b;
                }
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

    private String createAssignment() throws Exception {
        HttpResponse<String> response = http.postForm("/api/v1/assignments", "name=test");
        assertEquals(201, response.statusCode(), response.body());
        return field(response.body(), "id");
    }

    // ------------------------------------------------------------- static files

    @Test
    void theSinglePageAppIsServed() throws Exception {
        assertEquals(200, http.get("/").statusCode());
        assertEquals(200, http.get("/app.js").statusCode());
        assertEquals(200, http.get("/styles.css").statusCode());
    }

    // ------------------------------------------------------------- assignments

    @Test
    void creatingAnAssignmentReturnsAnId() throws Exception {
        HttpResponse<String> response = http.postForm("/api/v1/assignments", "name=CS101");

        assertEquals(201, response.statusCode());
        assertNotNull(field(response.body(), "id"));
        assertEquals("CS101", field(response.body(), "name"));
    }

    @Test
    void aMissingNameFallsBackToADefault() throws Exception {
        HttpResponse<String> response = http.postEmpty("/api/v1/assignments");

        assertEquals(201, response.statusCode(), response.body());
        assertEquals("assignment", field(response.body(), "name"));
    }

    @Test
    void anAbsurdlyLongNameIsRejected() throws Exception {
        HttpResponse<String> response =
                http.postForm("/api/v1/assignments", "name=" + "x".repeat(500));

        assertEquals(400, response.statusCode());
        assertEquals("name_too_long", field(response.body(), "error"));
    }

    @Test
    @DisplayName("Every assignment-scoped route 404s on an unknown id rather than failing oddly")
    void unknownAssignmentIdsReturn404Everywhere() throws Exception {
        assertEquals(404, http.postFiles("/api/v1/assignments/nope/submissions",
                files("A.java", GRADEBOOK)).statusCode());
        assertEquals(404, http.postFiles("/api/v1/assignments/nope/boilerplate",
                files("A.java", GRADEBOOK)).statusCode());
        assertEquals(404, http.postEmpty("/api/v1/assignments/nope/analyze").statusCode());

        HttpResponse<String> results = http.get("/api/v1/assignments/nope/results");
        assertEquals(404, results.statusCode());
        assertEquals("unknown_assignment", field(results.body(), "error"));
    }

    @Test
    void aSingleAssignmentCanBeFetched() throws Exception {
        String id = createAssignment();
        http.postFiles("/api/v1/assignments/" + id + "/submissions",
                files("a.java", GRADEBOOK, "b.java", RENAMED));

        HttpResponse<String> response = http.get("/api/v1/assignments/" + id);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(id, field(response.body(), "id"));
        assertEquals("2", field(response.body(), "submissionCount"));
        assertEquals("false", field(response.body(), "analysed"));
        assertEquals(404, http.get("/api/v1/assignments/nope").statusCode());
    }

    @Test
    @DisplayName("Deleting an assignment removes it and everything stored under it")
    void anAssignmentCanBeDeleted() throws Exception {
        String id = createAssignment();
        String other = createAssignment();
        http.postFiles("/api/v1/assignments/" + id + "/submissions",
                files("a.java", GRADEBOOK, "b.java", RENAMED));
        http.postFiles("/api/v1/assignments/" + other + "/submissions", files("c.java", UNRELATED));
        http.postEmpty("/api/v1/assignments/" + id + "/analyze");

        HttpResponse<String> response = http.delete("/api/v1/assignments/" + id);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals("2", field(response.body(), "submissionsDeleted"));
        assertEquals(404, http.get("/api/v1/assignments/" + id).statusCode());
        assertFalse(http.get("/api/v1/assignments").body().contains(id));
        assertEquals("1", field(http.get("/api/v1/assignments/" + other).body(), "submissionCount"),
                "deleting one assignment must not touch another");
        assertEquals(404, http.delete("/api/v1/assignments/" + id).statusCode());
    }

    // ------------------------------------------------------------------ service

    @Test
    void healthReportsOk() throws Exception {
        HttpResponse<String> response = http.get("/api/v1/health");

        assertEquals(200, response.statusCode());
        assertEquals("ok", field(response.body(), "status"));
        assertNotNull(field(response.body(), "version"));
    }

    @Test
    @DisplayName("An unknown API route answers in the same JSON error shape as every other failure")
    void unknownRoutesReturnJson() throws Exception {
        HttpResponse<String> missing = http.get("/api/v1/nope");
        assertEquals(404, missing.statusCode());
        assertEquals("unknown_route", field(missing.body(), "error"), missing.body());

        // A handler's own 404 keeps its specific error rather than being overwritten.
        assertEquals("unknown_assignment",
                field(http.get("/api/v1/assignments/nope/submissions").body(), "error"));
    }

    // -------------------------------------------------------------- submissions

    @Test
    void uploadingSourceFilesAcceptsThem() throws Exception {
        String id = createAssignment();

        HttpResponse<String> response = http.postFiles("/api/v1/assignments/" + id + "/submissions",
                files("alice.java", GRADEBOOK, "bob.java", RENAMED));

        assertEquals(200, response.statusCode(), response.body());
        assertEquals("2", field(response.body(), "acceptedCount"));
    }

    @Test
    void anUploadWithNoFilesIsRejected() throws Exception {
        String id = createAssignment();

        HttpResponse<String> response =
                http.postFiles("/api/v1/assignments/" + id + "/submissions", List.of());

        assertEquals(400, response.statusCode());
        assertEquals("no_files", field(response.body(), "error"));
    }

    @Test
    @DisplayName("Unrecognised file types are reported as rejected, not silently dropped")
    void unsupportedExtensionsAreRejectedWithAReason() throws Exception {
        String id = createAssignment();

        HttpResponse<String> response = http.postFiles("/api/v1/assignments/" + id + "/submissions",
                files("notes.txt", "just prose", "alice.java", GRADEBOOK));

        assertEquals(200, response.statusCode(), response.body());
        assertEquals("1", field(response.body(), "acceptedCount"));
        assertTrue(response.body().contains("notes.txt"), response.body());
        assertTrue(response.body().contains("unrecognised source extension"), response.body());
    }

    @Test
    void anUploadWhereEverythingIsRejectedReturns400() throws Exception {
        String id = createAssignment();

        HttpResponse<String> response = http.postFiles("/api/v1/assignments/" + id + "/submissions",
                files("notes.txt", "prose", "data.csv", "a,b"));

        assertEquals(400, response.statusCode());
        assertEquals("0", field(response.body(), "acceptedCount"));
    }

    @Test
    @DisplayName("A malformed multipart body produces a 4xx, not a 500")
    void malformedMultipartIsHandled() throws Exception {
        String id = createAssignment();

        HttpResponse<String> response =
                http.postBrokenMultipart("/api/v1/assignments/" + id + "/submissions");

        assertTrue(response.statusCode() >= 400 && response.statusCode() < 500,
                "expected a client error, got " + response.statusCode() + ": " + response.body());
    }

    @Test
    @DisplayName("A filename carrying a path is reduced to its last segment")
    void uploadedFilenamesAreSanitised() {
        assertEquals("Main.java", IntegrityApi.safeName("../../etc/Main.java"));
        assertEquals("Main.java", IntegrityApi.safeName("C:\\Users\\x\\Main.java"));
        assertEquals("Main.java", IntegrityApi.safeName("Main.java"));
        assertEquals(null, IntegrityApi.safeName(".."));
        assertEquals(null, IntegrityApi.safeName(""));
        assertEquals(null, IntegrityApi.safeName(null));
    }

    // ----------------------------------------------------------------- analyze

    @Test
    void analysingWithNoSubmissionsIsRejected() throws Exception {
        String id = createAssignment();

        HttpResponse<String> response = http.postEmpty("/api/v1/assignments/" + id + "/analyze");

        assertEquals(400, response.statusCode());
        assertEquals("no_submissions", field(response.body(), "error"));
    }

    @Test
    void resultsBeforeAnalysisReturn409() throws Exception {
        String id = createAssignment();
        http.postFiles("/api/v1/assignments/" + id + "/submissions", files("a.java", GRADEBOOK));

        HttpResponse<String> response = http.get("/api/v1/assignments/" + id + "/results");

        assertEquals(409, response.statusCode());
        assertEquals("not_analysed", field(response.body(), "error"));
    }

    @Test
    @DisplayName("BYOK: a key supplied in the header is never echoed back to the client")
    void theApiKeyIsNeverReturnedToTheCaller() throws Exception {
        String id = createAssignment();
        http.postFiles("/api/v1/assignments/" + id + "/submissions",
                files("a.java", GRADEBOOK, "b.java", RENAMED));
        String key = "sk-ant-secret-never-echo-this";

        HttpResponse<String> analyze = http.postEmpty(
                "/api/v1/assignments/" + id + "/analyze", IntegrityApi.API_KEY_HEADER, key);
        HttpResponse<String> results = http.get("/api/v1/assignments/" + id + "/results");

        assertEquals(200, analyze.statusCode(), analyze.body());
        assertFalse(analyze.body().contains(key), "the key came back in the analyze response");
        assertFalse(results.body().contains(key), "the key came back in the results payload");
    }

    // ----------------------------------------------------------------- results

    @Test
    @DisplayName("The full flow yields cohort statistics, ranked pairs and the source files")
    void endToEndFlowProducesAUsableResultsPayload() throws Exception {
        String id = createAssignment();
        http.postFiles("/api/v1/assignments/" + id + "/submissions",
                files("alice.java", GRADEBOOK, "dana.java", RENAMED, "queue.java", UNRELATED));
        assertEquals(200, http.postEmpty("/api/v1/assignments/" + id + "/analyze").statusCode());

        HttpResponse<String> response = http.get("/api/v1/assignments/" + id + "/results");
        String body = response.body();

        assertEquals(200, response.statusCode(), body);
        assertTrue(body.contains("\"cohort\""), body);
        assertTrue(body.contains("\"tierLabel\""), body);
        assertTrue(body.contains("\"reviewThreshold\""), body);
        assertTrue(body.contains("\"pairs\""), body);
        assertTrue(body.contains("\"containmentLeftInRight\""), body);
        assertTrue(body.contains("\"modifiedZ\""), body);
        // Source text travels with the results so the diff view needs no second call.
        assertTrue(body.contains("\"files\""), body);
        assertTrue(body.contains("GradeBook"), body);
        assertEquals("3", field(body, "submissionCount"));
        assertEquals("3", field(body, "pairCount"));
    }

    @Test
    @DisplayName("Flagged pairs carry line spans; unflagged pairs do not")
    void spansAreProvidedOnlyForFlaggedPairs() throws Exception {
        String id = createAssignment();
        http.postFiles("/api/v1/assignments/" + id + "/submissions",
                files("alice.java", GRADEBOOK, "dana.java", RENAMED, "queue.java", UNRELATED));
        http.postEmpty("/api/v1/assignments/" + id + "/analyze");

        String body = http.get("/api/v1/assignments/" + id + "/results").body();

        int flaggedCount = Integer.parseInt(field(body, "flaggedCount"));
        int spanBlocks = body.split("\"leftSpans\"", -1).length - 1;
        assertEquals(flaggedCount, spanBlocks,
                "one leftSpans block per flagged pair, got " + spanBlocks + " for " + flaggedCount);
        assertTrue(body.contains("\"startLine\""), body);
    }

    // ------------------------------------------------------------- boilerplate

    @Test
    @DisplayName("Reference files are stored and reported as applied")
    void boilerplateUploadIsStoredAndApplied() throws Exception {
        String id = createAssignment();

        HttpResponse<String> response = http.postFiles(
                "/api/v1/assignments/" + id + "/boilerplate", files("Starter.java", GRADEBOOK));

        assertEquals(200, response.statusCode(), response.body());
        assertEquals("true", field(response.body(), "applied"));
        assertEquals("1", field(response.body(), "referenceFileCount"));
    }

    @Test
    @DisplayName("A reference file lowers the score of a pair that shares it, even in a tiny cohort")
    void referenceFilesChangeScores() throws Exception {
        // Two submissions is below BoilerplateFilter.MINIMUM_COHORT_SIZE, so cohort
        // inference suppresses nothing here. Any drop comes from the reference file alone.
        String id = createAssignment();
        http.postFiles("/api/v1/assignments/" + id + "/submissions",
                files("a.java", STARTER_OPEN + METHOD_A, "b.java", STARTER_OPEN + METHOD_B));
        http.postEmpty("/api/v1/assignments/" + id + "/analyze");
        double before = Double.parseDouble(
                field(http.get("/api/v1/assignments/" + id + "/results").body(), "score"));

        http.postFiles("/api/v1/assignments/" + id + "/boilerplate",
                files("Starter.java", STARTER_OPEN + "}\n"));
        http.postEmpty("/api/v1/assignments/" + id + "/analyze");
        String after = http.get("/api/v1/assignments/" + id + "/results").body();

        assertTrue(Double.parseDouble(field(after, "score")) < before,
                "score should fall once the shared starter is excluded: before=" + before + " " + after);
    }

    @Test
    @DisplayName("Uploading reference files withdraws the existing analysis")
    void boilerplateUploadInvalidatesAnalysis() throws Exception {
        String id = createAssignment();
        http.postFiles("/api/v1/assignments/" + id + "/submissions",
                files("a.java", GRADEBOOK, "b.java", RENAMED));
        http.postEmpty("/api/v1/assignments/" + id + "/analyze");

        HttpResponse<String> response = http.postFiles(
                "/api/v1/assignments/" + id + "/boilerplate", files("Starter.java", GRADEBOOK));

        assertEquals("true", field(response.body(), "analysisInvalidated"));
        assertEquals(409, http.get("/api/v1/assignments/" + id + "/results").statusCode());
    }

    @Test
    @DisplayName("A reference file no tokenizer recognises is rejected, not silently stored")
    void unsupportedReferenceFileIsRejected() throws Exception {
        String id = createAssignment();

        HttpResponse<String> response = http.postFiles(
                "/api/v1/assignments/" + id + "/boilerplate", files("instructions.pdf", "not code"));

        assertEquals(400, response.statusCode(), response.body());
        assertEquals("no_usable_reference_files", field(response.body(), "error"));
        assertTrue(response.body().contains("instructions.pdf (unrecognised source extension)"),
                response.body());
        assertEquals("0", field(http.get("/api/v1/assignments/" + id).body(), "referenceFileCount"));
    }

    @Test
    void referenceFilesCanBeCleared() throws Exception {
        String id = createAssignment();
        http.postFiles("/api/v1/assignments/" + id + "/boilerplate",
                files("Starter.java", GRADEBOOK, "Helper.java", UNRELATED));

        HttpResponse<String> response = http.delete("/api/v1/assignments/" + id + "/boilerplate");

        assertEquals(200, response.statusCode(), response.body());
        assertEquals("2", field(response.body(), "removed"));
        assertEquals("0", field(http.get("/api/v1/assignments/" + id).body(), "referenceFileCount"));
    }

    @Test
    void boilerplateUploadWithNoFilesIsRejected() throws Exception {
        String id = createAssignment();

        HttpResponse<String> response =
                http.postFiles("/api/v1/assignments/" + id + "/boilerplate", List.of());

        assertEquals(400, response.statusCode());
    }

    @Test
    void storedReferenceFilesAreCountedInTheResults() throws Exception {
        String id = createAssignment();
        http.postFiles("/api/v1/assignments/" + id + "/boilerplate", files("Starter.java", GRADEBOOK));
        http.postFiles("/api/v1/assignments/" + id + "/submissions",
                files("a.java", GRADEBOOK, "b.java", RENAMED));
        http.postEmpty("/api/v1/assignments/" + id + "/analyze");

        assertEquals("1", field(http.get("/api/v1/assignments/" + id + "/results").body(),
                "referenceFilesStored"));
    }
}
