package com.integrityengine.tokenizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.integrityengine.domain.Token;
import com.integrityengine.domain.TokenKind;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Invariants every tokenizer must honour, checked polymorphically through ITokenizer. */
class TokenizerContractTest {

    static Stream<ITokenizer> tokenizers() {
        return Stream.of(new JavaTokenizer(), new CTokenizer(), new CppTokenizer(),
                new JavaScriptTokenizer(), new PythonTokenizer());
    }

    @ParameterizedTest
    @MethodSource("tokenizers")
    void nullSourceIsRejected(ITokenizer tokenizer) {
        assertThrows(NullPointerException.class, () -> tokenizer.tokenize(null));
    }

    @ParameterizedTest
    @MethodSource("tokenizers")
    void emptySourceProducesNoTokens(ITokenizer tokenizer) {
        assertEquals(List.of(), tokenizer.tokenize(""));
        assertEquals(List.of(), tokenizer.tokenize("   \n\n   \n"));
    }

    @ParameterizedTest
    @MethodSource("tokenizers")
    @DisplayName("No user-chosen identifier ever survives into the stream")
    void everyIdentifierIsGeneralized(ITokenizer tokenizer) {
        String source = tokenizer.language() == Language.PYTHON
                ? "def uniquelyNamedFunction(uniquelyNamedArg):\n    return uniquelyNamedArg\n"
                : "int uniquelyNamedFunction(int uniquelyNamedArg){ return uniquelyNamedArg; }";

        for (Token token : tokenizer.tokenize(source)) {
            if (token.getKind() == TokenKind.IDENTIFIER) {
                assertFalse(token.getValue().contains("uniquelyNamed"),
                        tokenizer.language() + " leaked an identifier: " + token);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("tokenizers")
    @DisplayName("No literal value ever survives into the stream")
    void everyLiteralIsGeneralized(ITokenizer tokenizer) {
        String source = tokenizer.language() == Language.PYTHON
                ? "x = 987654\ny = \"uniqueStringContent\"\n"
                : "int x = 987654; char* y = \"uniqueStringContent\";";

        for (Token token : tokenizer.tokenize(source)) {
            if (token.getKind() == TokenKind.LITERAL) {
                assertTrue(List.of("NUM", "STR", "CHR").contains(token.getValue()),
                        tokenizer.language() + " leaked a literal: " + token);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("tokenizers")
    @DisplayName("A file of nothing but comments tokenizes to nothing")
    void commentOnlySourceProducesNoTokens(ITokenizer tokenizer) {
        String source = tokenizer.language() == Language.PYTHON
                ? "# just a comment\n# and another\n"
                : "// just a comment\n/* and another */\n";
        assertEquals(List.of(), tokenizer.tokenize(source),
                tokenizer.language() + " produced tokens for a comment-only file");
    }

    @ParameterizedTest
    @MethodSource("tokenizers")
    @DisplayName("Tokenizing is deterministic and repeatable")
    void tokenizingTwiceGivesTheSameResult(ITokenizer tokenizer) {
        String source = tokenizer.language() == Language.PYTHON
                ? "def f(a):\n    return a + 1\n"
                : "int f(int a){ return a + 1; }";
        assertEquals(tokenizer.tokenize(source), tokenizer.tokenize(source));
    }

    @ParameterizedTest
    @MethodSource("tokenizers")
    @DisplayName("An unterminated string must not swallow the file or hang the lexer")
    void unterminatedLiteralsTerminate(ITokenizer tokenizer) {
        assertTrue(tokenizer.tokenize("x = \"unterminated").size() > 0);
        String unterminatedComment = tokenizer.language() == Language.PYTHON
                ? "x = 1 # unterminated\n"
                : "int x = 1; /* unterminated";
        assertTrue(tokenizer.tokenize(unterminatedComment).size() > 0);
    }
}
