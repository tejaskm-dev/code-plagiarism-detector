package com.integrityengine.engine;

import com.integrityengine.ai.AIAuthorshipDetector;
import com.integrityengine.domain.AIAuthorshipResult;
import com.integrityengine.domain.CodeSubmission;
import com.integrityengine.domain.Fingerprint;
import com.integrityengine.domain.Report;
import com.integrityengine.domain.SimilarityResult;
import com.integrityengine.fingerprint.WinnowingEngine;
import com.integrityengine.persistence.RepositoryFactory;
import com.integrityengine.persistence.ResultRepository;
import com.integrityengine.persistence.SubmissionRepository;
import com.integrityengine.similarity.BoilerplateFilter;
import com.integrityengine.similarity.SimilarityComparator;
import com.integrityengine.statistics.StatisticalAnalyzer;
import com.integrityengine.tokenizer.ITokenizer;
import com.integrityengine.tokenizer.TokenizerFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The single public-facing facade over the whole pipeline.
 *
 * <p>Library callers need only this type and the {@code domain} value classes:
 *
 * <pre>{@code
 * IntegrityEngine engine = new IntegrityEngine();
 * Report report = engine.analyzeBatch(List.of(
 *         new CodeSubmission("hw3/alice", "alice", "hw3", "Main.java", aliceSource),
 *         new CodeSubmission("hw3/bob", "bob", "hw3", "Main.java", bobSource)));
 * for (SimilarityResult flagged : report.getFlaggedResults()) { ... }
 * }</pre>
 *
 * <p>The no-argument engine stores nothing. To keep submissions and results in SQLite,
 * construct it with a {@link RepositoryFactory} instead.
 *
 * <p><b>Persistence ordering is enforced here, not delegated.</b> Results carry foreign
 * keys to their submissions, so a result written before its submissions is rejected by
 * the database. Rather than documenting that as a rule callers must follow,
 * {@link #analyzeBatch} owns the whole sequence — save every submission, run the
 * pipeline, then save every result. A caller holding only this facade cannot get the
 * order wrong, because it never gets to choose.
 */
public final class IntegrityEngine {

    /**
     * How widely a fingerprint must appear before this engine calls it boilerplate.
     *
     * <p>Deliberately higher than {@link BoilerplateFilter#DEFAULT_THRESHOLD}. The
     * filter's own default of 0.5 is the mechanism's neutral setting; choosing a policy
     * is the orchestrator's job, and 0.5 turns out to be actively harmful on real
     * cohorts. Measured on a real 8-submission batch containing four structurally
     * similar files: at 0.5 those four sit exactly at the threshold, and their shared
     * fingerprints are deleted as boilerplate — one genuinely similar pair fell from
     * 0.75 to 0.00, and the rest from ~0.85 to ~0.30.
     *
     * <p>At 0.8 a fingerprint must appear in almost every submission before it is
     * discarded, which still removes imports and provided scaffolding while leaving a
     * sizeable sub-group's shared work visible. This does not eliminate the underlying
     * hazard — a cohort where more than 80% collude would still defeat it — it just
     * moves the cliff somewhere a real cohort is unlikely to reach.
     */
    public static final double BOILERPLATE_THRESHOLD = 0.8;

    /** Null when the engine was built to store nothing. */
    private final SubmissionRepository submissionRepository;
    /** Null exactly when {@link #submissionRepository} is. */
    private final ResultRepository resultRepository;
    private final TokenizerFactory tokenizers;
    private final WinnowingEngine winnowing;
    private final BoilerplateFilter boilerplateFilter;
    private final SimilarityComparator comparator;
    private final StatisticalAnalyzer statistics;
    private final AIAuthorshipDetector aiDetector;

    /**
     * Analysis only: nothing is written to disk or kept between calls.
     *
     * <p>The usual choice when embedding the engine as a library. Needs no database and
     * no dependencies beyond this jar.
     */
    public IntegrityEngine() {
        this(null, null, new TokenizerFactory(), WinnowingEngine.withDefaults(),
                new BoilerplateFilter(), SimilarityComparator.jaccard(),
                new StatisticalAnalyzer(), new AIAuthorshipDetector());
    }

    /**
     * Stores every analysed submission and result through the given repositories.
     *
     * @param repositories where submissions and results are stored; the SQLite factory
     *                     needs {@code org.xerial:sqlite-jdbc} on the classpath
     */
    public IntegrityEngine(RepositoryFactory repositories) {
        this(Objects.requireNonNull(repositories, "repositories").submissions(),
                repositories.results(),
                new TokenizerFactory(),
                WinnowingEngine.withDefaults(),
                new BoilerplateFilter(),
                SimilarityComparator.jaccard(),
                new StatisticalAnalyzer(),
                new AIAuthorshipDetector());
    }

    /**
     * Full injection, for substituting any stage, such as a custom comparator, repository
     * or LLM judge.
     *
     * @param submissionRepository where submissions are stored, or null together with
     *                             {@code resultRepository} to store nothing
     * @param resultRepository     where results are stored, or null together with
     *                             {@code submissionRepository}
     * @param tokenizers           picks a tokenizer from each submission's filename
     * @param winnowing            turns token streams into fingerprints
     * @param boilerplateFilter    decides which fingerprints are cohort-wide boilerplate
     * @param comparator           scores each pair of fingerprint sets
     * @param statistics           decides which scores are outliers for the cohort
     * @param aiDetector           produces AI-authorship verdicts
     */
    public IntegrityEngine(SubmissionRepository submissionRepository,
                           ResultRepository resultRepository,
                           TokenizerFactory tokenizers,
                           WinnowingEngine winnowing,
                           BoilerplateFilter boilerplateFilter,
                           SimilarityComparator comparator,
                           StatisticalAnalyzer statistics,
                           AIAuthorshipDetector aiDetector) {
        // Both or neither: storing submissions without their results, or results whose
        // submissions were never stored, would leave the database inconsistent.
        if ((submissionRepository == null) != (resultRepository == null)) {
            throw new NullPointerException(submissionRepository == null
                    ? "submissionRepository" : "resultRepository");
        }
        this.submissionRepository = submissionRepository;
        this.resultRepository = resultRepository;
        this.tokenizers = Objects.requireNonNull(tokenizers, "tokenizers");
        this.winnowing = Objects.requireNonNull(winnowing, "winnowing");
        this.boilerplateFilter = Objects.requireNonNull(boilerplateFilter, "boilerplateFilter");
        this.comparator = Objects.requireNonNull(comparator, "comparator");
        this.statistics = Objects.requireNonNull(statistics, "statistics");
        this.aiDetector = Objects.requireNonNull(aiDetector, "aiDetector");
    }

    /**
     * Run a full cross-comparison over one batch.
     *
     * <p>AI authorship comes from the offline stylometric model only; no network calls
     * are made.
     *
     * @param submissions every submission for a single assignment
     * @return the completed report
     * @throws IllegalArgumentException if the batch spans assignments or repeats an id
     */
    public Report analyzeBatch(List<CodeSubmission> submissions) {
        return analyzeBatch(submissions, null);
    }

    /**
     * Run a full cross-comparison, optionally including AI-authorship analysis.
     *
     * <p>The offline stylometric model always runs. A key adds an LLM second opinion on
     * top of it. The key is a parameter rather than a field so that BYOK holds all the way
     * up through the facade: this object cannot retain a key between calls.
     *
     * @param submissions every submission for a single assignment
     * @param llmApiKey   caller-supplied Anthropic key, or null for the offline model only
     * @return the completed report
     * @throws IllegalArgumentException if the batch spans assignments or repeats an id
     */
    public Report analyzeBatch(List<CodeSubmission> submissions, String llmApiKey) {
        return analyzeBatch(submissions, llmApiKey, Map.of());
    }

    /**
     * Run a full cross-comparison with instructor-supplied starter code removed.
     *
     * <p>Reference files are the explicit counterpart to cohort-inferred suppression.
     * Every fingerprint they contain is discarded from every submission before
     * comparison, <b>regardless of cohort size</b>: the small-cohort guard in
     * {@link BoilerplateFilter} exists because frequency cannot tell starter code from
     * collusion, but a file the instructor handed out is starter code by definition, so
     * that ambiguity does not arise.
     *
     * @param submissions    every submission for a single assignment
     * @param llmApiKey      caller-supplied key, or null to skip the LLM second opinion
     * @param referenceFiles starter/skeleton sources keyed by filename; the extension
     *                       selects the tokenizer, and unrecognised files are ignored
     * @return the completed report
     * @throws IllegalArgumentException if the batch spans assignments or repeats an id
     */
    public Report analyzeBatch(List<CodeSubmission> submissions, String llmApiKey,
                               Map<String, String> referenceFiles) {
        Objects.requireNonNull(submissions, "submissions");
        Objects.requireNonNull(referenceFiles, "referenceFiles");
        String assignmentId = validateBatch(submissions);

        // ---- 1. Submissions first. Results reference them, so this cannot be reordered.
        if (submissionRepository != null) {
            for (CodeSubmission submission : submissions) {
                submissionRepository.save(submission);
            }
        }

        // ---- 2. Tokenize and fingerprint.
        Map<String, Set<Fingerprint>> fingerprints = fingerprintAll(submissions);

        // ---- 3. Suppress cohort-wide boilerplate and any supplied starter code.
        List<Set<Fingerprint>> batch = new ArrayList<>(fingerprints.values());
        Set<Fingerprint> boilerplate =
                new HashSet<>(boilerplateFilter.suppress(batch, BOILERPLATE_THRESHOLD));
        boilerplate.addAll(referenceFingerprints(referenceFiles));
        Map<String, Set<Fingerprint>> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, Set<Fingerprint>> entry : fingerprints.entrySet()) {
            Set<Fingerprint> reduced = new HashSet<>(entry.getValue());
            reduced.removeAll(boilerplate);
            cleaned.put(entry.getKey(), reduced);
        }

        // ---- 4. Compare every pair once.
        List<SimilarityResult> results = compareAllPairs(cleaned);

        // ---- 5. Flag outliers, computing the cohort statistics exactly once.
        Flags flags = flagOutliers(results);

        // ---- 6. AI authorship: the offline model, plus the LLM when a key was supplied.
        List<AIAuthorshipResult> aiResults = analyzeAuthorship(submissions, llmApiKey);

        // ---- 7. Results last, once every submission is safely stored.
        if (resultRepository != null) {
            for (SimilarityResult result : results) {
                resultRepository.save(result);
            }
        }

        return new Report(assignmentId, Instant.now(), results,
                flags.flagged, aiResults, flags.median, flags.mad, flags.mae);
    }

    /**
     * The fingerprints of instructor-supplied starter code.
     *
     * <p>Public so that anything re-deriving per-pair detail (the REST layer's diff view)
     * removes exactly the same set the scores were computed without.
     *
     * @param referenceFiles starter/skeleton sources keyed by filename
     * @return every fingerprint found in a file with a recognised extension
     */
    public Set<Fingerprint> referenceFingerprints(Map<String, String> referenceFiles) {
        Objects.requireNonNull(referenceFiles, "referenceFiles");
        Set<Fingerprint> fingerprints = new HashSet<>();
        for (Map.Entry<String, String> file : referenceFiles.entrySet()) {
            tokenizers.forFilename(file.getKey()).ifPresent(tokenizer -> fingerprints.addAll(
                    winnowing.generateFingerprints(tokenizer.tokenize(file.getValue()))));
        }
        return fingerprints;
    }

    // ------------------------------------------------------------------ pipeline

    /** @return the single assignment id shared by the batch */
    private static String validateBatch(List<CodeSubmission> submissions) {
        if (submissions.isEmpty()) {
            return "";
        }

        Set<String> ids = new HashSet<>();
        String assignmentId = submissions.get(0).getAssignmentId();
        for (CodeSubmission submission : submissions) {
            Objects.requireNonNull(submission, "submissions must not contain null");
            if (!submission.getAssignmentId().equals(assignmentId)) {
                // Mixing assignments would pool unrelated cohorts into one distribution,
                // and every statistic downstream is defined relative to a cohort.
                throw new IllegalArgumentException(
                        "batch spans assignments: " + assignmentId + " and " + submission.getAssignmentId());
            }
            if (!ids.add(submission.getSubmissionId())) {
                throw new IllegalArgumentException(
                        "duplicate submission id in batch: " + submission.getSubmissionId());
            }
        }
        return assignmentId;
    }

    /**
     * Submissions in a language no tokenizer recognises are stored but excluded from
     * comparison — scoring them as zeroes would drag the cohort median down and make
     * every real pair look more unusual than it is.
     */
    private Map<String, Set<Fingerprint>> fingerprintAll(List<CodeSubmission> submissions) {
        Map<String, Set<Fingerprint>> fingerprints = new LinkedHashMap<>();
        for (CodeSubmission submission : submissions) {
            Optional<ITokenizer> tokenizer = tokenizers.forFilename(submission.getFilename());
            if (tokenizer.isEmpty()) {
                continue;
            }
            fingerprints.put(submission.getSubmissionId(), new HashSet<>(
                    winnowing.generateFingerprints(tokenizer.get().tokenize(submission.getSourceCode()))));
        }
        return fingerprints;
    }

    private List<SimilarityResult> compareAllPairs(Map<String, Set<Fingerprint>> fingerprints) {
        List<String> ids = new ArrayList<>(fingerprints.keySet());
        List<SimilarityResult> results = new ArrayList<>();

        for (int i = 0; i < ids.size(); i++) {
            for (int j = i + 1; j < ids.size(); j++) {
                double score = comparator.compare(
                        fingerprints.get(ids.get(i)), fingerprints.get(ids.get(j)));
                results.add(new SimilarityResult(ids.get(i), ids.get(j), score, comparator.name()));
            }
        }
        return results;
    }

    /**
     * Flag the anomalous pairs.
     *
     * <p>The cohort's median, MAD and MAE are computed once for the whole batch and
     * passed in, rather than letting each pair recompute them. With N submissions there
     * are N(N-1)/2 pairs, so the per-call form would sort the same score list a
     * quadratic number of times.
     */
    private Flags flagOutliers(List<SimilarityResult> results) {
        if (results.isEmpty()) {
            return new Flags(List.of(), 0.0, 0.0, 0.0);
        }

        List<Double> scores = results.stream().map(SimilarityResult::getSimilarityScore).toList();
        double median = statistics.computeMedian(scores);
        double mad = statistics.computeMAD(scores);
        double mae = statistics.computeMAE(scores);

        List<SimilarityResult> flagged = new ArrayList<>();
        for (SimilarityResult result : results) {
            if (statistics.isOutlier(result.getSimilarityScore(), median, mad, mae)) {
                flagged.add(result);
            }
        }
        return new Flags(flagged, median, mad, mae);
    }

    /**
     * Authorship analysis now runs whether or not a key was supplied: the local model in
     * {@code AiAuthorshipModel} needs no network. A key adds a second opinion on top. The
     * blank-key short circuit that used to sit here dated from when the module could not
     * work without one, and left every keyless run with an empty AI section.
     */
    private List<AIAuthorshipResult> analyzeAuthorship(List<CodeSubmission> submissions, String llmApiKey) {
        List<AIAuthorshipResult> aiResults = new ArrayList<>();
        for (CodeSubmission submission : submissions) {
            aiDetector.analyze(llmApiKey, submission).ifPresent(aiResults::add);
        }
        return aiResults;
    }

    private static final class Flags {
        final List<SimilarityResult> flagged;
        final double median;
        final double mad;
        final double mae;

        Flags(List<SimilarityResult> flagged, double median, double mad, double mae) {
            this.flagged = flagged;
            this.median = median;
            this.mad = mad;
            this.mae = mae;
        }
    }
}
