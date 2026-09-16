package com.integrityengine.domain;

import java.util.Objects;

/**
 * A single lexical unit produced by a tokenizer. Immutable.
 */
public final class Token {

    private final String value;
    private final TokenKind kind;
    private final int line;

    public Token(String value, TokenKind kind, int line) {
        this.value = Objects.requireNonNull(value, "value");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.line = line;
    }

    public String getValue() {
        return value;
    }

    public TokenKind getKind() {
        return kind;
    }

    public int getLine() {
        return line;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Token other)) {
            return false;
        }
        return line == other.line && kind == other.kind && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, kind, line);
    }

    @Override
    public String toString() {
        return "Token[" + kind + " '" + value + "' @" + line + "]";
    }
}
