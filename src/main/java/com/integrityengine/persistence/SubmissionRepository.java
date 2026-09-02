package com.integrityengine.persistence;

import com.integrityengine.domain.CodeSubmission;
import java.util.List;

/**
 * Storage contract for submissions.
 *
 * <p>Public because {@code IntegrityEngine} lives in another package and is wired
 * against this interface rather than a concrete implementation; the SQLite class behind
 * it stays package-private and is reached through {@link RepositoryFactory}.
 */
public interface SubmissionRepository {

    /**
     * Store a submission, replacing any existing row with the same id.
     *
     * <p>Idempotent on purpose: re-analysing a directory is a normal thing to do and
     * should not fail on the second run.
     *
     * @throws PersistenceException if the write fails
     */
    void save(CodeSubmission submission);

    /** @return every stored submission, ordered by id so results are reproducible */
    List<CodeSubmission> findAll();

    /** @return submissions for one assignment, ordered by id; empty if none match */
    List<CodeSubmission> findByAssignment(String assignmentId);

    /**
     * Remove one submission.
     *
     * <p>Results carry foreign keys to submissions, so a submission that any result
     * references cannot be deleted while those results exist. Callers changing the
     * makeup of a batch should clear that batch's results first — which is correct
     * regardless, since a result computed over a different set of submissions is stale.
     *
     * @return true if a row was removed
     * @throws PersistenceException if the delete fails
     */
    boolean deleteById(String submissionId);
}
