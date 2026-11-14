
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Represents a foreign key constraint linking a column to a primary key in another table.
 */
public class ForeignKey {
    private final String targetTable;
    private final String targetColumn;

    /**
     * Creates a foreign key reference.
     *
     * @param targetTable  referenced table name
     * @param targetColumn referenced column name
     */
    @JsonCreator
    public ForeignKey(
            @JsonProperty("targetTable") String targetTable,
            @JsonProperty("targetColumn") String targetColumn) {
        this.targetTable = Objects.requireNonNull(targetTable, "targetTable cannot be null");
        this.targetColumn = Objects.requireNonNull(targetColumn, "targetColumn cannot be null");
    }

    /**
     * Returns the referenced parent table name.
     *
     * @return referenced table name
     */
    public String getTargetTable() {
        return targetTable;
    }

    /**
     * Returns the referenced column name in the parent table.
     *
     * @return referenced column name
     */
    public String getTargetColumn() {
        return targetColumn;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ForeignKey that)) return false;
        return targetTable.equalsIgnoreCase(that.targetTable) &&
               targetColumn.equalsIgnoreCase(that.targetColumn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetTable.toLowerCase(), targetColumn.toLowerCase());
    }

    @Override
    public String toString() {
        return targetTable + "(" + targetColumn + ")";
    }
}
