package com.integrityengine.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.integrityengine.domain.AIAuthorshipResult;
import com.integrityengine.domain.Report;
import com.integrityengine.domain.SimilarityResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TextReportWriterTest {

    private final TextReportWriter writer = new TextReportWriter();

    private static SimilarityResult pair(String left, String right, double score) {
        return new SimilarityResult(left, right, score, "jaccard");
    }

    private static final Map<String, String> STUDENTS =
            Map.of("a.java", "alice", "b.java", "bob", "c.java", "chen");

    private static Report report(List<SimilarityResult> all, List<SimilarityResult> flagged,
                                 double median, double mad, double mae) {
        return new Report("hw3", Instant.parse("2026-01-01T00:00:00Z"),
                all, flagged, List.of(), median, mad, mae);
    }

    @Test
    @DisplayName("Every score is shown next to the distribution it is being judged against")
    void scoresAreShownWithTheirContext() {
        String text = writer.render(report(
                List.of(pair("a.java", "b.java", 0.95), pair("a.java", "c.java", 0.10)),
                List.of(pair("a.java", "b.java", 0.95)), 0.10, 0.02, 0.05), STUDENTS);

        assertTrue(text.contains("Median similarity"), text);
        assertTrue(text.contains("cohort median"), text);
        assertTrue(text.contains("deviations from the median"), text);
        assertTrue(text.contains("review threshold"), text);
    }

    @Test
    @DisplayName("The displayed deviation is the actual modified z-score, not a placeholder")
    void theDeviationValueIsCorrect() {
        // median 0.10, MAD 0.02, score 0.95:
        //   0.6745 * (0.95 - 0.10) / 0.02 = 28.67
        // Asserting the value, not merely that the words "deviations" appear -- a report
        // that prints a confidently wrong number is worse than one that prints none.
        String text = writer.render(report(
                List.of(pair("a.java", "b.java", 0.95)),
                List.of(pair("a.java", "b.java", 0.95)), 0.10, 0.02, 0.05), STUDENTS);

        assertTrue(text.contains("28.7 deviations from the median"),
                "expected the real z-score in the output: " + text);
    }

    @Test
    @DisplayName("The stated review threshold is the score at which a pair would actually be flagged")
    void theReviewThresholdValueIsCorrect() {
        // median + 3.5 * MAD / 0.6745 = 0.10 + 3.5 * 0.02 / 0.6745 = 0.2038
        String text = writer.render(report(
                List.of(pair("a.java", "b.java", 0.95)),
                List.of(pair("a.java", "b.java", 0.95)), 0.10, 0.02, 0.05), STUDENTS);

        assertTrue(text.contains("roughly 0.20"),
                "expected the computed review threshold: " + text);
    }

    @Test
    @DisplayName("When the MAD collapses, both the deviation and the threshold switch to the MAE formula")
    void theFallbackFormulaIsUsedForBothNumbers() {
        // (0.95 - 0.10) / (1.253314 * 0.05) = 13.56, threshold 0.10 + 3.5 * 1.253314 * 0.05 = 0.319
        String text = writer.render(report(
                List.of(pair("a.java", "b.java", 0.95)),
                List.of(pair("a.java", "b.java", 0.95)), 0.10, 0.0, 0.05), STUDENTS);

        assertTrue(text.contains("13.6 deviations from the median"), text);
        assertTrue(text.contains("roughly 0.32"), text);
    }

    @Test
    @DisplayName("A pair below the threshold is described as below it")
    void belowThresholdPairsAreLabelledAsSuch() {
        String text = writer.render(report(
                List.of(pair("a.java", "b.java", 0.95), pair("a.java", "c.java", 0.12)),
                List.of(pair("a.java", "b.java", 0.95)), 0.10, 0.02, 0.05), STUDENTS);

        assertTrue(text.contains("past the 3.5 review threshold"), text);
        assertTrue(text.contains("(z  0.67)") || text.contains("z  0.67"),
                "the unflagged pair's z-score should be shown: " + text);
    }

    @Test
    @DisplayName("Students are named rather than shown only as file paths")
    void studentNamesAreUsedWhereKnown() {
        String text = writer.render(report(
                List.of(pair("a.java", "b.java", 0.95)),
                List.of(pair("a.java", "b.java", 0.95)), 0.10, 0.02, 0.05), STUDENTS);

        assertTrue(text.contains("alice"), text);
        assertTrue(text.contains("bob"), text);
        assertTrue(text.contains("a.java"), "the underlying file must still be shown");
    }

    @Test
    void unknownSubmissionsFallBackToTheirIdentifier() {
        String text = writer.render(report(
                List.of(pair("x.java", "y.java", 0.95)),
                List.of(pair("x.java", "y.java", 0.95)), 0.1, 0.02, 0.05), Map.of());

        assertTrue(text.contains("x.java"), text);
    }

    @Test
    @DisplayName("The reading given depends on how strong the match is")
    void interpretationVariesWithScore() {
        String strong = writer.render(report(List.of(pair("a.java", "b.java", 0.97)),
                List.of(pair("a.java", "b.java", 0.97)), 0.1, 0.02, 0.05), STUDENTS);
        String weak = writer.render(report(List.of(pair("a.java", "b.java", 0.30)),
                List.of(pair("a.java", "b.java", 0.30)), 0.1, 0.02, 0.05), STUDENTS);

        assertTrue(strong.contains("Effectively the same program"), strong);
        assertTrue(weak.contains("Convergent solutions look like this too"), weak);
        assertFalse(weak.contains("Effectively the same program"));
    }

    @Test
    @DisplayName("The report says which statistical tier actually decided the flags")
    void theMethodInUseIsStated() {
        String tierOne = writer.render(report(List.of(pair("a.java", "b.java", 0.9)),
                List.of(), 0.1, 0.02, 0.05), STUDENTS);
        String tierTwo = writer.render(report(List.of(pair("a.java", "b.java", 0.9)),
                List.of(), 0.1, 0.0, 0.05), STUDENTS);
        String tierThree = writer.render(report(List.of(pair("a.java", "b.java", 0.9)),
                List.of(), 0.1, 0.0, 0.0), STUDENTS);

        assertTrue(tierOne.contains("median and MAD"), tierOne);
        assertTrue(tierTwo.contains("MAD collapsed to zero"), tierTwo);
        assertTrue(tierThree.contains("every pair scored identically"), tierThree);
    }

    @Test
    @DisplayName("A zero MAD adds an explicit warning, because the fallback is more sensitive")
    void theMadCollapseIsCalledOut() {
        String text = writer.render(report(List.of(pair("a.java", "b.java", 0.9)),
                List.of(pair("a.java", "b.java", 0.9)), 0.1, 0.0, 0.05), STUDENTS);

        assertTrue(text.contains("MAD was zero"), text);
        assertTrue(text.contains("extra caution"), text);
    }

    @Test
    @DisplayName("Near misses are shown so the cut-off can be checked, not just trusted")
    void unflaggedPairsAreShownForContext() {
        String text = writer.render(report(
                List.of(pair("a.java", "b.java", 0.95), pair("a.java", "c.java", 0.44)),
                List.of(pair("a.java", "b.java", 0.95)), 0.1, 0.02, 0.05), STUDENTS);

        assertTrue(text.contains("NOT FLAGGED"), text);
        assertTrue(text.contains("0.4400"), text);
    }

    @Test
    @DisplayName("Finding nothing is reported as a fact about the distribution, not an all-clear")
    void anEmptyFlagListIsNotPresentedAsACleanBillOfHealth() {
        String text = writer.render(report(
                List.of(pair("a.java", "b.java", 0.10)), List.of(), 0.10, 0.02, 0.05), STUDENTS);

        assertTrue(text.contains("FLAGGED PAIRS (0 of 1)"), text);
        assertTrue(text.contains("if everyone copied the same source"), text);
    }

    @Test
    void aReportWithNoPairsRendersWithoutFailing() {
        String text = writer.render(report(List.of(), List.of(), 0.0, 0.0, 0.0), Map.of());

        assertTrue(text.contains("No pairs were compared"), text);
        assertTrue(text.contains("hw3"));
    }

    @Test
    @DisplayName("The caveat that a flag is not an accusation is always present")
    void theCaveatIsAlwaysIncluded() {
        String text = writer.render(report(List.of(pair("a.java", "b.java", 0.95)),
                List.of(pair("a.java", "b.java", 0.95)), 0.1, 0.02, 0.05), STUDENTS);

        assertTrue(text.contains("is not the same claim as"), text);
        assertTrue(text.contains("not a conclusion about a person"), text);
    }

    @Test
    void aiSectionAppearsOnlyWhenThereAreVerdicts() {
        String without = writer.render(report(List.of(pair("a.java", "b.java", 0.9)),
                List.of(), 0.1, 0.02, 0.05), STUDENTS);
        assertFalse(without.contains("AI-AUTHORSHIP"));

        Report withAi = new Report("hw3", Instant.parse("2026-01-01T00:00:00Z"),
                List.of(pair("a.java", "b.java", 0.9)), List.of(), 
                List.of(new AIAuthorshipResult("a.java", 0.8, 0.6, "heuristic-only", "degraded")),
                0.1, 0.02, 0.05);
        String text = writer.render(withAi, STUDENTS);

        assertTrue(text.contains("AI-AUTHORSHIP"), text);
        assertTrue(text.contains("could not be reached"), text);
        assertTrue(text.contains("tidy student scores the same"), text);
    }

    @Test
    @DisplayName("An unbounded z-score is described rather than printed as Infinity")
    void infiniteDeviationsAreWordedNotPrinted() {
        String text = writer.render(report(List.of(pair("a.java", "b.java", 0.9)),
                List.of(pair("a.java", "b.java", 0.9)), 0.1, 0.0, 0.0), STUDENTS);

        assertTrue(text.contains("unbounded"), text);
        assertFalse(text.contains("Infinity"), text);
    }
}
