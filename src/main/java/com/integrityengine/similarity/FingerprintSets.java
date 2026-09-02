package com.integrityengine.similarity;

import com.integrityengine.domain.Fingerprint;
import java.util.Set;

/**
 * Set arithmetic shared by the comparators. Package-private: an internal detail of how
 * similarity is computed, not part of any contract.
 */
final class FingerprintSets {

    private FingerprintSets() {
    }

    /**
     * Size of the intersection, iterating the smaller set against the larger one.
     *
     * <p>Fingerprints compare by hash alone, so this counts shared k-gram hashes
     * regardless of where in either file they occurred — which is the point.
     */
    static int intersectionSize(Set<Fingerprint> a, Set<Fingerprint> b) {
        Set<Fingerprint> smaller = a.size() <= b.size() ? a : b;
        Set<Fingerprint> larger = smaller == a ? b : a;

        int shared = 0;
        for (Fingerprint fingerprint : smaller) {
            if (larger.contains(fingerprint)) {
                shared++;
            }
        }
        return shared;
    }
}
