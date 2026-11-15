package ru.danis.dbms.sql;

import java.util.Objects;

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
