package com.integrityengine.ai;

import com.integrityengine.domain.Token;
import com.integrityengine.domain.TokenKind;
import com.integrityengine.tokenizer.ITokenizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Measurable properties of source code that differ between human and generated authorship.
 *
 * <p>Twenty features in five families. Each one is here because it captures a habit
 * rather than a preference — something a person does without deciding to, and a language
 * model does not do at all (or does far too evenly).
 *
 * <ul>
 *   <li><b>Naming.</b> People abbreviate under load: {@code idx}, {@code tmp},
 *       {@code cnt}, {@code n}. Generated code writes the word out every time.</li>
 *   <li><b>Comments.</b> People leave TODOs, fragments, and code they commented out
 *       and never deleted. Generated code writes complete sentences and documents
 *       every declaration or none.</li>
 *   <li><b>Formatting.</b> Trailing whitespace, ragged line lengths and irregular blank
 *       lines are residue of editing. Generated output arrives formatter-clean.</li>
 *   <li><b>Structure.</b> Human functions vary wildly in length; generated ones cluster
 *       around a comfortable size.</li>
 *   <li><b>Residue.</b> Debug prints and unexplained constants are traces of the process
 *       of getting something working, which generated code never went through.</li>
 * </ul>
 *
 * <p>Every value is normalised to roughly [0, 1] so the model's coefficients are
 * comparable. Nothing here is a verdict on its own; the combination is in
 * {@link AiAuthorshipModel}.
 */
final class StyleFeatures {

    /** Order is the model's input order and must not be rearranged. */
    static final String[] NAMES = {
        "meanIdentifierLength", "singleCharIdentifierRate", "multiWordIdentifierRate",
        "namingConsistency", "abbreviationRate",
        "commentDensity", "docCommentCoverage", "sentenceCommentRate",
        "todoMarkerRate", "commentedOutCodeRate",
        "indentationConsistency", "lineLengthVariation", "trailingWhitespaceRate",
        "blankLineRegularity", "longLineRate",
        "functionLengthVariation", "nestingRegularity", "meanNestingDepth",
        "magicNumberRate", "debugPrintRate",
        "commentRestatesCode", "identifierVocabularyRichness",
        "stringLiteralSentenceRate", "inlineCommentRate",
    };

    /**
     * Identifiers that are words with the vowels squeezed out, or conventional short
     * forms. Writing {@code cnt} instead of {@code count} is a keystroke decision, and
     * it is one of the strongest single tells in the whole set.
     */
    private static final Set<String> ABBREVIATIONS = Set.of(
        "idx", "tmp", "temp", "cnt", "num", "val", "buf", "len", "str", "arr", "msg",
        "err", "ctx", "cfg", "req", "res", "obj", "ptr", "src", "dst", "dest", "pos",
        "prev", "curr", "cur", "nxt", "elem", "attr", "arg", "args", "params", "init",
        "calc", "conv", "fmt", "recv", "sz", "ct", "ch", "sb", "br", "bw", "fr", "fw");

    private static final Pattern TODO_MARKER =
        Pattern.compile("\\b(TODO|FIXME|XXX|HACK|NOTE|WIP|BUG)\\b");
    private static final Pattern DEBUG_PRINT =
        Pattern.compile("\\b(System\\.out\\.print|System\\.err\\.print|printf|console\\.log|print\\s*\\()");
    /** A comment that still looks like code: the classic thing people never delete. */
    private static final Pattern CODE_IN_COMMENT =
        Pattern.compile(".*[;{}]\\s*$|.*\\w+\\s*=\\s*\\w+.*|.*\\w+\\([^)]*\\)\\s*[;{]?\\s*$");
    private static final Pattern DOC_OPENER = Pattern.compile("^\\s*(/\\*\\*|///|#\\s|\"\"\").*");

    private StyleFeatures() {
    }

    /** @return the feature vector in {@link #NAMES} order */
    static double[] extract(String source, ITokenizer tokenizer) {
        String stripped = tokenizer.withoutComments(source);
        List<Token> lexical = tokenizer.lexicalTokens(source);
        List<Token> normalised = tokenizer.tokenize(source);
        String[] rawLines = source.split("\n", -1);

        List<String> identifiers = new ArrayList<>();
        for (Token token : lexical) {
            if (token.getKind() == TokenKind.IDENTIFIER) {
                identifiers.add(token.getValue());
            }
        }

        double[] f = new double[NAMES.length];
        int i = 0;

        // ---- naming -------------------------------------------------------------
        f[i++] = clamp(mean(identifiers.stream().mapToInt(String::length)) / 16.0);
        f[i++] = rate(identifiers, id -> id.length() <= 2);
        f[i++] = rate(identifiers, StyleFeatures::isMultiWord);
        f[i++] = namingConsistency(identifiers);
        f[i++] = rate(identifiers, id -> ABBREVIATIONS.contains(id.toLowerCase(Locale.ROOT)));

        // ---- comments -----------------------------------------------------------
        List<String> comments = commentLines(rawLines, stripped.split("\n", -1));
        f[i++] = commentDensity(source, stripped);
        f[i++] = docCoverage(rawLines, lexical);
        f[i++] = rate(comments, StyleFeatures::readsAsSentence);
        f[i++] = clamp(countMatches(source, TODO_MARKER) / Math.max(1.0, rawLines.length / 40.0));
        f[i++] = rate(comments, c -> CODE_IN_COMMENT.matcher(c.trim()).matches());

        // ---- formatting ---------------------------------------------------------
        f[i++] = indentationConsistency(stripped.split("\n", -1));
        f[i++] = lineLengthVariation(rawLines);
        f[i++] = rate(List.of(rawLines), line -> !line.isEmpty() && Character.isWhitespace(
                line.charAt(line.length() - 1)));
        f[i++] = blankLineRegularity(rawLines);
        f[i++] = rate(List.of(rawLines), line -> line.length() > 100);

        // ---- structure ----------------------------------------------------------
        f[i++] = functionLengthVariation(lexical);
        Nesting nesting = nesting(normalised);
        f[i++] = nesting.regularity;
        f[i++] = clamp(nesting.mean / 5.0);

        // ---- residue ------------------------------------------------------------
        f[i++] = magicNumberRate(lexical);
        f[i++] = clamp(countMatches(source, DEBUG_PRINT) / Math.max(1.0, rawLines.length / 30.0));

        // ---- restatement --------------------------------------------------------
        f[i++] = commentRestatesCode(rawLines, stripped.split("\n", -1));
        f[i++] = vocabularyRichness(identifiers);
        f[i++] = stringLiteralSentenceRate(lexical);
        f[i] = inlineCommentRate(rawLines, stripped.split("\n", -1));
        return f;
    }

    // ------------------------------------------------------------------ helpers

    private static boolean isMultiWord(String id) {
        if (id.contains("_") && id.length() > 3) {
            return true;
        }
        int humps = 0;
        for (int i = 1; i < id.length(); i++) {
            if (Character.isUpperCase(id.charAt(i)) && Character.isLowerCase(id.charAt(i - 1))) {
                humps++;
            }
        }
        return humps >= 1 && id.length() > 4;
    }

    private static double namingConsistency(List<String> identifiers) {
        if (identifiers.isEmpty()) {
            return 0.5;
        }
        Map<String, Integer> styles = new HashMap<>();
        for (String id : identifiers) {
            styles.merge(styleOf(id), 1, Integer::sum);
        }
        return styles.values().stream().mapToInt(Integer::intValue).max().orElse(0)
                / (double) identifiers.size();
    }

    private static String styleOf(String id) {
        boolean upper = id.chars().anyMatch(Character::isUpperCase);
        boolean lower = id.chars().anyMatch(Character::isLowerCase);
        if (id.contains("_")) {
            return upper && !lower ? "SCREAMING" : "snake";
        }
        if (!upper) {
            return "lower";
        }
        if (!lower) {
            return "SCREAMING";
        }
        return Character.isUpperCase(id.charAt(0)) ? "Pascal" : "camel";
    }

    /** Comment text, recovered by diffing raw source against the comment-stripped copy. */
    private static List<String> commentLines(String[] raw, String[] stripped) {
        List<String> comments = new ArrayList<>();
        for (int i = 0; i < raw.length && i < stripped.length; i++) {
            if (!raw[i].isBlank() && stripped[i].isBlank()) {
                comments.add(raw[i]);
            }
        }
        return comments;
    }

    private static double commentDensity(String raw, String stripped) {
        int comment = 0;
        int code = 0;
        int limit = Math.min(raw.length(), stripped.length());
        for (int i = 0; i < limit; i++) {
            boolean rawVisible = !Character.isWhitespace(raw.charAt(i));
            boolean strippedVisible = !Character.isWhitespace(stripped.charAt(i));
            if (rawVisible && !strippedVisible) {
                comment++;
            } else if (strippedVisible) {
                code++;
            }
        }
        return comment + code == 0 ? 0 : (double) comment / (comment + code);
    }

    /** Fraction of declarations that carry a documentation comment directly above. */
    private static double docCoverage(String[] rawLines, List<Token> lexical) {
        List<Integer> declarationLines = declarationLines(lexical);
        if (declarationLines.isEmpty()) {
            return 0;
        }
        int documented = 0;
        for (int line : declarationLines) {
            for (int back = line - 2; back >= Math.max(0, line - 4); back--) {
                if (back < rawLines.length && DOC_OPENER.matcher(rawLines[back]).matches()) {
                    documented++;
                    break;
                }
                if (back < rawLines.length && !rawLines[back].isBlank()
                        && !rawLines[back].trim().startsWith("*")) {
                    break;
                }
            }
        }
        return (double) documented / declarationLines.size();
    }

    private static List<Integer> declarationLines(List<Token> lexical) {
        List<Integer> lines = new ArrayList<>();
        for (int i = 1; i + 1 < lexical.size(); i++) {
            Token token = lexical.get(i);
            Token next = lexical.get(i + 1);
            Token previous = lexical.get(i - 1);
            if (token.getKind() == TokenKind.IDENTIFIER
                    && next.getKind() == TokenKind.PUNCTUATION && next.getValue().equals("(")
                    && (previous.getKind() == TokenKind.KEYWORD
                        || previous.getKind() == TokenKind.IDENTIFIER)) {
                lines.add(token.getLine());
            }
        }
        return lines;
    }

    private static boolean readsAsSentence(String comment) {
        String text = comment.replaceAll("^\\s*(//+|/\\*+|\\*+/?|#+)\\s*", "").trim();
        if (text.length() < 12) {
            return false;
        }
        return Character.isUpperCase(text.charAt(0))
                && (text.endsWith(".") || text.endsWith("?") || text.endsWith("!"));
    }

    private static double indentationConsistency(String[] lines) {
        Map<Integer, Integer> widths = new HashMap<>();
        int counted = 0;
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            int width = 0;
            for (int i = 0; i < line.length() && Character.isWhitespace(line.charAt(i)); i++) {
                width += line.charAt(i) == '\t' ? 8 : 1;
            }
            widths.merge(width, 1, Integer::sum);
            counted++;
        }
        return 1.0 - normalisedEntropy(widths.values(), counted);
    }

    /** Coefficient of variation of line length: how ragged the right margin is. */
    private static double lineLengthVariation(String[] lines) {
        List<Integer> lengths = new ArrayList<>();
        for (String line : lines) {
            if (!line.isBlank()) {
                lengths.add(line.length());
            }
        }
        if (lengths.size() < 2) {
            return 0;
        }
        double mean = mean(lengths.stream().mapToInt(Integer::intValue));
        if (mean == 0) {
            return 0;
        }
        double variance = lengths.stream().mapToDouble(l -> (l - mean) * (l - mean)).average().orElse(0);
        return clamp(Math.sqrt(variance) / mean);
    }

    private static double blankLineRegularity(String[] lines) {
        List<Integer> gaps = new ArrayList<>();
        int run = 0;
        for (String line : lines) {
            if (line.isBlank()) {
                run++;
            } else if (run > 0) {
                gaps.add(run);
                run = 0;
            }
        }
        if (gaps.isEmpty()) {
            return 1;
        }
        Map<Integer, Integer> counts = new HashMap<>();
        gaps.forEach(g -> counts.merge(g, 1, Integer::sum));
        return counts.values().stream().mapToInt(Integer::intValue).max().orElse(0)
                / (double) gaps.size();
    }

    /** How unevenly sized the functions are, measured between declarations. */
    private static double functionLengthVariation(List<Token> lexical) {
        List<Integer> starts = declarationLines(lexical);
        if (starts.size() < 3) {
            return 0.5;
        }
        List<Integer> lengths = new ArrayList<>();
        for (int i = 1; i < starts.size(); i++) {
            int span = starts.get(i) - starts.get(i - 1);
            if (span > 0) {
                lengths.add(span);
            }
        }
        if (lengths.size() < 2) {
            return 0.5;
        }
        double mean = mean(lengths.stream().mapToInt(Integer::intValue));
        if (mean == 0) {
            return 0.5;
        }
        double variance = lengths.stream().mapToDouble(l -> (l - mean) * (l - mean)).average().orElse(0);
        return clamp(Math.sqrt(variance) / mean);
    }

    private record Nesting(double regularity, double mean) {
    }

    private static Nesting nesting(List<Token> tokens) {
        Map<Integer, Integer> histogram = new HashMap<>();
        int depth = 0;
        long sum = 0;
        for (Token token : tokens) {
            if (token.getKind() == TokenKind.INDENT
                    || (token.getKind() == TokenKind.PUNCTUATION && token.getValue().equals("{"))) {
                depth++;
            } else if (token.getKind() == TokenKind.DEDENT
                    || (token.getKind() == TokenKind.PUNCTUATION && token.getValue().equals("}"))) {
                depth = Math.max(0, depth - 1);
            }
            histogram.merge(depth, 1, Integer::sum);
            sum += depth;
        }
        double mean = tokens.isEmpty() ? 0 : (double) sum / tokens.size();
        return new Nesting(1.0 - normalisedEntropy(histogram.values(), tokens.size()), mean);
    }

    /** Numeric literals other than the ones nobody names. */
    private static double magicNumberRate(List<Token> lexical) {
        int literals = 0;
        int magic = 0;
        for (Token token : lexical) {
            if (token.getKind() != TokenKind.LITERAL) {
                continue;
            }
            String value = token.getValue();
            if (value.isEmpty() || !Character.isDigit(value.charAt(0))) {
                continue;
            }
            literals++;
            if (!value.equals("0") && !value.equals("1") && !value.equals("2")) {
                magic++;
            }
        }
        return literals == 0 ? 0 : (double) magic / literals;
    }

    private static final Set<String> STOPWORDS = Set.of(
        "the", "a", "an", "of", "to", "for", "and", "or", "is", "are", "this", "that",
        "with", "from", "into", "in", "on", "at", "by", "we", "it", "if", "then", "be",
        "as", "not", "all", "each", "new", "its", "our", "will", "can", "has", "have");

    /**
     * Fraction of comments that merely restate the code directly beneath them.
     *
     * <p>{@code // increment the counter} above {@code counter++} adds nothing a reader
     * did not already have. People write comments to record intent the code cannot
     * express — why a bound is off by one, which bug a workaround is for. Generated code
     * narrates the mechanics line by line, so its comment words collide with the
     * identifiers immediately below far more often.
     *
     * <p>This is the one feature here that survives a change of genre: platform library
     * code and student homework are worlds apart in how much they comment, but neither
     * human writes them as narration.
     */
    private static double commentRestatesCode(String[] raw, String[] stripped) {
        int comments = 0;
        int restating = 0;
        for (int i = 0; i < raw.length && i < stripped.length; i++) {
            if (raw[i].isBlank() || !stripped[i].isBlank()) {
                continue;
            }
            Set<String> words = significantWords(raw[i]);
            if (words.isEmpty()) {
                continue;
            }
            comments++;
            Set<String> below = new HashSet<>();
            int found = 0;
            for (int j = i + 1; j < stripped.length && found < 2; j++) {
                if (stripped[j].isBlank()) {
                    continue;
                }
                found++;
                for (String part : stripped[j].split("[^A-Za-z]+")) {
                    below.addAll(splitIdentifier(part));
                }
            }
            for (String word : words) {
                if (below.contains(word)) {
                    restating++;
                    break;
                }
            }
        }
        return comments == 0 ? 0 : (double) restating / comments;
    }

    private static Set<String> significantWords(String comment) {
        Set<String> words = new HashSet<>();
        for (String part : comment.toLowerCase(Locale.ROOT).split("[^a-z]+")) {
            if (part.length() > 3 && !STOPWORDS.contains(part)) {
                words.add(part);
            }
        }
        return words;
    }

    /** Break {@code parseInputLine} or {@code parse_input_line} into its words. */
    private static Set<String> splitIdentifier(String identifier) {
        Set<String> words = new HashSet<>();
        for (String chunk : identifier.split("_")) {
            for (String word : chunk.split("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")) {
                if (word.length() > 3) {
                    words.add(word.toLowerCase(Locale.ROOT));
                }
            }
        }
        return words;
    }

    /** Distinct identifiers over total: how much of a working vocabulary the author kept. */
    private static double vocabularyRichness(List<String> identifiers) {
        if (identifiers.isEmpty()) {
            return 0;
        }
        return (double) new HashSet<>(identifiers).size() / identifiers.size();
    }

    /** String literals written as prose — the register of generated error messages. */
    private static double stringLiteralSentenceRate(List<Token> lexical) {
        int strings = 0;
        int prose = 0;
        for (Token token : lexical) {
            if (token.getKind() != TokenKind.LITERAL) {
                continue;
            }
            String value = token.getValue();
            if (value.length() < 3 || value.charAt(0) != '"') {
                continue;
            }
            strings++;
            String text = value.substring(1, value.length() - 1).trim();
            if (text.split("\\s+").length >= 4 && !text.isEmpty()
                    && Character.isUpperCase(text.charAt(0))) {
                prose++;
            }
        }
        return strings == 0 ? 0 : (double) prose / strings;
    }

    /** Comments buried inside a body rather than sitting above a declaration. */
    private static double inlineCommentRate(String[] raw, String[] stripped) {
        int comments = 0;
        int inline = 0;
        for (int i = 0; i < raw.length && i < stripped.length; i++) {
            if (raw[i].isBlank() || !stripped[i].isBlank()) {
                continue;
            }
            comments++;
            int indent = 0;
            while (indent < raw[i].length() && Character.isWhitespace(raw[i].charAt(indent))) {
                indent++;
            }
            if (indent >= 8) {
                inline++;
            }
        }
        return comments == 0 ? 0 : (double) inline / comments;
    }

    // ------------------------------------------------------------------ maths

    private static double mean(java.util.stream.IntStream values) {
        return values.average().orElse(0);
    }

    private static <T> double rate(List<T> items, java.util.function.Predicate<T> test) {
        if (items.isEmpty()) {
            return 0;
        }
        long hits = items.stream().filter(test).count();
        return (double) hits / items.size();
    }

    private static int countMatches(String text, Pattern pattern) {
        var matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static double normalisedEntropy(java.util.Collection<Integer> counts, int total) {
        if (total <= 1) {
            return 0;
        }
        double entropy = 0;
        for (int count : counts) {
            if (count <= 0) {
                continue;
            }
            double p = (double) count / total;
            entropy -= p * (Math.log(p) / Math.log(2));
        }
        double max = Math.log(total) / Math.log(2);
        return max <= 0 ? 0 : clamp(entropy / max);
    }

    private static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0;
        }
        return Math.max(0, Math.min(1, value));
    }
}
