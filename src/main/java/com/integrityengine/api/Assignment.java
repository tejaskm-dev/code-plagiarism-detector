package com.integrityengine.api;

import com.integrityengine.domain.Report;
import java.time.Instant;
import java.util.Objects;

/**
 * Server-side state for one assignment.
 *
 * <p>Held in memory rather than in the database. Submissions and results are persisted by
 * the existing repositories exactly as the CLI persists them; what lives here is only the
 * things the HTTP layer needs between requests — the assignment's display name, the most
 * recent {@link Report}, and any uploaded reference files. Restarting the server loses
 * this and keeps the analysis data.
 */
final class Assignment {

    private final String id;
    private final String name;
    private final Instant createdAt;
    private volatile Report lastReport;

    /**
     * Fingerprint analysis for the batch {@link #lastReport} was computed over.
     *
     * <p>Cached because rebuilding it means re-tokenising and re-fingerprinting every
     * submission, which is far too expensive to repeat each time a reviewer opens a
     * pair. Tied to the report deliberately: both describe one exact set of submissions,
     * so they are cleared together and can never disagree.
     */
    private volatile Object matchAnalysis;

    /**
     * Numbers the generated placeholder identities.
     *
     * <p>Per assignment and never reset, so a second upload of unidentified files cannot
     * reuse numbers from the first and overwrite them.
     */
    private final java.util.concurrent.atomic.AtomicInteger unidentifiedCounter =
            new java.util.concurrent.atomic.AtomicInteger();

    Assignment(String id, String name, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    String getId() {
        return id;
    }

    String getName() {
        return name;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    /** @return the next placeholder identity, e.g. {@code unidentified-3} */
    String nextUnidentifiedId() {
        return StudentIdentity.UNIDENTIFIED_PREFIX + unidentifiedCounter.incrementAndGet();
    }

    /**
     * Raises the placeholder counter past anything already stored.
     *
     * <p>Called when an assignment is reloaded after a restart. Without it the counter
     * would restart at 1 and the next unattributed upload would take an identity another
     * submission already holds, quietly merging two students' work — the exact failure
     * the identity fix exists to prevent.
     */
    void seedUnidentifiedCounter(int highestSeen) {
        unidentifiedCounter.updateAndGet(current -> Math.max(current, highestSeen));
    }

    Report getLastReport() {
        return lastReport;
    }

    void setLastReport(Report report) {
        this.lastReport = report;
        if (report == null) {
            this.matchAnalysis = null;
        }
    }

    Object getMatchAnalysis() {
        return matchAnalysis;
    }

    void setMatchAnalysis(Object analysis) {
        this.matchAnalysis = analysis;
    }



    /**
     * A skeleton or starter file uploaded as a boilerplate reference.
     *
     * <p>Stored but not yet consumed: the reference-file suppression mechanism is not
     * built. The endpoint exists so the front end and the wire format are settled now,
     * and reports {@code applied: false} so no caller can mistake acceptance for effect.
     */
    record ReferenceFile(String filename, String content) {
        ReferenceFile {
            Objects.requireNonNull(filename, "filename");
            Objects.requireNonNull(content, "content");
        }
    }
}
