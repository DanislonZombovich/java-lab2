

import java.util.Objects;

/**
 * Statement representing CREATE TABLE operations.
 */
public class CreateTableStatement implements Statement {
    private final TableSchema schema;
    private final boolean ifNotExists;

    /**
     * Creates a CREATE TABLE statement.
     *
     * @param schema schema of the table to create
     * @param ifNotExists whether IF NOT EXISTS was specified
     */
    public CreateTableStatement(TableSchema schema, boolean ifNotExists) {
        this.schema = Objects.requireNonNull(schema, "schema cannot be null");
        this.ifNotExists = ifNotExists;
    }

    /**
     * Returns the target table schema.
     *
     * @return table schema
     */
    public TableSchema getSchema() {
        return schema;
    }

    /**
     * Returns whether IF NOT EXISTS is enabled.
     *
     * @return true if errors on existing tables should be suppressed
     */
    public boolean isIfNotExists() {
        return ifNotExists;
    }

    @Override
    public StatementType getType() {
        return StatementType.CREATE_TABLE;
    }
}
