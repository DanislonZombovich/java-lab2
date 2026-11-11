package ru.danis.dbms.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.*;

/**
 * Defines the structural schema of a relational table.
 */
public class TableSchema {
    private final String tableName;
    private final List<Column> columns;
    private final Map<String, Column> columnLookup;
    private final String primaryKeyColumn;

    /**
     * Creates a table schema definition.
     *
     * @param tableName name of the table
     * @param columns   ordered list of table columns
     */
    @JsonCreator
    public TableSchema(
            @JsonProperty("tableName") String tableName,
            @JsonProperty("columns") List<Column> columns) {
        this.tableName = Objects.requireNonNull(tableName, "tableName cannot be null").trim();
        if (this.tableName.isEmpty()) {
            throw new IllegalArgumentException("Table name cannot be empty");
        }
        this.columns = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(columns, "columns cannot be null")));
        if (this.columns.isEmpty()) {
            throw new IllegalArgumentException("Table '" + this.tableName + "' must have at least one column");
        }

        Map<String, Column> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        String pk = null;

        for (Column col : this.columns) {
            String lower = col.getName().toLowerCase();
            if (map.containsKey(lower)) {
                throw new IllegalArgumentException("Duplicate column name: " + col.getName());
            }
            map.put(col.getName(), col);
            if (col.isPrimaryKey()) {
                if (pk != null) {
                    throw new IllegalArgumentException("Multiple primary keys are not supported in table " + tableName);
                }
                pk = col.getName();
            }
        }

        this.columnLookup = Collections.unmodifiableMap(map);
        this.primaryKeyColumn = pk;
    }

    /**
     * Returns the table name.
     *
     * @return table name
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Returns the ordered unmodifiable list of table columns.
     *
     * @return unmodifiable list of columns
     */
    public List<Column> getColumns() {
        return columns;
    }

    /**
     * Returns the primary key column name, or null if none is defined.
     *
     * @return primary key column name or null
     */
    public String getPrimaryKeyColumn() {
        return primaryKeyColumn;
    }

    /**
     * Returns whether this table defines a primary key column.
     *
     * @return true if table defines a primary key
     */
    public boolean hasPrimaryKey() {
        return primaryKeyColumn != null;
    }

    /**
     * Looks up a column by case-insensitive name.
     *
     * @param name column name
     * @return matching Column or null
     */
    public Column getColumn(String name) {
        if (name == null) {
            return null;
        }
        return columnLookup.get(name);
    }

    /**
     * Checks whether a column exists in this schema.
     *
     * @param name column name
     * @return true if column exists
     */
    public boolean hasColumn(String name) {
        if (name == null) {
            return false;
        }
        return columnLookup.containsKey(name);
    }

    /**
     * Returns the zero-based position of a column.
     *
     * @param name column name
     * @return column index or -1 if absent
     */
    public int getColumnIndex(String name) {
        if (name == null) {
            return -1;
        }
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).getName().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TableSchema that)) return false;
        return tableName.equalsIgnoreCase(that.tableName) &&
               Objects.equals(columns, that.columns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tableName.toLowerCase(), columns);
    }

    @Override
    public String toString() {
        return "TABLE " + tableName + " (" + String.join(", ", columns.stream().map(Column::toString).toList()) + ")";
    }
}
