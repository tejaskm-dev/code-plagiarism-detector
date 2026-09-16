package com.integrityengine.tokenizer;

import static com.integrityengine.tokenizer.TokenizerAssertions.assertDifferentStream;
import static com.integrityengine.tokenizer.TokenizerAssertions.assertSameStream;
import static com.integrityengine.tokenizer.TokenizerAssertions.signature;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CppTokenizerTest {

    private final CppTokenizer tokenizer = new CppTokenizer();

    private static final String ORIGINAL = """
            #include <vector>
            using namespace std;

            // Returns the largest element.
            template <typename T>
            T largest(const vector<T>& items) {
                T best = items[0];
                for (size_t i = 1; i < items.size(); ++i) {
                    if (items[i] > best) { best = items[i]; }
                }
                return best;
            }
            """;

    private static final String DISGUISED = """
            #include <vector>
            using namespace std;
            template <typename U>
            U peak(const vector<U>& xs)
            {
                /* find the max */
                U winner = xs[0];
                for(size_t j=1;j<xs.size();++j)
                {
                    if(xs[j]>winner){winner=xs[j];}
                }
                return winner;
            }
            """;

    @Test
    @DisplayName("Adversarial: renamed template parameters and reformatted C++ is the same stream")
    void renamingReformattingAndRecommentingAreInvisible() {
        assertSameStream(tokenizer, ORIGINAL, DISGUISED);
    }

    @Test
    void differentLogicProducesADifferentStream() {
        assertDifferentStream(tokenizer,
                "int f(vector<int>& v){ if (v[0] > v[1]) return 1; return 0; }",
                "int f(vector<int>& v){ if (v[0] < v[1]) return 1; return 0; }");
    }

    @Test
    void keywordsAreNotGeneralizedAway() {
        assertDifferentStream(tokenizer,
                "void f(){ if (x) { g(); } }",
                "void f(){ while (x) { g(); } }");
    }

    @Test
    @DisplayName("C++-only keywords are recognised, unlike in the C tokenizer")
    void cppKeywordsAreRecognizedWhereCWouldNotBe() {
        List<String> cpp = signature(tokenizer.tokenize("class A { public: virtual void f(); };"));
        assertTrue(cpp.contains("KEYWORD:class"), cpp.toString());
        assertTrue(cpp.contains("KEYWORD:virtual"), cpp.toString());
        assertTrue(cpp.contains("KEYWORD:public"), cpp.toString());

        // The same text through the C tokenizer: none of those are C keywords.
        List<String> c = signature(new CTokenizer().tokenize("class A { public: virtual void f(); };"));
        assertTrue(c.contains("IDENTIFIER:ID"), c.toString());
        assertTrue(!c.contains("KEYWORD:class"), "C must not treat 'class' as a keyword: " + c);
    }

    @Test
    void scopeResolutionOperatorLexesAsOneToken() {
        assertTrue(signature(tokenizer.tokenize("std::cout")).contains("OPERATOR:::"),
                signature(tokenizer.tokenize("std::cout")).toString());
    }
}
