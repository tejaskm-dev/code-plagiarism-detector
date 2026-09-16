package com.integrityengine.tokenizer;

import com.integrityengine.domain.Token;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * C++ tokenizer. The C keyword set plus everything C++ adds on top of it.
 */
final class CppTokenizer extends CFamilyTokenizer {

    private static final Set<String> C_KEYWORDS = Set.of(
            "auto", "break", "case", "char", "const", "continue", "default", "do",
            "double", "else", "enum", "extern", "float", "for", "goto", "if", "inline",
            "int", "long", "register", "return", "short", "signed", "sizeof", "static",
            "struct", "switch", "typedef", "union", "unsigned", "void", "volatile", "while");

    private static final Set<String> CPP_ADDITIONS = Set.of(
            "alignas", "alignof", "and", "and_eq", "asm", "bitand", "bitor", "bool",
            "catch", "class", "compl", "concept", "const_cast", "consteval",
            "constexpr", "constinit", "co_await", "co_return", "co_yield", "decltype",
            "delete", "dynamic_cast", "explicit", "export", "false", "friend",
            "mutable", "namespace", "new", "noexcept", "not", "not_eq", "nullptr",
            "operator", "or", "or_eq", "private", "protected", "public",
            "reinterpret_cast", "requires", "static_assert", "static_cast",
            "template", "this", "thread_local", "throw", "true", "try", "typeid",
            "typename", "using", "virtual", "wchar_t", "char8_t", "char16_t",
            "char32_t", "xor", "xor_eq");

    private static final Set<String> KEYWORDS =
            Stream.concat(C_KEYWORDS.stream(), CPP_ADDITIONS.stream()).collect(Collectors.toUnmodifiableSet());

    @Override
    public Language language() {
        return Language.CPP;
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
