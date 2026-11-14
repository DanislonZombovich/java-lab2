
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.*;

/**
 * Represents a single tuple (record) within a database table.
 */
public class Row {
    private final Map<String, Object> values;

    /** Creates an empty row. */
    public Row() {
        this.values = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    }

    /**
     * Creates a row initialized with column values.
     *
     * @param initialValues map of column names to values
     */
    @JsonCreator
    public Row(@JsonProperty("values") Map<String, Object> initialValues) {
        this();
        if (initialValues != null) {
            this.values.putAll(initialValues);
        }
    }

    /**
     * Gets a column value by case-insensitive column name.
     *
     * @param columnName column name
     * @return stored value or null
     */
    public Object get(String columnName) {
        if (columnName == null) {
            return null;
        }
        return values.get(columnName);
    }

    /**
     * Sets a column value.
     *
     * @param columnName column name
     * @param value      column value
     */
    public void set(String columnName, Object value) {
        if (columnName == null) {
            throw new IllegalArgumentException("Column name cannot be null");
        }
        values.put(columnName, value);
    }

    /**
     * Checks if the row contains an entry for the specified column.
     *
     * @param columnName column name
     * @return true if present
     */
    public boolean has(String columnName) {
        if (columnName == null) {
            return false;
        }
        return values.containsKey(columnName);
    }

    /**
     * Returns an unmodifiable view of this row's column values.
     *
     * @return unmodifiable view of column values
     */
    public Map<String, Object> getValues() {
        return Collections.unmodifiableMap(values);
    }

    /**
     * Returns a shallow copy of this row.
     *
     * @return shallow copy of this row
     */
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
