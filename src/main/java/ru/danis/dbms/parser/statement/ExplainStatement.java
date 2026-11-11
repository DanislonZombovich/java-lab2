package ru.danis.dbms.parser.statement;

import java.util.Objects;

/**
 * Statement representing EXPLAIN query plan analysis.
 */
public class ExplainStatement implements Statement {
    private final Statement innerStatement;

    /**
     * Creates an EXPLAIN statement wrapping another parsed statement.
     *
     * @param innerStatement statement to analyze
     */
    public ExplainStatement(Statement innerStatement) {
        this.innerStatement = Objects.requireNonNull(innerStatement, "innerStatement cannot be null");
    }

    /**
     * Returns the wrapped statement being explained.
     *
     * @return inner SQL statement
     */
    public Statement getInnerStatement() {
        return innerStatement;
    }

    @Override
    public StatementType getType() {
        return StatementType.EXPLAIN;
    }
}
