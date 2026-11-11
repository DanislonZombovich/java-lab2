package ru.danis.dbms.parser.statement;

import ru.danis.dbms.parser.Condition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Statement representing SELECT queries.
 */
public class SelectStatement implements Statement {
    private final String tableName;
    private final List<String> columns;
    private final boolean selectAll;
    private final Condition condition;

    /**
     * Creates a SELECT query statement.
     *
     * @param tableName source table name
     * @param columns projected column names (ignored when selectAll is true)
     * @param selectAll true for SELECT * queries
     * @param condition optional WHERE filter predicate
     */
    public SelectStatement(String tableName, List<String> columns, boolean selectAll, Condition condition) {
        this.tableName = Objects.requireNonNull(tableName, "tableName cannot be null");
        this.columns = columns == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(columns));
        this.selectAll = selectAll;
        this.condition = condition;
    }

    /**
     * Returns the queried table name.
     *
     * @return table name
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Returns the requested projection column names.
     *
     * @return unmodifiable list of column names
     */
    public List<String> getColumns() {
        return columns;
    }

    /**
     * Returns whether all table columns are selected via *.
     *
     * @return true if wildcard projection is active
     */
    public boolean isSelectAll() {
        return selectAll;
    }

    /**
     * Returns the optional WHERE filter predicate.
     *
     * @return filter condition, or null if absent
     */
    public Condition getCondition() {
        return condition;
    }

    /**
     * Returns whether a WHERE condition is present.
     *
     * @return true if condition is non-null
     */
    public boolean hasCondition() {
        return condition != null;
    }

    @Override
    public StatementType getType() {
        return StatementType.SELECT;
    }
}
