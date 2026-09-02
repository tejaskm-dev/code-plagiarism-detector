package com.integrityengine.domain;

/**
 * Lexical category of a {@link Token}.
 *
 * <p>{@link #INDENT} and {@link #DEDENT} exist for whitespace-significant languages
 * (Python); C-family tokenizers never emit them.
 */
public enum TokenKind {
    KEYWORD,
    IDENTIFIER,
    LITERAL,
    OPERATOR,
    PUNCTUATION,
    INDENT,
    DEDENT,
    UNKNOWN
}
