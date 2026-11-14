
import java.util.Objects;

/**
 * Statement representing DROP TABLE operations.
 */
public class DropTableStatement implements Statement {
    private final String tableName;
    private final boolean ifExists;

    /**
     * Creates a DROP TABLE statement.
     *
     * @param tableName name of the table to drop
     * @param ifExists whether IF EXISTS was specified
     */
    public DropTableStatement(String tableName, boolean ifExists) {
        this.tableName = Objects.requireNonNull(tableName, "tableName cannot be null");
        this.ifExists = ifExists;
    }

    /**
     * Returns the table name to drop.
     *
     * @return table name
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Returns whether IF EXISTS is enabled.
     *
     * @return true if missing tables should be ignored
     */
    public boolean isIfExists() {
        return ifExists;
    }

    @Override
    public StatementType getType() {
        return StatementType.DROP_TABLE;
    }
}
