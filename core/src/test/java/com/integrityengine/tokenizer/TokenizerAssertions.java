package com.integrityengine.tokenizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.integrityengine.domain.Token;
import java.util.List;

/**
 * Shared vocabulary for the tokenizer tests.
 *
 * <p>Comparisons are made on (kind, value) pairs rather than on {@link Token} equality,
 * because {@code Token} includes its line number and reformatting legitimately moves
 * lines. That is the correct equivalence for this project rather than a convenience:
 * {@code WinnowingEngine} derives a token's hash from its kind and value alone, so two
 * streams with equal signatures are guaranteed to produce identical fingerprints.
 * {@code WinnowingEngineTest.lineNumbersDoNotAffectFingerprints} pins that dependency
 * from the other side.
 */
final class TokenizerAssertions {

    private TokenizerAssertions() {
    }

    static List<String> signature(List<Token> tokens) {
        return tokens.stream().map(t -> t.getKind() + ":" + t.getValue()).toList();
    }

    /** The core claim: cosmetic changes must not move the token stream. */
    static void assertSameStream(ITokenizer tokenizer, String original, String disguised) {
        assertEquals(signature(tokenizer.tokenize(original)),
                signature(tokenizer.tokenize(disguised)),
                "cosmetic edits changed the token stream");
    }

    /**
     * The complement, and the reason the suite cannot be satisfied by a degenerate
     * tokenizer: a tokenizer that mapped every token to one placeholder would pass
     * every invariance test and be completely useless.
     */
    static void assertDifferentStream(ITokenizer tokenizer, String a, String b) {
        assertNotEquals(signature(tokenizer.tokenize(a)),
                signature(tokenizer.tokenize(b)),
                "genuinely different programs collapsed to the same token stream");
    }
}
