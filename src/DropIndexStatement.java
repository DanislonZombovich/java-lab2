
import java.util.Objects;

/**
 * Statement representing DROP INDEX operations.
 */
public class DropIndexStatement implements Statement {
    private final String indexName;
    private final boolean ifExists;

    /**
     * Creates a DROP INDEX statement.
     *
     * @param indexName name of the index to drop
     * @param ifExists whether IF EXISTS was specified
     */
    public DropIndexStatement(String indexName, boolean ifExists) {
        this.indexName = Objects.requireNonNull(indexName, "indexName cannot be null");
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
