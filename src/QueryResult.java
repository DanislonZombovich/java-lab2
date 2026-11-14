
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Encapsulates the outcome of an executed SQL query or command.
 */
public class QueryResult {
    private final boolean select;
    private final List<String> columnNames;
    private final List<Row> rows;
    private final int rowsAffected;
    private final long executionTimeNs;
    private final String executionPlan;
    private final String message;

    private QueryResult(
            boolean select,
            List<String> columnNames,
            List<Row> rows,
            int rowsAffected,
            long executionTimeNs,
            String executionPlan,
            String message) {
        this.select = select;
        this.columnNames = columnNames == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(columnNames));
        this.rows = rows == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(rows));
        this.rowsAffected = rowsAffected;
        this.executionTimeNs = executionTimeNs;
        this.executionPlan = executionPlan;
        this.message = message;
    }

    /**
     * Creates a QueryResult for a SELECT query.
     *
     * @param columns         projected column names
     * @param rows            result rows
     * @param executionTimeNs execution time in nanoseconds
     * @param executionPlan   plan description
     * @return QueryResult instance
     */
    public static QueryResult forSelect(List<String> columns, List<Row> rows, long executionTimeNs, String executionPlan) {
        return new QueryResult(
                true,
                columns,
                rows,
                rows.size(),
                executionTimeNs,
                executionPlan,
                rows.size() + " row(s) returned"
        );
    }

    /**
     * Creates a QueryResult for a DDL or DML mutation statement.
     *
     * @param rowsAffected    number of modified rows
     * @param executionTimeNs execution time in nanoseconds
     * @param message         status message
     * @return QueryResult instance
     */
    public static QueryResult forMutation(int rowsAffected, long executionTimeNs, String message) {
        return new QueryResult(
                false,
                Collections.emptyList(),
                Collections.emptyList(),
                rowsAffected,
                executionTimeNs,
                null,
                message
        );
    }

    /**
     * Creates a QueryResult for an EXPLAIN statement.
     *
     * @param plan            formatted execution plan
     * @param executionTimeNs execution time in nanoseconds
     * @return QueryResult instance
     */
    public static QueryResult forExplain(String plan, long executionTimeNs) {
        Row planRow = new Row();
        planRow.set("PLAN", plan);
        return new QueryResult(
                true,
                List.of("PLAN"),
                List.of(planRow),
                1,
                executionTimeNs,
                plan,
                "Query execution plan"
        );
    }

    /**
     * Returns whether this result represents tabular rows from a SELECT-like query.
     *
     * @return true if result contains tabular rows
     */
    public boolean isSelect() {
        return select;
    }

    /**
     * Returns projected column names in order.
     *
     * @return projected column names
     */
    public List<String> getColumnNames() {
        return columnNames;
    }

    /**
     * Returns result rows.
     *
     * @return result rows
     */
    public List<Row> getRows() {
        return rows;
    }

    /**
     * Returns the count of affected or returned rows.
     *
     * @return count of affected or returned rows
     */
    public int getRowsAffected() {
        return rowsAffected;
    }

    /**
     * Returns elapsed execution time in nanoseconds.
     *
     * @return elapsed time in nanoseconds
     */
    public long getExecutionTimeNs() {
        return executionTimeNs;
    }

    /**
     * Returns elapsed execution time in milliseconds.
     *
     * @return elapsed time in milliseconds
     */
    public double getExecutionTimeMs() {
        return executionTimeNs / 1_000_000.0;
    }

    /**
     * Returns the chosen execution plan description, if available.
     *
     * @return execution plan description or null
     */
    public String getExecutionPlan() {
        return executionPlan;
    }

    /**
     * Returns a human-readable execution status message.
     *
     * @return human-readable execution message
     */
    public String getMessage() {
        return message;
    }
}
