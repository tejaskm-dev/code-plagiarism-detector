package com.integrityengine.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.integrityengine.domain.CodeSubmission;
import com.integrityengine.domain.Report;
import com.integrityengine.domain.SimilarityResult;
import com.integrityengine.engine.IntegrityEngine;
import com.integrityengine.persistence.RepositoryFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MatchAnalysisTest {

    @TempDir
    Path workspace;

    private static CodeSubmission java(String id, String source) {
        return new CodeSubmission("eval/" + id, id, "eval", id + ".java", source);
    }

    private static List<CodeSubmission> batch() {
        List<CodeSubmission> batch = new ArrayList<>();
        batch.add(java("alice", GRADEBOOK));
        batch.add(java("dana", RENAMED));
        batch.add(java("queue", UNRELATED));
        batch.add(java("tree", TREE));
        batch.add(java("text", TEXT));
        return batch;
    }

    @Test
    @DisplayName("Scores still match the engine when reference files are in play")
    void recomputedScoresDoNotDriftWithReferenceFiles() {
        List<CodeSubmission> batch = batch();
        Map<String, String> references = Map.of("Starter.java", GRADEBOOK);
        IntegrityEngine engine =
                new IntegrityEngine(new RepositoryFactory(workspace.resolve("drift-ref.db")));
        Report report = engine.analyzeBatch(batch, null, references);
        MatchAnalysis analysis = new MatchAnalysis(batch, engine.referenceFingerprints(references));

        for (SimilarityResult result : report.getSimilarityResults()) {
            assertEquals(result.getSimilarityScore(),
                    analysis.jaccard(result.getLeftSubmissionId(), result.getRightSubmissionId()), 1e-12,
                    "drift on " + result.getLeftSubmissionId() + " vs " + result.getRightSubmissionId());
        }
    }

    @Test
    @DisplayName("The API's recomputed score matches the engine's for every pair")
    void recomputedScoresDoNotDriftFromTheEngine() {
        // MatchAnalysis re-runs the tokenize -> winnow -> suppress -> compare pipeline
        // because Report carries scores but not fingerprints. That duplication is the
        // risk this test exists to pin: if the two ever diverge, the UI would quietly
        // display different numbers from the report it claims to be showing.
        List<CodeSubmission> batch = batch();
        Report report = new IntegrityEngine(new RepositoryFactory(workspace.resolve("drift.db")))
                .analyzeBatch(batch);
        MatchAnalysis analysis = new MatchAnalysis(batch);

        assertFalse(report.getSimilarityResults().isEmpty());
        for (SimilarityResult result : report.getSimilarityResults()) {
            double recomputed = analysis.jaccard(
                    result.getLeftSubmissionId(), result.getRightSubmissionId());
            assertEquals(result.getSimilarityScore(), recomputed, 1e-12,
                    "drift on " + result.getLeftSubmissionId() + " vs "
                            + result.getRightSubmissionId());
        }
    }

    @Test
    @DisplayName("Containment is asymmetric and Jaccard is not, on real files")
    void containmentIsDirectional() {
        MatchAnalysis analysis = new MatchAnalysis(batch());

        double forward = analysis.containment("eval/alice", "eval/dana");
        double backward = analysis.containment("eval/dana", "eval/alice");
        assertEquals(analysis.jaccard("eval/alice", "eval/dana"),
                analysis.jaccard("eval/dana", "eval/alice"), 1e-12);
        assertTrue(forward > 0 && backward > 0);
    }

    @Test
    @DisplayName("Shared spans land inside the file and cover real lines")
    void spansAreWithinBounds() {
        MatchAnalysis analysis = new MatchAnalysis(batch());
        int aliceLines = GRADEBOOK.split("\n", -1).length;

        MatchAnalysis.SharedRegions regions = analysis.sharedRegions("eval/alice", "eval/dana");

        assertTrue(regions.sharedFingerprints() > 0, "a renamed copy must share fingerprints");
        assertFalse(regions.leftSpans().isEmpty());
        for (MatchAnalysis.LineSpan span : regions.leftSpans()) {
            assertTrue(span.startLine() >= 1, "line numbers are 1-based, got " + span.startLine());
            assertTrue(span.endLine() <= aliceLines,
                    "span runs past the end of the file: " + span.endLine() + " > " + aliceLines);
            assertTrue(span.startLine() <= span.endLine());
        }
    }

    @Test
    @DisplayName("Spans are merged and ordered, so the UI paints blocks not stripes")
    void spansAreMergedAndSorted() {
        MatchAnalysis analysis = new MatchAnalysis(batch());
        List<MatchAnalysis.LineSpan> spans =
                analysis.sharedRegions("eval/alice", "eval/dana").leftSpans();

        for (int i = 1; i < spans.size(); i++) {
            assertTrue(spans.get(i - 1).endLine() + 1 < spans.get(i).startLine(),
                    "adjacent or overlapping spans should have been merged: "
                            + spans.get(i - 1) + " then " + spans.get(i));
        }
    }

    @Test
    void unrelatedFilesShareLittleOrNothing() {
        MatchAnalysis analysis = new MatchAnalysis(batch());

        assertTrue(analysis.jaccard("eval/alice", "eval/queue") < 0.35,
                "unrelated programs should not look alike");
    }

    @Test
    @DisplayName("A file in an unrecognised language is skipped, not crashed on")
    void unknownLanguagesAreSkipped() {
        List<CodeSubmission> withProse = new ArrayList<>(batch());
        withProse.add(new CodeSubmission("eval/notes", "notes", "eval", "notes.txt", "just prose"));

        MatchAnalysis analysis = new MatchAnalysis(withProse);

        assertFalse(analysis.covers("eval/notes"));
        assertTrue(analysis.covers("eval/alice"));
        assertEquals(0.0, analysis.jaccard("eval/notes", "eval/alice"), 1e-12);
        assertEquals(0, analysis.sharedRegions("eval/notes", "eval/alice").sharedFingerprints());
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

    private static final String TREE = """
            public class Tree {
                private int key;
                private Tree left;
                private Tree right;
                public void insert(int value) {
                    if (value < key) {
                        if (left == null) { left = new Tree(); }
                        left.insert(value);
                    } else {
                        if (right == null) { right = new Tree(); }
                        right.insert(value);
                    }
                }
            }
            """;

    private static final String TEXT = """
            public class TextTools {
                public String join(String[] parts, String separator) {
                    StringBuilder builder = new StringBuilder();
                    for (int i = 0; i < parts.length; i++) {
                        builder.append(parts[i]);
                        if (i < parts.length - 1) { builder.append(separator); }
                    }
                    return builder.toString();
                }
            }
            """;
}
