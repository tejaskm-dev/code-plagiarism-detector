package com.integrityengine.ai;

/**
 * The four stylometric features plus the raw counts behind them. Immutable.
 *
 * <p>Every feature is normalised to [0.0, 1.0] and oriented so that <b>higher means
 * more mechanically regular</b> — the property machine-generated code tends to exhibit
 * and hand-written student code tends not to.
 */
final class StyleProfile {

    private final double commentDensity;
    private final double formattingRegularity;
    private final double identifierConsistency;
    private final double nestingRegularity;
    private final double meanNestingDepth;
    private final int maxNestingDepth;
    private final int tokenCount;
    private final int identifierCount;
    private final int codeLineCount;

    StyleProfile(double commentDensity,
                 double formattingRegularity,
                 double identifierConsistency,
                 double nestingRegularity,
                 double meanNestingDepth,
                 int maxNestingDepth,
                 int tokenCount,
                 int identifierCount,
                 int codeLineCount) {
        this.commentDensity = commentDensity;
        this.formattingRegularity = formattingRegularity;
        this.identifierConsistency = identifierConsistency;
        this.nestingRegularity = nestingRegularity;
        this.meanNestingDepth = meanNestingDepth;
        this.maxNestingDepth = maxNestingDepth;
        this.tokenCount = tokenCount;
        this.identifierCount = identifierCount;
        this.codeLineCount = codeLineCount;
    }

    /** Comment characters as a fraction of all non-whitespace characters. */
    double getCommentDensity() {
        return commentDensity;
    }

    /** 1 minus the normalised entropy of the indentation-width distribution. */
    double getFormattingRegularity() {
        return formattingRegularity;
    }

    /** Share of identifiers following the file's single most common naming convention. */
    double getIdentifierConsistency() {
        return identifierConsistency;
    }

    /** 1 minus the normalised entropy of the per-token nesting-depth distribution. */
    double getNestingRegularity() {
        return nestingRegularity;
    }

    double getMeanNestingDepth() {
        return meanNestingDepth;
    }

    int getMaxNestingDepth() {
        return maxNestingDepth;
    }

    int getTokenCount() {
        return tokenCount;
    }

    int getIdentifierCount() {
        return identifierCount;
    }

    int getCodeLineCount() {
        return codeLineCount;
    }

    @Override
    public String toString() {
        return "StyleProfile[comments=" + commentDensity
                + " formatting=" + formattingRegularity
                + " identifiers=" + identifierConsistency
                + " nesting=" + nestingRegularity
                + " tokens=" + tokenCount + "]";
    }
}
