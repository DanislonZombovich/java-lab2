package ru.danis.dbms.parser.statement;

import java.util.Objects;

/**
 * Statement representing DROP INDEX operations.
 */
public class DropIndexStatement implements Statement {
    private final String indexName;
    private final boolean ifExists;

    public DropIndexStatement(String indexName, boolean ifExists) {
        this.indexName = Objects.requireNonNull(indexName, "indexName cannot be null");
        this.ifExists = ifExists;
    }

    public String getIndexName() {
        return indexName;
    }

    public boolean isIfExists() {
        return ifExists;
    }

    @Override
    public StatementType getType() {
        return StatementType.DROP_INDEX;
    }
}
