package com.integrityengine.tokenizer;

import com.integrityengine.domain.Token;
import java.util.List;
import java.util.Set;

/**
 * Java tokenizer. Inherits comment stripping, literal generalisation and the shared
 * lexer from {@link CFamilyTokenizer}; supplies the Java reserved words.
 */
final class JavaTokenizer extends CFamilyTokenizer {

    /**
     * The JLS reserved words, plus the three reserved literals and {@code var}.
     *
     * <p>Contextual keywords added since Java 9 ({@code record}, {@code sealed},
     * {@code permits}, {@code yield}, {@code module}) are deliberately absent: they are
     * legal identifiers, and a student with a variable named {@code record} would
     * otherwise have it preserved while every other name was generalised.
     */
    private static final Set<String> KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new",
            "package", "private", "protected", "public", "return", "short", "static",
            "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while",
            "true", "false", "null", "var");

    @Override
    public Language language() {
        return Language.JAVA;
    }

    @Override
    protected List<Token> extractKeywords(String source) {
        return lex(source, KEYWORDS);
    }

    @Override
    protected List<Token> generalizeIdentifiers(List<Token> tokens) {
        // Java preserves nothing: every name in a Java file is the author's choice.
        return generalizeIdentifiersExcept(tokens, Set.of());
    }
}
