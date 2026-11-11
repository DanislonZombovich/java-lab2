package ru.danis.dbms.parser.statement;

import ru.danis.dbms.parser.Condition;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Statement representing UPDATE queries.
 */
public class UpdateStatement implements Statement {
    private final String tableName;
    private final Map<String, String> rawUpdates;
    private final Condition condition;

    /**
     * Creates an UPDATE statement.
     *
     * @param tableName target table name
     * @param rawUpdates map of column names to raw assignment literals
     * @param condition optional WHERE filter predicate
     */
    public UpdateStatement(String tableName, Map<String, String> rawUpdates, Condition condition) {
        this.tableName = Objects.requireNonNull(tableName, "tableName cannot be null");
        this.rawUpdates = Collections.unmodifiableMap(Objects.requireNonNull(rawUpdates, "rawUpdates cannot be null"));
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
     * Returns the column-to-value assignment map.
     *
     * @return unmodifiable map of raw update values
     */
    public Map<String, String> getRawUpdates() {
        return rawUpdates;
    }

    /**
     * Returns the optional WHERE predicate.
     *
     * @return condition, or null if updating all rows
     */
    public Condition getCondition() {
        return condition;
    }

    /**
     * Returns whether a WHERE clause was provided.
     *
     * @return true if condition is present
     */
    public boolean hasCondition() {
        return condition != null;
    }

    @Override
    public StatementType getType() {
        return StatementType.UPDATE;
    }
}
