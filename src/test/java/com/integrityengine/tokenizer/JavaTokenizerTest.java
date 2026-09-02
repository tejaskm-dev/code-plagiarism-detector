package com.integrityengine.tokenizer;

import static com.integrityengine.tokenizer.TokenizerAssertions.assertDifferentStream;
import static com.integrityengine.tokenizer.TokenizerAssertions.assertSameStream;
import static com.integrityengine.tokenizer.TokenizerAssertions.signature;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JavaTokenizerTest {

    private final JavaTokenizer tokenizer = new JavaTokenizer();

    private static final String ORIGINAL = """
            public class Solution {
                // Computes n factorial iteratively.
                public static int factorial(int n) {
                    int result = 1;
                    for (int i = 2; i <= n; i++) {
                        result = result * i;   // accumulate
                    }
                    return result;
                }
            }
            """;

    /** Same program: every name changed, reformatted throughout, comments swapped out. */
    private static final String DISGUISED = """
            public class Solution
            {
                /* Iterative factorial.
                   Rewritten by a student in a hurry. */
                public   static   int   compute ( int  limit )
                {
                    int   acc = 1 ;
                    for ( int  idx = 2 ; idx <= limit ; idx ++ )
                    { acc = acc * idx ; }
                    return acc ;
                }
            }
            """;

    @Test
    @DisplayName("Adversarial: renamed variables, reflowed whitespace and swapped comments leave the stream identical")
    void renamingReformattingAndRecommentingAreInvisible() {
        assertSameStream(tokenizer, ORIGINAL, DISGUISED);
    }

    @Test
    @DisplayName("Discrimination: a real logic change does move the stream")
    void differentLogicProducesADifferentStream() {
        String multiplication = "class A { int f(int n){ int r=1; for(int i=2;i<=n;i++){ r=r*i; } return r; } }";
        String addition = "class A { int f(int n){ int r=1; for(int i=2;i<=n;i++){ r=r+i; } return r; } }";
        assertDifferentStream(tokenizer, multiplication, addition);
    }

    @Test
    @DisplayName("Keywords must survive generalisation, or every control structure would look alike")
    void keywordsAreNotGeneralizedAway() {
        // These differ only by a reserved word. A tokenizer with an empty keyword set
        // maps both to the same stream -- which is exactly the failure this catches.
        assertDifferentStream(tokenizer,
                "class A { void f(){ if (x) { g(); } } }",
                "class A { void f(){ while (x) { g(); } } }");
    }

    @Test
    @DisplayName("Changing a string's contents is a disguise, not a logic change")
    void literalContentIsGeneralized() {
        assertSameStream(tokenizer,
                "class A { void f(){ System.out.println(\"hello world\"); } }",
                "class A { void f(){ System.out.println(\"goodbye cruel world\"); } }");
    }

    @Test
    void numericAndCharLiteralsGetDistinctPlaceholders() {
        assertEquals(List.of("KEYWORD:int", "IDENTIFIER:ID", "OPERATOR:=", "LITERAL:NUM", "PUNCTUATION:;"),
                signature(tokenizer.tokenize("int x = 42;")));
        assertEquals(List.of("KEYWORD:char", "IDENTIFIER:ID", "OPERATOR:=", "LITERAL:CHR", "PUNCTUATION:;"),
                signature(tokenizer.tokenize("char c = 'a';")));
    }

    @Test
    @DisplayName("A // inside a string literal is not a comment")
    void commentStrippingRespectsStringLiterals() {
        assertTrue(signature(tokenizer.tokenize("String u = \"http://example.com/x\";"))
                        .contains("LITERAL:STR"),
                "the URL was eaten as a comment");
        assertEquals(5, tokenizer.tokenize("String u = \"http://example.com/x\";").size());
    }

    @Test
    void blockCommentInsideStringSurvives() {
        assertEquals(List.of("IDENTIFIER:ID", "IDENTIFIER:ID", "OPERATOR:=", "LITERAL:STR", "PUNCTUATION:;"),
                signature(tokenizer.tokenize("String s = \"/* not a comment */\";")));
    }

    @Test
    void identifiersCollapseButKeywordsDoNot() {
        assertEquals(List.of("KEYWORD:public", "KEYWORD:class", "IDENTIFIER:ID",
                        "PUNCTUATION:{", "PUNCTUATION:}"),
                signature(tokenizer.tokenize("public class Whatever {}")));
    }

}
