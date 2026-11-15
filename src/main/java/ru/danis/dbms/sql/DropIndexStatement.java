package ru.danis.dbms.sql;

import java.util.Objects;

/**
 * Statement representing DROP INDEX operations.
 */
public class DropIndexStatement implements Statement {
    private final String indexName;
    private final String tableName;
    private final boolean ifExists;

    /**
     * Creates a DROP INDEX statement.
     *
     * @param indexName name of the index to drop
     * @param ifExists whether IF EXISTS was specified
     */
    public DropIndexStatement(String indexName, boolean ifExists) {
        this(indexName, null, ifExists);
    }

    /**
     * Creates a DROP INDEX statement with an optional target table.
     *
     * @param indexName name of the index to drop
     * @param tableName optional target table name
     * @param ifExists whether IF EXISTS was specified
     */
    public DropIndexStatement(String indexName, String tableName, boolean ifExists) {
        this.indexName = Objects.requireNonNull(indexName, "indexName cannot be null");
        this.tableName = tableName;
        this.ifExists = ifExists;
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
     * Returns the optional table name specified in ON clause.
     *
     * @return table name or null if omitted
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Returns whether IF EXISTS is enabled.
     *
     * @return true if missing indexes should be ignored
     */
    public boolean isIfExists() {
        return ifExists;
    }

    @Override
    public StatementType getType() {
        return StatementType.DROP_INDEX;
    }
}
