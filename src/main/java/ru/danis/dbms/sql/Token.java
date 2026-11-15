package ru.danis.dbms.sql;

import java.util.Objects;

public class Token {
    private final TokenType type;
    private final String value;
    private final int position;

    public Token(TokenType type, String value, int position) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.value = Objects.requireNonNull(value, "value cannot be null");
        this.position = position;
    }

    public TokenType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public int getPosition() {
        return position;
    }

    public boolean isKeyword(String keyword) {
        return type == TokenType.KEYWORD && value.equalsIgnoreCase(keyword);
    }

    public boolean isOperator(String op) {
        return type == TokenType.OPERATOR && value.equalsIgnoreCase(op);
    }

    @Override
    public String toString() {
        return "Token{" + type + ", '" + value + "' @" + position + "}";
    }
}
