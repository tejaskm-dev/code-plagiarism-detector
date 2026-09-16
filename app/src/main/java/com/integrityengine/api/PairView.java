package com.integrityengine.api;

import com.integrityengine.domain.CodeSubmission;
import com.integrityengine.domain.Report;
import com.integrityengine.statistics.StatisticalAnalyzer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Full review detail for a single pair.
 *
 * <p>Carries both sources and the shared line ranges, plus the metrics expressed against
 * the cohort they were judged in — a similarity of 0.36 is meaningless without the
 * median it is being compared to, and a reviewer opening this screen is exactly the
 * person who needs that context.
 */
final class PairView {

    private final StatisticalAnalyzer statistics;

    PairView(StatisticalAnalyzer statistics) {
        this.statistics = statistics;
    }

    String render(Report report, MatchAnalysis analysis,
                  CodeSubmission left, CodeSubmission right) {
        String leftId = left.getSubmissionId();
        String rightId = right.getSubmissionId();

        double score = analysis.jaccard(leftId, rightId);
        double z = statistics.computeModifiedZScore(score, report.getCohortMedian(),
                report.getCohortMad(), report.getCohortMae());
        MatchAnalysis.SharedRegions regions = analysis.sharedRegions(leftId, rightId);

        boolean flagged = report.getFlaggedResults().stream().anyMatch(
                r -> (r.getLeftSubmissionId().equals(leftId) && r.getRightSubmissionId().equals(rightId))
                  || (r.getLeftSubmissionId().equals(rightId) && r.getRightSubmissionId().equals(leftId)));

        MatchEvidence.Evidence evidence = MatchEvidence.derive(
                analysis.normalisedTokens(leftId), analysis.lexicalTokens(leftId),
                analysis.normalisedTokens(rightId), analysis.lexicalTokens(rightId),
                analysis.positionsByHash(leftId), analysis.positionsByHash(rightId),
                analysis.sharedFingerprints(leftId, rightId));

        Map<String, String> body = new LinkedHashMap<>();
        body.put("evidence", evidence(evidence));
        body.put("left", side(left, regions.leftSpans()));
        body.put("right", side(right, regions.rightSpans()));
        body.put("score", JsonOut.number(score));
        body.put("modifiedZ", JsonOut.number(z));
        body.put("flagged", String.valueOf(flagged));
        body.put("sharedFingerprints", String.valueOf(regions.sharedFingerprints()));
        body.put("containmentLeftInRight", JsonOut.number(analysis.containment(leftId, rightId)));
        body.put("containmentRightInLeft", JsonOut.number(analysis.containment(rightId, leftId)));
        body.put("cohortMedian", JsonOut.number(report.getCohortMedian()));
        body.put("cohortMad", JsonOut.number(report.getCohortMad()));
        body.put("reviewThreshold", JsonOut.number(reviewThreshold(report)));
        body.put("matchedLineCount", String.valueOf(
                lineCount(regions.leftSpans()) + lineCount(regions.rightSpans())));
        return JsonOut.object(body);
    }

    /** The readable half of a finding: what matches what, and what was renamed. */
    private static String evidence(MatchEvidence.Evidence evidence) {
        List<String> routines = new ArrayList<>();
        for (MatchEvidence.RoutinePair routine : evidence.routines()) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("leftName", JsonOut.string(routine.leftName()));
            item.put("rightName", JsonOut.string(routine.rightName()));
            item.put("sharedFragments", String.valueOf(routine.sharedFragments()));
            item.put("leftStartLine", String.valueOf(routine.leftStartLine()));
            item.put("rightStartLine", String.valueOf(routine.rightStartLine()));
            routines.add(JsonOut.object(item));
        }

        List<String> renames = new ArrayList<>();
        for (MatchEvidence.Rename rename : evidence.renames()) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("from", JsonOut.string(rename.from()));
            item.put("to", JsonOut.string(rename.to()));
            item.put("occurrences", String.valueOf(rename.occurrences()));
            renames.add(JsonOut.object(item));
        }

        Map<String, String> block = new LinkedHashMap<>();
        block.put("routines", JsonOut.array(routines));
        block.put("renames", JsonOut.array(renames));
        block.put("renamedTokens", String.valueOf(evidence.renamedTokenCount()));
        block.put("identicalTokens", String.valueOf(evidence.identicalTokenCount()));
        return JsonOut.object(block);
    }

    private static String side(CodeSubmission submission, List<MatchAnalysis.LineSpan> spans) {
        Map<String, String> block = new LinkedHashMap<>();
        block.put("submissionId", JsonOut.string(submission.getSubmissionId()));
        block.put("student", JsonOut.string(submission.getStudentId()));
        block.put("filename", JsonOut.string(submission.getFilename()));
        block.put("source", JsonOut.string(submission.getSourceCode()));
        block.put("lineCount", String.valueOf(submission.getSourceCode().split("\n", -1).length));
        block.put("matchedLines", String.valueOf(lineCount(spans)));
        block.put("spans", spans(spans));
        return JsonOut.object(block);
    }

    private static String spans(List<MatchAnalysis.LineSpan> spans) {
        List<String> rendered = new ArrayList<>();
        for (MatchAnalysis.LineSpan span : spans) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("startLine", String.valueOf(span.startLine()));
            item.put("endLine", String.valueOf(span.endLine()));
            rendered.add(JsonOut.object(item));
        }
        return JsonOut.array(rendered);
    }

    private static int lineCount(List<MatchAnalysis.LineSpan> spans) {
        int total = 0;
        for (MatchAnalysis.LineSpan span : spans) {
            total += span.endLine() - span.startLine() + 1;
        }
        return total;
    }

    /** The similarity a pair must reach to be flagged in this cohort. */
    private static double reviewThreshold(Report report) {
        if (report.getCohortMad() > 0) {
            return report.getCohortMedian() + StatisticalAnalyzer.OUTLIER_THRESHOLD
                    * report.getCohortMad() / StatisticalAnalyzer.MAD_CONSISTENCY;
        }
        if (report.getCohortMae() > 0) {
            return report.getCohortMedian() + StatisticalAnalyzer.OUTLIER_THRESHOLD
                    * StatisticalAnalyzer.MAE_CONSISTENCY * report.getCohortMae();
        }
        return StatisticalAnalyzer.ABSOLUTE_GUARDRAIL;
    }
}
