package com.integrityengine.similarity;

import com.integrityengine.domain.Fingerprint;
import java.util.Objects;
import java.util.Set;

/**
 * |A &cap; B| / |A|. Asymmetric, and deliberately so: it answers "how much of A appears
 * in B", which catches a small file lifted wholesale into a much larger one — exactly
 * the case Jaccard dilutes away.
 *
 * <p>Direction matters. {@code compare(small, large)} can be 1.0 while
 * {@code compare(large, small)} is near zero, and reading the pair in the wrong order
 * turns a complete copy into a non-finding.
 */
final class ContainmentComparator implements SimilarityComparator {

    @Override
    public double compare(Set<Fingerprint> a, Set<Fingerprint> b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");

        // Same reasoning as Jaccard: nothing to contain is not evidence of copying.
        if (a.isEmpty()) {
            return 0.0;
        }
        return (double) FingerprintSets.intersectionSize(a, b) / a.size();
    }

    @Override
    public String name() {
        return "containment";
    }
}
