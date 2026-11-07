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

    @JsonCreator
    public TableSchema(
            @JsonProperty("tableName") String tableName,
            @JsonProperty("columns") List<Column> columns) {
        this.tableName = Objects.requireNonNull(tableName, "tableName cannot be null").trim();
        this.columns = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(columns, "columns cannot be null")));

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

    public String getTableName() {
        return tableName;
    }

    public List<Column> getColumns() {
        return columns;
    }

    public String getPrimaryKeyColumn() {
        return primaryKeyColumn;
    }

    public boolean hasPrimaryKey() {
        return primaryKeyColumn != null;
    }

    public Column getColumn(String name) {
        if (name == null) {
            return null;
        }
        return columnLookup.get(name);
    }

    public boolean hasColumn(String name) {
        if (name == null) {
            return false;
        }
        return columnLookup.containsKey(name);
    }

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
