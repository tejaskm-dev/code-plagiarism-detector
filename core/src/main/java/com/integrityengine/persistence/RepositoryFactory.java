package com.integrityengine.persistence;

import java.nio.file.Path;
import java.util.Objects;

/**
 * The public way to obtain repositories.
 *
 * <p>Exists so the SQLite classes can stay package-private: callers name a database file
 * and receive interfaces. Swapping the storage engine later changes nothing outside this
 * package.
 *
 * <p>The SQLite JDBC driver is an optional dependency of this library, so a caller who
 * wants persistence adds {@code org.xerial:sqlite-jdbc} themselves. Callers who only
 * analyse use {@code new IntegrityEngine()} and need neither.
 */
public final class RepositoryFactory {

    private final String jdbcUrl;

    /**
     * @param databaseFile path to the SQLite file; created if it does not exist
     * @throws IllegalStateException if the SQLite JDBC driver is not on the classpath
     */
    public RepositoryFactory(Path databaseFile) {
        Objects.requireNonNull(databaseFile, "databaseFile");
        requireDriver();
        this.jdbcUrl = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
    }

    RepositoryFactory(String jdbcUrl) {
        requireDriver();
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl");
    }

    /**
     * Fails at construction rather than at the first query, where the same mistake
     * surfaces as a bare "No suitable driver" wrapped in a persistence error.
     */
    private static void requireDriver() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("SQLite persistence needs org.xerial:sqlite-jdbc on the "
                    + "classpath. Add that dependency, or use new IntegrityEngine() to analyse "
                    + "without storing anything.", e);
        }
    }

    public SubmissionRepository submissions() {
        return new SqliteSubmissionRepository(jdbcUrl);
    }

    public ResultRepository results() {
        return new SqliteResultRepository(jdbcUrl);
    }
}
