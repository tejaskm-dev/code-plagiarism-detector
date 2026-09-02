package com.integrityengine.api;

import static com.integrityengine.api.HttpSupport.field;
import static com.integrityengine.api.HttpSupport.files;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.javalin.Javalin;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PERMANENT REGRESSION TEST -- do not delete or weaken.
 *
 * <p>Reproduces a bug that silently destroyed data. Every uploaded file resolved to the
 * submission id {@code <assignment>/<filename>}, so five files all called
 * {@code Gradebook.java} collided onto one id, and Stage 7's {@code INSERT OR REPLACE}
 * upsert overwrote four of them. The endpoint still reported five accepted, because it
 * counted files it had looped over rather than rows that survived. The visible symptom
 * was an empty results table: one submission means zero pairs.
 *
 * <p>The property under test is absolute and not negotiable by a later refactor: <b>N
 * files with identical names but distinct identities must produce N stored submissions
 * and C(N,2) compared pairs.</b> Same category as the boilerplate/collusion guards.
 */
class SubmissionIdentityRegressionTest {

    @TempDir
    Path workspace;

    private Javalin app;
    private HttpSupport http;

    @BeforeEach
    void startServer() {
        app = new IntegrityApi(workspace.resolve("regression.db")).server();
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
        return field(http.postForm("/api/v1/assignments", "name=regression").body(), "id");
    }

    /** Five distinct programs, so no two submissions are accidentally identical. */
    private static String program(int index) {
        return """
                public class Gradebook {
                    private final int[] scores;
                    public Gradebook(int[] scores) { this.scores = scores; }
                    public int variant%d() {
                        int acc%d = %d;
                        for (int i = 0; i < scores.length; i++) {
                            acc%d = acc%d * %d + scores[i];
                        }
                        return acc%d;
                    }
                }
                """.formatted(index, index, index, index, index, index + 2, index);
    }

    private static byte[] zipWithStudentFolders(int count) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (int i = 1; i <= count; i++) {
                // Every file has the SAME name. Only the folder distinguishes them.
                zip.putNextEntry(new ZipEntry("22CS10" + i + "/Gradebook.java"));
                zip.write(program(i).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    // ------------------------------------------------------------- the regression

    @Test
    @DisplayName("Five identically-named files in student folders yield 5 submissions and 10 pairs")
    void identicallyNamedFilesFromDifferentStudentsDoNotCollide() throws Exception {
        String id = createAssignment();

        HttpResponse<String> upload = http.postZip(
                "/api/v1/assignments/" + id + "/submissions", "batch.zip", zipWithStudentFolders(5));

        assertEquals(200, upload.statusCode(), upload.body());
        assertEquals("5", field(upload.body(), "acceptedCount"), upload.body());
        // The count that actually matters: rows that survived, not files looped over.
        assertEquals("5", field(upload.body(), "storedTotal"), upload.body());
        assertEquals("0", field(upload.body(), "unidentifiedCount"), upload.body());

        HttpResponse<String> analyze = http.postEmpty("/api/v1/assignments/" + id + "/analyze");
        assertEquals("5", field(analyze.body(), "submissionCount"), analyze.body());
        // C(5,2) = 10. Anything fewer means submissions were lost.
        assertEquals("10", field(analyze.body(), "pairCount"), analyze.body());

        String results = http.get("/api/v1/assignments/" + id + "/results").body();
        for (int i = 1; i <= 5; i++) {
            assertTrue(results.contains("22CS10" + i),
                    "student 22CS10" + i + " missing from results");
        }
    }

    @Test
    @DisplayName("Scaled up: 12 identical filenames still give C(12,2) = 66 pairs")
    void theGuaranteeHoldsAtLargerN() throws Exception {
        String id = createAssignment();

        http.postZip("/api/v1/assignments/" + id + "/submissions", "batch.zip",
                zipWithStudentFolders(12));
        HttpResponse<String> analyze = http.postEmpty("/api/v1/assignments/" + id + "/analyze");

        assertEquals("12", field(analyze.body(), "submissionCount"), analyze.body());
        assertEquals("66", field(analyze.body(), "pairCount"), analyze.body());
    }

    @Test
    @DisplayName("The flat drag-and-drop case that produced the bug: no folders, no identifiers")
    void identicallyNamedFilesWithNoIdentityAreStillKeptApart() throws Exception {
        String id = createAssignment();

        // This is exactly what a browser sends when five files are dragged in from five
        // different folders: five parts, all named Gradebook.java, no structure at all.
        List<HttpSupport.Upload> uploads = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            uploads.add(new HttpSupport.Upload("Gradebook.java", program(i)));
        }

        HttpResponse<String> upload =
                http.postFiles("/api/v1/assignments/" + id + "/submissions", uploads);

        assertEquals("5", field(upload.body(), "acceptedCount"), upload.body());
        assertEquals("5", field(upload.body(), "storedTotal"), upload.body());
        assertEquals("5", field(upload.body(), "unidentifiedCount"), upload.body());

        HttpResponse<String> analyze = http.postEmpty("/api/v1/assignments/" + id + "/analyze");
        assertEquals("5", field(analyze.body(), "submissionCount"), analyze.body());
        assertEquals("10", field(analyze.body(), "pairCount"), analyze.body());

        // Unattributed, but never merged, and visibly marked as needing identification.
        String results = http.get("/api/v1/assignments/" + id + "/results").body();
        assertEquals("5", field(results, "unidentifiedCount"), results);
        for (int i = 1; i <= 5; i++) {
            assertTrue(results.contains("unidentified-" + i), "unidentified-" + i + " missing");
        }
    }

    @Test
    @DisplayName("A second upload of unidentified files does not reuse the first batch's placeholders")
    void placeholderIdentitiesDoNotRepeatAcrossUploads() throws Exception {
        String id = createAssignment();

        http.postFiles("/api/v1/assignments/" + id + "/submissions",
                files("Gradebook.java", program(1), "Gradebook.java", program(2)));
        HttpResponse<String> second = http.postFiles("/api/v1/assignments/" + id + "/submissions",
                files("Gradebook.java", program(3), "Gradebook.java", program(4)));

        assertEquals("4", field(second.body(), "storedTotal"), second.body());
        assertTrue(second.body().contains("unidentified-3"), second.body());
        assertTrue(second.body().contains("unidentified-4"), second.body());

        assertEquals("6", field(http.postEmpty("/api/v1/assignments/" + id + "/analyze").body(),
                "pairCount"));
    }

    @Test
    @DisplayName("Submission ids are unique across the whole batch, whatever the filenames")
    void everySubmissionIdIsDistinct() throws Exception {
        String id = createAssignment();
        http.postZip("/api/v1/assignments/" + id + "/submissions", "batch.zip",
                zipWithStudentFolders(8));

        String results = http.get("/api/v1/assignments/" + id + "/results").body();
        // Analyze first so results exist.
        http.postEmpty("/api/v1/assignments/" + id + "/analyze");
        results = http.get("/api/v1/assignments/" + id + "/results").body();

        Set<String> ids = new HashSet<>();
        int at = 0;
        while ((at = results.indexOf("\"submissionId\":\"", at)) >= 0) {
            int start = at + "\"submissionId\":\"".length();
            int end = results.indexOf('"', start);
            assertTrue(ids.add(results.substring(start, end)),
                    "duplicate submission id: " + results.substring(start, end));
            at = end;
        }
        assertEquals(8, ids.size(), "expected 8 distinct submission ids, got " + ids);
    }

    @Test
    @DisplayName("Re-uploading the same student's same file replaces it rather than duplicating")
    void reuploadingOneStudentsFileIsStillIdempotent() throws Exception {
        String id = createAssignment();

        http.postZip("/api/v1/assignments/" + id + "/submissions", "a.zip", zipWithStudentFolders(3));
        http.postZip("/api/v1/assignments/" + id + "/submissions", "b.zip", zipWithStudentFolders(3));

        HttpResponse<String> analyze = http.postEmpty("/api/v1/assignments/" + id + "/analyze");

        assertEquals("3", field(analyze.body(), "submissionCount"), analyze.body());
        assertEquals("3", field(analyze.body(), "pairCount"), analyze.body());
    }

    @Test
    @DisplayName("Mixed identities in one batch: folder, filename and unattributed together")
    void mixedIdentitySourcesCoexist() throws Exception {
        String id = createAssignment();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("22CS101/Gradebook.java"));
            zip.write(program(1).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("Gradebook_22CS102.java"));
            zip.write(program(2).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("Gradebook.java"));
            zip.write(program(3).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        HttpResponse<String> upload =
                http.postZip("/api/v1/assignments/" + id + "/submissions", "mixed.zip",
                        bytes.toByteArray());

        assertEquals("3", field(upload.body(), "storedTotal"), upload.body());
        assertEquals("1", field(upload.body(), "unidentifiedCount"), upload.body());
        assertTrue(upload.body().contains("22CS101"), upload.body());
        assertTrue(upload.body().contains("22CS102"), upload.body());
        assertTrue(upload.body().contains("unidentified-1"), upload.body());
        assertFalse(upload.body().contains("\"student\":\"Gradebook\""),
                "the old bug used the filename stem as the student id");
    }
}
