package com.integrityengine.similarity;

import com.integrityengine.domain.Fingerprint;
import java.util.Set;

/**
 * A strategy for scoring how alike two fingerprint sets are.
 *
 * <p>The polymorphism seam of the similarity stage: the engine holds a
 * {@code SimilarityComparator} and never knows which metric it is running.
 */
public interface SimilarityComparator {

    /**
     * @return similarity in [0.0, 1.0]
     */
    double compare(Set<Fingerprint> a, Set<Fingerprint> b);

    /** Human-readable metric name, recorded on each {@code SimilarityResult}. */
    String name();

    /**
     * Symmetric overlap. The right default when submissions are comparable in size.
     */
    static SimilarityComparator jaccard() {
        return new JaccardComparator();
    }

    /**
     * Asymmetric "how much of A is in B". Catches a short file pasted into a long one.
     *
     * <p>Static factories rather than a separate factory class: the concrete comparators
     * stay package-private, and callers still never name them.
     */
    static SimilarityComparator containment() {
        return new ContainmentComparator();
    }
}
