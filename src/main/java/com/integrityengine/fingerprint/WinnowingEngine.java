package com.integrityengine.fingerprint;

import com.integrityengine.domain.Fingerprint;
import com.integrityengine.domain.Token;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Reduces a token stream to a bounded set of fingerprints using the winnowing
 * algorithm of Schleimer, Wilkerson &amp; Aiken (SIGMOD 2003).
 *
 * <p>Two parameters govern the trade-off:
 * <ul>
 *   <li><b>k</b> ({@code kGramSize}) is the <i>noise threshold</i>. Nothing shorter than
 *       k tokens can ever be reported, which is what stops shared idioms like
 *       {@code for (int i = 0; i < n; i++)} from registering as plagiarism.</li>
 *   <li><b>w</b> ({@code windowSize}) controls the <i>guarantee threshold</i>
 *       t = w + k - 1, via {@link #guaranteeThreshold()}. Any run of t or more
 *       identical tokens shared by two submissions is <i>guaranteed</i> to yield at
 *       least one shared fingerprint — not merely likely to.</li>
 * </ul>
 *
 * <p>That guarantee is the reason winnowing is used here instead of sampling every
 * n-th hash: it turns detection into a proof rather than a probability, while still
 * discarding most hashes (expected density 2/(w+1)).
 *
 * <p>The guarantee holds because a shared run of length t contains w consecutive
 * identical k-grams — a complete window. Selection within a window depends only on the
 * hashes inside it, so both submissions necessarily pick the same k-gram from it.
 */
public final class WinnowingEngine {

    /** Long enough to clear common language idioms, short enough to survive edits. */
    public static final int DEFAULT_K_GRAM_SIZE = 5;

    /** Yields a guarantee threshold of 8 tokens at the default k. */
    public static final int DEFAULT_WINDOW_SIZE = 4;

    private final int kGramSize;
    private final int windowSize;

    public WinnowingEngine(int kGramSize, int windowSize) {
        if (kGramSize < 1) {
            throw new IllegalArgumentException("kGramSize must be >= 1, got " + kGramSize);
        }
        if (windowSize < 1) {
            throw new IllegalArgumentException("windowSize must be >= 1, got " + windowSize);
        }
        this.kGramSize = kGramSize;
        this.windowSize = windowSize;
    }

    public static WinnowingEngine withDefaults() {
        return new WinnowingEngine(DEFAULT_K_GRAM_SIZE, DEFAULT_WINDOW_SIZE);
    }

    /**
     * The shared-run length at or above which a common fingerprint is guaranteed.
     *
     * @return w + k - 1
     */
    public int guaranteeThreshold() {
        return windowSize + kGramSize - 1;
    }

    /**
     * Fingerprint a normalised token stream.
     *
     * <p>Each {@link Fingerprint}'s position is the index of the <i>first token</i> of
     * the k-gram it hashes, so a match can be traced back to a source region later.
     * Positions in the returned list are strictly increasing.
     *
     * @param tokens normalised token stream
     * @return the winnowed fingerprints, in position order; empty if the stream is
     *         shorter than k
     */
    public List<Fingerprint> generateFingerprints(List<Token> tokens) {
        Objects.requireNonNull(tokens, "tokens");
        if (tokens.size() < kGramSize) {
            // Not even one k-gram exists; by definition there is nothing to fingerprint.
            return List.of();
        }

        long[] codes = new long[tokens.size()];
        for (int i = 0; i < codes.length; i++) {
            codes[i] = tokenCode(tokens.get(i));
        }

        long[] hashes = new long[codes.length - kGramSize + 1];
        RollingHash rolling = new RollingHash(kGramSize);
        hashes[0] = rolling.computeHash(codes, 0);
        for (int i = 1; i < hashes.length; i++) {
            hashes[i] = rolling.roll(codes[i - 1], codes[i + kGramSize - 1]);
        }

        return winnow(hashes);
    }

    /**
     * Select the rightmost minimal hash of every sliding window of w hashes.
     *
     * <p>Circular-buffer formulation from Figure 5 of the winnowing paper. The
     * {@code min == r} test detects that the previous minimum has just been evicted
     * from the window and forces a rescan; otherwise the running minimum is updated in
     * O(1). Re-selecting a hash that is already the standing minimum records nothing,
     * which is what keeps the output near the 2/(w+1) density bound.
     */
    private List<Fingerprint> winnow(long[] hashes) {
        List<Fingerprint> selected = new ArrayList<>();

        // Sentinel priming, so the first w-1 partial windows need no special case.
        // A genuine hash of exactly Long.MAX_VALUE would tie with a sentinel; at 1 in
        // 2^64 that is not worth a branch.
        long[] window = new long[windowSize];
        Arrays.fill(window, Long.MAX_VALUE);

        int r = 0;
        int min = 0;

        for (int globalIndex = 0; globalIndex < hashes.length; globalIndex++) {
            r = (r + 1) % windowSize;
            window[r] = hashes[globalIndex];

            if (min == r) {
                // The standing minimum occupied the slot we just overwrote, so it has
                // left the window. Rescan leftward; starting at r and comparing
                // strictly keeps the *rightmost* minimum, which is what makes the
                // selection identical for identical windows in two different files.
                for (int i = (r - 1 + windowSize) % windowSize; i != r; i = (i - 1 + windowSize) % windowSize) {
                    if (window[i] < window[min]) {
                        min = i;
                    }
                }
                int age = (r - min + windowSize) % windowSize;
                selected.add(new Fingerprint(window[min], globalIndex - age));
            } else if (window[r] <= window[min]) {
                // '<=' so a tie hands the title to the newer, rightmost hash.
                min = r;
                selected.add(new Fingerprint(window[min], globalIndex));
            }
        }

        return List.copyOf(selected);
    }

    /**
     * Collapse a token to a 64-bit code via FNV-1a.
     *
     * <p>Hashes the kind's {@code name()} rather than its ordinal on purpose:
     * fingerprints are persisted to SQLite and compared across runs, so reordering the
     * {@code TokenKind} enum must not silently invalidate every stored fingerprint.
     */
    private static long tokenCode(Token token) {
        long hash = FNV_OFFSET_BASIS;
        hash = fold(hash, token.getKind().name());
        hash ^= ':';
        hash *= FNV_PRIME;
        hash = fold(hash, token.getValue());
        return hash;
    }

    private static long fold(long hash, String text) {
        long h = hash;
        for (int i = 0; i < text.length(); i++) {
            h ^= text.charAt(i);
            h *= FNV_PRIME;
        }
        return h;
    }

    private static final long FNV_OFFSET_BASIS = 0xCBF29CE484222325L;
    private static final long FNV_PRIME = 0x100000001B3L;

    /**
     * Rabin-Karp rolling hash over a k-gram of token codes.
     *
     * <p>All arithmetic is mod 2^64 by way of Java's defined {@code long} overflow. The
     * base is odd, so it stays invertible mod 2^64 and the high bits do not collapse to
     * zero as the window advances.
     *
     * <p>Package-private helper: nothing outside {@code fingerprint} has any business
     * touching the hash state.
     */
    static final class RollingHash {

        private static final long BASE = 0x100000001B3L;

        private final int windowLength;
        private final long highOrderFactor;
        private long hash;
        private boolean primed;

        RollingHash(int windowLength) {
            if (windowLength < 1) {
                throw new IllegalArgumentException("windowLength must be >= 1, got " + windowLength);
            }
            this.windowLength = windowLength;

            long factor = 1L;
            for (int i = 0; i < windowLength - 1; i++) {
                factor *= BASE;
            }
            this.highOrderFactor = factor;
        }

        /**
         * Hash a full k-gram from scratch in O(k) and prime the rolling state.
         *
         * @param values token codes
         * @param offset index of the first token in the k-gram
         * @return the hash of that k-gram
         */
        long computeHash(long[] values, int offset) {
            Objects.requireNonNull(values, "values");
            if (offset < 0 || offset + windowLength > values.length) {
                throw new IndexOutOfBoundsException(
                        "k-gram at offset " + offset + " (length " + windowLength
                                + ") does not fit in " + values.length + " values");
            }
            long h = 0L;
            for (int i = 0; i < windowLength; i++) {
                h = h * BASE + values[offset + i];
            }
            this.hash = h;
            this.primed = true;
            return h;
        }

        /**
         * Advance the window one token in O(1) rather than rehashing.
         *
         * @param outgoing code of the token leaving the window
         * @param incoming code of the token entering the window
         * @return the updated hash
         */
        long roll(long outgoing, long incoming) {
            if (!primed) {
                throw new IllegalStateException("computeHash must prime the window before roll");
            }
            hash = (hash - outgoing * highOrderFactor) * BASE + incoming;
            return hash;
        }

        /** @return the hash of the window as it currently stands */
        long current() {
            if (!primed) {
                throw new IllegalStateException("computeHash must prime the window before current");
            }
            return hash;
        }
    }
}
