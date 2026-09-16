package com.integrityengine.tokenizer;

import static com.integrityengine.tokenizer.TokenizerAssertions.assertDifferentStream;
import static com.integrityengine.tokenizer.TokenizerAssertions.assertSameStream;
import static com.integrityengine.tokenizer.TokenizerAssertions.signature;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CTokenizerTest {

    private final CTokenizer tokenizer = new CTokenizer();

    private static final String ORIGINAL = """
            #include <stdio.h>

            /* Sums an array. */
            int total(int values[], int count) {
                int sum = 0;
                for (int i = 0; i < count; i++) {
                    sum += values[i];  // running total
                }
                return sum;
            }
            """;

    private static final String DISGUISED = """
            #include <stdio.h>
            int accumulate(int data[],int n)
            {
                int acc=0;
                for(int k=0;k<n;k++)
                {
                    acc+=data[k];
                }
                /* no comments in the original spot */
                return acc;
            }
            """;

    @Test
    @DisplayName("Adversarial: renamed, reformatted and re-commented C is the same stream")
    void renamingReformattingAndRecommentingAreInvisible() {
        assertSameStream(tokenizer, ORIGINAL, DISGUISED);
    }

    @Test
    void differentLogicProducesADifferentStream() {
        assertDifferentStream(tokenizer,
                "int f(int n){ int s=0; for(int i=0;i<n;i++){ s+=i; } return s; }",
                "int f(int n){ int s=0; for(int i=0;i<n;i++){ s-=i; } return s; }");
    }

    @Test
    void keywordsAreNotGeneralizedAway() {
        assertDifferentStream(tokenizer,
                "void f(){ if (x) { g(); } }",
                "void f(){ while (x) { g(); } }");
    }

    @Test
    @DisplayName("Preprocessor directives lex as one keyword token")
    void preprocessorDirectivesAreSingleKeywords() {
        List<String> stream = signature(tokenizer.tokenize("#include <stdio.h>\n#define MAX 10\n"));
        assertTrue(stream.contains("KEYWORD:#include"), stream.toString());
        assertTrue(stream.contains("KEYWORD:#define"), stream.toString());
    }

    @Test
    @DisplayName("The include target is generalised with every other identifier, by design")
    void includeTargetIsGeneralized() {
        assertSameStream(tokenizer, "#include <stdio.h>\n", "#include <stdlib.h>\n");
    }

    @Test
    void hexFloatExponentDoesNotSwallowTheOperator() {
        // 0x1e+2 is (0x1e) + (2): 'e' is a hex digit, not an exponent marker.
        assertEquals(List.of("LITERAL:NUM", "OPERATOR:+", "LITERAL:NUM"),
                signature(tokenizer.tokenize("0x1e+2")));
        // ...whereas in decimal it really is one literal.
        assertEquals(List.of("LITERAL:NUM"), signature(tokenizer.tokenize("1e+2")));
    }

    @Test
    void commentStrippingRespectsCharLiterals() {
        assertEquals(List.of("KEYWORD:char", "IDENTIFIER:ID", "OPERATOR:=", "LITERAL:CHR", "PUNCTUATION:;"),
                signature(tokenizer.tokenize("char slash = '/';")));
    }
}
