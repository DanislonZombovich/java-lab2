package ru.danis.dbms.parser;

/**
 * Supported comparison operators in WHERE clauses.
 */
public enum ComparisonOperator {
    EQUALS("="),
    NOT_EQUALS("!="),
    GREATER_THAN(">"),
    GREATER_THAN_OR_EQUAL(">="),
    LESS_THAN("<"),
    LESS_THAN_OR_EQUAL("<=");

    private final String symbol;

    ComparisonOperator(String symbol) {
        this.symbol = symbol;
    }

    public static ComparisonOperator fromString(String symbol) {
        if (symbol == null) {
            throw new IllegalArgumentException("Operator symbol cannot be null");
        }
        return switch (symbol.trim()) {
            case "=" -> EQUALS;
            case "!=", "<>" -> NOT_EQUALS;
            case ">" -> GREATER_THAN;
            case ">=" -> GREATER_THAN_OR_EQUAL;
            case "<" -> LESS_THAN;
            case "<=" -> LESS_THAN_OR_EQUAL;
            default -> throw new IllegalArgumentException("Unsupported comparison operator: " + symbol);
        };
    }

    public String getSymbol() {
        return symbol;
    }

    public boolean evaluate(int comparisonResult) {
        return switch (this) {
            case EQUALS -> comparisonResult == 0;
            case NOT_EQUALS -> comparisonResult != 0;
            case GREATER_THAN -> comparisonResult > 0;
            case GREATER_THAN_OR_EQUAL -> comparisonResult >= 0;
            case LESS_THAN -> comparisonResult < 0;
            case LESS_THAN_OR_EQUAL -> comparisonResult <= 0;
        };
    }
}
