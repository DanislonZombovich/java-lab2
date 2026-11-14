
import java.util.Objects;

/**
 * Statement representing DESCRIBE table command.
 */
public class DescribeTableStatement implements Statement {
    private final String tableName;

    /**
     * Creates a DESCRIBE statement for the specified table.
     *
     * @param tableName table name to inspect
     */
    public DescribeTableStatement(String tableName) {
        this.tableName = Objects.requireNonNull(tableName, "tableName cannot be null");
    }

    /**
     * Returns the inspected table name.
     *
     * @return table name
     */
    public String getTableName() {
        return tableName;
    }

    @Override
    public StatementType getType() {
        return StatementType.DESCRIBE_TABLE;
    }
}
