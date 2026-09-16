package com.integrityengine.persistence;

import com.integrityengine.domain.SimilarityResult;
import java.util.List;

/** Storage contract for comparison results. */
public interface ResultRepository {

    /**
     * Store one pairwise result.
     *
     * <p>Both submissions must already be stored — the result rows carry foreign keys
     * to them. A result whose submissions are unknown is rejected rather than written,
     * because a row that no query can reach is worse than an error.
     *
     * <p>Unlike submissions this is append-only: there is no natural key for a result,
     * so re-analysing an assignment adds rows rather than replacing them.
     *
     * @throws PersistenceException if the write fails or a submission is unknown
     */
    void save(SimilarityResult result);

    /**
     * @param assignmentId the assignment to report on
     * @return results whose left-hand submission belongs to that assignment
     */
    List<SimilarityResult> findByAssignment(String assignmentId);

    /**
     * Discard every result for one assignment.
     *
     * <p>Exists because results are only meaningful relative to the exact batch they were
     * computed over. Adding, removing or re-attributing a submission invalidates all of
     * them at once, and keeping stale rows around is worse than having none: they would
     * still be returned by {@link #findByAssignment} and read as current.
     *
     * @return the number of rows removed
     * @throws PersistenceException if the delete fails
     */
    int deleteByAssignment(String assignmentId);
}
