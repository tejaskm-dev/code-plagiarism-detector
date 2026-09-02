package com.integrityengine.tokenizer;

import com.integrityengine.domain.Token;
import java.util.List;
import java.util.Set;

/**
 * C tokenizer. Adds preprocessor support on top of {@link CFamilyTokenizer}.
 */
final class CTokenizer extends CFamilyTokenizer {

    /** C11 reserved words, including the {@code _}-prefixed keywords. */
    private static final Set<String> KEYWORDS = Set.of(
            "auto", "break", "case", "char", "const", "continue", "default", "do",
            "double", "else", "enum", "extern", "float", "for", "goto", "if", "inline",
            "int", "long", "register", "restrict", "return", "short", "signed",
            "sizeof", "static", "struct", "switch", "typedef", "union", "unsigned",
            "void", "volatile", "while",
            "_Bool", "_Complex", "_Imaginary", "_Alignas", "_Alignof", "_Atomic",
            "_Generic", "_Noreturn", "_Static_assert", "_Thread_local");

    @Override
    public Language language() {
        return Language.C;
    }

    @Override
    protected boolean supportsPreprocessor() {
        return true;
    }

    @Override
    protected List<Token> extractKeywords(String source) {
        return lex(source, KEYWORDS);
    }

    @Override
    protected List<Token> generalizeIdentifiers(List<Token> tokens) {
        return generalizeIdentifiersExcept(tokens, Set.of());
    }
}
