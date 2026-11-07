package ru.danis.dbms.model;

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

    public boolean isSelect() {
        return select;
    }

    public List<String> getColumnNames() {
        return columnNames;
    }

    public List<Row> getRows() {
        return rows;
    }

    public int getRowsAffected() {
        return rowsAffected;
    }

    public long getExecutionTimeNs() {
        return executionTimeNs;
    }

    public double getExecutionTimeMs() {
        return executionTimeNs / 1_000_000.0;
    }

    public String getExecutionPlan() {
        return executionPlan;
    }

    public String getMessage() {
        return message;
    }
}
