package com.integrityengine.api;

import com.integrityengine.domain.Fingerprint;
import com.integrityengine.domain.Token;
import com.integrityengine.domain.TokenKind;
import com.integrityengine.fingerprint.WinnowingEngine;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a match into something a person can read.
 *
 * <p>A highlighted line tells a marker where to look but not what they are looking at.
 * This derives the two facts that actually answer "why was this pair flagged":
 *
 * <ul>
 *   <li><b>Which routines match which.</b> The enclosing declaration is recovered on
 *       both sides, so a finding reads "their {@code average()} is your {@code mean()}"
 *       rather than "lines 12-18 resemble lines 30-36".</li>
 *   <li><b>What was renamed.</b> Where the normalised streams agree but the original
 *       spellings differ, one identifier was swapped for another. Listing those
 *       substitutions is the clearest evidence of a disguised copy there is.</li>
 * </ul>
 *
 * <p>This works because {@code tokenize} and {@code lexicalTokens} run the same lexer
 * and differ only in the final generalisation passes, which rewrite tokens in place.
 * The two streams are therefore the same length and index-aligned — verified across
 * Java, Python and C — so position <i>i</i> in one is the same token as position
 * <i>i</i> in the other, before and after names were stripped.
 */
final class MatchEvidence {

    /** How far back to look for the declaration a match sits inside. */
    private static final int DECLARATION_LOOKBACK = 400;

    /** A pair of routines found to contain matching code. */
    record RoutinePair(String leftName, String rightName, int sharedFragments,
                       int leftStartLine, int rightStartLine) {
    }

    /** One identifier consistently swapped for another across the matched code. */
    record Rename(String from, String to, int occurrences) {
    }

    record Evidence(List<RoutinePair> routines, List<Rename> renames,
                    int renamedTokenCount, int identicalTokenCount) {
    }

    private MatchEvidence() {
    }

    /**
     * @param sharedHashes fingerprint hashes both files contain
     */
    static Evidence derive(List<Token> leftNormalised, List<Token> leftLexical,
                           List<Token> rightNormalised, List<Token> rightLexical,
                           Map<Long, Integer> leftPositions, Map<Long, Integer> rightPositions,
                           List<Fingerprint> sharedHashes) {

        int k = WinnowingEngine.DEFAULT_K_GRAM_SIZE;
        Map<String, int[]> renameCounts = new HashMap<>();
        Map<String, RoutineTally> routines = new LinkedHashMap<>();
        int renamed = 0;
        int identical = 0;

        for (Fingerprint fingerprint : sharedHashes) {
            Integer leftAt = leftPositions.get(fingerprint.getHash());
            Integer rightAt = rightPositions.get(fingerprint.getHash());
            if (leftAt == null || rightAt == null) {
                continue;
            }

            String leftRoutine = enclosingRoutine(leftLexical, leftAt);
            String rightRoutine = enclosingRoutine(rightLexical, rightAt);
            String key = leftRoutine + "\u0000" + rightRoutine;
            routines.computeIfAbsent(key, ignored -> new RoutineTally(
                    leftRoutine, rightRoutine,
                    leftLexical.get(leftAt).getLine(), rightLexical.get(rightAt).getLine())).count++;

            for (int j = 0; j < k; j++) {
                int li = leftAt + j;
                int ri = rightAt + j;
                if (li >= leftLexical.size() || ri >= rightLexical.size()) {
                    break;
                }
                Token leftToken = leftLexical.get(li);
                Token rightToken = rightLexical.get(ri);
                if (leftToken.getKind() != TokenKind.IDENTIFIER
                        || rightToken.getKind() != TokenKind.IDENTIFIER) {
                    continue;
                }
                if (leftToken.getValue().equals(rightToken.getValue())) {
                    identical++;
                } else {
                    renamed++;
                    renameCounts.computeIfAbsent(
                            leftToken.getValue() + "\u0000" + rightToken.getValue(),
                            ignored -> new int[1])[0]++;
                }
            }
        }

        List<Rename> renames = new ArrayList<>();
        renameCounts.forEach((key, count) -> {
            String[] parts = key.split("\u0000", -1);
            renames.add(new Rename(parts[0], parts[1], count[0]));
        });
        renames.sort(Comparator.comparingInt(Rename::occurrences).reversed());

        List<RoutinePair> pairs = new ArrayList<>();
        routines.values().forEach(t -> pairs.add(new RoutinePair(
                t.leftName, t.rightName, t.count, t.leftLine, t.rightLine)));
        pairs.sort(Comparator.comparingInt(RoutinePair::sharedFragments).reversed());

        return new Evidence(pairs, renames, renamed, identical);
    }

    /**
     * Names the routine a token sits inside.
     *
     * <p>Heuristic, and honestly so: it walks back for the nearest identifier that is
     * immediately followed by an opening parenthesis and preceded by a keyword or type,
     * which is what a declaration looks like in every language here — {@code def
     * average(}, {@code public double average(}, {@code int total(}. Code that does not
     * fit that shape reports "top level" rather than guessing.
     */
    static String enclosingRoutine(List<Token> lexical, int position) {
        int floor = Math.max(0, position - DECLARATION_LOOKBACK);
        for (int i = Math.min(position, lexical.size() - 2); i > floor; i--) {
            Token candidate = lexical.get(i);
            if (candidate.getKind() != TokenKind.IDENTIFIER) {
                continue;
            }
            Token next = lexical.get(i + 1);
            if (next.getKind() != TokenKind.PUNCTUATION || !next.getValue().equals("(")) {
                continue;
            }
            if (i == 0) {
                continue;
            }
            Token previous = lexical.get(i - 1);
            boolean looksDeclared = previous.getKind() == TokenKind.KEYWORD
                    || previous.getKind() == TokenKind.IDENTIFIER;
            if (looksDeclared) {
                return candidate.getValue();
            }
        }
        return "top level";
    }

    private static final class RoutineTally {
        final String leftName;
        final String rightName;
        final int leftLine;
        final int rightLine;
        int count;

        RoutineTally(String leftName, String rightName, int leftLine, int rightLine) {
            this.leftName = leftName;
            this.rightName = rightName;
            this.leftLine = leftLine;
            this.rightLine = rightLine;
        }
    }
}
