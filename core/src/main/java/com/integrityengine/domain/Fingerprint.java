package com.integrityengine.domain;

/**
 * A winnowed k-gram hash plus the position it was selected from.
 *
 * <p>Deliberately hand-written rather than a record: private final fields, no setters,
 * and an explicit {@code equals}/{@code hashCode} pair. Fingerprints live in
 * {@link java.util.Set}s during comparison, so value equality must be correct.
 *
 * <p>Note that {@code position} is intentionally excluded from equality: two identical
 * hashes are the same fingerprint regardless of where in the file they came from, which
 * is exactly what set intersection needs.
 */
public final class Fingerprint {

    private final long hash;
    private final int position;

    public Fingerprint(long hash, int position) {
        this.hash = hash;
        this.position = position;
    }

    public long getHash() {
        return hash;
    }

    public int getPosition() {
        return position;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Fingerprint other)) {
            return false;
        }
        return hash == other.hash;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(hash);
    }

    @Override
    public String toString() {
        return "Fingerprint[" + Long.toHexString(hash) + " @" + position + "]";
    }
}
