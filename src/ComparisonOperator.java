
/**
 * Supported comparison operators in WHERE clauses.
 */
public enum ComparisonOperator {
    /** Equality operator (=). */
    EQUALS("="),
    /** Inequality operator (!= or &lt;&gt;). */
    NOT_EQUALS("!="),
    /** Strict greater-than operator (&gt;). */
    GREATER_THAN(">"),
    /** Greater-than-or-equal operator (&gt;=). */
    GREATER_THAN_OR_EQUAL(">="),
    /** Strict less-than operator (&lt;). */
    LESS_THAN("<"),
    /** Less-than-or-equal operator (&lt;=). */
    LESS_THAN_OR_EQUAL("<=");

    private final String symbol;

    ComparisonOperator(String symbol) {
        this.symbol = symbol;
    }

    /**
     * Parses a comparison operator symbol into an enum constant.
     *
     * @param symbol operator text
     * @return matching ComparisonOperator
     */
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

    /**
     * Returns the canonical string representation of this operator.
     *
     * @return operator symbol
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * Evaluates a numeric {@link Comparable#compareTo} result against this operator.
     *
     * @param comparisonResult comparison integer (negative, zero, or positive)
     * @return true if the comparison satisfies the operator
     */
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
