package com.integrityengine.similarity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.integrityengine.domain.Fingerprint;
import com.integrityengine.tokenizer.Language;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * End-to-end: real source files through the real tokenizer, real winnowing and the real
 * comparators. Everything up to this point was tested against synthetic token streams,
 * which cannot catch a pipeline that is individually correct and jointly useless.
 *
 * <p>Thresholds are set with wide margin around measured behaviour, so they assert
 * "the separation is large" rather than pinning today's exact scores.
 */
class SimilarityPipelineIntegrationTest {

    private final JaccardComparator jaccard = new JaccardComparator();
    private final ContainmentComparator containment = new ContainmentComparator();

    static List<Arguments> languageSamples() {
        return List.of(
                Arguments.of(Language.JAVA, CodeSamples.JAVA_ORIGINAL,
                        CodeSamples.JAVA_PLAGIARISED, CodeSamples.JAVA_INDEPENDENT),
                Arguments.of(Language.PYTHON, CodeSamples.PYTHON_ORIGINAL,
                        CodeSamples.PYTHON_PLAGIARISED, CodeSamples.PYTHON_INDEPENDENT),
                Arguments.of(Language.C, CodeSamples.C_ORIGINAL,
                        CodeSamples.C_PLAGIARISED, CodeSamples.C_INDEPENDENT),
                Arguments.of(Language.CPP, CodeSamples.CPP_ORIGINAL,
                        CodeSamples.CPP_PLAGIARISED, CodeSamples.CPP_INDEPENDENT),
                Arguments.of(Language.JAVASCRIPT, CodeSamples.JS_ORIGINAL,
                        CodeSamples.JS_PLAGIARISED, CodeSamples.JS_INDEPENDENT));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("languageSamples")
    @DisplayName("A renamed, reformatted, reordered copy scores high end-to-end")
    void plagiarisedCopiesScoreHigh(Language language, String original, String plagiarised, String independent) {
        double score = jaccard.compare(
                Pipeline.fingerprints(language, original),
                Pipeline.fingerprints(language, plagiarised));

        assertTrue(score >= 0.85,
                language + ": a renamed/reformatted/reordered copy scored only " + score);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("languageSamples")
    @DisplayName("Independently written programs in the same language score low")
    void independentProgramsScoreLow(Language language, String original, String plagiarised, String independent) {
        double score = jaccard.compare(
                Pipeline.fingerprints(language, original),
                Pipeline.fingerprints(language, independent));

        assertTrue(score <= 0.30,
                language + ": unrelated programs scored " + score + ", too close to a match");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("languageSamples")
    @DisplayName("The gap between a copy and an unrelated program is wide, not marginal")
    void separationIsWide(Language language, String original, String plagiarised, String independent) {
        Set<Fingerprint> base = Pipeline.fingerprints(language, original);
        double copied = jaccard.compare(base, Pipeline.fingerprints(language, plagiarised));
        double unrelated = jaccard.compare(base, Pipeline.fingerprints(language, independent));

        assertTrue(copied >= 4 * unrelated,
                language + ": copy=" + copied + " unrelated=" + unrelated + " -- separation too narrow to threshold on");
    }

    @Test
    @DisplayName("Containment catches a snippet pasted into a large file; Jaccard misses it")
    void containmentCatchesWhatJaccardDilutes() {
        Set<Fingerprint> snippet = Pipeline.fingerprints(Language.JAVA, CodeSamples.JAVA_SNIPPET);
        Set<Fingerprint> host = Pipeline.fingerprints(Language.JAVA, CodeSamples.JAVA_HOST_CONTAINING_SNIPPET);

        double containedInHost = containment.compare(snippet, host);
        double hostContained = containment.compare(host, snippet);
        double symmetric = jaccard.compare(snippet, host);

        assertTrue(containedInHost >= 0.75,
                "the snippet is verbatim inside the host but scored only " + containedInHost);
        assertTrue(symmetric <= 0.40,
                "Jaccard was expected to dilute this case, but scored " + symmetric);
        assertTrue(containedInHost > 3 * hostContained,
                "direction should matter here: " + containedInHost + " vs " + hostContained);
    }

    // ------------------------------------------------------------- boilerplate, for real

    private static String submission(String body) {
        // The shared starter every student in the cohort was handed.
        return """
                public class Assignment {
                    private final int[] data;
                    public Assignment(int[] data) { this.data = data; }
                    public int size() { return data.length; }
                    public boolean isEmpty() { return data.length == 0; }
                    public int[] raw() { return data; }
                """ + body + "\n}\n";
    }

    private static final String COLLUDER_A = submission(
            "    public int best() { int top = data[0]; for (int i = 1; i < data.length; i++) { if (data[i] > top) { top = data[i]; } } return top; }");
    private static final String COLLUDER_B = submission(
            "    public int peak() { int win = data[0]; for (int k = 1; k < data.length; k++) { if (data[k] > win) { win = data[k]; } } return win; }");
    private static final String HONEST_1 = submission(
            "    public double spread() { double lo = data[0]; double hi = data[0]; for (double v : data) { lo = Math.min(lo, v); hi = Math.max(hi, v); } return hi - lo; }");
    private static final String HONEST_2 = submission(
            "    public String join(String sep) { StringBuilder sb = new StringBuilder(); for (int v : data) { sb.append(v).append(sep); } return sb.toString(); }");
    private static final String HONEST_3 = submission(
            "    public boolean sorted() { for (int i = 1; i < data.length; i++) { if (data[i - 1] > data[i]) return false; } return true; }");
    private static final String HONEST_4 = submission(
            "    public int[] doubled() { int[] out = new int[data.length]; for (int i = 0; i < data.length; i++) { out[i] = data[i] * 2; } return out; }");

    private static List<Set<Fingerprint>> cohort() {
        List<Set<Fingerprint>> sets = new ArrayList<>();
        for (String source : List.of(COLLUDER_A, COLLUDER_B, HONEST_1, HONEST_2, HONEST_3, HONEST_4)) {
            sets.add(Pipeline.fingerprints(Language.JAVA, source));
        }
        return sets;
    }

    @Test
    @DisplayName("Shared starter code inflates honest pairs -- the problem the filter exists for")
    void sharedStarterCodeInflatesHonestPairs() {
        List<Set<Fingerprint>> sets = cohort();

        double honest = jaccard.compare(sets.get(2), sets.get(3));
        assertTrue(honest >= 0.25,
                "expected shared boilerplate to inflate an honest pair, but scored " + honest);
    }

    @Test
    @DisplayName("Filtering collapses honest pairs while leaving the colluding pair intact")
    void filteringSeparatesCollusionFromBoilerplate() {
        List<Set<Fingerprint>> sets = cohort();
        Set<Fingerprint> boilerplate = new BoilerplateFilter().suppress(sets, BoilerplateFilter.DEFAULT_THRESHOLD);

        assertTrue(boilerplate.size() > 0, "the shared starter should have been detected");

        double honestBefore = jaccard.compare(sets.get(2), sets.get(3));

        double honestAfter = jaccard.compare(
                Pipeline.without(sets.get(2), boilerplate), Pipeline.without(sets.get(3), boilerplate));
        double colludeAfter = jaccard.compare(
                Pipeline.without(sets.get(0), boilerplate), Pipeline.without(sets.get(1), boilerplate));

        assertTrue(honestAfter < honestBefore / 2,
                "honest pair should collapse: " + honestBefore + " -> " + honestAfter);
        assertTrue(colludeAfter >= 0.85,
                "the colluding pair must survive filtering, but scored " + colludeAfter);
        assertTrue(colludeAfter > 5 * honestAfter,
                "separation after filtering: collude=" + colludeAfter + " honest=" + honestAfter);
    }

    /**
     * PERMANENT REGRESSION TEST -- do not delete if {@code BoilerplateFilter} is
     * refactored or replaced. This pins a real, unfixed limitation of frequency-based
     * boilerplate suppression rather than asserting correct behaviour, so it must be
     * migrated, not dropped, along with any rewrite of the filter.
     */
    @Test
    @DisplayName("Documented hazard, end-to-end: too low a threshold erases the collusion it should expose")
    void tooLowAThresholdErasesRealCollusionEndToEnd() {
        List<Set<Fingerprint>> sets = cohort();
        // The colluding pair is 2 of 6 submissions, so a threshold of 1/3 treats their
        // shared work as cohort boilerplate. This is a genuine limitation of frequency
        // based filtering, pinned here so a future change cannot make it worse silently.
        Set<Fingerprint> overAggressive = new BoilerplateFilter().suppress(sets, 2.0 / 6.0);

        double colludeAfter = jaccard.compare(
                Pipeline.without(sets.get(0), overAggressive), Pipeline.without(sets.get(1), overAggressive));

        assertTrue(colludeAfter < 0.10,
                "expected the over-aggressive threshold to destroy the evidence, got " + colludeAfter);
    }
}
