package com.integrityengine.persistence;

import com.integrityengine.domain.CodeSubmission;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * JDBC/SQLite implementation of {@link SubmissionRepository}.
 *
 * <p>Every statement is prepared and every value bound as a parameter. Submission ids,
 * filenames and source code all come from untrusted input — a file called
 * {@code '; DROP TABLE submission; --.java} is a perfectly legal filename.
 */
final class SqliteSubmissionRepository implements SubmissionRepository {

    private static final String UPSERT = """
            INSERT OR REPLACE INTO submission
                (submission_id, student_id, assignment_id, filename, source_code)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String SELECT_ALL = """
            SELECT submission_id, student_id, assignment_id, filename, source_code
            FROM submission ORDER BY submission_id
            """;

    private static final String SELECT_BY_ASSIGNMENT = """
            SELECT submission_id, student_id, assignment_id, filename, source_code
            FROM submission WHERE assignment_id = ? ORDER BY submission_id
            """;

    private static final String DELETE_BY_ID = "DELETE FROM submission WHERE submission_id = ?";

    private final String jdbcUrl;

    SqliteSubmissionRepository(String jdbcUrl) {
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        SqliteSchema.initialise(jdbcUrl);
    }

    @Override
    public void save(CodeSubmission submission) {
        Objects.requireNonNull(submission, "submission");

        try (Connection connection = SqliteSchema.open(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(UPSERT)) {
            statement.setString(1, submission.getSubmissionId());
            statement.setString(2, submission.getStudentId());
            statement.setString(3, submission.getAssignmentId());
            statement.setString(4, submission.getFilename());
            statement.setString(5, submission.getSourceCode());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException(
                    "could not save submission " + submission.getSubmissionId(), e);
        }
    }

    @Override
    public List<CodeSubmission> findAll() {
        try (Connection connection = SqliteSchema.open(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
             ResultSet rows = statement.executeQuery()) {
            return readAll(rows);
        } catch (SQLException e) {
            throw new PersistenceException("could not read submissions", e);
        }
    }

    @Override
    public List<CodeSubmission> findByAssignment(String assignmentId) {
        Objects.requireNonNull(assignmentId, "assignmentId");

        try (Connection connection = SqliteSchema.open(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_ASSIGNMENT)) {
            statement.setString(1, assignmentId);
            try (ResultSet rows = statement.executeQuery()) {
                return readAll(rows);
            }
        } catch (SQLException e) {
            throw new PersistenceException("could not read submissions for " + assignmentId, e);
        }
    }

    @Override
    public boolean deleteById(String submissionId) {
        Objects.requireNonNull(submissionId, "submissionId");

        try (Connection connection = SqliteSchema.open(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(DELETE_BY_ID)) {
            statement.setString(1, submissionId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new PersistenceException("could not delete submission " + submissionId, e);
        }
    }

    private static List<CodeSubmission> readAll(ResultSet rows) throws SQLException {
        List<CodeSubmission> submissions = new ArrayList<>();
        while (rows.next()) {
            submissions.add(new CodeSubmission(
                    rows.getString("submission_id"),
                    rows.getString("student_id"),
                    rows.getString("assignment_id"),
                    rows.getString("filename"),
                    rows.getString("source_code")));
        }
        return List.copyOf(submissions);
    }
}
