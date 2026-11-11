package ru.danis.dbms.parser;

import java.util.Objects;

/**
 * Encapsulates a single token extracted from SQL query text.
 */
public class Token {
    private final TokenType type;
    private final String value;
    private final int position;

    /**
     * Creates a new token instance.
     *
     * @param type token classification
     * @param value extracted token text
     * @param position zero-based character offset in source SQL
     */
    public Token(TokenType type, String value, int position) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.value = Objects.requireNonNull(value, "value cannot be null");
        this.position = position;
    }

    /**
     * Returns the token type.
     *
     * @return token type
     */
    public TokenType getType() {
        return type;
    }

    /**
     * Returns the token text value.
     *
     * @return token string value
     */
    public String getValue() {
        return value;
    }

    /**
     * Returns the start offset of the token in the input SQL.
     *
     * @return zero-based character position
     */
    public int getPosition() {
        return position;
    }

    /**
     * Checks whether this token is a specific keyword (case-insensitive).
     *
     * @param keyword expected keyword
     * @return true if token type is KEYWORD and text matches
     */
    public boolean isKeyword(String keyword) {
        return type == TokenType.KEYWORD && value.equalsIgnoreCase(keyword);
    }

    /**
     * Checks whether this token represents a given operator symbol.
     *
     * @param op operator symbol
     * @return true if token type is OPERATOR and text matches
     */
    public boolean isOperator(String op) {
        return type == TokenType.OPERATOR && value.equalsIgnoreCase(op);
    }

    @Override
    public String toString() {
        return "Token{" + type + ", '" + value + "' @" + position + "}";
    }
}
