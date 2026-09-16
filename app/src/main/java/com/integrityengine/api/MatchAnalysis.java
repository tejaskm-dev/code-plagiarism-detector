package com.integrityengine.api;

import com.integrityengine.domain.CodeSubmission;
import com.integrityengine.domain.Fingerprint;
import com.integrityengine.domain.Token;
import com.integrityengine.engine.IntegrityEngine;
import com.integrityengine.fingerprint.WinnowingEngine;
import com.integrityengine.similarity.BoilerplateFilter;
import com.integrityengine.similarity.SimilarityComparator;
import com.integrityengine.tokenizer.ITokenizer;
import com.integrityengine.tokenizer.TokenizerFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Derives the per-pair detail the UI needs: containment in both directions, and the
 * source line ranges the two files actually share.
 *
 * <p>None of this is new business logic. It re-runs the same public building blocks the
 * engine uses — {@link TokenizerFactory}, {@link WinnowingEngine},
 * {@link BoilerplateFilter} at {@link IntegrityEngine#BOILERPLATE_THRESHOLD} — because
 * {@code Report} carries scores but not the fingerprints behind them, and a diff view
 * cannot be built from a number.
 *
 * <p>That re-run is duplication, and duplication drifts. {@code MatchAnalysisTest}
 * asserts that the Jaccard score recomputed here equals the engine's own reported score
 * for every pair in a real batch; if the two pipelines ever diverge, that test fails
 * rather than the UI quietly showing different numbers from the report.
 *
 * <p><b>Granularity is lines, not characters.</b> A {@link Fingerprint} records the token
 * index its k-gram started at, and {@link Token} records a line number but no column, so
 * a shared k-gram can be located to a line range and no finer.
 */
final class MatchAnalysis {

    private final TokenizerFactory tokenizers = new TokenizerFactory();
    private final WinnowingEngine winnowing = WinnowingEngine.withDefaults();
    private final SimilarityComparator jaccard = SimilarityComparator.jaccard();
    private final SimilarityComparator containment = SimilarityComparator.containment();

    private final Map<String, CodeSubmission> submissions = new LinkedHashMap<>();
    private final Map<String, List<Token>> tokensById = new HashMap<>();
    private final Map<String, List<Token>> lexicalById = new HashMap<>();
    private final Map<String, Set<Fingerprint>> cleaned = new LinkedHashMap<>();

    MatchAnalysis(List<CodeSubmission> batch) {
        this(batch, Set.of());
    }

    /**
     * @param referenceBoilerplate fingerprints of instructor-supplied starter code, as
     *                             produced by {@link IntegrityEngine#referenceFingerprints};
     *                             removed alongside cohort boilerplate so spans and scores
     *                             here match the report
     */
    MatchAnalysis(List<CodeSubmission> batch, Set<Fingerprint> referenceBoilerplate) {
        Map<String, Set<Fingerprint>> raw = new LinkedHashMap<>();
        for (CodeSubmission submission : batch) {
            submissions.put(submission.getSubmissionId(), submission);
            Optional<ITokenizer> tokenizer = tokenizers.forFilename(submission.getFilename());
            if (tokenizer.isEmpty()) {
                continue;
            }
            List<Token> tokens = tokenizer.get().tokenize(submission.getSourceCode());
            tokensById.put(submission.getSubmissionId(), tokens);
            // Same lexer, one stage earlier: identical length and index-aligned with the
            // normalised stream, which is what lets evidence name what was renamed.
            lexicalById.put(submission.getSubmissionId(),
                    tokenizer.get().lexicalTokens(submission.getSourceCode()));
            raw.put(submission.getSubmissionId(),
                    new HashSet<>(winnowing.generateFingerprints(tokens)));
        }

        Set<Fingerprint> boilerplate = new HashSet<>(new BoilerplateFilter()
                .suppress(new ArrayList<>(raw.values()), IntegrityEngine.BOILERPLATE_THRESHOLD));
        boilerplate.addAll(referenceBoilerplate);
        for (Map.Entry<String, Set<Fingerprint>> entry : raw.entrySet()) {
            Set<Fingerprint> reduced = new HashSet<>(entry.getValue());
            reduced.removeAll(boilerplate);
            cleaned.put(entry.getKey(), reduced);
        }
    }

    boolean covers(String submissionId) {
        return cleaned.containsKey(submissionId);
    }

    double jaccard(String left, String right) {
        return jaccard.compare(fingerprints(left), fingerprints(right));
    }

    /** How much of {@code left} appears in {@code right}. Direction matters. */
    double containment(String left, String right) {
        return containment.compare(fingerprints(left), fingerprints(right));
    }

    /** The source line ranges of every k-gram the two files share, per file. */
    SharedRegions sharedRegions(String left, String right) {
        Set<Fingerprint> shared = new HashSet<>(fingerprints(left));
        shared.retainAll(fingerprints(right));

        return new SharedRegions(
                spansFor(left, shared), spansFor(right, shared), shared.size());
    }

    CodeSubmission submission(String id) {
        return submissions.get(id);
    }

    List<Token> normalisedTokens(String id) {
        return tokensById.getOrDefault(id, List.of());
    }

    List<Token> lexicalTokens(String id) {
        return lexicalById.getOrDefault(id, List.of());
    }

    /** Fingerprints present in both files, by hash. */
    List<Fingerprint> sharedFingerprints(String left, String right) {
        Set<Fingerprint> shared = new HashSet<>(fingerprints(left));
        shared.retainAll(fingerprints(right));
        return List.copyOf(shared);
    }

    /** Earliest token position of each selected hash within one file. */
    Map<Long, Integer> positionsByHash(String id) {
        Map<Long, Integer> positions = new HashMap<>();
        for (Fingerprint fingerprint : fingerprints(id)) {
            positions.merge(fingerprint.getHash(), fingerprint.getPosition(), Math::min);
        }
        return positions;
    }

    private Set<Fingerprint> fingerprints(String id) {
        return cleaned.getOrDefault(id, Set.of());
    }

    /**
     * Map each shared fingerprint back to the lines its k-gram spans in one file, then
     * merge overlapping ranges so the UI paints contiguous blocks rather than stripes.
     */
    private List<LineSpan> spansFor(String submissionId, Set<Fingerprint> shared) {
        List<Token> tokens = tokensById.get(submissionId);
        if (tokens == null || tokens.isEmpty()) {
            return List.of();
        }

        Map<Long, Integer> positionByHash = new HashMap<>();
        for (Fingerprint fingerprint : fingerprints(submissionId)) {
            // Keep the earliest occurrence when a hash was selected more than once.
            positionByHash.merge(fingerprint.getHash(), fingerprint.getPosition(), Math::min);
        }

        List<LineSpan> spans = new ArrayList<>();
        int k = WinnowingEngine.DEFAULT_K_GRAM_SIZE;
        for (Fingerprint fingerprint : shared) {
            Integer start = positionByHash.get(fingerprint.getHash());
            if (start == null) {
                continue;
            }
            int end = Math.min(start + k - 1, tokens.size() - 1);
            spans.add(new LineSpan(tokens.get(start).getLine(), tokens.get(end).getLine()));
        }
        return merge(spans);
    }

    private static List<LineSpan> merge(List<LineSpan> spans) {
        if (spans.isEmpty()) {
            return List.of();
        }
        List<LineSpan> sorted = new ArrayList<>(spans);
        sorted.sort(Comparator.comparingInt(LineSpan::startLine).thenComparingInt(LineSpan::endLine));

        List<LineSpan> merged = new ArrayList<>();
        LineSpan current = sorted.get(0);
        for (int i = 1; i < sorted.size(); i++) {
            LineSpan next = sorted.get(i);
            if (next.startLine() <= current.endLine() + 1) {
                current = new LineSpan(current.startLine(), Math.max(current.endLine(), next.endLine()));
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return List.copyOf(merged);
    }

    /** Inclusive, 1-based line range. */
    record LineSpan(int startLine, int endLine) {
    }

    record SharedRegions(List<LineSpan> leftSpans, List<LineSpan> rightSpans, int sharedFingerprints) {
    }
}
