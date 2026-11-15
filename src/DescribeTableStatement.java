import java.util.Objects;

public class DescribeTableStatement implements Statement {
    private final String tableName;

    public DescribeTableStatement(String tableName) {
        this.tableName = Objects.requireNonNull(tableName, "tableName cannot be null");
    }

    public String getTableName() {
        return tableName;
    }

    @Override
    public StatementType getType() {
        return StatementType.DESCRIBE_TABLE;
    }
}
