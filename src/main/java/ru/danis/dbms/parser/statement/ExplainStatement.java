package ru.danis.dbms.parser.statement;

import java.util.Objects;

/**
 * Statement representing EXPLAIN query plan analysis.
 */
public class ExplainStatement implements Statement {
    private final Statement innerStatement;

    public ExplainStatement(Statement innerStatement) {
        this.innerStatement = Objects.requireNonNull(innerStatement, "innerStatement cannot be null");
    }

    public Statement getInnerStatement() {
        return innerStatement;
    }

    @Override
    public StatementType getType() {
        return StatementType.EXPLAIN;
    }
}
