package com.integrityengine.similarity;

import com.integrityengine.domain.Fingerprint;
import com.integrityengine.fingerprint.WinnowingEngine;
import com.integrityengine.tokenizer.Language;
import com.integrityengine.tokenizer.TokenizerFactory;
import java.util.HashSet;
import java.util.Set;

/** Drives the real chain: source -> tokenizer -> winnowing -> fingerprint set. */
final class Pipeline {

    private static final TokenizerFactory FACTORY = new TokenizerFactory();
    private static final WinnowingEngine ENGINE = WinnowingEngine.withDefaults();

    private Pipeline() {
    }

    static Set<Fingerprint> fingerprints(Language language, String source) {
        return new HashSet<>(ENGINE.generateFingerprints(FACTORY.forLanguage(language).tokenize(source)));
    }

    static Set<Fingerprint> without(Set<Fingerprint> fingerprints, Set<Fingerprint> suppressed) {
        Set<Fingerprint> copy = new HashSet<>(fingerprints);
        copy.removeAll(suppressed);
        return copy;
    }
}
