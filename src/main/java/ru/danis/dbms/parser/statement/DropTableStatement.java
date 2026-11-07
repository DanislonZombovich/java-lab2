package ru.danis.dbms.parser.statement;

import java.util.Objects;

/**
 * Statement representing DROP TABLE operations.
 */
public class DropTableStatement implements Statement {
    private final String tableName;
    private final boolean ifExists;

    public DropTableStatement(String tableName, boolean ifExists) {
        this.tableName = Objects.requireNonNull(tableName, "tableName cannot be null");
        this.ifExists = ifExists;
    }

    public String getTableName() {
        return tableName;
    }

    public boolean isIfExists() {
        return ifExists;
    }

    @Override
    public StatementType getType() {
        return StatementType.DROP_TABLE;
    }
}
