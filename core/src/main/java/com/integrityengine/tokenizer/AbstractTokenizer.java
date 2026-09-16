package com.integrityengine.tokenizer;

import com.integrityengine.domain.Token;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Template-method base for all tokenizers.
 *
 * <p>{@link #tokenize(String)} is {@code final}: it fixes the four-stage normalisation
 * pipeline once, and subclasses supply the stages. That ordering is the whole point —
 * comments must go before keywords are read, and identifiers/literals must be
 * generalised after the stream exists, or two files that differ only in variable names
 * would not fingerprint identically.
 *
 * <p>The four protected hooks are declared abstract here and are the only extension
 * points. Subclasses never override {@code tokenize} itself.
 */
abstract class AbstractTokenizer implements ITokenizer {

    /**
     * The template method. Fixed pipeline, subclass-supplied stages.
     */
    @Override
    public final List<Token> tokenize(String source) {
        Objects.requireNonNull(source, "source");
        String withoutComments = stripComments(source);
        List<Token> raw = extractKeywords(withoutComments);
        List<Token> identifiersGeneralised = generalizeIdentifiers(raw);
        return generalizeLiterals(identifiersGeneralised);
    }

    /**
     * Stages 1-2 only, stopping before generalisation. Same parse, earlier stage.
     */
    @Override
    public final List<Token> lexicalTokens(String source) {
        Objects.requireNonNull(source, "source");
        return extractKeywords(stripComments(source));
    }

    /** Stage 1 alone, exposed for comment-density measurement. */
    @Override
    public final String withoutComments(String source) {
        Objects.requireNonNull(source, "source");
        return stripComments(source);
    }

    /** Stage 1: remove comments so prose changes cannot mask a copy. */
    protected abstract String stripComments(String source);

    /** Stage 2: lex the cleaned source, tagging language keywords as {@code KEYWORD}. */
    protected abstract List<Token> extractKeywords(String source);

    /** Stage 3: collapse every identifier to a single placeholder token. */
    protected abstract List<Token> generalizeIdentifiers(List<Token> tokens);

    /** Stage 4: collapse every literal to a placeholder token of its kind. */
    protected abstract List<Token> generalizeLiterals(List<Token> tokens);

    /** The placeholder every user-chosen name collapses to. */
    protected static final String IDENTIFIER_PLACEHOLDER = "ID";

    /**
     * Shared mechanic behind {@link #generalizeIdentifiers(List)}: rewrite every
     * IDENTIFIER token to a single placeholder, so that renaming variables cannot
     * change the token stream.
     *
     * <p>Subclasses decide *what* to preserve, which is where the languages genuinely
     * differ — Python keeps {@code self}/{@code cls} because they carry method
     * structure rather than a user's choice of name.
     *
     * @param tokens    the stream to rewrite
     * @param preserved identifier spellings to leave untouched
     */
    protected final List<Token> generalizeIdentifiersExcept(List<Token> tokens, Set<String> preserved) {
        Objects.requireNonNull(tokens, "tokens");
        Objects.requireNonNull(preserved, "preserved");

        List<Token> out = new ArrayList<>(tokens.size());
        for (Token token : tokens) {
            if (token.getKind() == com.integrityengine.domain.TokenKind.IDENTIFIER
                    && !preserved.contains(token.getValue())) {
                out.add(new Token(IDENTIFIER_PLACEHOLDER, token.getKind(), token.getLine()));
            } else {
                out.add(token);
            }
        }
        return List.copyOf(out);
    }
}
