package com.integrityengine.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Fingerprint equality is real behaviour, not a stub, and the fingerprint pipeline
 * depends on it working in sets — so it is worth locking down now.
 */
class FingerprintTest {

    @Test
    void equalHashesAreEqualRegardlessOfPosition() {
        Fingerprint a = new Fingerprint(0xABCDEFL, 10);
        Fingerprint b = new Fingerprint(0xABCDEFL, 999);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentHashesAreNotEqual() {
        assertNotEquals(new Fingerprint(1L, 0), new Fingerprint(2L, 0));
    }

    @Test
    void setDeduplicatesByHash() {
        Set<Fingerprint> set = Set.of(new Fingerprint(7L, 0), new Fingerprint(8L, 1));

        assertEquals(2, set.size());
        assertEquals(true, set.contains(new Fingerprint(7L, 4242)));
    }
}
