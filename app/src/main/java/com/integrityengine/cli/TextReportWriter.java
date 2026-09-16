package com.integrityengine.cli;

import com.integrityengine.domain.AIAuthorshipResult;
import com.integrityengine.domain.Report;
import com.integrityengine.domain.SimilarityResult;
import com.integrityengine.statistics.StatisticalAnalyzer;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Renders a {@link Report} as prose a human can act on.
 *
 * <p>The JSON form is for machines. This one has a different job: a similarity score is
 * meaningless without the distribution it sits in, so every number here is shown next to
 * what it is being compared against, and every flag is accompanied by the reasoning that
 * produced it. A reader should be able to disagree with a finding on the evidence
 * printed, which they cannot do with a bare score.
 */
final class TextReportWriter {

    private static final int BAR_WIDTH = 20;
    private static final String RULE = "=".repeat(74);
    private static final String THIN = "-".repeat(74);

    private final StatisticalAnalyzer statistics = new StatisticalAnalyzer();

    /**
     * @param report        the completed analysis
     * @param studentBySubmission maps submission id to the student it belongs to
     */
    String render(Report report, Map<String, String> studentBySubmission) {
        StringBuilder out = new StringBuilder(4096);

        header(out, report);
        cohortOverview(out, report);
        flagged(out, report, studentBySubmission);
        nearMisses(out, report, studentBySubmission);
        authorship(out, report);
        howToRead(out, report);

        return out.toString();
    }

    private void header(StringBuilder out, Report report) {
        out.append(RULE).append('\n');
        out.append("  ACADEMIC INTEGRITY REPORT").append('\n');
        out.append("  Assignment : ").append(report.getAssignmentId()).append('\n');
        out.append("  Generated  : ").append(report.getGeneratedAt()).append('\n');
        out.append(RULE).append("\n\n");
    }

    private void cohortOverview(StringBuilder out, Report report) {
        List<SimilarityResult> pairs = report.getSimilarityResults();
        out.append("COHORT OVERVIEW\n").append(THIN).append('\n');

        if (pairs.isEmpty()) {
            out.append("  No pairs were compared. A single submission, or none in a\n")
                    .append("  language this tool recognises, leaves nothing to compare.\n\n");
            return;
        }

        int submissionCount = submissionCount(pairs);
        out.append(String.format("  Submissions compared   %d%n", submissionCount));
        out.append(String.format("  Pairs examined         %d%n", pairs.size()));
        out.append(String.format("  Metric                 %s%n", pairs.get(0).getComparatorName()));
        out.append(String.format("  Median similarity      %.4f%n", report.getCohortMedian()));
        out.append(String.format("  Spread (MAD)           %.4f%n", report.getCohortMad()));
        out.append(String.format("  Spread (MAE)           %.4f%n", report.getCohortMae()));
        out.append('\n');

        out.append("  Method   ").append(methodDescription(report)).append('\n');

        double threshold = reviewThreshold(report);
        if (Double.isFinite(threshold)) {
            out.append(String.format(
                    "  In this cohort a typical pair shares about %.0f%% of its structure,%n"
                    + "  so a pair has to reach roughly %.2f before it counts as unusual.%n",
                    report.getCohortMedian() * 100, threshold));
        }
        out.append('\n');
    }

    /** Says which of the three statistical tiers actually decided the flags. */
    private String methodDescription(Report report) {
        if (report.getCohortMad() > 0) {
            return "modified z-score against the median and MAD";
        }
        if (report.getCohortMae() > 0) {
            return "MAD collapsed to zero (half the pairs scored alike);\n"
                    + "           fell back to the mean absolute deviation";
        }
        return "every pair scored identically, so no pair is unusual;\n"
                + "           fell back to an absolute similarity bar";
    }

    /** The similarity at which a pair would cross the review threshold, if computable. */
    private double reviewThreshold(Report report) {
        if (report.getCohortMad() > 0) {
            return report.getCohortMedian()
                    + StatisticalAnalyzer.OUTLIER_THRESHOLD * report.getCohortMad()
                            / StatisticalAnalyzer.MAD_CONSISTENCY;
        }
        if (report.getCohortMae() > 0) {
            return report.getCohortMedian()
                    + StatisticalAnalyzer.OUTLIER_THRESHOLD
                            * StatisticalAnalyzer.MAE_CONSISTENCY * report.getCohortMae();
        }
        return Double.NaN;
    }

    private void flagged(StringBuilder out, Report report, Map<String, String> students) {
        List<SimilarityResult> ranked = report.getFlaggedResults().stream()
                .sorted(Comparator.comparingDouble(SimilarityResult::getSimilarityScore).reversed())
                .toList();

        out.append(String.format("FLAGGED PAIRS (%d of %d)%n",
                ranked.size(), report.getSimilarityResults().size()));
        out.append(THIN).append('\n');

        if (ranked.isEmpty()) {
            out.append("  Nothing in this cohort stands out from the rest of it.\n\n")
                    .append("  Note this is a statement about the distribution, not a clean bill of\n")
                    .append("  health: if everyone copied the same source, no pair would look\n")
                    .append("  unusual relative to the others.\n\n");
            return;
        }

        int rank = 1;
        for (SimilarityResult pair : ranked) {
            double z = zScore(report, pair.getSimilarityScore());
            out.append(String.format("  #%-3d %s  %s%n", rank++, bar(pair.getSimilarityScore()),
                    describePair(pair, students)));
            out.append(String.format("       similarity   %.4f   (cohort median %.4f)%n",
                    pair.getSimilarityScore(), report.getCohortMedian()));
            out.append(String.format("       deviation    %s%n", formatZ(z)));
            out.append(String.format("       files        %s%n                    %s%n",
                    pair.getLeftSubmissionId(), pair.getRightSubmissionId()));
            for (String line : interpret(pair.getSimilarityScore(), z)) {
                out.append("       ").append(line).append('\n');
            }
            out.append('\n');
        }
    }

    /** The strongest pairs that did NOT cross the threshold, for context. */
    private void nearMisses(StringBuilder out, Report report, Map<String, String> students) {
        Set<SimilarityResult> flagged = Set.copyOf(report.getFlaggedResults());
        List<SimilarityResult> rest = report.getSimilarityResults().stream()
                .filter(r -> !flagged.contains(r))
                .sorted(Comparator.comparingDouble(SimilarityResult::getSimilarityScore).reversed())
                .limit(5)
                .toList();

        if (rest.isEmpty()) {
            return;
        }

        out.append("HIGHEST PAIRS THAT WERE NOT FLAGGED\n").append(THIN).append('\n');
        out.append("  Shown so the cut-off can be sanity-checked rather than trusted.\n\n");
        for (SimilarityResult pair : rest) {
            out.append(String.format("  %.4f  (z %5.2f)  %s%n", pair.getSimilarityScore(),
                    zScore(report, pair.getSimilarityScore()), describePair(pair, students)));
        }
        out.append('\n');
    }

    private void authorship(StringBuilder out, Report report) {
        List<AIAuthorshipResult> results = report.getAiResults();
        if (results.isEmpty()) {
            return;
        }

        out.append("AI-AUTHORSHIP SIGNALS\n").append(THIN).append('\n');
        long degraded = results.stream()
                .filter(r -> r.getModelUsed().equals("heuristic-only")).count();
        if (degraded == results.size()) {
            out.append("  The external model could not be reached. Every score below is local\n")
                    .append("  stylometry alone, which measures how mechanically regular a file is.\n")
                    .append("  A tidy student scores the same as a generated file on this signal.\n\n");
        }

        results.stream()
                .sorted(Comparator.comparingDouble(AIAuthorshipResult::getAiLikelihood).reversed())
                .forEach(r -> {
                    out.append(String.format("  %s  %.3f  %s%n",
                            bar(r.getAiLikelihood()), r.getAiLikelihood(), r.getSubmissionId()));
                    out.append(String.format("       stylometry %.3f  via %s%n",
                            r.getStylometricAnomalyScore(), r.getModelUsed()));
                    out.append("       ").append(r.getRationale()).append('\n');
                });
        out.append('\n');
    }

    private void howToRead(StringBuilder out, Report report) {
        out.append("HOW TO READ THIS\n").append(THIN).append('\n');
        out.append("""
                  A flag means "unusual for this cohort", which is not the same claim as
                  "copied". Two students solving a short, standard exercise the obvious way
                  will genuinely converge, and on a tight distribution that convergence can
                  cross the threshold. Every finding here is a prompt to look at the files,
                  not a conclusion about a person.

                  Scores are computed after identifiers, literals and comments are
                  normalised away, so renaming variables, reformatting and rewriting
                  comments do not reduce them. Reordering whole methods barely does either.

                  Fingerprints appearing across most of the cohort are discarded as
                  boilerplate before comparison, so shared starter code is not counted
                  against anyone.
                """);
        if (report.getCohortMad() == 0 && report.getCohortMae() > 0) {
            out.append("""

                      This cohort's MAD was zero: more than half the pairs scored exactly
                      alike. The fallback measure is more sensitive to small differences, so
                      treat the flags below the top of the list with extra caution.
                    """);
        }
        out.append('\n').append(RULE).append('\n');
    }

    // ------------------------------------------------------------------- helpers

    private double zScore(Report report, double score) {
        return statistics.computeModifiedZScore(
                score, report.getCohortMedian(), report.getCohortMad(), report.getCohortMae());
    }

    private static String formatZ(double z) {
        if (Double.isInfinite(z)) {
            return "unbounded (the rest of the cohort scored identically)";
        }
        String comparison = Math.abs(z) >= StatisticalAnalyzer.OUTLIER_THRESHOLD
                ? String.format("past the %.1f review threshold", StatisticalAnalyzer.OUTLIER_THRESHOLD)
                : String.format("below the %.1f review threshold", StatisticalAnalyzer.OUTLIER_THRESHOLD);
        return String.format("%.1f deviations from the median - %s", z, comparison);
    }

    private static List<String> interpret(double score, double z) {
        if (score >= 0.9) {
            return List.of(
                    "reading      Effectively the same program once naming and layout are",
                    "             stripped out. Very little of this can happen by accident.");
        }
        if (score >= 0.7) {
            return List.of(
                    "reading      Substantial shared structure, well beyond what two",
                    "             independent solutions to this task produced.");
        }
        if (score >= 0.45) {
            return List.of(
                    "reading      Notable overlap. Worth opening both files: it could be a",
                    "             partially rewritten copy, or heavy use of a shared source.");
        }
        return List.of(
                "reading      Moderate overlap that stands out only because this cohort is",
                "             tightly clustered. Convergent solutions look like this too.");
    }

    private static String bar(double score) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, score)) * BAR_WIDTH);
        return "[" + "#".repeat(filled) + ".".repeat(BAR_WIDTH - filled) + "]";
    }

    private static String describePair(SimilarityResult pair, Map<String, String> students) {
        return label(pair.getLeftSubmissionId(), students)
                + "  <->  " + label(pair.getRightSubmissionId(), students);
    }

    private static String label(String submissionId, Map<String, String> students) {
        String student = students.get(submissionId);
        return student == null ? submissionId : student;
    }

    private static int submissionCount(List<SimilarityResult> pairs) {
        return pairs.stream()
                .flatMap(p -> java.util.stream.Stream.of(
                        p.getLeftSubmissionId(), p.getRightSubmissionId()))
                .collect(Collectors.toSet()).size();
    }
}
