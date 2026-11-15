import java.util.Objects;

public class DeleteStatement implements Statement {
    private final String tableName;
    private final Condition condition;

    public DeleteStatement(String tableName, Condition condition) {
        this.tableName = Objects.requireNonNull(tableName, "tableName cannot be null");
        this.condition = condition;
    }

    public String getTableName() {
        return tableName;
    }

    public Condition getCondition() {
        return condition;
    }

    public boolean hasCondition() {
        return condition != null;
    }

    @Override
    public StatementType getType() {
        return StatementType.DELETE;
    }
}
