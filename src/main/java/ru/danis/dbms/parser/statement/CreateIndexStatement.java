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

    /**
     * Creates a CREATE INDEX statement.
     *
     * @param indexName index name
     * @param tableName indexed table name
     * @param columnName indexed column name
     * @param ifNotExists whether IF NOT EXISTS was specified
     */
    public CreateIndexStatement(String indexName, String tableName, String columnName, boolean ifNotExists) {
        this.indexName = Objects.requireNonNull(indexName, "indexName cannot be null");
        this.tableName = Objects.requireNonNull(tableName, "tableName cannot be null");
        this.columnName = Objects.requireNonNull(columnName, "columnName cannot be null");
        this.ifNotExists = ifNotExists;
    }

    /**
     * Returns the index name.
     *
     * @return index name
     */
    public String getIndexName() {
        return indexName;
    }

    /**
     * Returns the target table name.
     *
     * @return table name
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Returns the indexed column name.
     *
     * @return column name
     */
    public String getColumnName() {
        return columnName;
    }

    /**
     * Returns whether IF NOT EXISTS is enabled.
     *
     * @return true if existing indexes should be ignored
     */
    public boolean isIfNotExists() {
        return ifNotExists;
    }

    @Override
    public StatementType getType() {
        return StatementType.CREATE_INDEX;
    }
}
