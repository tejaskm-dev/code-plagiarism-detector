package com.integrityengine.persistence;

import com.integrityengine.domain.SimilarityResult;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** JDBC/SQLite implementation of {@link ResultRepository}. */
final class SqliteResultRepository implements ResultRepository {

    private static final String INSERT = """
            INSERT INTO similarity_result
                (left_submission_id, right_submission_id, similarity_score, comparator_name)
            VALUES (?, ?, ?, ?)
            """;

    /**
     * The assignment is not stored on the result row; it is reached through the
     * left-hand submission. Keeping it in exactly one place means a result can never
     * disagree with the submission it describes.
     */
    private static final String SELECT_BY_ASSIGNMENT = """
            SELECT r.left_submission_id, r.right_submission_id, r.similarity_score, r.comparator_name
            FROM similarity_result r
            JOIN submission s ON s.submission_id = r.left_submission_id
            WHERE s.assignment_id = ?
            ORDER BY r.similarity_score DESC, r.left_submission_id, r.right_submission_id
            """;

    /** Reaches results through the same join findByAssignment uses. */
    private static final String DELETE_BY_ASSIGNMENT = """
            DELETE FROM similarity_result
            WHERE left_submission_id IN (SELECT submission_id FROM submission WHERE assignment_id = ?)
               OR right_submission_id IN (SELECT submission_id FROM submission WHERE assignment_id = ?)
            """;

    private final String jdbcUrl;

    SqliteResultRepository(String jdbcUrl) {
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        SqliteSchema.initialise(jdbcUrl);
    }

    @Override
    public void save(SimilarityResult result) {
        Objects.requireNonNull(result, "result");

        try (Connection connection = SqliteSchema.open(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setString(1, result.getLeftSubmissionId());
            statement.setString(2, result.getRightSubmissionId());
            statement.setDouble(3, result.getSimilarityScore());
            statement.setString(4, result.getComparatorName());
            statement.executeUpdate();
        } catch (SQLException e) {
            // The common cause is a foreign-key violation: a result saved before, or
            // without, the submissions it refers to.
            throw new PersistenceException("could not save result "
                    + result.getLeftSubmissionId() + " vs " + result.getRightSubmissionId(), e);
        }
    }

    @Override
    public int deleteByAssignment(String assignmentId) {
        Objects.requireNonNull(assignmentId, "assignmentId");

        try (Connection connection = SqliteSchema.open(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(DELETE_BY_ASSIGNMENT)) {
            // Matches on either side, so a cross-assignment pair is removed with the
            // assignment it was computed for rather than being orphaned.
            statement.setString(1, assignmentId);
            statement.setString(2, assignmentId);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("could not delete results for " + assignmentId, e);
        }
    }

    @Override
    public List<SimilarityResult> findByAssignment(String assignmentId) {
        Objects.requireNonNull(assignmentId, "assignmentId");

        try (Connection connection = SqliteSchema.open(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_ASSIGNMENT)) {
            statement.setString(1, assignmentId);
            try (ResultSet rows = statement.executeQuery()) {
                List<SimilarityResult> results = new ArrayList<>();
                while (rows.next()) {
                    results.add(new SimilarityResult(
                            rows.getString("left_submission_id"),
                            rows.getString("right_submission_id"),
                            rows.getDouble("similarity_score"),
                            rows.getString("comparator_name")));
                }
                return List.copyOf(results);
            }
        } catch (SQLException e) {
            throw new PersistenceException("could not read results for " + assignmentId, e);
        }
    }
}
