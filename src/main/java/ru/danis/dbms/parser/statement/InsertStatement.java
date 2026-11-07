package ru.danis.dbms.parser.statement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Statement representing single or multi-row INSERT operations.
 */
public class InsertStatement implements Statement {
    private final String tableName;
    private final List<String> columns;
    private final List<List<String>> rowsValues;

    public InsertStatement(String tableName, List<String> columns, List<List<String>> rowsValues) {
        this.tableName = Objects.requireNonNull(tableName, "tableName cannot be null");
        this.columns = columns == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(columns));
        this.rowsValues = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(rowsValues, "rowsValues cannot be null")));
    }

    public String getTableName() {
        return tableName;
    }

    public List<String> getColumns() {
        return columns;
    }

    public boolean hasExplicitColumns() {
        return !columns.isEmpty();
    }

    public List<List<String>> getRowsValues() {
        return rowsValues;
    }

    @Override
    public StatementType getType() {
        return StatementType.INSERT;
    }
}
