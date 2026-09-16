package com.integrityengine.tokenizer;

import java.util.Optional;

/**
 * The public way to obtain a tokenizer.
 *
 * <p>Exists so that the concrete tokenizers can stay package-private: callers outside
 * this package name a {@link Language} (or a filename) and receive an
 * {@link ITokenizer}, never a {@code JavaTokenizer}. The hierarchy behind the interface
 * can be reshaped freely without breaking anything downstream.
 */
public final class TokenizerFactory {

    private final LanguageDetector detector = new LanguageDetector();

    /**
     * @param language the language to tokenize
     * @return a tokenizer for that language
     */
    public ITokenizer forLanguage(Language language) {
        java.util.Objects.requireNonNull(language, "language");
        return switch (language) {
            case JAVA -> new JavaTokenizer();
            case C -> new CTokenizer();
            case CPP -> new CppTokenizer();
            case PYTHON -> new PythonTokenizer();
            case JAVASCRIPT -> new JavaScriptTokenizer();
        };
    }

    /**
     * @param filename submission filename, extension included
     * @return a tokenizer for the detected language, or empty if the extension is
     *         unrecognised — an unknown file type is a caller decision, not an error
     */
    public Optional<ITokenizer> forFilename(String filename) {
        return detector.detectLanguage(filename).map(this::forLanguage);
    }
}
