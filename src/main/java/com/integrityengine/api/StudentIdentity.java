package com.integrityengine.api;

import java.util.Objects;

/**
 * Who a submitted file belongs to, and how that was determined.
 *
 * <p>The {@code how} matters as much as the id: an identity read from a folder name is
 * something the uploader asserted, while an {@link Source#UNIDENTIFIED} one is a
 * placeholder the server invented so the file would not be lost. A reviewer must be able
 * to tell those apart before trusting a report.
 */
record StudentIdentity(String studentId, Source how) {

    /** Prefix marking a generated placeholder identity. */
    static final String UNIDENTIFIED_PREFIX = "unidentified-";

    StudentIdentity {
        Objects.requireNonNull(studentId, "studentId");
        Objects.requireNonNull(how, "how");
    }

    enum Source {
        /** Taken from the per-student folder inside an uploaded archive. */
        FOLDER,
        /** Extracted from an identifier embedded in the filename. */
        FILENAME,
        /** Nothing identifiable was present; the server generated a unique placeholder. */
        UNIDENTIFIED
    }

    boolean isUnidentified() {
        return how == Source.UNIDENTIFIED;
    }

    static boolean looksUnidentified(String studentId) {
        return studentId != null && studentId.startsWith(UNIDENTIFIED_PREFIX);
    }
}
