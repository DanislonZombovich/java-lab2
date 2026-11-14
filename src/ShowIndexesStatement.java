
/**
 * Statement representing SHOW INDEXES command.
 */
public class ShowIndexesStatement implements Statement {
    private final String tableName;

    /**
     * Creates a SHOW INDEXES statement.
     *
     * @param tableName optional table filter, or null to show indexes across all tables
     */
    public ShowIndexesStatement(String tableName) {
        this.tableName = tableName;
    }

    /**
     * Returns the optional table filter.
     *
     * @return table name, or null if unfiltered
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Returns whether indexes are filtered by a specific table.
     *
     * @return true if a non-blank table name filter is present
     */
    public boolean hasTableFilter() {
        return tableName != null && !tableName.isBlank();
    }

    @Override
    public StatementType getType() {
        return StatementType.SHOW_INDEXES;
    }
}
