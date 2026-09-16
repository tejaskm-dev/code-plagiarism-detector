package com.integrityengine.tokenizer;

import com.integrityengine.domain.Token;
import com.integrityengine.domain.TokenKind;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * Python tokenizer.
 *
 * <p>Extends {@link AbstractTokenizer} directly rather than {@link CFamilyTokenizer}:
 * Python has no block comments and no braces, so none of the C-family shared logic
 * applies. It implements all four template stages itself, and adds the stage the C
 * family has no analogue for — synthesising INDENT/DEDENT tokens, because in Python
 * block structure *is* whitespace and has to survive into the token stream to be
 * comparable at all.
 */
final class PythonTokenizer extends AbstractTokenizer {

    /**
     * Python 3 reserved words.
     *
     * <p>The soft keywords {@code match}, {@code case} and {@code type} are omitted on
     * purpose: they are ordinary identifiers outside their statement context, and
     * matching them here would preserve a variable named {@code match} while
     * generalising every other name.
     */
    private static final Set<String> KEYWORDS = Set.of(
            "False", "None", "True", "and", "as", "assert", "async", "await", "break",
            "class", "continue", "def", "del", "elif", "else", "except", "finally",
            "for", "from", "global", "if", "import", "in", "is", "lambda", "nonlocal",
            "not", "or", "pass", "raise", "return", "try", "while", "with", "yield");

    /**
     * Preserved rather than generalised: {@code self} and {@code cls} are not names the
     * author chose, they mark instance and class methods. Collapsing them into ID would
     * erase the difference between a method and a free function.
     */
    private static final Set<String> PRESERVED_IDENTIFIERS = Set.of("self", "cls");

    private static final List<String> OPERATORS = List.of(
            "**=", "//=", ">>=", "<<=", "...", "!=", "==", "<=", ">=", "->", ":=",
            "**", "//", "<<", ">>", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "@=",
            "+", "-", "*", "/", "%", "=", "<", ">", "~", "&", "|", "^", ".", "@", ":");

    private static final Set<Character> PUNCTUATION = Set.of('(', ')', '[', ']', '{', '}', ',', ';');

    /** A tab advances to the next multiple of 8, matching CPython's own rule. */
    private static final int TAB_WIDTH = 8;

    @Override
    public Language language() {
        return Language.PYTHON;
    }

    // ------------------------------------------------------------ template stage 1

    /**
     * Strip {@code #} comments.
     *
     * <p>String-aware, including triple-quoted strings: a {@code #} inside a string is
     * data, not a comment. Comments are blanked to spaces rather than removed so that
     * column positions survive — which matters far more here than in the C family,
     * because those columns *are* the block structure.
     */
    @Override
    protected String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();

        while (i < n) {
            char c = source.charAt(i);

            if (c == '#') {
                while (i < n && source.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
            } else if (c == '"' || c == '\'') {
                int end = scanString(source, i);
                out.append(source, i, end);
                i = end;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    // ------------------------------------------------------------ template stage 2

    /**
     * Lex comment-free Python into tokens, driving indentation as it goes.
     *
     * <p>Works in logical lines rather than physical ones: a newline inside brackets or
     * after a backslash continues the statement and must not trigger indentation
     * handling, or every multi-line call would produce spurious INDENT tokens.
     */
    @Override
    protected List<Token> extractKeywords(String source) {
        List<Token> tokens = new ArrayList<>();
        Deque<Integer> indentStack = new ArrayDeque<>();
        indentStack.push(0);

        int i = 0;
        int n = source.length();
        int line = 1;
        int bracketDepth = 0;
        boolean atLineStart = true;
        boolean lineHasTokens = false;

        while (i < n) {
            if (atLineStart && bracketDepth == 0) {
                int width = 0;
                int scan = i;
                while (scan < n && (source.charAt(scan) == ' ' || source.charAt(scan) == '\t')) {
                    width = source.charAt(scan) == '\t'
                            ? ((width / TAB_WIDTH) + 1) * TAB_WIDTH
                            : width + 1;
                    scan++;
                }
                if (scan >= n || source.charAt(scan) == '\n') {
                    // Blank or comment-only line: carries no indentation information.
                    i = scan;
                    if (i < n) {
                        line++;
                        i++;
                    }
                    continue;
                }
                tokens.addAll(applyIndentation(width, indentStack, line));
                i = scan;
                atLineStart = false;
            }

            char c = source.charAt(i);

            if (c == '\\' && i + 1 < n && source.charAt(i + 1) == '\n') {
                line++;
                i += 2;
                continue;
            }

            if (c == '\n') {
                line++;
                i++;
                if (bracketDepth == 0 && lineHasTokens) {
                    tokens.add(new Token("NEWLINE", TokenKind.PUNCTUATION, line - 1));
                    lineHasTokens = false;
                    atLineStart = true;
                }
                continue;
            }

            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            if (Character.isJavaIdentifierStart(c) && c != '$') {
                int end = i + 1;
                while (end < n && Character.isJavaIdentifierPart(source.charAt(end))
                        && source.charAt(end) != '$') {
                    end++;
                }
                String word = source.substring(i, end);
                // String prefixes (f"", rb'', u"") belong to the literal that follows.
                if (end < n && isStringPrefix(word)
                        && (source.charAt(end) == '"' || source.charAt(end) == '\'')) {
                    int close = scanString(source, end);
                    String raw = source.substring(i, close);
                    tokens.add(new Token(raw, TokenKind.LITERAL, line));
                    line += countNewlines(raw);
                    i = close;
                } else {
                    tokens.add(new Token(word,
                            KEYWORDS.contains(word) ? TokenKind.KEYWORD : TokenKind.IDENTIFIER, line));
                    i = end;
                }
                lineHasTokens = true;
                continue;
            }

            if (Character.isDigit(c) || (c == '.' && i + 1 < n && Character.isDigit(source.charAt(i + 1)))) {
                int end = scanNumber(source, i);
                tokens.add(new Token(source.substring(i, end), TokenKind.LITERAL, line));
                i = end;
                lineHasTokens = true;
                continue;
            }

            if (c == '"' || c == '\'') {
                int end = scanString(source, i);
                String raw = source.substring(i, end);
                tokens.add(new Token(raw, TokenKind.LITERAL, line));
                line += countNewlines(raw);
                i = end;
                lineHasTokens = true;
                continue;
            }

            if (PUNCTUATION.contains(c)) {
                if (c == '(' || c == '[' || c == '{') {
                    bracketDepth++;
                } else if (c == ')' || c == ']' || c == '}') {
                    bracketDepth = Math.max(0, bracketDepth - 1);
                }
                tokens.add(new Token(String.valueOf(c), TokenKind.PUNCTUATION, line));
                i++;
                lineHasTokens = true;
                continue;
            }

            String operator = matchOperator(source, i);
            if (operator != null) {
                tokens.add(new Token(operator, TokenKind.OPERATOR, line));
                i += operator.length();
                lineHasTokens = true;
                continue;
            }

            tokens.add(new Token(String.valueOf(c), TokenKind.UNKNOWN, line));
            i++;
            lineHasTokens = true;
        }

        if (lineHasTokens) {
            tokens.add(new Token("NEWLINE", TokenKind.PUNCTUATION, line));
        }
        // Close every block still open at end of file.
        while (indentStack.peek() != null && indentStack.peek() > 0) {
            indentStack.pop();
            tokens.add(new Token("DEDENT", TokenKind.DEDENT, line));
        }
        return List.copyOf(tokens);
    }

    /**
     * Python-specific: turn one logical line's leading-whitespace width into explicit
     * INDENT/DEDENT tokens, so block nesting becomes structural rather than positional.
     *
     * <p>This is the stage that has no C-family counterpart, and the reason
     * {@code PythonTokenizer} does not extend {@link CFamilyTokenizer}.
     *
     * <p>An inconsistent dedent — closing to a column that was never opened — is
     * tolerated rather than rejected: this tokenizer runs over submissions that may not
     * even compile, and refusing to fingerprint a broken file helps nobody.
     *
     * @param indentWidth this line's leading whitespace, tabs already expanded
     * @param indentStack open block columns, innermost on top; mutated in place
     * @param line        line number to stamp on any emitted token
     * @return the INDENT/DEDENT tokens this line introduces, possibly empty
     */
    List<Token> applyIndentation(int indentWidth, Deque<Integer> indentStack, int line) {
        List<Token> emitted = new ArrayList<>();
        int current = indentStack.peek() == null ? 0 : indentStack.peek();

        if (indentWidth > current) {
            indentStack.push(indentWidth);
            emitted.add(new Token("INDENT", TokenKind.INDENT, line));
            return emitted;
        }
        while (indentStack.peek() != null && indentWidth < indentStack.peek()) {
            indentStack.pop();
            emitted.add(new Token("DEDENT", TokenKind.DEDENT, line));
        }
        return emitted;
    }

    // ------------------------------------------------------------ template stage 3

    @Override
    protected List<Token> generalizeIdentifiers(List<Token> tokens) {
        return generalizeIdentifiersExcept(tokens, PRESERVED_IDENTIFIERS);
    }

    // ------------------------------------------------------------ template stage 4

    /**
     * Unlike the C family, a single quote in Python opens a string, not a character
     * literal — so there is no CHR placeholder here.
     */
    @Override
    protected List<Token> generalizeLiterals(List<Token> tokens) {
        List<Token> out = new ArrayList<>(tokens.size());
        for (Token token : tokens) {
            if (token.getKind() != TokenKind.LITERAL) {
                out.add(token);
                continue;
            }
            out.add(new Token(isStringLiteral(token.getValue()) ? "STR" : "NUM",
                    TokenKind.LITERAL, token.getLine()));
        }
        return List.copyOf(out);
    }

    private static boolean isStringLiteral(String raw) {
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"' || c == '\'') {
                return true;
            }
            if (!isStringPrefixChar(c)) {
                return false;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- scanners

    private static boolean isStringPrefix(String word) {
        if (word.isEmpty() || word.length() > 2) {
            return false;
        }
        for (int i = 0; i < word.length(); i++) {
            if (!isStringPrefixChar(word.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isStringPrefixChar(char c) {
        char lower = Character.toLowerCase(c);
        return lower == 'r' || lower == 'b' || lower == 'u' || lower == 'f';
    }

    /**
     * Scan a string literal, triple-quoted or not.
     *
     * @return index just past the closing quote
     */
    private static int scanString(String source, int start) {
        int n = source.length();
        char quote = source.charAt(start);
        boolean triple = start + 2 < n
                && source.charAt(start + 1) == quote
                && source.charAt(start + 2) == quote;

        int i = start + (triple ? 3 : 1);
        while (i < n) {
            char c = source.charAt(i);
            if (c == '\\' && i + 1 < n) {
                i += 2;
                continue;
            }
            if (triple) {
                if (c == quote && i + 2 < n
                        && source.charAt(i + 1) == quote && source.charAt(i + 2) == quote) {
                    return i + 3;
                }
                i++;
                continue;
            }
            if (c == quote) {
                return i + 1;
            }
            if (c == '\n') {
                // Unterminated single-quoted string: stop at the line end rather than
                // swallowing the rest of the file.
                return i;
            }
            i++;
        }
        return n;
    }

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
            boolean exponentMarker = !hex && (c == 'e' || c == 'E');
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
