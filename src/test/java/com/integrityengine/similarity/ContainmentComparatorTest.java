package com.integrityengine.similarity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.integrityengine.domain.Fingerprint;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContainmentComparatorTest {

    private final ContainmentComparator comparator = new ContainmentComparator();

    private static Set<Fingerprint> setOf(long... hashes) {
        Set<Fingerprint> set = new java.util.HashSet<>();
        for (long hash : hashes) {
            set.add(new Fingerprint(hash, 0));
        }
        return set;
    }

    @Test
    void computesIntersectionOverTheFirstSet() {
        // {1,2} fully inside {1,2,3,4}.
        assertEquals(1.0, comparator.compare(setOf(1, 2), setOf(1, 2, 3, 4)), 1e-9);
        // Half of {1,2,3,4} appears in {1,2}.
        assertEquals(0.5, comparator.compare(setOf(1, 2, 3, 4), setOf(1, 2)), 1e-9);
    }

    @Test
    @DisplayName("Containment is asymmetric, and the direction is the whole point")
    void isAsymmetric() {
        Set<Fingerprint> small = setOf(1, 2);
        Set<Fingerprint> large = setOf(1, 2, 3, 4, 5, 6, 7, 8);

        assertEquals(1.0, comparator.compare(small, large), 1e-9);
        assertEquals(0.25, comparator.compare(large, small), 1e-9);
        assertNotEquals(comparator.compare(small, large), comparator.compare(large, small));
    }

    @Test
    @DisplayName("Containment sees a wholly-copied short file that Jaccard dilutes away")
    void catchesWhatJaccardDilutes() {
        Set<Fingerprint> small = setOf(1, 2);
        Set<Fingerprint> large = setOf(1, 2, 3, 4, 5, 6, 7, 8);

        assertEquals(1.0, comparator.compare(small, large), 1e-9);
        assertTrue(new JaccardComparator().compare(small, large) < 0.3,
                "this is the case Jaccard is expected to miss");
    }

    @Test
    void emptyFirstSetScoresZero() {
        assertEquals(0.0, comparator.compare(Set.of(), setOf(1, 2)), 1e-9);
        assertEquals(0.0, comparator.compare(Set.of(), Set.of()), 1e-9);
    }

    @Test
    void nullsAreRejected() {
        assertThrows(NullPointerException.class, () -> comparator.compare(null, Set.of()));
        assertThrows(NullPointerException.class, () -> comparator.compare(Set.of(), null));
    }

    @Test
    void isNamedForReporting() {
        assertEquals("containment", comparator.name());
    }
}
