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
 * <p>Everything behind this class is package-private or reached through a factory. The
 * CLI, the REST layer and the JavaFX front end talk to this type and the {@code domain}
 * value classes, and to nothing else.
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

    private final SubmissionRepository submissionRepository;
    private final ResultRepository resultRepository;
    private final TokenizerFactory tokenizers;
    private final WinnowingEngine winnowing;
    private final BoilerplateFilter boilerplateFilter;
    private final SimilarityComparator comparator;
    private final StatisticalAnalyzer statistics;
    private final AIAuthorshipDetector aiDetector;

    /**
     * Production wiring: real implementations throughout, Jaccard as the comparator.
     *
     * @param repositories where submissions and results are stored
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

    /** Full injection, used by tests to substitute a stubbed LLM judge or comparator. */
    public IntegrityEngine(SubmissionRepository submissionRepository,
                           ResultRepository resultRepository,
                           TokenizerFactory tokenizers,
                           WinnowingEngine winnowing,
                           BoilerplateFilter boilerplateFilter,
                           SimilarityComparator comparator,
                           StatisticalAnalyzer statistics,
                           AIAuthorshipDetector aiDetector) {
        this.submissionRepository = Objects.requireNonNull(submissionRepository, "submissionRepository");
        this.resultRepository = Objects.requireNonNull(resultRepository, "resultRepository");
        this.tokenizers = Objects.requireNonNull(tokenizers, "tokenizers");
        this.winnowing = Objects.requireNonNull(winnowing, "winnowing");
        this.boilerplateFilter = Objects.requireNonNull(boilerplateFilter, "boilerplateFilter");
        this.comparator = Objects.requireNonNull(comparator, "comparator");
        this.statistics = Objects.requireNonNull(statistics, "statistics");
        this.aiDetector = Objects.requireNonNull(aiDetector, "aiDetector");
    }

    /**
     * Run a full cross-comparison over one batch, with no AI-authorship analysis.
     *
     * @param submissions every submission for a single assignment
     * @return the completed report
     */
    public Report analyzeBatch(List<CodeSubmission> submissions) {
        return analyzeBatch(submissions, null);
    }

    /**
     * Run a full cross-comparison, optionally including AI-authorship analysis.
     *
     * <p>The key is a parameter rather than a field so that BYOK holds all the way up
     * through the facade: this object cannot retain a key between calls. A null or blank
     * key skips the AI module entirely and is a supported mode, not an error.
     *
     * @param submissions every submission for a single assignment
     * @param llmApiKey   caller-supplied key, or null to skip AI-authorship analysis
     * @return the completed report
     * @throws IllegalArgumentException if the batch spans assignments or repeats an id
     */
    public Report analyzeBatch(List<CodeSubmission> submissions, String llmApiKey) {
        Objects.requireNonNull(submissions, "submissions");
        String assignmentId = validateBatch(submissions);

        // ---- 1. Submissions first. Results reference them, so this cannot be reordered.
        for (CodeSubmission submission : submissions) {
            submissionRepository.save(submission);
        }

        // ---- 2. Tokenize and fingerprint.
        Map<String, Set<Fingerprint>> fingerprints = fingerprintAll(submissions);

        // ---- 3. Suppress cohort-wide boilerplate.
        List<Set<Fingerprint>> batch = new ArrayList<>(fingerprints.values());
        Set<Fingerprint> boilerplate =
                boilerplateFilter.suppress(batch, BOILERPLATE_THRESHOLD);
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

        // ---- 6. AI authorship, only when a key was supplied.
        List<AIAuthorshipResult> aiResults = analyzeAuthorship(submissions, llmApiKey);

        // ---- 7. Results last, once every submission is safely stored.
        for (SimilarityResult result : results) {
            resultRepository.save(result);
        }

        return new Report(assignmentId, Instant.now(), results,
                flags.flagged, aiResults, flags.median, flags.mad, flags.mae);
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
