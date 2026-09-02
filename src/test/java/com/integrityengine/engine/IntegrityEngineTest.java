package com.integrityengine.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.integrityengine.ai.AIAuthorshipDetector;
import com.integrityengine.ai.LlmJudge;
import com.integrityengine.ai.LlmVerdict;
import com.integrityengine.domain.CodeSubmission;
import com.integrityengine.domain.Report;
import com.integrityengine.domain.SimilarityResult;
import com.integrityengine.fingerprint.WinnowingEngine;
import com.integrityengine.persistence.RepositoryFactory;
import com.integrityengine.persistence.ResultRepository;
import com.integrityengine.persistence.SubmissionRepository;
import com.integrityengine.similarity.BoilerplateFilter;
import com.integrityengine.similarity.SimilarityComparator;
import com.integrityengine.statistics.StatisticalAnalyzer;
import com.integrityengine.tokenizer.TokenizerFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End-to-end: real tokenizers, real winnowing, real comparators, real SQLite. */
class IntegrityEngineTest {

    @TempDir
    Path directory;

    private RepositoryFactory repositories;

    @BeforeEach
    void setUp() {
        repositories = new RepositoryFactory(directory.resolve("engine.db"));
    }

    private IntegrityEngine engine() {
        return new IntegrityEngine(repositories);
    }

    private static CodeSubmission java(String id, String assignment, String source) {
        return new CodeSubmission(id, "student-" + id, assignment, id + ".java", source);
    }

    // ---------------------------------------------------------------- ordering

    /**
     * Records the order of every write so the sequence can be asserted.
     *
     * <p>Two classes rather than one: both repository interfaces declare
     * {@code findByAssignment(String)} with different element types, which erasure will
     * not allow on a single implementation.
     */
    private static final class RecordingSubmissions implements SubmissionRepository {
        private final List<String> log;

        RecordingSubmissions(List<String> log) {
            this.log = log;
        }

        @Override
        public void save(CodeSubmission submission) {
            log.add("submission:" + submission.getSubmissionId());
        }

        @Override
        public List<CodeSubmission> findAll() {
            return List.of();
        }

        @Override
        public List<CodeSubmission> findByAssignment(String assignmentId) {
            return List.of();
        }

        @Override
        public boolean deleteById(String submissionId) {
            log.add("delete-submission:" + submissionId);
            return false;
        }
    }

    private static final class RecordingResults implements ResultRepository {
        private final List<String> log;

        RecordingResults(List<String> log) {
            this.log = log;
        }

        @Override
        public void save(SimilarityResult result) {
            log.add("result:" + result.getLeftSubmissionId() + "/" + result.getRightSubmissionId());
        }

        @Override
        public List<SimilarityResult> findByAssignment(String assignmentId) {
            return List.of();
        }

        @Override
        public int deleteByAssignment(String assignmentId) {
            log.add("delete-results:" + assignmentId);
            return 0;
        }
    }

    @Test
    @DisplayName("Every submission is saved before any result -- the caller cannot get this wrong")
    void submissionsArePersistedBeforeResults() {
        // Results carry foreign keys to submissions, so the reverse order is rejected by
        // the database. The facade owns the sequence rather than documenting it.
        List<String> log = new ArrayList<>();
        IntegrityEngine engine = new IntegrityEngine(new RecordingSubmissions(log),
                new RecordingResults(log), new TokenizerFactory(),
                WinnowingEngine.withDefaults(), new BoilerplateFilter(), SimilarityComparator.jaccard(),
                new StatisticalAnalyzer(), new AIAuthorshipDetector());

        engine.analyzeBatch(List.of(
                java("s1", "a1", loopSource("alpha")),
                java("s2", "a1", loopSource("beta")),
                java("s3", "a1", loopSource("gamma"))));

        int lastSubmission = -1;
        int firstResult = Integer.MAX_VALUE;
        for (int i = 0; i < log.size(); i++) {
            if (log.get(i).startsWith("submission:")) {
                lastSubmission = i;
            } else if (i < firstResult) {
                firstResult = i;
            }
        }

        assertEquals(3, log.stream().filter(e -> e.startsWith("submission:")).count());
        assertEquals(3, log.stream().filter(e -> e.startsWith("result:")).count());
        assertTrue(lastSubmission < firstResult,
                "a result was written before the last submission: " + log);
    }

    @Test
    @DisplayName("Against the real database, the foreign keys are satisfied by construction")
    void realPersistenceRoundTripSucceeds() {
        engine().analyzeBatch(List.of(
                java("s1", "a1", loopSource("alpha")),
                java("s2", "a1", loopSource("beta"))));

        assertEquals(2, repositories.submissions().findByAssignment("a1").size());
        assertEquals(1, repositories.results().findByAssignment("a1").size());
    }

    // ------------------------------------------------------- statistics hoisting

    /** Counts how often the cohort statistics are recomputed. */
    private static final class CountingAnalyzer extends StatisticalAnalyzer {
        int medianCalls;

        @Override
        public double computeMedian(List<Double> values) {
            medianCalls++;
            return super.computeMedian(values);
        }
    }

    @Test
    @DisplayName("Cohort statistics are computed once per batch, not once per pair")
    void statisticsAreHoistedOutOfThePairwiseLoop() {
        CountingAnalyzer analyzer = new CountingAnalyzer();
        List<String> log = new ArrayList<>();
        IntegrityEngine engine = new IntegrityEngine(new RecordingSubmissions(log),
                new RecordingResults(log), new TokenizerFactory(),
                WinnowingEngine.withDefaults(), new BoilerplateFilter(), SimilarityComparator.jaccard(),
                analyzer, new AIAuthorshipDetector());

        List<CodeSubmission> batch = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            batch.add(java("s" + i, "a1", loopSource("name" + i)));
        }
        engine.analyzeBatch(batch);

        // 8 submissions is 28 pairs. computeMAD and computeMAE each call computeMedian
        // internally, so one hoisted pass is 3 calls; a per-pair implementation would be
        // in the hundreds.
        assertTrue(analyzer.medianCalls <= 4,
                "expected the statistics to be computed once, saw " + analyzer.medianCalls + " median calls");
    }

    // ------------------------------------------------------------ the real pipeline

    @Test
    @DisplayName("A renamed, reformatted copy is flagged and dominates the ranking")
    void plagiarisedPairIsFlaggedAndRanksFarAboveEverythingElse() {
        Report report = engine().analyzeBatch(mixedCohort());

        assertEquals(28, report.getSimilarityResults().size());

        List<SimilarityResult> ranked = report.getSimilarityResults().stream()
                .sorted((a, b) -> Double.compare(b.getSimilarityScore(), a.getSimilarityScore()))
                .toList();

        assertEquals("copy-a", ranked.get(0).getLeftSubmissionId());
        assertEquals("copy-b", ranked.get(0).getRightSubmissionId());
        assertTrue(ranked.get(0).getSimilarityScore() > 4 * ranked.get(1).getSimilarityScore(),
                "the copy should dominate: " + ranked.get(0).getSimilarityScore()
                        + " vs next " + ranked.get(1).getSimilarityScore());

        assertTrue(report.getFlaggedResults().stream()
                        .anyMatch(r -> r.getLeftSubmissionId().equals("copy-a")
                                && r.getRightSubmissionId().equals("copy-b")),
                "the copied pair must be flagged");
        assertTrue(report.getFlaggedResults().size() <= 3,
                "flags should be a small minority of 28 pairs, got " + report.getFlaggedResults().size());
    }

    @Test
    @DisplayName("The threshold answers \"unusual for this cohort\", which is not the same as \"plagiarised\"")
    void aTightCohortCanFlagAModestSimilarity() {
        // Documented behaviour, pinned deliberately. In this cohort the median pairwise
        // score is ~0.03 with a MAD of ~0.02, so an unrelated pair scoring ~0.18 sits
        // nearly five MADs out and is flagged. That is the statistic working correctly,
        // and it is precisely why the output is "review these" rather than an accusation.
        Report report = engine().analyzeBatch(mixedCohort());

        assertTrue(report.getCohortMad() < 0.1,
                "this fixture is meant to produce a tight distribution, got MAD " + report.getCohortMad());

        List<SimilarityResult> modestFlags = report.getFlaggedResults().stream()
                .filter(r -> r.getSimilarityScore() < 0.5)
                .toList();
        assertFalse(modestFlags.isEmpty(),
                "expected at least one flag well below any plausible copying threshold");
    }

    @Test
    void reportCarriesTheCohortStatisticsThatExplainTheFlag() {
        Report report = engine().analyzeBatch(mixedCohort());

        assertTrue(report.getCohortMedian() >= 0.0 && report.getCohortMedian() <= 1.0);
        assertTrue(report.getCohortMad() >= 0.0);
        assertEquals("a1", report.getAssignmentId());
        assertTrue(report.getFlaggedResults().size() <= report.getSimilarityResults().size());
    }

    // --------------------------------------------------------- boilerplate policy

    @Test
    @DisplayName("Shared starter code is actually suppressed: engine scores sit below raw scores")
    void boilerplateSuppressionIsApplied() {
        List<CodeSubmission> batch = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            batch.add(java("s" + i, "a1", STARTER + uniqueMethod(i) + "\n}\n"));
        }

        Report report = engine().analyzeBatch(batch);

        double rawScore = rawJaccard(STARTER + uniqueMethod(0) + "\n}\n",
                STARTER + uniqueMethod(1) + "\n}\n");
        double engineScore = report.getSimilarityResults().stream()
                .filter(r -> r.getLeftSubmissionId().equals("s0") && r.getRightSubmissionId().equals("s1"))
                .findFirst().orElseThrow().getSimilarityScore();

        assertTrue(rawScore > 0.3, "the fixture must actually share boilerplate, raw was " + rawScore);
        assertTrue(engineScore < rawScore,
                "suppression should have lowered this pair: raw=" + rawScore + " engine=" + engineScore);
    }

    @Test
    @DisplayName("The threshold leaves a large sub-group's shared work visible")
    void theBoilerplateThresholdDoesNotEraseAHalfCohortSubGroup() {
        // This is the failure observed on a real 8-file run: four structurally similar
        // submissions sit at exactly 4/8, so a 0.5 threshold deletes their shared
        // fingerprints and their scores collapse -- one pair fell from 0.75 to 0.00.
        // The engine's higher threshold is what keeps them visible, and this pins it.
        assertEquals(0.8, IntegrityEngine.BOILERPLATE_THRESHOLD, 1e-9);

        List<CodeSubmission> batch = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            batch.add(java("twin" + i, "a1", GRADEBOOK.replace("scores", "values" + i)));
        }
        for (int i = 0; i < 4; i++) {
            batch.add(java("other" + i, "a1", honestSource(i)));
        }

        Report report = engine().analyzeBatch(batch);

        List<SimilarityResult> twinPairs = report.getSimilarityResults().stream()
                .filter(r -> r.getLeftSubmissionId().startsWith("twin")
                        && r.getRightSubmissionId().startsWith("twin"))
                .toList();

        assertEquals(6, twinPairs.size());
        for (SimilarityResult pair : twinPairs) {
            assertTrue(pair.getSimilarityScore() > 0.5,
                    "the sub-group's shared work was suppressed away: "
                            + pair.getLeftSubmissionId() + " vs " + pair.getRightSubmissionId()
                            + " = " + pair.getSimilarityScore());
        }
    }

    /** Jaccard over unfiltered fingerprints, for comparison against the engine's output. */
    private static double rawJaccard(String left, String right) {
        TokenizerFactory factory = new TokenizerFactory();
        WinnowingEngine winnowing = WinnowingEngine.withDefaults();
        var a = new java.util.HashSet<>(winnowing.generateFingerprints(
                factory.forLanguage(com.integrityengine.tokenizer.Language.JAVA).tokenize(left)));
        var b = new java.util.HashSet<>(winnowing.generateFingerprints(
                factory.forLanguage(com.integrityengine.tokenizer.Language.JAVA).tokenize(right)));
        return SimilarityComparator.jaccard().compare(a, b);
    }

    private static final String STARTER = """
            public class Assignment {
                private final int[] data;
                public Assignment(int[] data) { this.data = data; }
                public int size() { return data.length; }
                public boolean isEmpty() { return data.length == 0; }
                public int[] raw() { return data; }
                public int first() { return data[0]; }
                public int last() { return data[data.length - 1]; }
            """;

    private static String uniqueMethod(int index) {
        return "    public int op" + index + "(int seed) { int acc" + index + " = seed;"
                + " for (int i = 0; i < data.length; i++) { acc" + index + " = acc" + index
                + " * " + (index + 2) + " + data[i]; } return acc" + index + "; }";
    }

    // ------------------------------------------------------------------ AI module

    @Test
    @DisplayName("Without a key the local model still judges every submission")
    void noApiKeyStillProducesAiResults() {
        // Behaviour change, deliberate: authorship analysis used to require a key and
        // returned nothing without one. The local trained model needs no network, so a
        // keyless run now carries a full AI section. The short circuit that used to sit
        // in analyzeAuthorship() silently emptied that section on every offline run.
        Report report = engine().analyzeBatch(List.of(
                java("s1", "a1", loopSource("alpha")),
                java("s2", "a1", loopSource("beta"))));

        assertEquals(2, report.getAiResults().size(),
                "the local model should judge every measurable submission without a key");
        report.getAiResults().forEach(result -> {
            assertEquals("stylometric-forest-v1", result.getModelUsed());
            assertTrue(result.getAiLikelihood() >= 0.0 && result.getAiLikelihood() <= 1.0);
        });
    }

    @Test
    @DisplayName("With a key and a stubbed judge, verdicts reach the report")
    void withAnApiKeyTheAiModuleContributes() {
        LlmJudge stub = (key, submission) -> Optional.of(new LlmVerdict(0.9, "looks generated", "stub-model"));
        IntegrityEngine engine = new IntegrityEngine(repositories.submissions(), repositories.results(),
                new TokenizerFactory(), WinnowingEngine.withDefaults(), new BoilerplateFilter(),
                SimilarityComparator.jaccard(), new StatisticalAnalyzer(),
                new AIAuthorshipDetector(stub));

        Report report = engine.analyzeBatch(List.of(
                java("s1", "a1", GRADEBOOK),
                java("s2", "a1", GRADEBOOK_RENAMED)), "sk-ant-test");

        assertEquals(2, report.getAiResults().size());
        assertEquals("stub-model", report.getAiResults().get(0).getModelUsed());
    }

    @Test
    @DisplayName("A failing judge degrades the report rather than failing the batch")
    void aFailingJudgeDoesNotFailTheBatch() {
        LlmJudge broken = (key, submission) -> {
            throw new IllegalStateException("network down");
        };
        IntegrityEngine engine = new IntegrityEngine(repositories.submissions(), repositories.results(),
                new TokenizerFactory(), WinnowingEngine.withDefaults(), new BoilerplateFilter(),
                SimilarityComparator.jaccard(), new StatisticalAnalyzer(),
                new AIAuthorshipDetector(broken));

        Report report = engine.analyzeBatch(List.of(
                java("s1", "a1", GRADEBOOK),
                java("s2", "a1", GRADEBOOK_RENAMED)), "sk-ant-test");

        assertEquals(2, report.getAiResults().size());
        assertEquals(AIAuthorshipDetector.HEURISTIC_ONLY, report.getAiResults().get(0).getModelUsed());
        assertEquals(1, report.getSimilarityResults().size(), "similarity analysis must be unaffected");
    }

    // ------------------------------------------------------------ edge cases

    @Test
    void anEmptyBatchProducesAnEmptyReportRatherThanThrowing() {
        Report report = engine().analyzeBatch(List.of());

        assertEquals(List.of(), report.getSimilarityResults());
        assertEquals(List.of(), report.getFlaggedResults());
        assertEquals(0.0, report.getCohortMedian(), 1e-9);
    }

    @Test
    void aSingleSubmissionHasNoPairsToCompare() {
        Report report = engine().analyzeBatch(List.of(java("s1", "a1", loopSource("alpha"))));

        assertEquals(List.of(), report.getSimilarityResults());
        assertEquals(1, repositories.submissions().findByAssignment("a1").size(),
                "the submission is still stored");
    }

    @Test
    void aBatchSpanningAssignmentsIsRejected() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> engine().analyzeBatch(List.of(
                        java("s1", "a1", loopSource("alpha")),
                        java("s2", "a2", loopSource("beta")))));

        assertTrue(failure.getMessage().contains("spans assignments"), failure.getMessage());
    }

    @Test
    void duplicateSubmissionIdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> engine().analyzeBatch(List.of(
                java("s1", "a1", loopSource("alpha")),
                java("s1", "a1", loopSource("beta")))));
    }

    @Test
    @DisplayName("Unrecognised file types are stored but excluded from the score distribution")
    void unknownLanguagesArePersistedButNotCompared() {
        Report report = engine().analyzeBatch(List.of(
                java("s1", "a1", loopSource("alpha")),
                java("s2", "a1", loopSource("beta")),
                new CodeSubmission("notes", "student-x", "a1", "notes.txt", "just some prose")));

        assertEquals(3, repositories.submissions().findByAssignment("a1").size());
        assertEquals(1, report.getSimilarityResults().size(),
                "only the two Java files should be compared");
        assertFalse(report.getSimilarityResults().toString().contains("notes"));
    }

    @Test
    void nullsAreRejected() {
        assertThrows(NullPointerException.class, () -> engine().analyzeBatch(null));
        assertThrows(NullPointerException.class, () -> new IntegrityEngine((RepositoryFactory) null));
    }

    // ------------------------------------------------------------------ fixtures

    /** Two copies of one program plus six unrelated submissions. */
    private static List<CodeSubmission> mixedCohort() {
        List<CodeSubmission> batch = new ArrayList<>();
        batch.add(java("copy-a", "a1", GRADEBOOK));
        batch.add(java("copy-b", "a1", GRADEBOOK_RENAMED));
        for (int i = 0; i < 6; i++) {
            batch.add(java("honest-" + i, "a1", honestSource(i)));
        }
        return batch;
    }

    private static String loopSource(String name) {
        return "public class C { int " + name + "(int[] v){ int " + name + "Total = 0;"
                + " for (int i = 0; i < v.length; i++) { if (v[i] > 0) { " + name + "Total += v[i]; } }"
                + " return " + name + "Total; } }";
    }

    /**
     * Six unrelated, realistically sized submissions.
     *
     * <p>Size matters here. An earlier version of this fixture used one-method classes,
     * and three honest pairs came out flagged: with tiny files most pairs score exactly
     * zero, the MAD collapses, and the MAE fallback then treats any small incidental
     * overlap as a large deviation. That is a real property of the pipeline on very
     * short submissions, not a fixture artefact -- see the Stage 8 report.
     */
    private static String honestSource(int seed) {
        return switch (seed % 6) {
            case 0 -> """
                    public class TextTools {
                        public String join(String[] parts, String separator) {
                            StringBuilder builder = new StringBuilder();
                            for (int i = 0; i < parts.length; i++) {
                                builder.append(parts[i]);
                                if (i < parts.length - 1) { builder.append(separator); }
                            }
                            return builder.toString();
                        }
                        public String reverse(String text) {
                            char[] letters = text.toCharArray();
                            for (int i = 0, j = letters.length - 1; i < j; i++, j--) {
                                char swap = letters[i];
                                letters[i] = letters[j];
                                letters[j] = swap;
                            }
                            return new String(letters);
                        }
                        public boolean palindrome(String text) {
                            return text.equals(reverse(text));
                        }
                    }
                    """;
            case 1 -> """
                    public class NumberTheory {
                        public boolean isPrime(long candidate) {
                            if (candidate < 2) { return false; }
                            if (candidate % 2 == 0) { return candidate == 2; }
                            for (long divisor = 3; divisor * divisor <= candidate; divisor += 2) {
                                if (candidate % divisor == 0) { return false; }
                            }
                            return true;
                        }
                        public long gcd(long first, long second) {
                            while (second != 0) {
                                long remainder = first % second;
                                first = second;
                                second = remainder;
                            }
                            return first;
                        }
                        public long lcm(long first, long second) {
                            return first / gcd(first, second) * second;
                        }
                    }
                    """;
            case 2 -> """
                    public class Matrix {
                        private final double[][] cells;
                        public Matrix(double[][] cells) { this.cells = cells; }
                        public Matrix multiply(Matrix other) {
                            int rows = cells.length;
                            int inner = other.cells.length;
                            int columns = other.cells[0].length;
                            double[][] product = new double[rows][columns];
                            for (int r = 0; r < rows; r++) {
                                for (int c = 0; c < columns; c++) {
                                    double sum = 0;
                                    for (int k = 0; k < inner; k++) {
                                        sum += cells[r][k] * other.cells[k][c];
                                    }
                                    product[r][c] = sum;
                                }
                            }
                            return new Matrix(product);
                        }
                    }
                    """;
            case 3 -> """
                    public class WordCounter {
                        private final Map<String, Integer> tallies = new HashMap<>();
                        public void ingest(String line) {
                            for (String word : line.split("\\s+")) {
                                if (word.isBlank()) { continue; }
                                tallies.merge(word.toLowerCase(), 1, Integer::sum);
                            }
                        }
                        public List<String> topWords(int limit) {
                            List<String> words = new ArrayList<>(tallies.keySet());
                            words.sort((left, right) -> tallies.get(right) - tallies.get(left));
                            return words.subList(0, Math.min(limit, words.size()));
                        }
                        public int frequency(String word) {
                            return tallies.getOrDefault(word.toLowerCase(), 0);
                        }
                    }
                    """;
            case 4 -> """
                    public class BankAccount {
                        private long balanceInCents;
                        private final List<String> history = new ArrayList<>();
                        public void deposit(long cents) {
                            if (cents <= 0) { throw new IllegalArgumentException("deposit must be positive"); }
                            balanceInCents += cents;
                            history.add("deposit " + cents);
                        }
                        public void withdraw(long cents) {
                            if (cents > balanceInCents) { throw new IllegalStateException("insufficient funds"); }
                            balanceInCents -= cents;
                            history.add("withdraw " + cents);
                        }
                        public long balance() { return balanceInCents; }
                        public List<String> statement() { return List.copyOf(history); }
                    }
                    """;
            default -> """
                    public class BinarySearchTree {
                        private Node root;
                        private static class Node {
                            int key; Node left; Node right;
                            Node(int key) { this.key = key; }
                        }
                        public void insert(int key) { root = insertAt(root, key); }
                        private Node insertAt(Node node, int key) {
                            if (node == null) { return new Node(key); }
                            if (key < node.key) { node.left = insertAt(node.left, key); }
                            else if (key > node.key) { node.right = insertAt(node.right, key); }
                            return node;
                        }
                        public boolean contains(int key) {
                            Node cursor = root;
                            while (cursor != null) {
                                if (key == cursor.key) { return true; }
                                cursor = key < cursor.key ? cursor.left : cursor.right;
                            }
                            return false;
                        }
                    }
                    """;
        };
    }

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

    private static final String GRADEBOOK_RENAMED = """
            public class MarkRegister
            {
                private final int[] values;
                public MarkRegister(int[] values) { this.values = values; }
                /* highest mark */
                public int peak()
                {
                    int top = values[0];
                    for (int k = 1; k < values.length; k++) {
                        if (values[k] > top) { top = values[k]; }
                    }
                    return top;
                }
                public double mean()
                {
                    int sum = 0;
                    for (int value : values) { sum += value; }
                    return (double) sum / values.length;
                }
            }
            """;
}
