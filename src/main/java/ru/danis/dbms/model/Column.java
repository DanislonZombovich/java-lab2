package ru.danis.dbms.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Represents a column schema definition in a database table.
 */
public class Column {
    private final String name;
    private final DataType type;
    private final boolean primaryKey;
    private final boolean nullable;
    private final ForeignKey foreignKey;

    /**
     * Creates a column definition.
     *
     * @param name       column name
     * @param type       column data type
     * @param primaryKey true if column is primary key
     * @param nullable   true if column accepts null values
     * @param foreignKey optional foreign key constraint
     */
    @JsonCreator
    public Column(
            @JsonProperty("name") String name,
            @JsonProperty("type") DataType type,
            @JsonProperty("primaryKey") boolean primaryKey,
            @JsonProperty("nullable") boolean nullable,
            @JsonProperty("foreignKey") ForeignKey foreignKey) {
        this.name = Objects.requireNonNull(name, "Column name cannot be null");
        this.type = Objects.requireNonNull(type, "Column type cannot be null");
        this.primaryKey = primaryKey;
        this.nullable = !primaryKey && nullable;
        this.foreignKey = foreignKey;
    }

    /**
     * Creates a column with optional primary key flag.
     *
     * @param name       column name
     * @param type       column data type
     * @param primaryKey true if column is primary key
     */
    public Column(String name, DataType type, boolean primaryKey) {
        this(name, type, primaryKey, !primaryKey, null);
    }

    /**
     * Creates a nullable non-key column.
     *
     * @param name column name
     * @param type column data type
     */
    public Column(String name, DataType type) {
        this(name, type, false, true, null);
    }

    /**
     * Creates a nullable column with a foreign key reference.
     *
     * @param name       column name
     * @param type       column data type
     * @param foreignKey foreign key constraint
     */
    public Column(String name, DataType type, ForeignKey foreignKey) {
        this(name, type, false, true, foreignKey);
    }

    /**
     * Returns the column name.
     *
     * @return column name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the column data type.
     *
     * @return column data type
     */
    public DataType getType() {
        return type;
    }

    /**
     * Returns whether this column is the primary key.
     *
     * @return true if this column is the primary key
     */
    public boolean isPrimaryKey() {
        return primaryKey;
    }

    /**
     * Returns whether NULL values are permitted.
     *
     * @return true if null values are permitted
     */
    public boolean isNullable() {
        return nullable;
    }

    /**
     * Returns the referenced foreign key constraint, if present.
     *
     * @return foreign key definition or null
     */
    public ForeignKey getForeignKey() {
        return foreignKey;
    }

    /**
     * Returns whether this column declares a foreign key reference.
     *
     * @return true if this column defines a foreign key
     */
    public boolean hasForeignKey() {
        return foreignKey != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Column column)) return false;
        return primaryKey == column.primaryKey &&
               nullable == column.nullable &&
               name.equalsIgnoreCase(column.name) &&
               type == column.type &&
               Objects.equals(foreignKey, column.foreignKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name.toLowerCase(), type, primaryKey, nullable, foreignKey);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append(" ").append(type);
        if (primaryKey) {
            sb.append(" PRIMARY KEY");
        } else if (!nullable) {
            sb.append(" NOT NULL");
        }
        if (foreignKey != null) {
            sb.append(" REFERENCES ").append(foreignKey);
        }
        return sb.toString();
    }
}
