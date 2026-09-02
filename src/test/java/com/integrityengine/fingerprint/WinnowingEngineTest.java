package com.integrityengine.fingerprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.integrityengine.domain.Fingerprint;
import com.integrityengine.domain.Token;
import com.integrityengine.domain.TokenKind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WinnowingEngineTest {

    // ---------------------------------------------------------------- rolling hash

    @Test
    @DisplayName("roll() agrees with a full recomputation at every offset")
    void rollMatchesFullRecomputation() {
        Random random = new Random(20260823L);
        long[] values = new long[500];
        for (int i = 0; i < values.length; i++) {
            values[i] = random.nextLong();
        }

        for (int k = 1; k <= 12; k++) {
            WinnowingEngine.RollingHash rolling = new WinnowingEngine.RollingHash(k);
            WinnowingEngine.RollingHash reference = new WinnowingEngine.RollingHash(k);

            long running = rolling.computeHash(values, 0);
            assertEquals(reference.computeHash(values, 0), running, "k=" + k + " offset=0");

            for (int offset = 1; offset + k <= values.length; offset++) {
                running = rolling.roll(values[offset - 1], values[offset + k - 1]);
                assertEquals(reference.computeHash(values, offset), running,
                        "k=" + k + " offset=" + offset);
                assertEquals(running, rolling.current());
            }
        }
    }

    @Test
    void rollBeforePrimingIsRejected() {
        WinnowingEngine.RollingHash rolling = new WinnowingEngine.RollingHash(4);
        assertThrows(IllegalStateException.class, () -> rolling.roll(1L, 2L));
    }

    @Test
    void rollingHashRejectsKGramThatDoesNotFit() {
        WinnowingEngine.RollingHash rolling = new WinnowingEngine.RollingHash(4);
        assertThrows(IndexOutOfBoundsException.class, () -> rolling.computeHash(new long[3], 0));
    }

    // ------------------------------------------------------------ the guarantee

    @ParameterizedTest(name = "k={0}, w={1}: a shared run of w+k-1 tokens always matches")
    @CsvSource({"5, 4", "4, 8", "8, 4", "3, 3", "10, 6", "2, 12", "6, 1"})
    @DisplayName("The winnowing guarantee: a shared run of exactly t = w+k-1 always yields a shared fingerprint")
    void sharedRunAtGuaranteeThresholdAlwaysProducesASharedFingerprint(int k, int w) {
        WinnowingEngine engine = new WinnowingEngine(k, w);
        int threshold = engine.guaranteeThreshold();
        assertEquals(w + k - 1, threshold);

        Random random = new Random(99L * k + w);

        // 200 independent trials: the shared run is the same in both streams, and
        // everything around it is random and drawn from disjoint alphabets.
        for (int trial = 0; trial < 200; trial++) {
            List<Token> sharedRun = randomTokens(random, threshold, "shared");

            List<Token> left = surround(sharedRun, random, "left",
                    random.nextInt(40), random.nextInt(40));
            List<Token> right = surround(sharedRun, random, "right",
                    random.nextInt(40), random.nextInt(40));

            Set<Fingerprint> leftPrints = new HashSet<>(engine.generateFingerprints(left));
            Set<Fingerprint> rightPrints = new HashSet<>(engine.generateFingerprints(right));

            Set<Fingerprint> shared = new HashSet<>(leftPrints);
            shared.retainAll(rightPrints);

            assertFalse(shared.isEmpty(),
                    "k=" + k + " w=" + w + " trial=" + trial
                            + ": a shared run of " + threshold + " tokens produced no shared fingerprint");
        }
    }

    @ParameterizedTest(name = "k={0}, w={1}: the guarantee survives heavy hash ties")
    @CsvSource({"1, 4", "2, 4", "1, 8", "2, 2", "3, 6"})
    @DisplayName("The guarantee still holds when hashes tie constantly, which is what the tie-break rule is for")
    void guaranteeHoldsWhenHashesTieHeavily(int k, int w) {
        // A two-symbol alphabet at small k leaves only a handful of distinct k-gram
        // hashes, so windows are saturated with ties and the same hash recurs all over
        // both streams -- a regime the sparse 64-bit test above never reaches.
        //
        // Note what this does *not* test: the direction of the tie-break. Tied hashes
        // are by definition equal, and Fingerprint compares by hash, so resolving a tie
        // leftward instead of rightward changes the recorded *position* but not the
        // recorded *value*. The guarantee is therefore insensitive to tie direction;
        // everyWindowRetainsItsRightmostMinimum is what pins that rule down.
        WinnowingEngine engine = new WinnowingEngine(k, w);
        int threshold = engine.guaranteeThreshold();
        Random random = new Random(500L * k + w);

        for (int trial = 0; trial < 300; trial++) {
            List<Token> sharedRun = randomTokens(random, threshold, "shared", 2);
            List<Token> left = new ArrayList<>(randomTokens(random, random.nextInt(25), "left", 2));
            left.addAll(sharedRun);
            left.addAll(randomTokens(random, random.nextInt(25), "left", 2));
            List<Token> right = new ArrayList<>(randomTokens(random, random.nextInt(25), "right", 2));
            right.addAll(sharedRun);
            right.addAll(randomTokens(random, random.nextInt(25), "right", 2));

            Set<Fingerprint> shared = new HashSet<>(engine.generateFingerprints(left));
            shared.retainAll(new HashSet<>(engine.generateFingerprints(right)));

            assertFalse(shared.isEmpty(),
                    "k=" + k + " w=" + w + " trial=" + trial
                            + ": tied windows broke the guarantee");
        }
    }

    @ParameterizedTest(name = "k={0}, w={1}: a shared run below k never matches")
    @CsvSource({"5, 4", "4, 8", "8, 4", "10, 6"})
    @DisplayName("The noise threshold: a shared run shorter than k can never be reported")
    void sharedRunShorterThanKGramSizeSharesNothing(int k, int w) {
        WinnowingEngine engine = new WinnowingEngine(k, w);
        Random random = new Random(7L * k + w);

        for (int trial = 0; trial < 200; trial++) {
            // One token short of a single k-gram, so every k-gram spanning the run must
            // also swallow a filler token -- and the fillers come from disjoint
            // alphabets, so no k-gram can coincide.
            List<Token> sharedRun = randomTokens(random, k - 1, "shared");

            List<Token> left = surround(sharedRun, random, "left", 30, 30);
            List<Token> right = surround(sharedRun, random, "right", 30, 30);

            Set<Fingerprint> shared = new HashSet<>(engine.generateFingerprints(left));
            shared.retainAll(new HashSet<>(engine.generateFingerprints(right)));

            assertTrue(shared.isEmpty(),
                    "k=" + k + " w=" + w + " trial=" + trial
                            + ": a shared run of only " + (k - 1) + " tokens was reported");
        }
    }

    // ------------------------------------------------------- selection invariants

    @ParameterizedTest(name = "k={0}, w={1}: every window keeps a fingerprint")
    @CsvSource({"5, 4", "4, 8", "1, 2", "3, 7", "6, 1"})
    @DisplayName("Coverage invariant: every window of w consecutive k-grams has a selected fingerprint")
    void everyWindowRetainsItsRightmostMinimum(int k, int w) {
        WinnowingEngine engine = new WinnowingEngine(k, w);
        Random random = new Random(31L * k + w);
        List<Token> tokens = randomTokens(random, 400, "tok");

        List<Fingerprint> fingerprints = engine.generateFingerprints(tokens);
        Set<Integer> selectedPositions = new HashSet<>();
        for (Fingerprint fingerprint : fingerprints) {
            selectedPositions.add(fingerprint.getPosition());
        }

        long[] hashes = hashesOf(engine, tokens, k);
        for (int start = 0; start + w <= hashes.length; start++) {
            int rightmostMin = start;
            for (int i = start; i < start + w; i++) {
                if (hashes[i] <= hashes[rightmostMin]) {
                    rightmostMin = i;
                }
            }
            assertTrue(selectedPositions.contains(rightmostMin),
                    "k=" + k + " w=" + w + ": window [" + start + "," + (start + w - 1)
                            + "] contributed no fingerprint");
        }
    }

    @Test
    @DisplayName("Positions are strictly increasing and each names its own k-gram's hash")
    void positionsAreStrictlyIncreasingAndHashesAreConsistent() {
        WinnowingEngine engine = WinnowingEngine.withDefaults();
        List<Token> tokens = randomTokens(new Random(5150L), 300, "tok");

        List<Fingerprint> fingerprints = engine.generateFingerprints(tokens);
        long[] hashes = hashesOf(engine, tokens, WinnowingEngine.DEFAULT_K_GRAM_SIZE);

        int previous = -1;
        for (Fingerprint fingerprint : fingerprints) {
            assertTrue(fingerprint.getPosition() > previous,
                    "positions must strictly increase, saw " + fingerprint.getPosition()
                            + " after " + previous);
            previous = fingerprint.getPosition();
            assertEquals(hashes[fingerprint.getPosition()], fingerprint.getHash(),
                    "fingerprint at " + fingerprint.getPosition() + " carries the wrong hash");
        }
    }

    @Test
    @DisplayName("Density stays within the 2/(w+1) neighbourhood rather than keeping everything")
    void densityIsBounded() {
        int k = 5;
        int w = 8;
        WinnowingEngine engine = new WinnowingEngine(k, w);
        List<Token> tokens = randomTokens(new Random(4242L), 5000, "tok");

        int kGramCount = tokens.size() - k + 1;
        int selected = engine.generateFingerprints(tokens).size();

        // Lower bound is structural: no window may be left uncovered.
        assertTrue(selected >= kGramCount / w,
                "selected " + selected + " is below the coverage floor " + (kGramCount / w));
        // Upper bound: the expected density is 2/(w+1); allow generous slack so this
        // asserts "winnowing actually discards most hashes" and not a specific RNG draw.
        assertTrue(selected <= kGramCount / 2,
                "selected " + selected + " of " + kGramCount + " k-grams -- barely winnowing at all");
    }

    @Test
    void windowSizeOfOneSelectsEveryKGram() {
        WinnowingEngine engine = new WinnowingEngine(4, 1);
        List<Token> tokens = randomTokens(new Random(1L), 50, "tok");

        assertEquals(tokens.size() - 4 + 1, engine.generateFingerprints(tokens).size());
    }

    @Test
    void identicalStreamsProduceIdenticalFingerprints() {
        WinnowingEngine engine = WinnowingEngine.withDefaults();
        List<Token> a = randomTokens(new Random(88L), 200, "tok");
        List<Token> b = randomTokens(new Random(88L), 200, "tok");

        assertEquals(engine.generateFingerprints(a), engine.generateFingerprints(b));
    }

    @Test
    @DisplayName("Renaming every identifier does not change the fingerprints once generalised")
    void generalisedIdentifiersMakeRenamingInvisible() {
        WinnowingEngine engine = WinnowingEngine.withDefaults();

        // What a tokenizer emits after stage 3 generalisation: the identifier's own
        // spelling is gone, so the two streams are byte-identical by construction.
        List<Token> original = List.of(
                new Token("int", TokenKind.KEYWORD, 1),
                new Token("ID", TokenKind.IDENTIFIER, 1),
                new Token("=", TokenKind.OPERATOR, 1),
                new Token("NUM", TokenKind.LITERAL, 1),
                new Token(";", TokenKind.PUNCTUATION, 1),
                new Token("return", TokenKind.KEYWORD, 2),
                new Token("ID", TokenKind.IDENTIFIER, 2));
        List<Token> renamed = new ArrayList<>(original);

        assertEquals(engine.generateFingerprints(original), engine.generateFingerprints(renamed));
    }

    @Test
    @DisplayName("Line numbers do not reach the hash, so reformatting cannot move a fingerprint")
    void lineNumbersDoNotAffectFingerprints() {
        // The tokenizer tests compare (kind, value) signatures rather than Token
        // equality, on the grounds that reformatting legitimately moves line numbers.
        // This is the other half of that argument: it holds only because a token's
        // hash is derived from kind and value alone.
        WinnowingEngine engine = WinnowingEngine.withDefaults();

        List<Token> compact = new ArrayList<>();
        List<Token> spreadOut = new ArrayList<>();
        Random random = new Random(606L);
        for (int i = 0; i < 200; i++) {
            String value = "tok_" + random.nextInt(12);
            compact.add(new Token(value, TokenKind.IDENTIFIER, 1));
            spreadOut.add(new Token(value, TokenKind.IDENTIFIER, i * 7 + 100));
        }

        assertEquals(engine.generateFingerprints(compact), engine.generateFingerprints(spreadOut));
    }

    // -------------------------------------------------------------- degenerate input

    @Test
    void streamShorterThanKProducesNoFingerprints() {
        WinnowingEngine engine = new WinnowingEngine(5, 4);
        assertTrue(engine.generateFingerprints(randomTokens(new Random(2L), 4, "tok")).isEmpty());
    }

    @Test
    void streamOfExactlyKProducesOneFingerprint() {
        WinnowingEngine engine = new WinnowingEngine(5, 4);
        assertEquals(1, engine.generateFingerprints(randomTokens(new Random(3L), 5, "tok")).size());
    }

    @Test
    void emptyStreamProducesNoFingerprints() {
        assertTrue(WinnowingEngine.withDefaults().generateFingerprints(List.of()).isEmpty());
    }

    @Test
    void returnedListIsImmutable() {
        WinnowingEngine engine = WinnowingEngine.withDefaults();
        List<Fingerprint> fingerprints = engine.generateFingerprints(
                randomTokens(new Random(4L), 100, "tok"));

        assertThrows(UnsupportedOperationException.class,
                () -> fingerprints.add(new Fingerprint(1L, 0)));
    }

    @Test
    void invalidConfigurationIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new WinnowingEngine(0, 4));
        assertThrows(IllegalArgumentException.class, () -> new WinnowingEngine(5, 0));
        assertThrows(NullPointerException.class,
                () -> WinnowingEngine.withDefaults().generateFingerprints(null));
    }

    // ----------------------------------------------------------------- helpers

    /** Recompute every k-gram hash the slow way, for cross-checking selection. */
    private static long[] hashesOf(WinnowingEngine engine, List<Token> tokens, int k) {
        // Reconstructed from the engine's own output contract: fingerprint i carries
        // the hash of the k-gram starting at token i, so a k=..., w=1 run yields the
        // complete hash sequence.
        List<Fingerprint> everyKGram = new WinnowingEngine(k, 1).generateFingerprints(tokens);
        long[] hashes = new long[everyKGram.size()];
        for (int i = 0; i < hashes.length; i++) {
            hashes[i] = everyKGram.get(i).getHash();
        }
        return hashes;
    }

    private static List<Token> randomTokens(Random random, int count, String alphabet) {
        return randomTokens(random, count, alphabet, 12);
    }

    private static List<Token> randomTokens(Random random, int count, String alphabet, int symbols) {
        List<Token> tokens = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            tokens.add(new Token(alphabet + "_" + random.nextInt(symbols), TokenKind.IDENTIFIER, i));
        }
        return tokens;
    }

    /** Embed {@code core} in filler drawn from an alphabet unique to {@code alphabet}. */
    private static List<Token> surround(List<Token> core, Random random, String alphabet,
                                        int before, int after) {
        List<Token> stream = new ArrayList<>(randomTokens(random, before, alphabet));
        stream.addAll(core);
        stream.addAll(randomTokens(random, after, alphabet));
        return stream;
    }

    /** Kept deliberately: proves the helper alphabets really are disjoint. */
    @Test
    void fillerAlphabetsAreDisjoint() {
        Random random = new Random(11L);
        Set<String> left = new LinkedHashSet<>();
        Set<String> right = new LinkedHashSet<>();
        randomTokens(random, 100, "left").forEach(t -> left.add(t.getValue()));
        randomTokens(random, 100, "right").forEach(t -> right.add(t.getValue()));

        Set<String> overlap = new HashSet<>(left);
        overlap.retainAll(right);
        assertTrue(overlap.isEmpty(), "filler alphabets overlap: " + overlap);
    }
}
