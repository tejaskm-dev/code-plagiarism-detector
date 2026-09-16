package com.integrityengine.tokenizer;

import static com.integrityengine.tokenizer.TokenizerAssertions.assertDifferentStream;
import static com.integrityengine.tokenizer.TokenizerAssertions.assertSameStream;
import static com.integrityengine.tokenizer.TokenizerAssertions.signature;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JavaScriptTokenizerTest {

    private final JavaScriptTokenizer tokenizer = new JavaScriptTokenizer();

    private static final String ORIGINAL = """
            // Filters even numbers and doubles them.
            function transform(numbers) {
                const output = [];
                for (const value of numbers) {
                    if (value % 2 === 0) {
                        output.push(value * 2);   // keep it
                    }
                }
                return output;
            }
            """;

    private static final String DISGUISED = """
            function convert(items)
            {
                /* Same thing, different names. */
                const results = [] ;
                for ( const entry of items )
                {
                    if ( entry % 2 === 0 )
                    { results.push( entry * 2 ) ; }
                }
                return results ;
            }
            """;

    @Test
    @DisplayName("Adversarial: renamed, reformatted and re-commented JavaScript is the same stream")
    void renamingReformattingAndRecommentingAreInvisible() {
        assertSameStream(tokenizer, ORIGINAL, DISGUISED);
    }

    @Test
    void differentLogicProducesADifferentStream() {
        assertDifferentStream(tokenizer,
                "function f(a){ return a.filter(x => x % 2 === 0); }",
                "function f(a){ return a.filter(x => x % 2 !== 0); }");
    }

    @Test
    void keywordsAreNotGeneralizedAway() {
        assertDifferentStream(tokenizer,
                "function f(){ if (x) { g(); } }",
                "function f(){ while (x) { g(); } }");
    }

    @Test
    @DisplayName("Template literals lex as a single string literal, newlines included")
    void templateLiteralsAreOneLiteral() {
        assertEquals(List.of("KEYWORD:const", "IDENTIFIER:ID", "OPERATOR:=", "LITERAL:STR", "PUNCTUATION:;"),
                signature(tokenizer.tokenize("const s = `hello ${name} bye`;")));
    }

    @Test
    void multiLineTemplateLiteralDoesNotDerailTheLexer() {
        List<String> stream = signature(tokenizer.tokenize("const a = `line1\nline2`;\nconst b = 1;"));
        assertEquals(List.of("KEYWORD:const", "IDENTIFIER:ID", "OPERATOR:=", "LITERAL:STR", "PUNCTUATION:;",
                        "KEYWORD:const", "IDENTIFIER:ID", "OPERATOR:=", "LITERAL:NUM", "PUNCTUATION:;"),
                stream);
    }

    @Test
    void arrowAndStrictEqualityLexAsSingleOperators() {
        assertTrue(signature(tokenizer.tokenize("a => b")).contains("OPERATOR:=>"));
        assertTrue(signature(tokenizer.tokenize("a === b")).contains("OPERATOR:==="));
        assertTrue(signature(tokenizer.tokenize("a !== b")).contains("OPERATOR:!=="));
    }

    @Test
    @DisplayName("A // inside a template literal or string is not a comment")
    void commentStrippingRespectsTemplateLiterals() {
        assertEquals(List.of("KEYWORD:const", "IDENTIFIER:ID", "OPERATOR:=", "LITERAL:STR", "PUNCTUATION:;"),
                signature(tokenizer.tokenize("const u = `http://example.com`;")));
    }
}
