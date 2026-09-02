package com.integrityengine.domain;

import java.util.Objects;

/**
 * One student's source file submitted against one assignment. Immutable.
 */
public final class CodeSubmission {

    private final String submissionId;
    private final String studentId;
    private final String assignmentId;
    private final String filename;
    private final String sourceCode;

    public CodeSubmission(String submissionId,
                          String studentId,
                          String assignmentId,
                          String filename,
                          String sourceCode) {
        this.submissionId = Objects.requireNonNull(submissionId, "submissionId");
        this.studentId = Objects.requireNonNull(studentId, "studentId");
        this.assignmentId = Objects.requireNonNull(assignmentId, "assignmentId");
        this.filename = Objects.requireNonNull(filename, "filename");
        this.sourceCode = Objects.requireNonNull(sourceCode, "sourceCode");
    }

    public String getSubmissionId() {
        return submissionId;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getAssignmentId() {
        return assignmentId;
    }

    public String getFilename() {
        return filename;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CodeSubmission other)) {
            return false;
        }
        return submissionId.equals(other.submissionId);
    }

    @Override
    public int hashCode() {
        return submissionId.hashCode();
    }

    @Override
    public String toString() {
        return "CodeSubmission[" + submissionId + " student=" + studentId
                + " assignment=" + assignmentId + " file=" + filename + "]";
    }
}
