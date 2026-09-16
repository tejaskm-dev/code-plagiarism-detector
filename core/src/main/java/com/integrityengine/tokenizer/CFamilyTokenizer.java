package com.integrityengine.tokenizer;

import com.integrityengine.domain.Token;
import com.integrityengine.domain.TokenKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Shared behaviour for brace-and-semicolon languages (Java, C, C++, JavaScript).
 *
 * <p>Implements the two template stages that are genuinely identical across the family —
 * {@code //} and block comment stripping, and literal generalisation — plus a shared
 * lexer subclasses drive with their own keyword set. What stays abstract is exactly
 * what actually differs: the reserved words, and what each language preserves when
 * generalising identifiers.
 */
abstract class CFamilyTokenizer extends AbstractTokenizer {

    /** Preprocessor directives recognised when {@link #supportsPreprocessor()} is on. */
    private static final Set<String> PREPROCESSOR_DIRECTIVES = Set.of(
            "include", "define", "undef", "ifdef", "ifndef", "endif", "else", "elif",
            "if", "pragma", "error", "warning", "line", "import");

    /**
     * Multi-character operators, longest first so that maximal munch works by simple
     * prefix matching. The union across the family is safe: a sequence like {@code ===}
     * simply never occurs in valid C.
     */
    private static final List<String> OPERATORS = List.of(
            ">>>=", "<<=", ">>=", "===", "!==", ">>>", "...", "**=", "&&=", "||=", "??=",
            "==", "!=", "<=", ">=", "&&", "||", "++", "--", "+=", "-=", "*=", "/=", "%=",
            "&=", "|=", "^=", "<<", ">>", "->", "=>", "::", "??", "?.", "**",
            "+", "-", "*", "/", "%", "=", "<", ">", "!", "&", "|", "^", "~", "?", ":", ".", "#", "@");

    private static final Set<Character> PUNCTUATION = Set.of('(', ')', '[', ']', '{', '}', ';', ',');

    /** C and C++ have a preprocessor; Java and JavaScript do not. */
    protected boolean supportsPreprocessor() {
        return false;
    }

    /** Only JavaScript has backtick template literals. */
    protected boolean supportsTemplateLiterals() {
        return false;
    }

    // ------------------------------------------------------------ template stage 1

    /**
     * Remove {@code //} line comments and {@code /*} block comments.
     *
     * <p>String- and char-aware by necessity: the naive version deletes the {@code //}
     * inside {@code "http://x"} and silently corrupts every submission containing a URL.
     *
     * <p>Comments are blanked to spaces rather than deleted, and newlines inside block
     * comments are kept, so line numbers and column positions survive intact.
     */
    @Override
    protected final String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();

        while (i < n) {
            char c = source.charAt(i);

            if (c == '/' && i + 1 < n && source.charAt(i + 1) == '/') {
                while (i < n && source.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
            } else if (c == '/' && i + 1 < n && source.charAt(i + 1) == '*') {
                out.append("  ");
                i += 2;
                while (i < n && !(source.charAt(i) == '*' && i + 1 < n && source.charAt(i + 1) == '/')) {
                    out.append(source.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                if (i < n) {
                    out.append("  ");
                    i += 2;
                }
            } else if (c == '"' || c == '\'' || (supportsTemplateLiterals() && c == '`')) {
                i = copyQuoted(source, i, out);
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** Copy a quoted run verbatim, honouring backslash escapes. */
    private int copyQuoted(String source, int start, StringBuilder out) {
        char quote = source.charAt(start);
        int n = source.length();
        out.append(quote);
        int i = start + 1;

        while (i < n) {
            char c = source.charAt(i);
            if (c == '\\' && i + 1 < n) {
                out.append(c).append(source.charAt(i + 1));
                i += 2;
                continue;
            }
            out.append(c);
            i++;
            if (c == quote) {
                break;
            }
            // An unterminated '...' or "..." must not swallow the rest of the file.
            if (c == '\n' && quote != '`') {
                break;
            }
        }
        return i;
    }

    // ------------------------------------------------------------ template stage 4

    /**
     * Collapse literals to placeholders by class, keyed off the raw text the lexer kept.
     * Distinguishing NUM from STR from CHR preserves a little structure without
     * preserving any of the student's actual values.
     */
    @Override
    protected final List<Token> generalizeLiterals(List<Token> tokens) {
        List<Token> out = new ArrayList<>(tokens.size());
        for (Token token : tokens) {
            if (token.getKind() != TokenKind.LITERAL) {
                out.add(token);
                continue;
            }
            out.add(new Token(literalPlaceholder(token.getValue()), TokenKind.LITERAL, token.getLine()));
        }
        return List.copyOf(out);
    }

    /** In the C family a single quote is a character literal, not a string. */
    private static String literalPlaceholder(String raw) {
        if (raw.isEmpty()) {
            return "NUM";
        }
        char first = raw.charAt(0);
        if (first == '"' || first == '`') {
            return "STR";
        }
        if (first == '\'') {
            return "CHR";
        }
        return "NUM";
    }

    // ---------------------------------------------------------------- shared lexer

    /**
     * Lex comment-free source into tokens, tagging anything in {@code keywords} as
     * KEYWORD and every other word as IDENTIFIER.
     *
     * @param source   source with comments already blanked by {@link #stripComments}
     * @param keywords this language's reserved words
     */
    protected final List<Token> lex(String source, Set<String> keywords) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int n = source.length();
        int line = 1;

        while (i < n) {
            char c = source.charAt(i);

            if (c == '\n') {
                line++;
                i++;
                continue;
            }
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            if (c == '#' && supportsPreprocessor()) {
                int consumed = lexPreprocessor(source, i, line, tokens);
                if (consumed > i) {
                    i = consumed;
                    continue;
                }
            }

            if (Character.isJavaIdentifierStart(c)) {
                int end = i + 1;
                while (end < n && Character.isJavaIdentifierPart(source.charAt(end))) {
                    end++;
                }
                String word = source.substring(i, end);
                tokens.add(new Token(word, keywords.contains(word) ? TokenKind.KEYWORD : TokenKind.IDENTIFIER, line));
                i = end;
                continue;
            }

            if (Character.isDigit(c)
                    || (c == '.' && i + 1 < n && Character.isDigit(source.charAt(i + 1)))) {
                int end = scanNumber(source, i);
                tokens.add(new Token(source.substring(i, end), TokenKind.LITERAL, line));
                i = end;
                continue;
            }

            if (c == '"' || c == '\'' || (supportsTemplateLiterals() && c == '`')) {
                int end = scanQuoted(source, i);
                String raw = source.substring(i, end);
                tokens.add(new Token(raw, TokenKind.LITERAL, line));
                line += countNewlines(raw);
                i = end;
                continue;
            }

            if (PUNCTUATION.contains(c)) {
                tokens.add(new Token(String.valueOf(c), TokenKind.PUNCTUATION, line));
                i++;
                continue;
            }

            String operator = matchOperator(source, i);
            if (operator != null) {
                tokens.add(new Token(operator, TokenKind.OPERATOR, line));
                i += operator.length();
                continue;
            }

            tokens.add(new Token(String.valueOf(c), TokenKind.UNKNOWN, line));
            i++;
        }
        return List.copyOf(tokens);
    }

    /**
     * Emit {@code #include}, {@code #define} etc. as one KEYWORD token.
     *
     * <p>The include target itself is deliberately left to lex normally and be
     * generalised away with every other identifier. Preserving it would mostly record
     * that an entire C cohort includes {@code <stdio.h>} — boilerplate that stage 4
     * would then have to filter back out.
     *
     * @return the index after the directive, or {@code start} if this was not one
     */
    private int lexPreprocessor(String source, int start, int line, List<Token> tokens) {
        int n = source.length();
        int i = start + 1;
        while (i < n && (source.charAt(i) == ' ' || source.charAt(i) == '\t')) {
            i++;
        }
        int wordStart = i;
        while (i < n && Character.isLetter(source.charAt(i))) {
            i++;
        }
        String directive = source.substring(wordStart, i);
        if (!PREPROCESSOR_DIRECTIVES.contains(directive)) {
            return start;
        }
        tokens.add(new Token("#" + directive, TokenKind.KEYWORD, line));
        return i;
    }

    /** Permissive number scanner: hex, binary, floats, exponents, digit separators, suffixes. */
    private static int scanNumber(String source, int start) {
        int n = source.length();
        int i = start;
        boolean hex = false;

        if (source.charAt(i) == '0' && i + 1 < n) {
            char marker = Character.toLowerCase(source.charAt(i + 1));
            if (marker == 'x' || marker == 'b' || marker == 'o') {
                hex = marker == 'x';
                i += 2;
            }
        }

        while (i < n) {
            char c = source.charAt(i);
            // In hex, 'e' is a digit -- only 'p' introduces an exponent. Getting this
            // wrong makes 0x1e+2 lex as one token instead of an addition.
            boolean exponentMarker = hex
                    ? (c == 'p' || c == 'P')
                    : (c == 'e' || c == 'E');
            if (exponentMarker && i + 1 < n
                    && (source.charAt(i + 1) == '+' || source.charAt(i + 1) == '-')) {
                i += 2;
                continue;
            }
            if (Character.isLetterOrDigit(c) || c == '_' || c == '.') {
                i++;
                continue;
            }
            break;
        }
        return i;
    }

    /** @return index just past the closing quote */
    private int scanQuoted(String source, int start) {
        char quote = source.charAt(start);
        int n = source.length();
        int i = start + 1;

        while (i < n) {
            char c = source.charAt(i);
            if (c == '\\' && i + 1 < n) {
                i += 2;
                continue;
            }
            i++;
            if (c == quote) {
                break;
            }
            if (c == '\n' && quote != '`') {
                break;
            }
        }
        return i;
    }

    private static String matchOperator(String source, int at) {
        for (String operator : OPERATORS) {
            if (source.startsWith(operator, at)) {
                return operator;
            }
        }
        return null;
    }

    private static int countNewlines(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }
}
