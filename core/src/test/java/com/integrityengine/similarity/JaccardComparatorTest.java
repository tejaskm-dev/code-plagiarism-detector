package com.integrityengine.similarity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.integrityengine.domain.Fingerprint;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JaccardComparatorTest {

    private final JaccardComparator comparator = new JaccardComparator();

    private static Set<Fingerprint> setOf(long... hashes) {
        Set<Fingerprint> set = new java.util.HashSet<>();
        for (long hash : hashes) {
            set.add(new Fingerprint(hash, 0));
        }
        return set;
    }

    @Test
    void computesIntersectionOverUnion() {
        // {1,2,3} vs {2,3,4}: shared 2, union 4.
        assertEquals(0.5, comparator.compare(setOf(1, 2, 3), setOf(2, 3, 4)), 1e-9);
        // {1,2} vs {1,2,3,4}: shared 2, union 4.
        assertEquals(0.5, comparator.compare(setOf(1, 2), setOf(1, 2, 3, 4)), 1e-9);
    }

    @Test
    void identicalSetsScoreOneAndDisjointSetsScoreZero() {
        assertEquals(1.0, comparator.compare(setOf(1, 2, 3), setOf(1, 2, 3)), 1e-9);
        assertEquals(0.0, comparator.compare(setOf(1, 2), setOf(3, 4)), 1e-9);
    }

    @Test
    @DisplayName("Jaccard is symmetric")
    void isSymmetric() {
        Set<Fingerprint> a = setOf(1, 2, 3, 4, 5);
        Set<Fingerprint> b = setOf(4, 5, 6);
        assertEquals(comparator.compare(a, b), comparator.compare(b, a), 1e-12);
    }

    @Test
    @DisplayName("Two unfingerprintable files score 0.0, not 1.0")
    void emptySetsScoreZeroRatherThanVacuouslyIdentical() {
        // The mathematical convention would say 1.0. That would flag every pair of
        // too-short submissions as identical, which is the opposite of useful.
        assertEquals(0.0, comparator.compare(Set.of(), Set.of()), 1e-9);
        assertEquals(0.0, comparator.compare(setOf(1, 2), Set.of()), 1e-9);
    }

    @Test
    void nullsAreRejected() {
        assertThrows(NullPointerException.class, () -> comparator.compare(null, Set.of()));
        assertThrows(NullPointerException.class, () -> comparator.compare(Set.of(), null));
    }

    @Test
    void isNamedForReporting() {
        assertEquals("jaccard", comparator.name());
    }
}
