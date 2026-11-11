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

    /**
     * Creates an INSERT statement.
     *
     * @param tableName destination table name
     * @param columns optional explicit column list, or null/empty for positional values
     * @param rowsValues list of value tuples to insert
     */
    public InsertStatement(String tableName, List<String> columns, List<List<String>> rowsValues) {
        this.tableName = Objects.requireNonNull(tableName, "tableName cannot be null");
        this.columns = columns == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(columns));
        this.rowsValues = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(rowsValues, "rowsValues cannot be null")));
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
     * Returns explicit column names specified in INSERT, if any.
     *
     * @return unmodifiable list of column names
     */
    public List<String> getColumns() {
        return columns;
    }

    /**
     * Returns whether an explicit column list was supplied.
     *
     * @return true if explicit columns were provided
     */
    public boolean hasExplicitColumns() {
        return !columns.isEmpty();
    }

    /**
     * Returns the list of parsed literal value lists to insert.
     *
     * @return unmodifiable list of row value tuples
     */
    public List<List<String>> getRowsValues() {
        return rowsValues;
    }

    @Override
    public StatementType getType() {
        return StatementType.INSERT;
    }
}
