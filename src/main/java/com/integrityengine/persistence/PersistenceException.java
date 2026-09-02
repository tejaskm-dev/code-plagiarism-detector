package com.integrityengine.persistence;

/**
 * Wraps the checked {@link java.sql.SQLException} the JDBC layer throws.
 *
 * <p>The repository interfaces are deliberately free of JDBC types: a caller should not
 * have to know that storage happens to be SQL, and a future in-memory or file-backed
 * implementation should not have to invent a {@code SQLException} to satisfy a throws
 * clause. The original exception is always retained as the cause.
 */
public class PersistenceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }

    public PersistenceException(String message) {
        super(message);
    }
}
