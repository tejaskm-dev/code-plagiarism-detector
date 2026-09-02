package com.integrityengine.api;

import com.integrityengine.domain.CodeSubmission;
import com.integrityengine.persistence.SubmissionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The assignments this server knows about, restored from disk on startup.
 *
 * <p>Everything except the analysis report survives a restart. The report does not,
 * deliberately: it is only valid for the exact batch it was computed over, and serving a
 * cached one after a restart risks presenting results that no longer match the
 * submissions on disk.
 */
final class AssignmentRegistry {

    private final ConcurrentMap<String, Assignment> assignments = new ConcurrentHashMap<>();
    private final AssignmentStore store;

    AssignmentRegistry(AssignmentStore store, SubmissionRepository submissions) {
        this.store = store;
        for (AssignmentStore.Record stored : store.loadAll()) {
            Assignment assignment =
                    new Assignment(stored.id(), stored.name(), stored.createdAt());
            assignment.seedUnidentifiedCounter(
                    highestPlaceholder(submissions.findByAssignment(stored.id())));
            assignments.put(stored.id(), assignment);
        }
    }

    /**
     * Finds the largest {@code unidentified-N} already used, so a reloaded assignment
     * carries on numbering from there rather than colliding with its own history.
     */
    static int highestPlaceholder(List<CodeSubmission> stored) {
        int highest = 0;
        for (CodeSubmission submission : stored) {
            String studentId = submission.getStudentId();
            if (!StudentIdentity.looksUnidentified(studentId)) {
                continue;
            }
            try {
                highest = Math.max(highest, Integer.parseInt(
                        studentId.substring(StudentIdentity.UNIDENTIFIED_PREFIX.length())));
            } catch (NumberFormatException e) {
                // A hand-edited identity that merely starts with the prefix. Ignore it
                // rather than failing the whole reload.
            }
        }
        return highest;
    }

    Assignment create(String name) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        Assignment assignment = new Assignment(id, name, Instant.now());
        store.save(id, name, assignment.getCreatedAt());
        assignments.put(id, assignment);
        return assignment;
    }

    Optional<Assignment> find(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(assignments.get(id));
    }

    /** Newest first, so the UI can offer the most recent work without sorting. */
    Collection<Assignment> all() {
        List<Assignment> ordered = new ArrayList<>(assignments.values());
        ordered.sort(Comparator.comparing(Assignment::getCreatedAt).reversed());
        return ordered;
    }
}
