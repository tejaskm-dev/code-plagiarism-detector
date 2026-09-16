package com.integrityengine.tokenizer;

import com.integrityengine.domain.Token;
import java.util.List;
import java.util.Set;

/**
 * JavaScript tokenizer. Enables backtick template literals; no preprocessor.
 */
final class JavaScriptTokenizer extends CFamilyTokenizer {

    /**
     * ECMAScript reserved words, plus the contextual keywords common enough that
     * treating them as identifiers would lose real structure ({@code let},
     * {@code async}, {@code of}, {@code static}).
     */
    private static final Set<String> KEYWORDS = Set.of(
            "await", "break", "case", "catch", "class", "const", "continue",
            "debugger", "default", "delete", "do", "else", "enum", "export", "extends",
            "false", "finally", "for", "function", "if", "import", "in", "instanceof",
            "new", "null", "return", "super", "switch", "this", "throw", "true", "try",
            "typeof", "var", "void", "while", "with", "yield",
            "let", "async", "of", "static");

    @Override
    public Language language() {
        return Language.JAVASCRIPT;
    }

    @Override
    protected boolean supportsTemplateLiterals() {
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
