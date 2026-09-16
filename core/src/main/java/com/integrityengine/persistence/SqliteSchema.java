package com.integrityengine.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Connection handling and DDL, shared by both SQLite repositories.
 *
 * <p>Connections are opened per operation rather than pooled or held. For an embedded
 * file database that is cheap — there is no network round trip and no handshake — and it
 * keeps the repositories stateless, so nothing has to be closed by the caller and two
 * repositories can safely share one file.
 */
final class SqliteSchema {

    private SqliteSchema() {
    }

    private static final String CREATE_SUBMISSION = """
            CREATE TABLE IF NOT EXISTS submission (
                submission_id TEXT PRIMARY KEY,
                student_id    TEXT NOT NULL,
                assignment_id TEXT NOT NULL,
                filename      TEXT NOT NULL,
                source_code   TEXT NOT NULL
            )
            """;

    private static final String CREATE_SUBMISSION_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_submission_assignment ON submission(assignment_id)";

    private static final String CREATE_RESULT = """
            CREATE TABLE IF NOT EXISTS similarity_result (
                id                   INTEGER PRIMARY KEY AUTOINCREMENT,
                left_submission_id   TEXT NOT NULL,
                right_submission_id  TEXT NOT NULL,
                similarity_score     REAL NOT NULL,
                comparator_name      TEXT NOT NULL,
                FOREIGN KEY (left_submission_id)  REFERENCES submission(submission_id),
                FOREIGN KEY (right_submission_id) REFERENCES submission(submission_id)
            )
            """;

    private static final String CREATE_RESULT_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_result_left ON similarity_result(left_submission_id)";

    /**
     * Open a connection with foreign keys enforced.
     *
     * <p>SQLite ignores foreign keys unless the pragma is set, and it is per-connection,
     * not per-database — so it has to be issued every time or the constraints declared
     * above are decorative.
     */
    static Connection open(String jdbcUrl) {
        try {
            Connection connection = DriverManager.getConnection(jdbcUrl);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
            }
            return connection;
        } catch (SQLException e) {
            throw new PersistenceException("could not open database at " + jdbcUrl, e);
        }
    }

    /** Create the tables if they are not already there. Safe to call repeatedly. */
    static void initialise(String jdbcUrl) {
        try (Connection connection = open(jdbcUrl);
             Statement statement = connection.createStatement()) {
            statement.execute(CREATE_SUBMISSION);
            statement.execute(CREATE_SUBMISSION_INDEX);
            statement.execute(CREATE_RESULT);
            statement.execute(CREATE_RESULT_INDEX);
        } catch (SQLException e) {
            throw new PersistenceException("could not initialise schema at " + jdbcUrl, e);
        }
    }
}
