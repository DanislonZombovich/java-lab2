

import java.util.Objects;

/**
 * Statement representing DELETE queries.
 */
public class DeleteStatement implements Statement {
    private final String tableName;
    private final Condition condition;

    /**
     * Creates a DELETE statement.
     *
     * @param tableName target table name
     * @param condition optional WHERE filter predicate
     */
    public DeleteStatement(String tableName, Condition condition) {
        this.tableName = Objects.requireNonNull(tableName, "tableName cannot be null");
        this.condition = condition;
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
     * Returns the optional WHERE predicate.
     *
     * @return filter condition, or null if deleting all rows
     */
    public Condition getCondition() {
        return condition;
    }

    /**
     * Returns whether a WHERE clause was specified.
     *
     * @return true if condition is non-null
     */
    public boolean hasCondition() {
        return condition != null;
    }

    @Override
    public StatementType getType() {
        return StatementType.DELETE;
    }
}
