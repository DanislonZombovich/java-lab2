package ru.danis.dbms.parser.statement;

import ru.danis.dbms.model.TableSchema;

import java.util.Objects;

/**
 * Statement representing CREATE TABLE operations.
 */
public class CreateTableStatement implements Statement {
    private final TableSchema schema;
    private final boolean ifNotExists;

    public CreateTableStatement(TableSchema schema, boolean ifNotExists) {
        this.schema = Objects.requireNonNull(schema, "schema cannot be null");
        this.ifNotExists = ifNotExists;
    }

    public TableSchema getSchema() {
        return schema;
    }

    public boolean isIfNotExists() {
        return ifNotExists;
    }

    @Override
    public StatementType getType() {
        return StatementType.CREATE_TABLE;
    }
}
