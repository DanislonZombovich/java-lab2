package ru.danis.dbms.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.*;

/**
 * Represents a single tuple (record) within a database table.
 */
public class Row {
    private final Map<String, Object> values;

    public Row() {
        this.values = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    }

    @JsonCreator
    public Row(@JsonProperty("values") Map<String, Object> initialValues) {
        this();
        if (initialValues != null) {
            this.values.putAll(initialValues);
        }
    }

    public Object get(String columnName) {
        if (columnName == null) {
            return null;
        }
        return values.get(columnName);
    }

    public void set(String columnName, Object value) {
        if (columnName == null) {
            throw new IllegalArgumentException("Column name cannot be null");
        }
        values.put(columnName, value);
    }

    public boolean has(String columnName) {
        if (columnName == null) {
            return false;
        }
        return values.containsKey(columnName);
    }

    public Map<String, Object> getValues() {
        return Collections.unmodifiableMap(values);
    }

    public Row copy() {
        return new Row(new HashMap<>(this.values));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Row row)) return false;
        return Objects.equals(values, row.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
