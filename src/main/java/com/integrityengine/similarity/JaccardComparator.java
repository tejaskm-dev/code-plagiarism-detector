package com.integrityengine.similarity;

import com.integrityengine.domain.Fingerprint;
import java.util.Objects;
import java.util.Set;

/**
 * |A &cap; B| / |A &cup; B|. Symmetric — the right default when both submissions are
 * roughly the same size.
 *
 * <p>Because the denominator grows with both files, Jaccard punishes size mismatch: a
 * short file copied verbatim into a long one scores low here even though it was wholly
 * copied. {@link ContainmentComparator} is the answer to that case, not this one.
 */
final class JaccardComparator implements SimilarityComparator {

    @Override
    public double compare(Set<Fingerprint> a, Set<Fingerprint> b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");

        int shared = FingerprintSets.intersectionSize(a, b);
        int union = a.size() + b.size() - shared;

        // An empty union means at least one submission was too short to fingerprint at
        // all. The mathematical convention would call two empty sets identical; we
        // return 0.0 instead, because reporting certainty from an absence of evidence
        // would flag every pair of trivial submissions as plagiarism.
        return union == 0 ? 0.0 : (double) shared / union;
    }

    @Override
    public String name() {
        return "jaccard";
    }
}
