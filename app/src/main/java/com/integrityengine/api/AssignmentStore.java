package com.integrityengine.api;

import com.integrityengine.persistence.PersistenceException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Durable storage for the HTTP layer's own state: an assignment's display name, when it
 * was created, and any uploaded boilerplate reference files.
 *
 * <p>Deliberately kept out of the {@code persistence} package. None of this is domain
 * data — the engine has no interest in what an assignment is called — and adding a
 * repository for it would widen the domain model to serve a UI concern. It lives in the
 * same SQLite file, in its own tables, so a deployment is still one file.
 *
 * <p>What is <b>not</b> stored here is the analysis report. A report is only valid for
 * the exact batch it was computed over, so caching it across a restart would risk
 * serving results that no longer match the submissions on disk. After a restart an
 * assignment reloads with its submissions intact and its analysis absent, and the UI
 * offers to re-run it.
 */
final class AssignmentStore {

    private static final String CREATE_ASSIGNMENT = """
            CREATE TABLE IF NOT EXISTS assignment (
                assignment_id TEXT PRIMARY KEY,
                name          TEXT NOT NULL,
                created_at    TEXT NOT NULL
            )
            """;

    private static final String CREATE_REFERENCE = """
            CREATE TABLE IF NOT EXISTS assignment_reference_file (
                assignment_id TEXT NOT NULL,
                filename      TEXT NOT NULL,
                content       TEXT NOT NULL,
                PRIMARY KEY (assignment_id, filename)
            )
            """;

    private final String jdbcUrl;

    AssignmentStore(Path databaseFile) {
        Objects.requireNonNull(databaseFile, "databaseFile");
        this.jdbcUrl = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
        initialise();
    }

    private void initialise() {
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute(CREATE_ASSIGNMENT);
            statement.execute(CREATE_REFERENCE);
        } catch (SQLException e) {
            throw new PersistenceException("could not initialise assignment tables", e);
        }
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    void save(String id, String name, Instant createdAt) {
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT OR REPLACE INTO assignment (assignment_id, name, created_at) VALUES (?, ?, ?)")) {
            statement.setString(1, id);
            statement.setString(2, name);
            statement.setString(3, createdAt.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("could not save assignment " + id, e);
        }
    }

    void delete(String id) {
        try (Connection connection = open();
             PreparedStatement assignments = connection.prepareStatement(
                     "DELETE FROM assignment WHERE assignment_id = ?");
             PreparedStatement references = connection.prepareStatement(
                     "DELETE FROM assignment_reference_file WHERE assignment_id = ?")) {
            references.setString(1, id);
            references.executeUpdate();
            assignments.setString(1, id);
            assignments.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("could not delete assignment " + id, e);
        }
    }

    void addReferenceFile(String assignmentId, String filename, String content) {
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT OR REPLACE INTO assignment_reference_file "
                             + "(assignment_id, filename, content) VALUES (?, ?, ?)")) {
            statement.setString(1, assignmentId);
            statement.setString(2, filename);
            statement.setString(3, content);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("could not store reference file " + filename, e);
        }
    }

    /** @return how many reference files were removed */
    int deleteReferenceFiles(String assignmentId) {
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM assignment_reference_file WHERE assignment_id = ?")) {
            statement.setString(1, assignmentId);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("could not delete reference files for " + assignmentId, e);
        }
    }

    List<Assignment.ReferenceFile> referenceFiles(String assignmentId) {
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT filename, content FROM assignment_reference_file "
                             + "WHERE assignment_id = ? ORDER BY filename")) {
            statement.setString(1, assignmentId);
            try (ResultSet rows = statement.executeQuery()) {
                List<Assignment.ReferenceFile> files = new ArrayList<>();
                while (rows.next()) {
                    files.add(new Assignment.ReferenceFile(
                            rows.getString("filename"), rows.getString("content")));
                }
                return files;
            }
        } catch (SQLException e) {
            throw new PersistenceException("could not read reference files for " + assignmentId, e);
        }
    }

    /** Every stored assignment, newest first. */
    List<Record> loadAll() {
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT assignment_id, name, created_at FROM assignment ORDER BY created_at DESC");
             ResultSet rows = statement.executeQuery()) {
            List<Record> loaded = new ArrayList<>();
            while (rows.next()) {
                loaded.add(new Record(rows.getString("assignment_id"), rows.getString("name"),
                        Instant.parse(rows.getString("created_at"))));
            }
            return loaded;
        } catch (SQLException e) {
            throw new PersistenceException("could not read assignments", e);
        }
    }

    record Record(String id, String name, Instant createdAt) {
    }
}
