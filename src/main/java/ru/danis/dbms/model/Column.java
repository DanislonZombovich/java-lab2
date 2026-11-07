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
        this.nullable = primaryKey ? false : nullable;
        this.foreignKey = foreignKey;
    }

    public Column(String name, DataType type, boolean primaryKey) {
        this(name, type, primaryKey, !primaryKey, null);
    }

    public Column(String name, DataType type) {
        this(name, type, false, true, null);
    }

    public Column(String name, DataType type, ForeignKey foreignKey) {
        this(name, type, false, true, foreignKey);
    }

    public String getName() {
        return name;
    }

    public DataType getType() {
        return type;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public boolean isNullable() {
        return nullable;
    }

    public ForeignKey getForeignKey() {
        return foreignKey;
    }

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
