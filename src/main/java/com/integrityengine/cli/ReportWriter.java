package com.integrityengine.cli;

import com.integrityengine.domain.AIAuthorshipResult;
import com.integrityengine.domain.Report;
import com.integrityengine.domain.SimilarityResult;
import java.util.List;

/** Renders a {@link Report} as JSON. */
final class ReportWriter {

    private ReportWriter() {
    }

    static String toJson(Report report) {
        StringBuilder out = new StringBuilder(1024);
        out.append("{\n");
        out.append("  \"assignmentId\": ").append(Json.string(report.getAssignmentId())).append(",\n");
        out.append("  \"generatedAt\": ").append(Json.string(report.getGeneratedAt().toString())).append(",\n");
        out.append("  \"cohortMedian\": ").append(Json.number(report.getCohortMedian())).append(",\n");
        out.append("  \"cohortMad\": ").append(Json.number(report.getCohortMad())).append(",\n");
        out.append("  \"pairCount\": ").append(report.getSimilarityResults().size()).append(",\n");
        out.append("  \"flaggedCount\": ").append(report.getFlaggedResults().size()).append(",\n");

        out.append("  \"flagged\": ");
        appendResults(out, report.getFlaggedResults());
        out.append(",\n");

        out.append("  \"allPairs\": ");
        appendResults(out, report.getSimilarityResults());
        out.append(",\n");

        out.append("  \"aiAuthorship\": ");
        appendAi(out, report.getAiResults());
        out.append("\n}\n");
        return out.toString();
    }

    private static void appendResults(StringBuilder out, List<SimilarityResult> results) {
        if (results.isEmpty()) {
            out.append("[]");
            return;
        }
        out.append("[\n");
        for (int i = 0; i < results.size(); i++) {
            SimilarityResult r = results.get(i);
            out.append("    {")
                    .append("\"left\": ").append(Json.string(r.getLeftSubmissionId())).append(", ")
                    .append("\"right\": ").append(Json.string(r.getRightSubmissionId())).append(", ")
                    .append("\"score\": ").append(Json.number(r.getSimilarityScore())).append(", ")
                    .append("\"metric\": ").append(Json.string(r.getComparatorName()))
                    .append("}").append(i < results.size() - 1 ? ",\n" : "\n");
        }
        out.append("  ]");
    }

    private static void appendAi(StringBuilder out, List<AIAuthorshipResult> results) {
        if (results.isEmpty()) {
            out.append("[]");
            return;
        }
        out.append("[\n");
        for (int i = 0; i < results.size(); i++) {
            AIAuthorshipResult r = results.get(i);
            out.append("    {")
                    .append("\"submissionId\": ").append(Json.string(r.getSubmissionId())).append(", ")
                    .append("\"aiLikelihood\": ").append(Json.number(r.getAiLikelihood())).append(", ")
                    .append("\"stylometricScore\": ").append(Json.number(r.getStylometricAnomalyScore())).append(", ")
                    .append("\"model\": ").append(Json.string(r.getModelUsed())).append(", ")
                    .append("\"rationale\": ").append(Json.string(r.getRationale()))
                    .append("}").append(i < results.size() - 1 ? ",\n" : "\n");
        }
        out.append("  ]");
    }
}
