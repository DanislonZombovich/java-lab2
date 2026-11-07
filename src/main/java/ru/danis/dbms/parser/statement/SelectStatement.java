package ru.danis.dbms.parser.statement;

import ru.danis.dbms.parser.Condition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Statement representing SELECT queries.
 */
public class SelectStatement implements Statement {
    private final String tableName;
    private final List<String> columns;
    private final boolean selectAll;
    private final Condition condition;

    public SelectStatement(String tableName, List<String> columns, boolean selectAll, Condition condition) {
        this.tableName = Objects.requireNonNull(tableName, "tableName cannot be null");
        this.columns = columns == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(columns));
        this.selectAll = selectAll;
        this.condition = condition;
    }

    public String getTableName() {
        return tableName;
    }

    public List<String> getColumns() {
        return columns;
    }

    public boolean isSelectAll() {
        return selectAll;
    }

    public Condition getCondition() {
        return condition;
    }

    public boolean hasCondition() {
        return condition != null;
    }

    @Override
    public StatementType getType() {
        return StatementType.SELECT;
    }
}
