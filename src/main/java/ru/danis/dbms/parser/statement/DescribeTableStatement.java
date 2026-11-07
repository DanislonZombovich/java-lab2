package ru.danis.dbms.parser.statement;

import java.util.Objects;

/**
 * Statement representing DESCRIBE table command.
 */
public class DescribeTableStatement implements Statement {
    private final String tableName;

    public DescribeTableStatement(String tableName) {
        this.tableName = Objects.requireNonNull(tableName, "tableName cannot be null");
    }

    public String getTableName() {
        return tableName;
    }

    @Override
    public StatementType getType() {
        return StatementType.DESCRIBE_TABLE;
    }
}
