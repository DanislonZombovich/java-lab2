package ru.danis.dbms.parser;

import java.util.*;

/**
 * Lexical analyzer converting SQL text into a stream of tokens.
 */
public class SqlLexer {

    private static final Set<String> KEYWORDS = Set.of(
            "CREATE", "TABLE", "DROP", "INDEX", "ON",
            "INSERT", "INTO", "VALUES", "SELECT", "FROM",
            "WHERE", "UPDATE", "SET", "DELETE",
            "SHOW", "TABLES", "DESCRIBE", "EXPLAIN",
            "PRIMARY", "KEY", "REFERENCES", "IF", "NOT", "EXISTS",
            "AND", "OR", "IS", "NULL",
            "INT", "INTEGER", "DOUBLE", "FLOAT", "VARCHAR", "TEXT", "DATE", "BOOLEAN"
    );

    private final String source;
    private final int length;
    private int cursor;

    /**
     * Creates a lexer for the given SQL input string.
     *
     * @param source SQL query text
     */
    public SqlLexer(String source) {
        this.source = source != null ? source : "";
        this.length = this.source.length();
        this.cursor = 0;
    }

    /**
     * Tokenizes the entire SQL source into a sequence terminated by EOF.
     *
     * @return list of parsed tokens
     */
    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (cursor < length) {
            char ch = source.charAt(cursor);

            if (Character.isWhitespace(ch)) {
                cursor++;
                continue;
            }

            if (ch == '-' && peek() == '-') {
                cursor += 2;
                while (cursor < length && source.charAt(cursor) != '\n') {
                    cursor++;
                }
                continue;
            }

            int startPos = cursor;

            if (ch == ';') {
                tokens.add(new Token(TokenType.SEMICOLON, ";", startPos));
                cursor++;
            } else if (ch == ',') {
                tokens.add(new Token(TokenType.COMMA, ",", startPos));
                cursor++;
            } else if (ch == '(') {
                tokens.add(new Token(TokenType.LPAREN, "(", startPos));
                cursor++;
            } else if (ch == ')') {
                tokens.add(new Token(TokenType.RPAREN, ")", startPos));
                cursor++;
            } else if (ch == '*') {
                tokens.add(new Token(TokenType.STAR, "*", startPos));
                cursor++;
            } else if (ch == '\'' || ch == '"') {
                tokens.add(tokenizeString(ch, startPos));
            } else if (ch == '!' && peek() == '=') {
                tokens.add(new Token(TokenType.OPERATOR, "!=", startPos));
                cursor += 2;
            } else if (ch == '<') {
                if (peek() == '=') {
                    tokens.add(new Token(TokenType.OPERATOR, "<=", startPos));
                    cursor += 2;
                } else if (peek() == '>') {
                    tokens.add(new Token(TokenType.OPERATOR, "<>", startPos));
                    cursor += 2;
                } else {
                    tokens.add(new Token(TokenType.OPERATOR, "<", startPos));
                    cursor++;
                }
            } else if (ch == '>') {
                if (peek() == '=') {
                    tokens.add(new Token(TokenType.OPERATOR, ">=", startPos));
                    cursor += 2;
                } else {
                    tokens.add(new Token(TokenType.OPERATOR, ">", startPos));
                    cursor++;
                }
            } else if (ch == '=') {
                tokens.add(new Token(TokenType.OPERATOR, "=", startPos));
                cursor++;
            } else if (Character.isDigit(ch) || (ch == '-' && Character.isDigit(peek()))) {
                tokens.add(tokenizeNumber(startPos));
            } else if (Character.isLetter(ch) || ch == '_') {
                tokens.add(tokenizeWord(startPos));
            } else {
                throw new IllegalArgumentException("Unexpected character at position " + startPos + ": '" + ch + "'");
            }
        }

        tokens.add(new Token(TokenType.EOF, "", length));
        return tokens;
    }

    private char peek() {
        if (cursor + 1 < length) {
            return source.charAt(cursor + 1);
        }
        return '\0';
    }

    private Token tokenizeString(char quoteChar, int startPos) {
        StringBuilder sb = new StringBuilder();
        cursor++;
        boolean closed = false;

        while (cursor < length) {
            char ch = source.charAt(cursor);
            if (ch == quoteChar) {
                if (peek() == quoteChar) {
                    sb.append(quoteChar);
                    cursor += 2;
                } else {
                    closed = true;
                    cursor++;
                    break;
                }
            } else if (ch == '\\' && peek() == quoteChar) {
                sb.append(quoteChar);
                cursor += 2;
            } else {
                sb.append(ch);
                cursor++;
            }
        }

        if (!closed) {
            throw new IllegalArgumentException("Unclosed string literal starting at position " + startPos);
        }

        return new Token(TokenType.STRING_LITERAL, sb.toString(), startPos);
    }

    private Token tokenizeNumber(int startPos) {
        StringBuilder sb = new StringBuilder();
        if (source.charAt(cursor) == '-') {
            sb.append('-');
            cursor++;
        }

        boolean hasDot = false;
        while (cursor < length) {
            char ch = source.charAt(cursor);
            if (Character.isDigit(ch)) {
                sb.append(ch);
                cursor++;
            } else if (ch == '.' && !hasDot && Character.isDigit(peek())) {
                hasDot = true;
                sb.append('.');
                cursor++;
            } else {
                break;
            }
        }

        return new Token(TokenType.NUMBER_LITERAL, sb.toString(), startPos);
    }

    private Token tokenizeWord(int startPos) {
        StringBuilder sb = new StringBuilder();
        while (cursor < length) {
            char ch = source.charAt(cursor);
            if (Character.isLetterOrDigit(ch) || ch == '_') {
                sb.append(ch);
                cursor++;
            } else {
                break;
            }
        }

        String word = sb.toString();
        String upper = word.toUpperCase();

        if (upper.equals("TRUE") || upper.equals("FALSE")) {
            return new Token(TokenType.BOOLEAN_LITERAL, upper, startPos);
        }
        if (KEYWORDS.contains(upper)) {
            return new Token(TokenType.KEYWORD, upper, startPos);
        }

        return new Token(TokenType.IDENTIFIER, word, startPos);
    }
}
