package com.integrityengine.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.integrityengine.domain.CodeSubmission;
import com.integrityengine.tokenizer.ITokenizer;
import com.integrityengine.tokenizer.Language;
import com.integrityengine.tokenizer.TokenizerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class StylometricExtractorTest {

    private static final double EPSILON = 1e-9;

    private final StylometricExtractor extractor = new StylometricExtractor();

    private static CodeSubmission submission(String filename, String source) {
        return new CodeSubmission("s1", "student1", "assignment1", filename, source);
    }

    private StyleProfile profileOf(String filename, String source) {
        return extractor.profile(submission(filename, source)).orElseThrow();
    }

    // ------------------------------------------------------------- comment density

    @Test
    @DisplayName("Comment density is hand-checkable: 8 comment chars over 8 + 7 code chars")
    void commentDensityIsExact() {
        // "//abcdef" is 8 comment characters. "int x=1;" is 7 *visible* characters --
        // the space is whitespace and counts toward neither side.
        StyleProfile profile = profileOf("A.java", "//abcdef\nint x=1;\n");

        assertEquals(8.0 / 15.0, profile.getCommentDensity(), EPSILON);
    }

    @Test
    void aFileWithNoCommentsHasZeroDensity() {
        assertEquals(0.0, profileOf("A.java", "int x = 1;\nint y = 2;\n").getCommentDensity(), EPSILON);
    }

    @Test
    @DisplayName("A // inside a string is code, not a comment -- inherited from stage 2")
    void commentDensityRespectsStringLiterals() {
        StyleProfile profile = profileOf("A.java", "String u = \"http://example.com\";\n");
        assertEquals(0.0, profile.getCommentDensity(), EPSILON);
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    @DisplayName("withoutComments preserves length in every language -- comment density depends on it")
    void commentStrippingPreservesLengthInEveryLanguage(Language language) {
        // Comment density lines the raw and stripped strings up index for index. If a
        // tokenizer ever deleted comments instead of blanking them, the metric would
        // silently produce nonsense rather than fail.
        ITokenizer tokenizer = new TokenizerFactory().forLanguage(language);
        String source = language == Language.PYTHON
                ? "x = 1  # a comment\ny = 2\n# whole line\n"
                : "int x = 1; // a comment\n/* block\n   comment */\nint y = 2;\n";

        assertEquals(source.length(), tokenizer.withoutComments(source).length(),
                language + " changed the source length while stripping comments");
    }

    // -------------------------------------------------------- formatting regularity

    @Test
    @DisplayName("Perfectly uniform indentation is maximally regular")
    void uniformIndentationScoresOne() {
        String uniform = "int a = 1;\nint b = 2;\nint c = 3;\nint d = 4;\n";
        assertEquals(1.0, profileOf("A.java", uniform).getFormattingRegularity(), EPSILON);
    }

    @Test
    @DisplayName("Every line at a different indent is maximally irregular")
    void everyLineAtADifferentIndentScoresZero() {
        String ragged = "int a=1;\n int b=2;\n  int c=3;\n   int d=4;\n";
        assertEquals(0.0, profileOf("A.java", ragged).getFormattingRegularity(), EPSILON);
    }

    @Test
    void mixedIndentationLandsBetween() {
        String mixed = "int a=1;\n    int b=2;\n    int c=3;\n        int d=4;\n";
        double regularity = profileOf("A.java", mixed).getFormattingRegularity();

        assertTrue(regularity > 0.0 && regularity < 1.0, "expected an interior value, got " + regularity);
    }

    // ------------------------------------------------------ identifier consistency

    @Test
    void allIdentifiersInOneConventionScoreOne() {
        assertEquals(1.0,
                profileOf("A.java", "int totalCount = itemCount + otherCount;").getIdentifierConsistency(),
                EPSILON);
    }

    @Test
    @DisplayName("An even split between two conventions scores 0.5")
    void aFiftyFiftySplitScoresAHalf() {
        // two camelCase, two snake_case
        double consistency = profileOf("A.java", "int totalCount = item_count + otherCount + other_count;")
                .getIdentifierConsistency();
        assertEquals(0.5, consistency, EPSILON);
    }

    @Test
    @DisplayName("No identifiers reports 0.0, so absence of evidence cannot imply machine-written")
    void noIdentifiersScoresZeroRatherThanVacuouslyConsistent() {
        assertEquals(0.0, profileOf("A.java", "return 1 + 2;").getIdentifierConsistency(), EPSILON);
    }

    @ParameterizedTest
    @CsvSource({
            "totalCount, CAMEL_CASE", "total_count, SNAKE_CASE", "TOTAL_COUNT, SCREAMING_SNAKE",
            "TotalCount, PASCAL_CASE", "total, FLAT_LOWER", "MAX, SCREAMING_SNAKE",
            "Total_Count, OTHER", "_x, SNAKE_CASE"})
    void namingConventionsAreClassified(String identifier, String expected) {
        assertEquals(StylometricExtractor.NamingConvention.valueOf(expected),
                StylometricExtractor.NamingConvention.of(identifier));
    }

    // ----------------------------------------------------------------- nesting

    @Test
    @DisplayName("Brace nesting is tracked for C-family languages")
    void braceNestingIsMeasured() {
        StyleProfile flat = profileOf("A.java", "int a = 1;");
        StyleProfile nested = profileOf("A.java", "class A { void f() { if (x) { g(); } } }");

        assertEquals(0, flat.getMaxNestingDepth());
        assertEquals(3, nested.getMaxNestingDepth());
        assertTrue(nested.getMeanNestingDepth() > flat.getMeanNestingDepth());
    }

    @Test
    @DisplayName("INDENT/DEDENT nesting is tracked for Python, with no separate parser")
    void pythonNestingIsMeasuredFromIndentTokens() {
        StyleProfile nested = profileOf("a.py", "def f(x):\n    if x:\n        return 1\n    return 0\n");

        assertEquals(2, nested.getMaxNestingDepth());
        assertTrue(nested.getMeanNestingDepth() > 0.0);
    }

    // ------------------------------------------------------------ the composite

    @Test
    @DisplayName("The score is exactly the documented weighted sum of the four features")
    void scoreIsTheWeightedSumOfItsParts() {
        CodeSubmission submission = submission("A.java", tidySource());
        StyleProfile profile = extractor.profile(submission).orElseThrow();

        double expected = StylometricExtractor.WEIGHT_FORMATTING * profile.getFormattingRegularity()
                + StylometricExtractor.WEIGHT_IDENTIFIERS * profile.getIdentifierConsistency()
                + StylometricExtractor.WEIGHT_COMMENTS * profile.getCommentDensity()
                + StylometricExtractor.WEIGHT_NESTING * profile.getNestingRegularity();

        assertEquals(expected, extractor.computeAnomalyScore(submission), EPSILON);
    }

    @Test
    @DisplayName("Weights sum to 1, so the score cannot exceed its own bounds")
    void weightsSumToOne() {
        assertEquals(1.0, StylometricExtractor.WEIGHT_FORMATTING + StylometricExtractor.WEIGHT_IDENTIFIERS
                + StylometricExtractor.WEIGHT_COMMENTS + StylometricExtractor.WEIGHT_NESTING, EPSILON);
    }

    @Test
    @DisplayName("Uniform, heavily commented, consistently named code scores above ragged code")
    void tidyCodeScoresHigherThanRaggedCode() {
        double tidy = extractor.computeAnomalyScore(submission("A.java", tidySource()));
        double ragged = extractor.computeAnomalyScore(submission("B.java", raggedSource()));

        assertTrue(tidy > ragged, "tidy=" + tidy + " ragged=" + ragged);
        // Deliberately a weak claim. The separation this heuristic achieves is modest,
        // and asserting a wide margin would overstate what it can actually do.
        assertTrue(tidy - ragged > 0.05, "separation was only " + (tidy - ragged));
    }

    @Test
    void scoreIsAlwaysWithinBounds() {
        for (String source : new String[] {tidySource(), raggedSource(), "", "int x;", "//\n//\n//\n"}) {
            double score = extractor.computeAnomalyScore(submission("A.java", source));
            assertTrue(score >= 0.0 && score <= 1.0, "score out of bounds: " + score);
        }
    }

    @Test
    @DisplayName("Tiny files score 0.0: they are regular by construction, not by authorship")
    void filesBelowTheMinimumTokenCountScoreZero() {
        assertEquals(0.0, extractor.computeAnomalyScore(submission("A.java", "int x = 1;")), EPSILON);
        assertTrue(profileOf("A.java", "int x = 1;").getTokenCount() < StylometricExtractor.MINIMUM_TOKENS);
    }

    @Test
    void unrecognisedLanguagesYieldNoProfileAndNoScore() {
        assertTrue(extractor.profile(submission("notes.txt", tidySource())).isEmpty());
        assertEquals(0.0, extractor.computeAnomalyScore(submission("notes.txt", tidySource())), EPSILON);
    }

    @ParameterizedTest
    @CsvSource({"A.java", "a.c", "a.cpp", "a.py", "a.js"})
    @DisplayName("All five languages profile without a second parsing path")
    void everyLanguageProducesAProfile(String filename) {
        String source = filename.endsWith(".py")
                ? "def compute(values):\n    total = 0\n    for value in values:\n        total += value\n    return total\n"
                : "int compute(int[] values){ int total = 0; for (int i=0;i<10;i++){ total += values[i]; } return total; }";

        StyleProfile profile = profileOf(filename, source);
        assertTrue(profile.getTokenCount() > 0, filename + " produced no tokens");
    }

    @Test
    void extractionIsDeterministic() {
        CodeSubmission submission = submission("A.java", tidySource());
        assertEquals(extractor.computeAnomalyScore(submission), extractor.computeAnomalyScore(submission), EPSILON);
    }

    @Test
    void nullsAreRejected() {
        assertThrows(NullPointerException.class, () -> extractor.profile(null));
        assertThrows(NullPointerException.class, () -> new StylometricExtractor(null));
    }

    // ------------------------------------------------------------------ fixtures

    private static String tidySource() {
        return """
                public class OrderProcessor {
                    private final List<Order> orders;

                    /**
                     * Creates a new order processor.
                     */
                    public OrderProcessor(List<Order> orders) {
                        this.orders = orders;
                    }

                    /**
                     * Calculates the total value of all orders.
                     */
                    public double calculateTotal() {
                        double total = 0.0;
                        for (Order order : orders) {
                            total += order.getAmount();
                        }
                        return total;
                    }
                }
                """;
    }

    private static String raggedSource() {
        return """
                public class stuff {
                  int[] MyData; int total_count;
                  public stuff(int[] d){ MyData=d;
                      total_count=0; }
                  public int doIt(){
                  int X=0;
                        for(int i=0;i<MyData.length;i++){
                    if(MyData[i]>0){ X+=MyData[i]; total_count++; }
                          }
                    return X; }
                    public int Get_count(){ return total_count; }
                }
                """;
    }
}
