package com.integrityengine.api;

import com.integrityengine.domain.CodeSubmission;
import com.integrityengine.domain.Report;
import com.integrityengine.domain.SimilarityResult;
import com.integrityengine.engine.IntegrityEngine;
import com.integrityengine.persistence.RepositoryFactory;
import com.integrityengine.persistence.SubmissionRepository;
import com.integrityengine.statistics.StatisticalAnalyzer;
import com.integrityengine.tokenizer.TokenizerFactory;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;
import io.javalin.http.staticfiles.Location;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Optional;

/**
 * HTTP entry point over {@link IntegrityEngine}.
 *
 * <p>A second front door onto the same system, not a second system: every route
 * delegates to the engine and the repositories the CLI already uses. The only logic here
 * is request validation, multipart handling and response shaping.
 *
 * <p><b>BYOK discipline matches the CLI.</b> The LLM key arrives per request in the
 * {@code X-LLM-Api-Key} header, is passed straight to {@code analyzeBatch}, and is never
 * stored on the assignment, written to a log, or echoed into a response.
 */
public final class IntegrityApi {

    /** Header carrying the caller's own LLM key. Never persisted. */
    public static final String API_KEY_HEADER = "X-LLM-Api-Key";

    private static final long MAX_UPLOAD_BYTES = 2L * 1024 * 1024;

    /** From the jar manifest; absent when running from classes, e.g. under test. */
    static final String VERSION = Optional.ofNullable(
            IntegrityApi.class.getPackage().getImplementationVersion()).orElse("dev");

    private final TokenizerFactory tokenizers = new TokenizerFactory();
    private final RepositoryFactory repositories;
    private final AssignmentStore store;
    private final AssignmentRegistry registry;
    private final IntegrityEngine engine;
    private final StatisticalAnalyzer statistics = new StatisticalAnalyzer();

    public IntegrityApi(Path databaseFile) {
        Objects.requireNonNull(databaseFile, "databaseFile");
        this.repositories = new RepositoryFactory(databaseFile);
        this.store = new AssignmentStore(databaseFile);
        this.registry = new AssignmentRegistry(store, repositories.submissions());
        this.engine = new IntegrityEngine(repositories);
    }

    /** Build the server without starting it, so tests can pick their own port. */
    public Javalin server() {
        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/public", Location.CLASSPATH);
            config.showJavalinBanner = false;
        });

        app.exception(Exception.class, (e, ctx) -> {
            // Never surface a stack trace or an upstream message: either could echo
            // request content, and request content can include a key.
            ctx.status(500).contentType("application/json")
                    .result(JsonOut.error("internal_error", e.getClass().getSimpleName()));
        });

        app.error(404, ctx -> {
            // Only unmatched routes. A handler that answered 404 itself (unknown
            // assignment, unknown submission) has already written a JSON body. Note
            // ctx.contentType() is the request's type; the response's lives on res().
            String written = ctx.res().getContentType();
            if (ctx.path().startsWith("/api/")
                    && (written == null || !written.startsWith("application/json"))) {
                ctx.contentType("application/json").result(JsonOut.error(
                        "unknown_route", "no route " + ctx.method() + " " + ctx.path()));
            }
        });

        app.get("/api/v1/health", this::health);
        app.post("/api/v1/assignments", this::createAssignment);
        app.get("/api/v1/assignments", this::listAssignments);
        app.get("/api/v1/assignments/{id}", this::getAssignment);
        app.delete("/api/v1/assignments/{id}", this::deleteAssignment);
        app.post("/api/v1/assignments/{id}/submissions", this::uploadSubmissions);
        app.get("/api/v1/assignments/{id}/submissions", this::listSubmissions);
        app.patch("/api/v1/assignments/{id}/submissions/<sid>", this::updateSubmission);
        app.delete("/api/v1/assignments/{id}/submissions/<sid>", this::deleteSubmission);
        app.post("/api/v1/assignments/{id}/boilerplate", this::uploadBoilerplate);
        app.delete("/api/v1/assignments/{id}/boilerplate", this::clearBoilerplate);
        app.post("/api/v1/assignments/{id}/analyze", this::analyze);
        app.get("/api/v1/assignments/{id}/results", this::results);
        app.get("/api/v1/assignments/{id}/pair", this::pairDetail);
        return app;
    }

    // ------------------------------------------------------------------- routes

    /** Liveness for container orchestrators and load balancers. Touches no storage. */
    private void health(Context ctx) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("status", JsonOut.string("ok"));
        body.put("version", JsonOut.string(VERSION));
        ctx.contentType("application/json").result(JsonOut.object(body));
    }

    private void createAssignment(Context ctx) {
        String name = Optional.ofNullable(ctx.formParam("name"))
                .filter(n -> !n.isBlank())
                .orElse("assignment");
        if (name.length() > 120) {
            badRequest(ctx, "name_too_long", "assignment name must be 120 characters or fewer");
            return;
        }

        Assignment assignment = registry.create(name.trim());
        Map<String, String> body = new LinkedHashMap<>();
        body.put("id", JsonOut.string(assignment.getId()));
        body.put("name", JsonOut.string(assignment.getName()));
        body.put("createdAt", JsonOut.string(assignment.getCreatedAt().toString()));
        ctx.status(201).contentType("application/json").result(JsonOut.object(body));
    }

    private void listAssignments(Context ctx) {
        List<String> rendered = new ArrayList<>();
        for (Assignment assignment : registry.all()) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("id", JsonOut.string(assignment.getId()));
            item.put("name", JsonOut.string(assignment.getName()));
            item.put("analysed", String.valueOf(assignment.getLastReport() != null));
            rendered.add(JsonOut.object(item));
        }
        ctx.contentType("application/json").result(JsonOut.array(rendered));
    }

    private void getAssignment(Context ctx) {
        Assignment assignment = require(ctx);
        if (assignment == null) {
            return;
        }

        Map<String, String> body = new LinkedHashMap<>();
        body.put("id", JsonOut.string(assignment.getId()));
        body.put("name", JsonOut.string(assignment.getName()));
        body.put("createdAt", JsonOut.string(assignment.getCreatedAt().toString()));
        body.put("submissionCount", String.valueOf(
                repositories.submissions().findByAssignment(assignment.getId()).size()));
        body.put("referenceFileCount",
                String.valueOf(store.referenceFiles(assignment.getId()).size()));
        body.put("analysed", String.valueOf(assignment.getLastReport() != null));
        ctx.contentType("application/json").result(JsonOut.object(body));
    }

    /**
     * Removes an assignment and everything stored under it.
     *
     * <p>Ordered by foreign key: results reference submissions, so results go first,
     * then submissions, then the assignment's own rows. The registry entry goes last so
     * that if a step fails partway, the assignment is still reachable and the delete can
     * simply be retried.
     */
    private void deleteAssignment(Context ctx) {
        Assignment assignment = require(ctx);
        if (assignment == null) {
            return;
        }

        invalidateAnalysis(assignment);
        SubmissionRepository submissions = repositories.submissions();
        int removed = 0;
        for (CodeSubmission submission : submissions.findByAssignment(assignment.getId())) {
            if (submissions.deleteById(submission.getSubmissionId())) {
                removed++;
            }
        }
        store.delete(assignment.getId());
        registry.remove(assignment.getId());

        Map<String, String> body = new LinkedHashMap<>();
        body.put("deleted", JsonOut.string(assignment.getId()));
        body.put("submissionsDeleted", String.valueOf(removed));
        ctx.contentType("application/json").result(JsonOut.object(body));
    }

    private void uploadSubmissions(Context ctx) {
        Assignment assignment = require(ctx);
        if (assignment == null) {
            return;
        }

        List<UploadedFile> uploads = readUploads(ctx, "no files were uploaded");
        if (uploads == null) {
            return;
        }

        SubmissionIntake intake = new SubmissionIntake();
        List<SubmissionIntake.Extracted> extracted = new ArrayList<>();
        List<String> rejected = new ArrayList<>();

        for (UploadedFile upload : uploads) {
            String filename = safeName(upload.filename());
            if (filename == null) {
                rejected.add(reason("(unnamed)", "missing filename"));
                continue;
            }

            if (SubmissionIntake.isArchive(filename)) {
                // A ZIP is the only upload shape that carries folder structure, which is
                // the most reliable way to tell whose work a file is.
                try (InputStream in = upload.content()) {
                    SubmissionIntake.Result result = intake.readArchive(filename, in);
                    extracted.addAll(result.files());
                    for (SubmissionIntake.Skipped skipped : result.skipped()) {
                        rejected.add(reason(skipped.path(), skipped.reason()));
                    }
                } catch (IOException e) {
                    rejected.add(reason(filename, "could not be read"));
                }
                continue;
            }

            if (upload.size() > SubmissionIntake.MAX_FILE_BYTES) {
                rejected.add(reason(filename, "larger than "
                        + SubmissionIntake.MAX_FILE_BYTES + " bytes"));
                continue;
            }
            if (!intake.isSupportedSource(filename)) {
                rejected.add(reason(filename, "unrecognised source extension"));
                continue;
            }
            try (InputStream in = upload.content()) {
                intake.readPlainFile(filename, in).ifPresentOrElse(
                        extracted::add,
                        () -> rejected.add(reason(filename, "could not be read")));
            } catch (IOException e) {
                rejected.add(reason(filename, "could not be read"));
            }
        }

        SubmissionRepository submissions = repositories.submissions();
        List<SubmissionIntake.Identified> identified = SubmissionIntake.identify(
                assignment.getId(), extracted, assignment::nextUnidentifiedId);

        List<String> accepted = new ArrayList<>();
        int unidentified = 0;
        for (SubmissionIntake.Identified item : identified) {
            submissions.save(new CodeSubmission(
                    item.submissionId(),
                    item.identity().studentId(),
                    assignment.getId(),
                    item.file().filename(),
                    item.file().content()));
            if (item.identity().isUnidentified()) {
                unidentified++;
            }
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("filename", JsonOut.string(item.file().filename()));
            entry.put("path", JsonOut.string(item.file().path()));
            entry.put("student", JsonOut.string(item.identity().studentId()));
            entry.put("identifiedBy", JsonOut.string(item.identity().how().name().toLowerCase()));
            accepted.add(JsonOut.object(entry));
        }

        if (!identified.isEmpty()) {
            invalidateAnalysis(assignment);
        }

        // Reported from what was actually stored, not from what was processed. The
        // previous version counted files it had looped over, which meant five uploads
        // that collided onto one row were still reported as five accepted.
        int storedNow = submissions.findByAssignment(assignment.getId()).size();

        Map<String, String> body = new LinkedHashMap<>();
        body.put("acceptedCount", String.valueOf(accepted.size()));
        body.put("storedTotal", String.valueOf(storedNow));
        body.put("unidentifiedCount", String.valueOf(unidentified));
        body.put("accepted", JsonOut.array(accepted));
        body.put("rejected", JsonOut.array(rejected));
        ctx.status(accepted.isEmpty() ? 400 : 200)
                .contentType("application/json").result(JsonOut.object(body));
    }

    /**
     * Lists the batch as it currently stands, before or after analysis.
     *
     * <p>Reports whether an analysis is still valid. Any change to the batch invalidates
     * it, and a stale report is worse than none — so this is what the UI reads to decide
     * whether to show results or prompt for a re-run.
     */
    private void listSubmissions(Context ctx) {
        Assignment assignment = require(ctx);
        if (assignment == null) {
            return;
        }

        List<CodeSubmission> batch =
                repositories.submissions().findByAssignment(assignment.getId());

        List<String> rendered = new ArrayList<>();
        int unidentified = 0;
        for (CodeSubmission submission : batch) {
            boolean unknown = StudentIdentity.looksUnidentified(submission.getStudentId());
            if (unknown) {
                unidentified++;
            }
            Map<String, String> item = new LinkedHashMap<>();
            item.put("submissionId", JsonOut.string(submission.getSubmissionId()));
            item.put("student", JsonOut.string(submission.getStudentId()));
            item.put("filename", JsonOut.string(submission.getFilename()));
            item.put("language", JsonOut.string(languageOf(submission.getFilename())));
            item.put("lineCount", String.valueOf(
                    submission.getSourceCode().split("\n", -1).length));
            item.put("byteCount", String.valueOf(
                    submission.getSourceCode().getBytes(StandardCharsets.UTF_8).length));
            item.put("unidentified", String.valueOf(unknown));
            rendered.add(JsonOut.object(item));
        }

        Map<String, String> body = new LinkedHashMap<>();
        body.put("assignmentId", JsonOut.string(assignment.getId()));
        body.put("assignmentName", JsonOut.string(assignment.getName()));
        body.put("createdAt", JsonOut.string(assignment.getCreatedAt().toString()));
        body.put("count", String.valueOf(batch.size()));
        body.put("unidentifiedCount", String.valueOf(unidentified));
        body.put("analysed", String.valueOf(assignment.getLastReport() != null));
        body.put("referenceFileCount",
                String.valueOf(store.referenceFiles(assignment.getId()).size()));
        body.put("submissions", JsonOut.array(rendered));
        ctx.contentType("application/json").result(JsonOut.object(body));
    }

    /**
     * Re-attributes a submission to a student.
     *
     * <p>The submission id is left alone. It is opaque, nothing displays it, and changing
     * it would mean deleting and re-inserting a row that results reference. Only the
     * student changes — which is the only part anyone reads.
     */
    private void updateSubmission(Context ctx) {
        Assignment assignment = require(ctx);
        if (assignment == null) {
            return;
        }

        String submissionId = ctx.pathParam("sid");
        CodeSubmission existing = findSubmission(assignment.getId(), submissionId);
        if (existing == null) {
            ctx.status(404).contentType("application/json").result(JsonOut.error(
                    "unknown_submission", "no submission " + submissionId + " in this assignment"));
            return;
        }

        String student = ctx.formParam("student");
        if (student == null || student.isBlank()) {
            badRequest(ctx, "missing_student", "a student identifier is required");
            return;
        }
        student = student.trim();
        if (student.length() > 80) {
            badRequest(ctx, "student_too_long", "student identifier must be 80 characters or fewer");
            return;
        }
        if (StudentIdentity.looksUnidentified(student)) {
            // Otherwise a human could hand-assign a placeholder that the automatic
            // counter later hands to a different file.
            badRequest(ctx, "reserved_identifier",
                    "'" + StudentIdentity.UNIDENTIFIED_PREFIX + "' is reserved for generated identities");
            return;
        }

        repositories.submissions().save(new CodeSubmission(
                existing.getSubmissionId(), student, assignment.getId(),
                existing.getFilename(), existing.getSourceCode()));
        invalidateAnalysis(assignment);

        Map<String, String> body = new LinkedHashMap<>();
        body.put("submissionId", JsonOut.string(existing.getSubmissionId()));
        body.put("student", JsonOut.string(student));
        body.put("analysisInvalidated", "true");
        ctx.contentType("application/json").result(JsonOut.object(body));
    }

    private void deleteSubmission(Context ctx) {
        Assignment assignment = require(ctx);
        if (assignment == null) {
            return;
        }

        String submissionId = ctx.pathParam("sid");
        if (findSubmission(assignment.getId(), submissionId) == null) {
            ctx.status(404).contentType("application/json").result(JsonOut.error(
                    "unknown_submission", "no submission " + submissionId + " in this assignment"));
            return;
        }

        // Results hold foreign keys to submissions, so they have to go first. They would
        // have been invalidated by this change in any case.
        invalidateAnalysis(assignment);
        repositories.submissions().deleteById(submissionId);

        Map<String, String> body = new LinkedHashMap<>();
        body.put("deleted", JsonOut.string(submissionId));
        body.put("remaining", String.valueOf(
                repositories.submissions().findByAssignment(assignment.getId()).size()));
        body.put("analysisInvalidated", "true");
        ctx.contentType("application/json").result(JsonOut.object(body));
    }

    /**
     * Accepts reference/skeleton files for boilerplate suppression.
     *
     * <p>Every fingerprint in a stored file is removed from every submission at the next
     * analysis. A file no tokenizer recognises could never be applied, so it is rejected
     * rather than stored — acceptance must never be mistaken for effect.
     */
    private void uploadBoilerplate(Context ctx) {
        Assignment assignment = require(ctx);
        if (assignment == null) {
            return;
        }

        List<UploadedFile> files = readUploads(ctx, "no reference files were uploaded");
        if (files == null) {
            return;
        }

        List<String> stored = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (UploadedFile file : files) {
            String filename = safeName(file.filename());
            String problem = null;
            if (filename == null) {
                filename = "(unnamed)";
                problem = "missing filename";
            } else if (file.size() > MAX_UPLOAD_BYTES) {
                problem = "larger than " + MAX_UPLOAD_BYTES + " bytes";
            } else if (tokenizers.forFilename(filename).isEmpty()) {
                problem = "unrecognised source extension";
            } else {
                try (InputStream in = file.content()) {
                    store.addReferenceFile(assignment.getId(), filename,
                            new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    stored.add(JsonOut.string(filename));
                } catch (IOException e) {
                    problem = "could not be read";
                }
            }
            if (problem != null) {
                rejected.add(reason(filename, problem));
                problems.add(filename + " (" + problem + ")");
            }
        }

        if (stored.isEmpty()) {
            badRequest(ctx, "no_usable_reference_files",
                    "none of the files could be used: " + String.join(", ", problems));
            return;
        }

        // Reference files change every score, so an analysis computed without them no
        // longer describes this assignment.
        invalidateAnalysis(assignment);

        Map<String, String> body = new LinkedHashMap<>();
        body.put("stored", JsonOut.array(stored));
        body.put("rejected", JsonOut.array(rejected));
        body.put("referenceFileCount",
                String.valueOf(store.referenceFiles(assignment.getId()).size()));
        body.put("applied", "true");
        body.put("analysisInvalidated", "true");
        body.put("note", JsonOut.string("Starter code in these files is excluded from every "
                + "comparison. Re-run the analysis to see updated scores."));
        ctx.contentType("application/json").result(JsonOut.object(body));
    }

    private void clearBoilerplate(Context ctx) {
        Assignment assignment = require(ctx);
        if (assignment == null) {
            return;
        }

        int removed = store.deleteReferenceFiles(assignment.getId());
        if (removed > 0) {
            invalidateAnalysis(assignment);
        }

        Map<String, String> body = new LinkedHashMap<>();
        body.put("removed", String.valueOf(removed));
        body.put("analysisInvalidated", String.valueOf(removed > 0));
        ctx.contentType("application/json").result(JsonOut.object(body));
    }

    private void analyze(Context ctx) {
        Assignment assignment = require(ctx);
        if (assignment == null) {
            return;
        }

        List<CodeSubmission> batch =
                repositories.submissions().findByAssignment(assignment.getId());
        if (batch.isEmpty()) {
            badRequest(ctx, "no_submissions", "upload submissions before analysing");
            return;
        }

        String apiKey = ctx.header(API_KEY_HEADER);
        Report report = engine.analyzeBatch(batch, apiKey, referenceSources(assignment));
        assignment.setLastReport(report);

        Map<String, String> body = new LinkedHashMap<>();
        body.put("assignmentId", JsonOut.string(assignment.getId()));
        body.put("submissionCount", String.valueOf(batch.size()));
        body.put("pairCount", String.valueOf(report.getSimilarityResults().size()));
        body.put("flaggedCount", String.valueOf(report.getFlaggedResults().size()));
        body.put("aiResultCount", String.valueOf(report.getAiResults().size()));
        ctx.contentType("application/json").result(JsonOut.object(body));
    }

    private void results(Context ctx) {
        Assignment assignment = require(ctx);
        if (assignment == null) {
            return;
        }
        Report report = assignment.getLastReport();
        if (report == null) {
            ctx.status(409).contentType("application/json").result(JsonOut.error(
                    "not_analysed", "run analyze before requesting results"));
            return;
        }

        List<CodeSubmission> batch =
                repositories.submissions().findByAssignment(assignment.getId());
        ctx.contentType("application/json").result(new ResultsView(statistics).render(
                assignment, report, batch, store.referenceFiles(assignment.getId()).size(),
                matchAnalysisFor(assignment, batch)));
    }

    /**
     * Everything needed to review one pair: both sources, the shared line ranges, and
     * the metrics in context.
     *
     * <p>Available for <b>every</b> pair, not only flagged ones. A reviewer who cannot
     * open the pair that just missed the threshold cannot check whether the threshold
     * was right, and "unusual for this cohort" is a claim that invites exactly that
     * scrutiny. Spans are computed on demand rather than shipped for all N(N-1)/2 pairs
     * in the results payload.
     */
    private void pairDetail(Context ctx) {
        Assignment assignment = require(ctx);
        if (assignment == null) {
            return;
        }
        Report report = assignment.getLastReport();
        if (report == null) {
            ctx.status(409).contentType("application/json").result(JsonOut.error(
                    "not_analysed", "run analyze before requesting pair detail"));
            return;
        }

        String left = ctx.queryParam("left");
        String right = ctx.queryParam("right");
        if (left == null || right == null || left.isBlank() || right.isBlank()) {
            badRequest(ctx, "missing_pair", "both left and right submission ids are required");
            return;
        }

        CodeSubmission leftSubmission = findSubmission(assignment.getId(), left);
        CodeSubmission rightSubmission = findSubmission(assignment.getId(), right);
        if (leftSubmission == null || rightSubmission == null) {
            ctx.status(404).contentType("application/json").result(JsonOut.error(
                    "unknown_submission", "one or both submissions are not in this assignment"));
            return;
        }

        List<CodeSubmission> batch =
                repositories.submissions().findByAssignment(assignment.getId());
        MatchAnalysis analysis = matchAnalysisFor(assignment, batch);

        ctx.contentType("application/json").result(new PairView(statistics)
                .render(report, analysis, leftSubmission, rightSubmission));
    }

    /** Reuses the cached analysis when the batch has not changed under it. */
    private MatchAnalysis matchAnalysisFor(Assignment assignment, List<CodeSubmission> batch) {
        Object cached = assignment.getMatchAnalysis();
        if (cached instanceof MatchAnalysis analysis) {
            return analysis;
        }
        MatchAnalysis analysis = new MatchAnalysis(
                batch, engine.referenceFingerprints(referenceSources(assignment)));
        assignment.setMatchAnalysis(analysis);
        return analysis;
    }

    private Map<String, String> referenceSources(Assignment assignment) {
        Map<String, String> sources = new LinkedHashMap<>();
        for (Assignment.ReferenceFile file : store.referenceFiles(assignment.getId())) {
            sources.put(file.filename(), file.content());
        }
        return sources;
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Reads the uploaded parts, treating an unparseable body as the client error it is.
     *
     * <p>Javalin surfaces a broken multipart body as an exception from
     * {@code uploadedFiles()}. Left alone that reaches the catch-all handler and becomes
     * a 500, which tells the caller the server broke when in fact their request did.
     *
     * @return the uploaded files, or null after writing a 400
     */
    private static List<UploadedFile> readUploads(Context ctx, String emptyMessage) {
        List<UploadedFile> files;
        try {
            files = ctx.uploadedFiles();
        } catch (Exception e) {
            // Catching Exception rather than RuntimeException on purpose: Javalin is
            // written in Kotlin, so uploadedFiles() can throw a checked IOException
            // without declaring it, and a narrower catch silently misses it.
            badRequest(ctx, "malformed_upload",
                    "the request body could not be read as multipart/form-data");
            return null;
        }
        if (files.isEmpty()) {
            badRequest(ctx, "no_files", emptyMessage);
            return null;
        }
        return files;
    }

    private CodeSubmission findSubmission(String assignmentId, String submissionId) {
        for (CodeSubmission submission : repositories.submissions().findByAssignment(assignmentId)) {
            if (submission.getSubmissionId().equals(submissionId)) {
                return submission;
            }
        }
        return null;
    }

    /**
     * Drops the analysis after the batch changes.
     *
     * <p>Both the cached report and the stored result rows go. A result is only
     * meaningful for the exact set of submissions it was computed over; leaving rows
     * behind would let {@code findByAssignment} return numbers that silently describe a
     * batch that no longer exists.
     */
    private void invalidateAnalysis(Assignment assignment) {
        assignment.setLastReport(null);
        repositories.results().deleteByAssignment(assignment.getId());
    }

    private String languageOf(String filename) {
        return tokenizers.forFilename(filename)
                .map(t -> t.language().name().toLowerCase(java.util.Locale.ROOT))
                .orElse("unknown");
    }

    /** @return the assignment, or null after writing a 404 */
    private Assignment require(Context ctx) {
        String id = ctx.pathParam("id");
        Optional<Assignment> found = registry.find(id);
        if (found.isEmpty()) {
            ctx.status(404).contentType("application/json")
                    .result(JsonOut.error("unknown_assignment", "no assignment with id " + id));
            return null;
        }
        return found.get();
    }

    private static void badRequest(Context ctx, String code, String message) {
        ctx.status(400).contentType("application/json").result(JsonOut.error(code, message));
    }

    private static String reason(String filename, String why) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("filename", JsonOut.string(filename));
        item.put("reason", JsonOut.string(why));
        return JsonOut.object(item);
    }

    /**
     * Strips any directory component from an uploaded filename.
     *
     * <p>A multipart filename is attacker-controlled. Nothing here writes it to disk, but
     * it becomes a submission id and is echoed back to the browser, so path segments are
     * removed rather than trusted.
     */
    static String safeName(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String name = raw.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.trim();
        return name.isEmpty() || name.equals(".") || name.equals("..") ? null : name;
    }
}
