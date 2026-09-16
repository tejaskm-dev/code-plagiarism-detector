package com.integrityengine.ai;

import com.integrityengine.domain.CodeSubmission;
import com.integrityengine.domain.Token;
import com.integrityengine.domain.TokenKind;
import com.integrityengine.tokenizer.ITokenizer;
import com.integrityengine.tokenizer.TokenizerFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Local, offline authorship signal. No API key, no network, fully deterministic.
 *
 * <p>Works for all five languages off the token streams the tokenizers already
 * produce — there is no second parser here. Comment density comes from
 * {@link ITokenizer#withoutComments}, naming style from
 * {@link ITokenizer#lexicalTokens}, and nesting from the normalised stream's braces
 * and INDENT/DEDENT tokens.
 *
 * <p><b>What this score is and is not.</b> It measures how mechanically regular a file
 * is: uniform indentation, one consistent naming convention, even nesting, steady
 * commenting. Machine-generated code tends to score high. So does a careful student
 * using an auto-formatter and a linter, which is most of them. The weights below are a
 * judgement call, not a calibrated model — there is no labelled corpus behind them, and
 * calling this an "AI detector" on its own would be dishonest. Treat it as one input
 * to a human review, never as evidence.
 */
final class StylometricExtractor {

    /** Below this many tokens a file has too little structure to profile meaningfully. */
    static final int MINIMUM_TOKENS = 40;

    // Unvalidated weights. Formatting and naming carry the most weight because they are
    // the most mechanical; comment density carries the least because a heavily
    // commented file is just as likely to be a conscientious student.
    static final double WEIGHT_FORMATTING = 0.30;
    static final double WEIGHT_IDENTIFIERS = 0.30;
    static final double WEIGHT_COMMENTS = 0.25;
    static final double WEIGHT_NESTING = 0.15;

    private final TokenizerFactory factory;

    StylometricExtractor() {
        this(new TokenizerFactory());
    }

    StylometricExtractor(TokenizerFactory factory) {
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    /**
     * Build the full feature profile for a submission.
     *
     * @return the profile, or empty if the language is unrecognised
     */
    Optional<StyleProfile> profile(CodeSubmission submission) {
        Objects.requireNonNull(submission, "submission");

        Optional<ITokenizer> tokenizer = factory.forFilename(submission.getFilename());
        if (tokenizer.isEmpty()) {
            return Optional.empty();
        }

        ITokenizer lexer = tokenizer.get();
        String raw = submission.getSourceCode();
        String stripped = lexer.withoutComments(raw);

        List<Token> normalised = lexer.tokenize(raw);
        List<Token> lexical = lexer.lexicalTokens(raw);

        NestingStats nesting = measureNesting(normalised);
        int identifierCount = 0;
        for (Token token : lexical) {
            if (token.getKind() == TokenKind.IDENTIFIER) {
                identifierCount++;
            }
        }

        return Optional.of(new StyleProfile(
                commentDensity(raw, stripped),
                formattingRegularity(stripped),
                identifierConsistency(lexical),
                nesting.regularity,
                nesting.meanDepth,
                nesting.maxDepth,
                normalised.size(),
                identifierCount,
                countCodeLines(stripped)));
    }

    /**
     * Collapse the profile into a single regularity score in [0.0, 1.0].
     *
     * @return 0.0 for an unrecognised language or a file too small to profile
     */
    double computeAnomalyScore(CodeSubmission submission) {
        Optional<StyleProfile> profile = profile(submission);
        if (profile.isEmpty() || profile.get().getTokenCount() < MINIMUM_TOKENS) {
            // Silence rather than a guess: a five-line file is regular by construction
            // and would otherwise score near the top.
            return 0.0;
        }

        StyleProfile style = profile.get();
        double score = WEIGHT_FORMATTING * style.getFormattingRegularity()
                + WEIGHT_IDENTIFIERS * style.getIdentifierConsistency()
                + WEIGHT_COMMENTS * style.getCommentDensity()
                + WEIGHT_NESTING * style.getNestingRegularity();

        return clamp(score);
    }

    // ------------------------------------------------------------------- features

    /**
     * Comment characters over all visible characters.
     *
     * <p>Relies on {@code withoutComments} blanking comments in place rather than
     * deleting them, so the two strings line up index for index. The length guard below
     * keeps a future change to that behaviour from silently producing nonsense.
     */
    private static double commentDensity(String raw, String stripped) {
        int limit = Math.min(raw.length(), stripped.length());
        int commentChars = 0;
        int codeChars = 0;

        for (int i = 0; i < limit; i++) {
            boolean visibleInRaw = !Character.isWhitespace(raw.charAt(i));
            boolean visibleInStripped = !Character.isWhitespace(stripped.charAt(i));
            if (visibleInRaw && !visibleInStripped) {
                commentChars++;
            } else if (visibleInStripped) {
                codeChars++;
            }
        }

        int total = commentChars + codeChars;
        return total == 0 ? 0.0 : (double) commentChars / total;
    }

    /**
     * 1 minus the normalised Shannon entropy of the indentation-width distribution.
     *
     * <p>Machine-generated code indents on one unit consistently, producing few distinct
     * widths and low entropy. Hand-written code mixes tabs, spaces and ad-hoc alignment.
     */
    private static double formattingRegularity(String strippedSource) {
        Map<Integer, Integer> widths = new HashMap<>();
        int lines = 0;

        for (String line : strippedSource.split("\n", -1)) {
            if (line.isBlank()) {
                continue;
            }
            int width = 0;
            for (int i = 0; i < line.length() && Character.isWhitespace(line.charAt(i)); i++) {
                width += line.charAt(i) == '\t' ? 8 : 1;
            }
            widths.merge(width, 1, Integer::sum);
            lines++;
        }

        return 1.0 - normalisedEntropy(widths.values(), lines);
    }

    /** Share of identifiers written in the file's single most common naming convention. */
    private static double identifierConsistency(List<Token> lexicalTokens) {
        Map<NamingConvention, Integer> counts = new HashMap<>();
        int total = 0;

        for (Token token : lexicalTokens) {
            if (token.getKind() != TokenKind.IDENTIFIER) {
                continue;
            }
            counts.merge(NamingConvention.of(token.getValue()), 1, Integer::sum);
            total++;
        }

        if (total == 0) {
            // No names to be consistent about. Report 0 rather than a vacuous 1.0, so
            // an absence of evidence cannot push a file toward "machine-written".
            return 0.0;
        }
        int dominant = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return (double) dominant / total;
    }

    /** Walks braces and INDENT/DEDENT to get depth without re-parsing anything. */
    private static NestingStats measureNesting(List<Token> tokens) {
        Map<Integer, Integer> histogram = new HashMap<>();
        int depth = 0;
        int max = 0;
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
            max = Math.max(max, depth);
        }

        double mean = tokens.isEmpty() ? 0.0 : (double) sum / tokens.size();
        double regularity = 1.0 - normalisedEntropy(histogram.values(), tokens.size());
        return new NestingStats(regularity, mean, max);
    }

    // ------------------------------------------------------------------- helpers

    /**
     * Shannon entropy of a distribution, normalised to [0, 1] against log2(n).
     *
     * <p>log2(n) is the maximum entropy achievable with n observations (every one
     * distinct), so a single repeated value gives 0 and maximal variety gives 1.
     */
    private static double normalisedEntropy(Iterable<Integer> counts, int total) {
        if (total <= 1) {
            return 0.0;
        }

        double entropy = 0.0;
        for (int count : counts) {
            if (count <= 0) {
                continue;
            }
            double probability = (double) count / total;
            entropy -= probability * (Math.log(probability) / Math.log(2));
        }

        double maximum = Math.log(total) / Math.log(2);
        return maximum <= 0 ? 0.0 : clamp(entropy / maximum);
    }

    private static int countCodeLines(String strippedSource) {
        int lines = 0;
        for (String line : strippedSource.split("\n", -1)) {
            if (!line.isBlank()) {
                lines++;
            }
        }
        return lines;
    }

    private static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static final class NestingStats {
        final double regularity;
        final double meanDepth;
        final int maxDepth;

        NestingStats(double regularity, double meanDepth, int maxDepth) {
            this.regularity = regularity;
            this.meanDepth = meanDepth;
            this.maxDepth = maxDepth;
        }
    }

    /** The naming conventions a student or a model might use. */
    enum NamingConvention {
        SCREAMING_SNAKE, SNAKE_CASE, CAMEL_CASE, PASCAL_CASE, FLAT_LOWER, OTHER;

        static NamingConvention of(String identifier) {
            if (identifier.isEmpty()) {
                return OTHER;
            }
            boolean hasUnderscore = identifier.indexOf('_') >= 0;
            boolean hasUpper = identifier.chars().anyMatch(Character::isUpperCase);
            boolean hasLower = identifier.chars().anyMatch(Character::isLowerCase);
            boolean startsUpper = Character.isUpperCase(identifier.charAt(0));

            if (hasUpper && !hasLower) {
                return SCREAMING_SNAKE;
            }
            if (hasUnderscore && !hasUpper) {
                return SNAKE_CASE;
            }
            if (hasUnderscore) {
                return OTHER;
            }
            if (startsUpper && hasLower) {
                return PASCAL_CASE;
            }
            if (hasUpper) {
                return CAMEL_CASE;
            }
            return hasLower ? FLAT_LOWER : OTHER;
        }
    }
}
