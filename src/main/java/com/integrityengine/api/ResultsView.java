package com.integrityengine.api;

import com.integrityengine.ai.AIAuthorshipDetector;
import com.integrityengine.domain.AIAuthorshipResult;
import com.integrityengine.domain.CodeSubmission;
import com.integrityengine.domain.Report;
import com.integrityengine.domain.SimilarityResult;
import com.integrityengine.statistics.StatisticalAnalyzer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renders the results payload: the report the CLI already produces, plus the per-pair
 * detail the UI needs - containment in both directions, the modified z-score, which
 * statistical tier decided the outcome, and the shared line ranges for the diff view.
 */
final class ResultsView {

    private final StatisticalAnalyzer statistics;

    ResultsView(StatisticalAnalyzer statistics) {
        this.statistics = statistics;
    }

    String render(Assignment assignment, Report report, List<CodeSubmission> batch,
                  int referenceFileCount, MatchAnalysis analysis) {
        Set<String> flagged = new HashSet<>();
        for (SimilarityResult result : report.getFlaggedResults()) {
            flagged.add(key(result));
        }

        // Identity is what a reviewer acts on, so it is resolved once here and attached
        // to every pair rather than being re-derived from a submission id in the browser.
        Map<String, CodeSubmission> byId = new LinkedHashMap<>();
        for (CodeSubmission submission : batch) {
            byId.put(submission.getSubmissionId(), submission);
        }

        Map<String, String> body = new LinkedHashMap<>();
        body.put("assignmentId", JsonOut.string(assignment.getId()));
        body.put("assignmentName", JsonOut.string(assignment.getName()));
        body.put("generatedAt", JsonOut.string(report.getGeneratedAt().toString()));
        body.put("cohort", cohort(report, batch));
        body.put("pairs", pairs(report, analysis, flagged, byId));
        body.put("files", files(batch, report));
        body.put("ai", aiSummary(report));
        body.put("referenceFilesStored", String.valueOf(referenceFileCount));
        return JsonOut.object(body);
    }

    /**
     * The cohort block. Reports which tier fired and the score a pair would have to reach
     * to be flagged, so a reader can see why a verdict came out the way it did - and so
     * the tier 2 / tier 3 disagreement is visible at a glance rather than inferred.
     */
    private String cohort(Report report, List<CodeSubmission> batch) {
        int submissionCount = batch.size();
        int unidentified = 0;
        for (CodeSubmission submission : batch) {
            if (StudentIdentity.looksUnidentified(submission.getStudentId())) {
                unidentified++;
            }
        }
        double median = report.getCohortMedian();
        double mad = report.getCohortMad();
        double mae = report.getCohortMae();

        int tier;
        String tierLabel;
        double reviewThreshold;
        if (mad > 0) {
            tier = 1;
            tierLabel = "modified z-score against the median and MAD";
            reviewThreshold = median + StatisticalAnalyzer.OUTLIER_THRESHOLD * mad
                    / StatisticalAnalyzer.MAD_CONSISTENCY;
        } else if (mae > 0) {
            tier = 2;
            tierLabel = "MAD collapsed to zero; fell back to the mean absolute deviation";
            reviewThreshold = median + StatisticalAnalyzer.OUTLIER_THRESHOLD
                    * StatisticalAnalyzer.MAE_CONSISTENCY * mae;
        } else {
            tier = 3;
            tierLabel = "no dispersion at all; fell back to an absolute similarity bar";
            reviewThreshold = StatisticalAnalyzer.ABSOLUTE_GUARDRAIL;
        }

        Map<String, String> block = new LinkedHashMap<>();
        block.put("submissionCount", String.valueOf(submissionCount));
        block.put("pairCount", String.valueOf(report.getSimilarityResults().size()));
        block.put("flaggedCount", String.valueOf(report.getFlaggedResults().size()));
        block.put("median", JsonOut.number(median));
        block.put("mad", JsonOut.number(mad));
        block.put("mae", JsonOut.number(mae));
        block.put("tier", String.valueOf(tier));
        block.put("tierLabel", JsonOut.string(tierLabel));
        block.put("reviewThreshold", JsonOut.number(reviewThreshold));
        block.put("outlierThreshold", JsonOut.number(StatisticalAnalyzer.OUTLIER_THRESHOLD));
        // Surfaced so the UI can warn: results are not trustworthy while submissions
        // remain unattributed, because a reviewer cannot act on "unidentified-3".
        block.put("unidentifiedCount", String.valueOf(unidentified));
        return JsonOut.object(block);
    }

    private String pairs(Report report, MatchAnalysis analysis, Set<String> flagged,
                         Map<String, CodeSubmission> byId) {
        List<SimilarityResult> ranked = new ArrayList<>(report.getSimilarityResults());
        ranked.sort(Comparator.comparingDouble(SimilarityResult::getSimilarityScore).reversed());

        List<String> rendered = new ArrayList<>();
        for (SimilarityResult result : ranked) {
            String left = result.getLeftSubmissionId();
            String right = result.getRightSubmissionId();
            boolean isFlagged = flagged.contains(key(result));

            double z = statistics.computeModifiedZScore(result.getSimilarityScore(),
                    report.getCohortMedian(), report.getCohortMad(), report.getCohortMae());

            Map<String, String> row = new LinkedHashMap<>();
            row.put("left", JsonOut.string(left));
            row.put("right", JsonOut.string(right));
            row.put("leftLabel", JsonOut.string(studentOf(byId, left)));
            row.put("rightLabel", JsonOut.string(studentOf(byId, right)));
            row.put("leftFilename", JsonOut.string(filenameOf(byId, left)));
            row.put("rightFilename", JsonOut.string(filenameOf(byId, right)));
            row.put("leftUnidentified", String.valueOf(
                    StudentIdentity.looksUnidentified(studentOf(byId, left))));
            row.put("rightUnidentified", String.valueOf(
                    StudentIdentity.looksUnidentified(studentOf(byId, right))));
            row.put("score", JsonOut.number(result.getSimilarityScore()));
            row.put("metric", JsonOut.string(result.getComparatorName()));
            row.put("containmentLeftInRight", JsonOut.number(analysis.containment(left, right)));
            row.put("containmentRightInLeft", JsonOut.number(analysis.containment(right, left)));
            row.put("modifiedZ", JsonOut.number(z));
            row.put("flagged", String.valueOf(isFlagged));

            // Spans are only needed for pairs a reviewer will open. Computing them for
            // every pair in a large cohort would dominate the response for no benefit.
            if (isFlagged) {
                MatchAnalysis.SharedRegions regions = analysis.sharedRegions(left, right);
                row.put("sharedFingerprints", String.valueOf(regions.sharedFingerprints()));
                row.put("leftSpans", spans(regions.leftSpans()));
                row.put("rightSpans", spans(regions.rightSpans()));
            }
            rendered.add(JsonOut.object(row));
        }
        return JsonOut.array(rendered);
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

    /** Source text, so the diff view can render both sides without a second round trip. */
    private static String files(List<CodeSubmission> batch, Report report) {
        Map<String, AIAuthorshipResult> ai = new LinkedHashMap<>();
        for (AIAuthorshipResult result : report.getAiResults()) {
            ai.put(result.getSubmissionId(), result);
        }

        List<String> rendered = new ArrayList<>();
        for (CodeSubmission submission : batch) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("submissionId", JsonOut.string(submission.getSubmissionId()));
            item.put("filename", JsonOut.string(submission.getFilename()));
            item.put("label", JsonOut.string(submission.getStudentId()));
            item.put("student", JsonOut.string(submission.getStudentId()));
            item.put("unidentified", String.valueOf(
                    StudentIdentity.looksUnidentified(submission.getStudentId())));
            item.put("source", JsonOut.string(submission.getSourceCode()));

            // Authorship travels with the file rather than in a parallel list, so the
            // browser never has to join two collections on submission id.
            AIAuthorshipResult authorship = ai.get(submission.getSubmissionId());
            if (authorship == null) {
                item.put("aiScore", "null");
                item.put("aiVerdict", JsonOut.string("unmeasured"));
                item.put("aiRationale", JsonOut.string(
                        "Too short, or in a language this build cannot tokenise, so no "
                        + "authorship estimate was made."));
                item.put("aiModel", "null");
            } else {
                item.put("aiScore", JsonOut.number(authorship.getAiLikelihood()));
                item.put("aiVerdict", JsonOut.string(
                        AIAuthorshipDetector.verdictLabel(authorship.getAiLikelihood())));
                item.put("aiRationale", JsonOut.string(authorship.getRationale()));
                item.put("aiModel", JsonOut.string(authorship.getModelUsed()));
            }
            rendered.add(JsonOut.object(item));
        }
        return JsonOut.array(rendered);
    }

    /**
     * Cohort-level authorship counts. Kept separate from the per-file list so the
     * overview can show the shape of the batch without walking every file.
     */
    private static String aiSummary(Report report) {
        int generated = 0;
        int human = 0;
        int unsure = 0;
        for (AIAuthorshipResult result : report.getAiResults()) {
            switch (AIAuthorshipDetector.verdictLabel(result.getAiLikelihood())) {
                case "generated" -> generated++;
                case "human" -> human++;
                default -> unsure++;
            }
        }
        Map<String, String> block = new LinkedHashMap<>();
        block.put("measured", String.valueOf(report.getAiResults().size()));
        block.put("generated", String.valueOf(generated));
        block.put("human", String.valueOf(human));
        block.put("unsure", String.valueOf(unsure));
        block.put("band", JsonOut.number(AIAuthorshipDetector.UNSURE_BAND));
        return JsonOut.object(block);
    }

    private static String studentOf(Map<String, CodeSubmission> byId, String submissionId) {
        CodeSubmission submission = byId.get(submissionId);
        return submission == null ? submissionId : submission.getStudentId();
    }

    private static String filenameOf(Map<String, CodeSubmission> byId, String submissionId) {
        CodeSubmission submission = byId.get(submissionId);
        return submission == null ? submissionId : submission.getFilename();
    }

    private static String key(SimilarityResult result) {
        return result.getLeftSubmissionId() + " " + result.getRightSubmissionId();
    }
}
