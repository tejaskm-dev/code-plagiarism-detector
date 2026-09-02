package com.integrityengine.api;

import static com.integrityengine.api.HttpSupport.field;
import static com.integrityengine.api.HttpSupport.files;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.javalin.Javalin;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Phase 1: listing, re-attributing and removing submissions, and surviving a restart. */
class SubmissionManagementTest {

    @TempDir
    Path workspace;

    private Javalin app;
    private HttpSupport http;
    private Path database;

    private static final String A = "public class A { int f(int x){ int t=0; for(int i=0;i<x;i++){ t+=i; } return t; } }";
    private static final String B = "public class B { int g(int y){ int s=1; for(int j=1;j<=y;j++){ s*=j; } return s; } }";
    private static final String C = "public class C { String h(String[] p){ StringBuilder b=new StringBuilder(); for(String s:p){ b.append(s); } return b.toString(); } }";

    @BeforeEach
    void startServer() {
        database = workspace.resolve("phase1.db");
        app = new IntegrityApi(database).server();
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
        return field(http.postForm("/api/v1/assignments", "name=Phase One").body(), "id");
    }

    private String firstSubmissionId(String assignmentId) throws Exception {
        String body = http.get("/api/v1/assignments/" + assignmentId + "/submissions").body();
        return field(body, "submissionId");
    }

    // ------------------------------------------------------------------ listing

    @Test
    @DisplayName("The batch can be listed before any analysis has run")
    void submissionsAreListableBeforeAnalysis() throws Exception {
        String id = createAssignment();
        http.postFiles("/api/v1/assignments/" + id + "/submissions",
                files("alice.java", A, "bob.java", B));

        HttpResponse<String> response = http.get("/api/v1/assignments/" + id + "/submissions");
        String body = response.body();

        assertEquals(200, response.statusCode(), body);
        assertEquals("2", field(body, "count"));
        assertEquals("false", field(body, "analysed"));
        assertTrue(body.contains("\"language\":\"java\""), body);
        assertTrue(body.contains("\"lineCount\""), body);
    }

    @Test
    void listingReportsWhetherAnalysisIsCurrent() throws Exception {
        String id = createAssignment();
        http.postFiles("/api/v1/assignments/" + id + "/submissions",
                files("alice.java", A, "bob.java", B));
        http.postEmpty("/api/v1/assignments/" + id + "/analyze");

        assertEquals("true",
                field(http.get("/api/v1/assignments/" + id + "/submissions").body(), "analysed"));
    }

    // -------------------------------------------------------------- attribution

    @Test
    @DisplayName("An unidentified submission can be attributed to a real student")
    void anUnidentifiedSubmissionCanBeRenamed() throws Exception {
        // This is the dead end the phase exists to remove: before this endpoint the UI
        // could report "needs identification" and offer no way to supply one.
        String id = createAssignment();
        http.postFiles("/api/v1/assignments/" + id + "/submissions", files("Gradebook.java", A));
        String sid = firstSubmissionId(id);
        assertTrue(sid.contains("unidentified-1"), sid);

        HttpResponse<String> patch = http.patchForm(
                "/api/v1/assignments/" + id + "/submissions/" + sid, "student=22CS101");

        assertEquals(200, patch.statusCode(), patch.body());
        assertEquals("22CS101", field(patch.body(), "student"));

        String listing = http.get("/api/v1/assignments/" + id + "/submissions").body();
        assertEquals("0", field(listing, "unidentifiedCount"), listing);
        assertTrue(listing.contains("22CS101"), listing);
    }

    @Test
    @DisplayName("Re-attributing invalidates the analysis rather than leaving stale results")
    void renamingClearsAPreviousAnalysis() throws Exception {
        String id = createAssignment();
        http.postFiles("/api/v1/assignments/" + id + "/submissions",
                files("Gradebook.java", A, "Gradebook.java", B));
        http.postEmpty("/api/v1/assignments/" + id + "/analyze");
        assertEquals(200, http.get("/api/v1/assignments/" + id + "/results").statusCode());

        http.patchForm("/api/v1/assignments/" + id + "/submissions/" + firstSubmissionId(id),
                "student=22CS101");

        // A result computed over a different roster is not merely out of date, it is
        // wrong, so it is withdrawn rather than served with a warning.
        assertEquals(409, http.get("/api/v1/assignments/" + id + "/results").statusCode());
        assertEquals("false",
                field(http.get("/api/v1/assignments/" + id + "/submissions").body(), "analysed"));
    }

    @Test
    void aBlankOrReservedIdentifierIsRejected() throws Exception {
        String id = createAssignment();
        http.postFiles("/api/v1/assignments/" + id + "/submissions", files("Gradebook.java", A));
        String sid = firstSubmissionId(id);

        assertEquals(400, http.patchForm(
                "/api/v1/assignments/" + id + "/submissions/" + sid, "student=").statusCode());
        assertEquals(400, http.patchForm(
                "/api/v1/assignments/" + id + "/submissions/" + sid, "student=   ").statusCode());

        // Hand-assigning a placeholder would let a later automatic one collide with it.
        HttpResponse<String> reserved = http.patchForm(
                "/api/v1/assignments/" + id + "/submissions/" + sid, "student=unidentified-9");
        assertEquals(400, reserved.statusCode());
        assertEquals("reserved_identifier", field(reserved.body(), "error"));
    }

    @Test
    void patchingAnUnknownSubmissionIs404() throws Exception {
        String id = createAssignment();
        assertEquals(404, http.patchForm(
                "/api/v1/assignments/" + id + "/submissions/nope", "student=x").statusCode());
    }

    // ------------------------------------------------------------------ removal

    @Test
    void aSubmissionCanBeRemoved() throws Exception {
        String id = createAssignment();
        http.postFiles("/api/v1/assignments/" + id + "/submissions",
                files("alice.java", A, "bob.java", B, "chen.java", C));
        String sid = firstSubmissionId(id);

        HttpResponse<String> response =
                http.delete("/api/v1/assignments/" + id + "/submissions/" + sid);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals("2", field(response.body(), "remaining"));
        assertEquals("2", field(http.get("/api/v1/assignments/" + id + "/submissions").body(), "count"));
    }

    @Test
    @DisplayName("Removing a submission that results reference does not hit a foreign key error")
    void removalAfterAnalysisSucceeds() throws Exception {
        // Results carry foreign keys to submissions, so the delete would be rejected if
        // the analysis were not withdrawn first.
        String id = createAssignment();
        http.postFiles("/api/v1/assignments/" + id + "/submissions",
                files("alice.java", A, "bob.java", B, "chen.java", C));
        http.postEmpty("/api/v1/assignments/" + id + "/analyze");

        HttpResponse<String> response =
                http.delete("/api/v1/assignments/" + id + "/submissions/" + firstSubmissionId(id));

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(409, http.get("/api/v1/assignments/" + id + "/results").statusCode());
    }

    @Test
    void deletingAnUnknownSubmissionIs404() throws Exception {
        String id = createAssignment();
        assertEquals(404, http.delete("/api/v1/assignments/" + id + "/submissions/nope").statusCode());
    }

    // -------------------------------------------------------------- persistence

    @Test
    @DisplayName("Assignments and submissions survive a server restart")
    void theWorkspaceSurvivesARestart() throws Exception {
        String id = createAssignment();
        http.postFiles("/api/v1/assignments/" + id + "/submissions",
                files("alice.java", A, "bob.java", B));
        http.postEmpty("/api/v1/assignments/" + id + "/analyze");

        app.stop();
        app = new IntegrityApi(database).server();
        app.start(0);
        http = new HttpSupport(app.port());

        HttpResponse<String> listing = http.get("/api/v1/assignments/" + id + "/submissions");
        assertEquals(200, listing.statusCode(), "the assignment should have been reloaded");
        assertEquals("Phase One", field(listing.body(), "assignmentName"));
        assertEquals("2", field(listing.body(), "count"));

        // The report is deliberately not restored: it is only valid for the batch it was
        // computed over, so it is recomputed on demand rather than trusted across a boot.
        assertEquals("false", field(listing.body(), "analysed"));
        assertEquals(409, http.get("/api/v1/assignments/" + id + "/results").statusCode());

        assertEquals(200, http.postEmpty("/api/v1/assignments/" + id + "/analyze").statusCode());
        assertEquals(200, http.get("/api/v1/assignments/" + id + "/results").statusCode());
    }

    @Test
    @DisplayName("After a restart, placeholder numbering resumes rather than colliding")
    void placeholderCounterResumesAfterRestart() throws Exception {
        // Restarting used to reset the counter to zero, so the next unattributed upload
        // would claim unidentified-1 and overwrite the file that already held it.
        String id = createAssignment();
        http.postFiles("/api/v1/assignments/" + id + "/submissions",
                files("Gradebook.java", A, "Gradebook.java", B));

        app.stop();
        app = new IntegrityApi(database).server();
        app.start(0);
        http = new HttpSupport(app.port());

        HttpResponse<String> response = http.postFiles(
                "/api/v1/assignments/" + id + "/submissions", files("Gradebook.java", C));

        assertTrue(response.body().contains("unidentified-3"), response.body());
        assertFalse(response.body().contains("unidentified-1"), response.body());
        assertEquals("3", field(http.get("/api/v1/assignments/" + id + "/submissions").body(), "count"));
    }

    @Test
    void reopeningIsPossibleFromTheAssignmentList() throws Exception {
        String id = createAssignment();

        app.stop();
        app = new IntegrityApi(database).server();
        app.start(0);
        http = new HttpSupport(app.port());

        assertTrue(http.get("/api/v1/assignments").body().contains(id));
    }

    @Test
    void referenceFilesSurviveARestart() throws Exception {
        String id = createAssignment();
        http.postFiles("/api/v1/assignments/" + id + "/boilerplate", files("Starter.java", A));

        app.stop();
        app = new IntegrityApi(database).server();
        app.start(0);
        http = new HttpSupport(app.port());

        assertEquals("1",
                field(http.get("/api/v1/assignments/" + id + "/submissions").body(), "referenceFileCount"));
    }

    // ------------------------------------------------------------ invalidation

    @Test
    @DisplayName("Uploading more files also withdraws a previous analysis")
    void uploadingInvalidatesAnalysis() throws Exception {
        String id = createAssignment();
        http.postFiles("/api/v1/assignments/" + id + "/submissions",
                files("alice.java", A, "bob.java", B));
        http.postEmpty("/api/v1/assignments/" + id + "/analyze");

        http.postFiles("/api/v1/assignments/" + id + "/submissions", files("chen.java", C));

        assertEquals(409, http.get("/api/v1/assignments/" + id + "/results").statusCode());
    }
}
