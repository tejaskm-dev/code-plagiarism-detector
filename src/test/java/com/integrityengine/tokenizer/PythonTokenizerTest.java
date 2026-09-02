package com.integrityengine.tokenizer;

import static com.integrityengine.tokenizer.TokenizerAssertions.assertDifferentStream;
import static com.integrityengine.tokenizer.TokenizerAssertions.assertSameStream;
import static com.integrityengine.tokenizer.TokenizerAssertions.signature;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.integrityengine.domain.Token;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PythonTokenizerTest {

    private final PythonTokenizer tokenizer = new PythonTokenizer();

    private static final String ORIGINAL = """
            class Accumulator:
                \"\"\"Adds up the even numbers it is given.\"\"\"

                def __init__(self):
                    self.total = 0

                # Consume a list of numbers.
                def consume(self, numbers):
                    for value in numbers:
                        if value % 2 == 0:
                            self.total += value
                    return self.total
            """;

    /**
     * Same program: every name changed, 2-space indent instead of 4, comments added and
     * removed, and the docstring's text rewritten.
     *
     * <p>The docstring stays at class scope. Moving it into a method would be a
     * structural change, not a cosmetic one, and the tokenizer is supposed to notice
     * that -- see {@link #nestingDepthChangesTheStream()}.
     */
    private static final String DISGUISED = """
            class Summer:
              \"\"\"Completely different docstring text.\"\"\"
              def __init__(self):
                self.acc = 0

              # A comment the original did not have.
              def absorb(self, items):
                for item in items:
                  if item % 2 == 0:
                    self.acc += item
                return self.acc
            """;

    @Test
    @DisplayName("Adversarial: renamed, re-indented 4->2 spaces, docstring and comments swapped")
    void renamingReindentingAndRecommentingAreInvisible() {
        assertSameStream(tokenizer, ORIGINAL, DISGUISED);
    }

    @Test
    @DisplayName("Indent width is structure, not content: 4 spaces, 2 spaces and a tab all agree")
    void indentWidthDoesNotMatterOnlyNesting() {
        String fourSpaces = "def f(x):\n    if x:\n        return 1\n    return 0\n";
        String twoSpaces = "def f(x):\n  if x:\n    return 1\n  return 0\n";
        String tabs = "def f(x):\n\tif x:\n\t\treturn 1\n\treturn 0\n";

        assertSameStream(tokenizer, fourSpaces, twoSpaces);
        assertSameStream(tokenizer, fourSpaces, tabs);
    }

    @Test
    @DisplayName("A tab advances to the next multiple of 8, so mixed tabs and spaces align correctly")
    void tabExpandsToTheNextTabStop() {
        // One tab and eight spaces are the SAME column under CPython's rule, so these
        // two bodies are siblings, not nested. Counting a tab as a single column would
        // silently nest the second line instead -- and because relative nesting stays
        // monotonic, only a file that MIXES tabs and spaces can expose the difference.
        String mixed = "if a:\n\tb\n        c\n";
        String allTabs = "if a:\n\tb\n\tc\n";

        assertSameStream(tokenizer, allTabs, mixed);
        assertEquals(1, signature(tokenizer.tokenize(mixed)).stream()
                .filter(t -> t.equals("INDENT:INDENT")).count(),
                "mixed tabs and spaces nested when they should have aligned: "
                        + signature(tokenizer.tokenize(mixed)));
    }

    @Test
    @DisplayName("Nesting depth is NOT cosmetic: a differently nested body is a different stream")
    void nestingDepthChangesTheStream() {
        // Same tokens, different block structure. Without INDENT/DEDENT these would be
        // indistinguishable -- which is the entire reason Python needs them.
        assertDifferentStream(tokenizer,
                "def f(x):\n    if x:\n        g()\n    h()\n",
                "def f(x):\n    if x:\n        g()\n        h()\n");
    }

    @Test
    void differentLogicProducesADifferentStream() {
        assertDifferentStream(tokenizer,
                "def f(n):\n    s = 0\n    for i in range(n):\n        s += i\n    return s\n",
                "def f(n):\n    s = 0\n    for i in range(n):\n        s -= i\n    return s\n");
    }

    @Test
    void keywordsAreNotGeneralizedAway() {
        assertDifferentStream(tokenizer,
                "def f():\n    if x:\n        g()\n",
                "def f():\n    while x:\n        g()\n");
    }

    @Test
    @DisplayName("self and cls survive generalisation because they are structure, not names")
    void selfAndClsArePreserved() {
        List<String> stream = signature(tokenizer.tokenize("def m(self, other):\n    return self.x + other.y\n"));
        assertTrue(stream.contains("IDENTIFIER:self"), stream.toString());
        assertTrue(stream.contains("IDENTIFIER:ID"), stream.toString());
        assertTrue(!stream.contains("IDENTIFIER:other"), "user names must be generalised: " + stream);

        assertTrue(signature(tokenizer.tokenize("def m(cls):\n    return cls\n")).contains("IDENTIFIER:cls"));
    }

    @Test
    @DisplayName("A method and a free function with the same body are not the same stream")
    void preservingSelfDistinguishesMethodsFromFunctions() {
        assertDifferentStream(tokenizer,
                "def f(self, a):\n    return self.x\n",
                "def f(obj, a):\n    return obj.x\n");
    }

    @Test
    @DisplayName("A # inside a string is data, not a comment")
    void commentStrippingRespectsStrings() {
        assertEquals(List.of("IDENTIFIER:ID", "OPERATOR:=", "LITERAL:STR", "PUNCTUATION:NEWLINE"),
                signature(tokenizer.tokenize("colour = \"#ff0000\"\n")));
    }

    @Test
    void tripleQuotedStringsAreOneLiteralAndDoNotBreakLineTracking() {
        List<String> stream = signature(tokenizer.tokenize("x = \"\"\"a\nb\nc\"\"\"\ny = 1\n"));
        assertEquals(List.of("IDENTIFIER:ID", "OPERATOR:=", "LITERAL:STR", "PUNCTUATION:NEWLINE",
                        "IDENTIFIER:ID", "OPERATOR:=", "LITERAL:NUM", "PUNCTUATION:NEWLINE"),
                stream);
    }

    @Test
    @DisplayName("A # inside a triple-quoted string is not a comment either")
    void tripleQuotedStringsHideHashes() {
        List<String> stream = signature(tokenizer.tokenize("x = \"\"\"not # a comment\"\"\"\ny = 1\n"));
        assertTrue(stream.contains("LITERAL:NUM"), "the rest of the file was eaten: " + stream);
    }

    @Test
    @DisplayName("Newlines inside brackets continue the statement and must not emit INDENT")
    void bracketContinuationDoesNotTriggerIndentation() {
        String oneLine = "f(a, b, c)\n";
        String wrapped = "f(a,\n  b,\n  c)\n";
        assertSameStream(tokenizer, oneLine, wrapped);
        assertTrue(signature(tokenizer.tokenize(wrapped)).stream().noneMatch(s -> s.startsWith("INDENT")),
                "a wrapped call produced a spurious INDENT");
    }

    @Test
    void backslashContinuationDoesNotTriggerIndentation() {
        assertSameStream(tokenizer, "x = a + b\n", "x = a + \\\n    b\n");
    }

    @Test
    void blankAndCommentOnlyLinesCarryNoIndentation() {
        assertSameStream(tokenizer,
                "def f():\n    return 1\n",
                "def f():\n\n        # stray comment at a weird column\n\n    return 1\n");
    }

    @Test
    @DisplayName("Every block open at EOF is closed")
    void danglingBlocksAreClosedAtEndOfFile() {
        List<String> stream = signature(tokenizer.tokenize("def f():\n    if x:\n        g()\n"));
        long dedents = stream.stream().filter(s -> s.equals("DEDENT:DEDENT")).count();
        long indents = stream.stream().filter(s -> s.equals("INDENT:INDENT")).count();
        assertEquals(indents, dedents, "unbalanced INDENT/DEDENT: " + stream);
        assertEquals(2, indents, stream.toString());
    }

    @Test
    @DisplayName("applyIndentation drives the stack directly")
    void applyIndentationEmitsAndPopsCorrectly() {
        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(0);

        assertEquals(List.of(), kinds(tokenizer.applyIndentation(0, stack, 1)));
        assertEquals(List.of("INDENT"), kinds(tokenizer.applyIndentation(4, stack, 2)));
        assertEquals(List.of("INDENT"), kinds(tokenizer.applyIndentation(8, stack, 3)));
        assertEquals(List.of(), kinds(tokenizer.applyIndentation(8, stack, 4)));
        assertEquals(List.of("DEDENT", "DEDENT"), kinds(tokenizer.applyIndentation(0, stack, 5)));
        assertEquals(List.of(0), List.copyOf(stack));
    }

    @Test
    @DisplayName("An inconsistent dedent is tolerated, because submissions need not compile")
    void inconsistentDedentDoesNotThrow() {
        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(0);
        tokenizer.applyIndentation(8, stack, 1);
        // Close to column 3, which was never opened: align to the nearest enclosing level.
        assertEquals(List.of("DEDENT"), kinds(tokenizer.applyIndentation(3, stack, 2)));
    }

    @Test
    void stringPrefixesBelongToTheirLiteral() {
        assertEquals(List.of("IDENTIFIER:ID", "OPERATOR:=", "LITERAL:STR", "PUNCTUATION:NEWLINE"),
                signature(tokenizer.tokenize("x = f\"value {y}\"\n")));
        assertEquals(List.of("IDENTIFIER:ID", "OPERATOR:=", "LITERAL:STR", "PUNCTUATION:NEWLINE"),
                signature(tokenizer.tokenize("x = rb'raw bytes'\n")));
    }

    @Test
    void literalContentIsGeneralized() {
        assertSameStream(tokenizer, "print(\"hello\")\n", "print('goodbye cruel world')\n");
    }

    private static List<String> kinds(List<Token> tokens) {
        return tokens.stream().map(t -> t.getKind().toString()).toList();
    }
}
