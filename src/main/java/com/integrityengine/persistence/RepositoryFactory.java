package com.integrityengine.persistence;

import java.nio.file.Path;
import java.util.Objects;

/**
 * The public way to obtain repositories.
 *
 * <p>Exists so the SQLite classes can stay package-private: callers name a database file
 * and receive interfaces. Swapping the storage engine later changes nothing outside this
 * package.
 */
public final class RepositoryFactory {

    private final String jdbcUrl;

    /**
     * @param databaseFile path to the SQLite file; created if it does not exist
     */
    public RepositoryFactory(Path databaseFile) {
        Objects.requireNonNull(databaseFile, "databaseFile");
        this.jdbcUrl = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
    }

    RepositoryFactory(String jdbcUrl) {
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl");
    }

    public SubmissionRepository submissions() {
        return new SqliteSubmissionRepository(jdbcUrl);
    }

    public ResultRepository results() {
        return new SqliteResultRepository(jdbcUrl);
    }
}
