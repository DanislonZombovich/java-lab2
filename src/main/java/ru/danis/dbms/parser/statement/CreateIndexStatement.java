package ru.danis.dbms.parser.statement;

import java.util.Objects;

/**
 * Statement representing CREATE INDEX operations.
 */
public class CreateIndexStatement implements Statement {
    private final String indexName;
    private final String tableName;
    private final String columnName;
    private final boolean ifNotExists;

    public CreateIndexStatement(String indexName, String tableName, String columnName, boolean ifNotExists) {
        this.indexName = Objects.requireNonNull(indexName, "indexName cannot be null");
        this.tableName = Objects.requireNonNull(tableName, "tableName cannot be null");
        this.columnName = Objects.requireNonNull(columnName, "columnName cannot be null");
        this.ifNotExists = ifNotExists;
    }

    public String getIndexName() {
        return indexName;
    }

    public String getTableName() {
        return tableName;
    }

    public String getColumnName() {
        return columnName;
    }

    public boolean isIfNotExists() {
        return ifNotExists;
    }

    @Override
    public StatementType getType() {
        return StatementType.CREATE_INDEX;
    }
}
