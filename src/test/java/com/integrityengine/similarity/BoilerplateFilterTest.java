package com.integrityengine.similarity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.integrityengine.domain.Fingerprint;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BoilerplateFilterTest {

    private final BoilerplateFilter filter = new BoilerplateFilter();

    private static Set<Fingerprint> setOf(long... hashes) {
        Set<Fingerprint> set = new HashSet<>();
        for (long hash : hashes) {
            set.add(new Fingerprint(hash, 0));
        }
        return set;
    }

    /** A cohort of {@code size} submissions that all share hash 1, plus a unique hash each. */
    private static List<Set<Fingerprint>> cohortSharing(long shared, int size) {
        List<Set<Fingerprint>> batch = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            batch.add(setOf(shared, 1000 + i));
        }
        return batch;
    }

    @Test
    void suppressesFingerprintsPresentAcrossTheCohort() {
        Set<Fingerprint> suppressed = filter.suppress(cohortSharing(1L, 6), 0.5);

        assertTrue(suppressed.contains(new Fingerprint(1L, 0)), "shared hash was not suppressed");
        assertEquals(1, suppressed.size(), "unique hashes must survive: " + suppressed);
    }

    @Test
    @DisplayName("The threshold boundary is exact: 5 of 10 suppresses, 4 of 10 does not")
    void thresholdBoundaryIsExact() {
        List<Set<Fingerprint>> batch = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            // hash 1 in exactly 5 submissions, hash 2 in exactly 4.
            Set<Fingerprint> set = setOf(2000 + i);
            if (i < 5) {
                set.add(new Fingerprint(1L, 0));
            }
            if (i < 4) {
                set.add(new Fingerprint(2L, 0));
            }
            batch.add(set);
        }

        Set<Fingerprint> suppressed = filter.suppress(batch, 0.5);
        assertTrue(suppressed.contains(new Fingerprint(1L, 0)), "5/10 should meet a 0.5 threshold");
        assertFalse(suppressed.contains(new Fingerprint(2L, 0)), "4/10 should not meet a 0.5 threshold");
    }

    @Test
    @DisplayName("The boundary rounds up when threshold x cohort is fractional")
    void fractionalBoundaryRoundsUp() {
        // 7 submissions at threshold 0.5 needs 3.5 documents. Rounding down would
        // suppress a fingerprint present in only 3 of 7 (43%), below the threshold the
        // caller asked for. Every other boundary test here uses a cohort size that
        // divides evenly, where rounding up and down agree -- so this is the only case
        // that can tell them apart.
        List<Set<Fingerprint>> batch = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            Set<Fingerprint> set = setOf(3000 + i);
            if (i < 4) {
                set.add(new Fingerprint(1L, 0));  // 4 of 7 = 57%, above threshold
            }
            if (i < 3) {
                set.add(new Fingerprint(2L, 0));  // 3 of 7 = 43%, below threshold
            }
            batch.add(set);
        }

        Set<Fingerprint> suppressed = filter.suppress(batch, 0.5);
        assertTrue(suppressed.contains(new Fingerprint(1L, 0)), "4/7 is above 0.5 and must be suppressed");
        assertFalse(suppressed.contains(new Fingerprint(2L, 0)), "3/7 is below 0.5 and must survive");
    }

    @Test
    @DisplayName("A file repeating one idiom does not make it look like cohort boilerplate")
    void frequencyIsCountedPerDocumentNotPerOccurrence() {
        // Sets already deduplicate, so this is really a guard against the counting
        // being changed to occurrence-based later.
        List<Set<Fingerprint>> batch = cohortSharing(1L, 6);
        batch.set(0, setOf(1L, 1L, 1L, 9999L));

        assertEquals(1, filter.suppress(batch, 0.5).size());
    }

    @Test
    @DisplayName("Below the minimum cohort size the filter is inert")
    void smallCohortsSuppressNothing() {
        for (int size = 0; size < BoilerplateFilter.MINIMUM_COHORT_SIZE; size++) {
            assertEquals(Set.of(), filter.suppress(cohortSharing(1L, size), 0.5),
                    "cohort of " + size + " should suppress nothing");
        }
        assertFalse(filter.suppress(cohortSharing(1L, BoilerplateFilter.MINIMUM_COHORT_SIZE), 0.5).isEmpty(),
                "at the minimum cohort size the filter should engage");
    }

    /**
     * PERMANENT REGRESSION TEST -- do not delete if {@code BoilerplateFilter} is
     * refactored or replaced. This pins a real, unfixed limitation of frequency-based
     * boilerplate suppression rather than asserting correct behaviour, so it must be
     * migrated, not dropped, along with any rewrite of the filter.
     */
    @Test
    @DisplayName("Documented hazard: a threshold at or below the colluding cluster's share erases the evidence")
    void thresholdBelowCollusionShareDeletesTheEvidence() {
        // Two of six submissions share hash 42 and nothing else does. That is exactly
        // what collusion looks like -- and at a threshold of 2/6 the filter cannot tell
        // it apart from boilerplate. This test exists to pin the failure mode, not to
        // claim it is fixed.
        List<Set<Fingerprint>> batch = new ArrayList<>();
        batch.add(setOf(42L, 100L));
        batch.add(setOf(42L, 101L));
        for (int i = 2; i < 6; i++) {
            batch.add(setOf(1000 + i));
        }

        assertTrue(filter.suppress(batch, 2.0 / 6.0).contains(new Fingerprint(42L, 0)),
                "at threshold 2/6 the colluding pair's shared fingerprint is suppressed");
        // ...whereas a threshold above the cluster share leaves it intact.
        assertFalse(filter.suppress(batch, 0.5).contains(new Fingerprint(42L, 0)),
                "at threshold 0.5 the evidence must survive");
    }

    @Test
    void invalidThresholdsAreRejected() {
        List<Set<Fingerprint>> batch = cohortSharing(1L, 6);

        assertThrows(IllegalArgumentException.class, () -> filter.suppress(batch, 0.0));
        assertThrows(IllegalArgumentException.class, () -> filter.suppress(batch, -0.1));
        assertThrows(IllegalArgumentException.class, () -> filter.suppress(batch, 1.01));
        assertThrows(IllegalArgumentException.class, () -> filter.suppress(batch, Double.NaN));
        assertThrows(NullPointerException.class, () -> filter.suppress(null, 0.5));
    }

    @Test
    void thresholdOfOneSuppressesOnlyUniversalFingerprints() {
        List<Set<Fingerprint>> batch = cohortSharing(1L, 6);
        batch.get(0).remove(new Fingerprint(1L, 0));

        assertEquals(Set.of(), filter.suppress(batch, 1.0),
                "hash 1 is in 5 of 6, so a threshold of 1.0 must not suppress it");
    }

    @Test
    void resultIsImmutable() {
        Set<Fingerprint> suppressed = filter.suppress(cohortSharing(1L, 6), 0.5);
        assertThrows(UnsupportedOperationException.class, () -> suppressed.add(new Fingerprint(7L, 0)));
    }
}
