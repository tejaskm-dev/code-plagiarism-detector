package com.integrityengine.tokenizer;

import com.integrityengine.domain.Token;
import java.util.List;

/**
 * Contract every language tokenizer honours. The abstraction the rest of the pipeline
 * codes against — nothing downstream knows which language it is holding.
 */
public interface ITokenizer {

    /**
     * Normalise raw source into a comparable token stream.
     *
     * @param source raw file contents
     * @return tokens with identifiers and literals generalised
     */
    List<Token> tokenize(String source);

    /**
     * The token stream *before* identifiers and literals are generalised.
     *
     * <p>Stylometry needs this view for a specific reason: the generalisation that
     * makes {@link #tokenize} rename-invariant is exactly what destroys authorship
     * signal. Naming style is noise to a plagiarism comparator and evidence to an
     * authorship analyser, so the two read different stages of one parse rather than
     * running two parsers.
     *
     * @param source raw file contents
     * @return tokens with original identifier and literal spellings intact
     */
    List<Token> lexicalTokens(String source);

    /**
     * The source with comments blanked to spaces, length and column positions intact.
     *
     * <p>Exposed so comment density can be measured with the tokenizer's own
     * comment handling rather than a second, divergent scanner.
     *
     * @param source raw file contents
     * @return the same text with comment characters replaced by spaces
     */
    String withoutComments(String source);

    /** The language this tokenizer handles. */
    Language language();
}
