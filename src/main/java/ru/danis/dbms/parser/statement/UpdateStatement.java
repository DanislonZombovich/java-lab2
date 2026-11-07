package ru.danis.dbms.parser.statement;

import ru.danis.dbms.parser.Condition;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Statement representing UPDATE queries.
 */
public class UpdateStatement implements Statement {
    private final String tableName;
    private final Map<String, String> rawUpdates;
    private final Condition condition;

    public UpdateStatement(String tableName, Map<String, String> rawUpdates, Condition condition) {
        this.tableName = Objects.requireNonNull(tableName, "tableName cannot be null");
        this.rawUpdates = Collections.unmodifiableMap(Objects.requireNonNull(rawUpdates, "rawUpdates cannot be null"));
        this.condition = condition;
    }

    public String getTableName() {
        return tableName;
    }

    public Map<String, String> getRawUpdates() {
        return rawUpdates;
    }

    public Condition getCondition() {
        return condition;
    }

    public boolean hasCondition() {
        return condition != null;
    }

    @Override
    public StatementType getType() {
        return StatementType.UPDATE;
    }
}
