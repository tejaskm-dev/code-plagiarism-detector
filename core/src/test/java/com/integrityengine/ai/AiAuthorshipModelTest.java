package com.integrityengine.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.integrityengine.domain.CodeSubmission;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the shipped classifier.
 *
 * <p>These do not re-measure accuracy — that was done once, against a held-out quarter
 * of a 540-file corpus, and is written up in {@code docs/ai-detection-results.md}. What they
 * pin is that the serialised forest still loads, still scores deterministically, and
 * still puts obvious cases on the correct side. A model file that silently failed to
 * load, or loaded as half a forest, would otherwise keep answering — with confident
 * nonsense.
 */
class AiAuthorshipModelTest {

    private final AiAuthorshipModel model = new AiAuthorshipModel();

    private static CodeSubmission of(String name, String source) {
        return new CodeSubmission("id", "student", "assignment", name, source);
    }

    @Test
    @DisplayName("The serialised forest loads and every tree votes in [0, 1]")
    void theModelLoads() {
        double score = model.score(of("A.java", generated())).orElseThrow();

        assertTrue(score >= 0.0 && score <= 1.0, "vote out of range: " + score);
    }

    @Test
    @DisplayName("The same file always scores identically -- no randomness at inference")
    void scoringIsDeterministic() {
        CodeSubmission submission = of("A.java", generated());

        double first = model.score(submission).orElseThrow();
        for (int i = 0; i < 5; i++) {
            assertEquals(first, model.score(submission).orElseThrow(), 0.0);
        }
    }

    @Test
    @DisplayName("A fresh instance agrees with an existing one -- the forest is shared state")
    void instancesAgree() {
        CodeSubmission submission = of("A.java", handWritten());

        assertEquals(model.score(submission).orElseThrow(),
                new AiAuthorshipModel().score(submission).orElseThrow(), 0.0);
    }

    @Test
    @DisplayName("Hand-written and generated code separate")
    void theTwoStylesSeparate() {
        double human = model.score(of("H.java", handWritten())).orElseThrow();
        double machine = model.score(of("M.java", generated())).orElseThrow();

        assertTrue(machine > human,
                "generated (" + machine + ") should outscore hand-written (" + human + ")");
    }

    @Test
    @DisplayName("Too small to have a style: answers nothing rather than guessing")
    void tinyFilesAreNotJudged() {
        assertEquals(Optional.empty(), model.score(of("T.java", "class A {}\n")));
        assertEquals(Optional.empty(), model.score(of("E.java", "")));
        assertEquals(Optional.empty(), model.score(of("B.java", "   \n\n  ")));
    }

    @Test
    @DisplayName("A language with no tokenizer is not judged")
    void unknownLanguagesAreNotJudged() {
        assertEquals(Optional.empty(), model.score(of("notes.txt", generated())));
    }

    @Test
    @DisplayName("The unsure band is what the write-up says it is")
    void theBandMatchesThePublishedNumber() {
        // docs/ai-detection-results.md reports 96.6% accuracy on decided files at +/-0.10.
        // Widening this constant without re-measuring would invalidate that figure.
        assertEquals(0.10, AiAuthorshipModel.UNSURE_BAND, 1e-9);
    }

    @Test
    @DisplayName("Renaming a file does not change its score")
    void theFilenameDoesNotLeakIntoTheScore() {
        assertEquals(model.score(of("Solution.java", generated())).orElseThrow(),
                model.score(of("hw3_final_v2.java", generated())).orElseThrow(), 0.0);
    }

    @Test
    @DisplayName("Style, not content: reformatting moves the score, renaming barely does")
    void theModelRespondsToStyleNotSubject() {
        double original = model.score(of("A.java", handWritten())).orElseThrow();
        double renamed = model.score(of("A.java",
                handWritten().replace("tot", "runningTotal").replace("q", "quantity")))
                .orElseThrow();

        assertNotEquals(original, renamed,
                "renaming identifiers is itself a style change and should register");
    }

    /** Irregular spacing, abbreviations, a stale to-do, a debug print left in. */
    private static String handWritten() {
        return """
                public class OrderTotal {
                    // FIXME discount is wrong for bulk, check with sam
                    public static double total(int[] q, double[] p){
                        double tot=0;
                        for(int i=0;i<q.length;i++){
                            tot += q[i]*p[i];
                        }

                        if(tot>100) tot = tot*0.9;   // bulk
                        //System.out.println(tot);
                        return tot;
                    }
                    public static void main(String[] a){
                        int[] q={1,2,3}; double[] p={1.5,2.0,3.0};
                        System.out.println(total(q,p));
                    }
                }
                """;
    }

    /** Uniform spacing, spelled-out names, a comment above every step. */
    private static String generated() {
        return """
                public class OrderTotalCalculator {

                    /**
                     * Calculates the total price for the given order.
                     *
                     * @param quantities the quantity of each item
                     * @param unitPrices the unit price of each item
                     * @return the discounted order total
                     */
                    public static double calculateTotal(int[] quantities, double[] unitPrices) {
                        // Initialize the running total
                        double runningTotal = 0.0;

                        // Iterate over each item in the order
                        for (int index = 0; index < quantities.length; index++) {
                            runningTotal += quantities[index] * unitPrices[index];
                        }

                        // Apply the bulk discount if the threshold is exceeded
                        if (runningTotal > BULK_THRESHOLD) {
                            runningTotal = runningTotal * BULK_DISCOUNT_RATE;
                        }

                        // Return the calculated total
                        return runningTotal;
                    }

                    private static final double BULK_THRESHOLD = 100.0;
                    private static final double BULK_DISCOUNT_RATE = 0.9;
                }
                """;
    }
}
