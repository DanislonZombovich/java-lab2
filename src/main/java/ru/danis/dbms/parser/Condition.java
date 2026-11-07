package ru.danis.dbms.parser;

import ru.danis.dbms.model.DataType;
import ru.danis.dbms.model.Row;
import ru.danis.dbms.model.TableSchema;

import java.util.Objects;

/**
 * Predicate condition for filtering records in WHERE clauses.
 */
public class Condition {

    public enum LogicalConnector {
        AND,
        OR
    }

    private final String columnName;
    private final ComparisonOperator operator;
    private final Object targetValue;

    private final Condition left;
    private final LogicalConnector connector;
    private final Condition right;

    public Condition(String columnName, ComparisonOperator operator, Object targetValue) {
        this.columnName = Objects.requireNonNull(columnName, "columnName cannot be null");
        this.operator = Objects.requireNonNull(operator, "operator cannot be null");
        this.targetValue = targetValue;
        this.left = null;
        this.connector = null;
        this.right = null;
    }

    public Condition(Condition left, LogicalConnector connector, Condition right) {
        this.left = Objects.requireNonNull(left, "left condition cannot be null");
        this.connector = Objects.requireNonNull(connector, "connector cannot be null");
        this.right = Objects.requireNonNull(right, "right condition cannot be null");
        this.columnName = null;
        this.operator = null;
        this.targetValue = null;
    }

    public boolean isSimple() {
        return left == null && right == null;
    }

    public String getColumnName() {
        return columnName;
    }

    public ComparisonOperator getOperator() {
        return operator;
    }

    public Object getTargetValue() {
        return targetValue;
    }

    public Condition getLeft() {
        return left;
    }

    public LogicalConnector getConnector() {
        return connector;
    }

    public Condition getRight() {
        return right;
    }

    public boolean test(Row row, TableSchema schema) {
        if (!isSimple()) {
            boolean leftRes = left.test(row, schema);
            if (connector == LogicalConnector.AND) {
                return leftRes && right.test(row, schema);
            } else {
                return leftRes || right.test(row, schema);
            }
        }

        Object rowValue = row.get(columnName);
        if (targetValue == null && rowValue == null) {
            return operator == ComparisonOperator.EQUALS;
        }
        if (targetValue == null || rowValue == null) {
            return operator == ComparisonOperator.NOT_EQUALS;
        }

        DataType type = schema.hasColumn(columnName) ? schema.getColumn(columnName).getType() : DataType.VARCHAR;
        int cmp = type.compareValues(rowValue, targetValue);
        return operator.evaluate(cmp);
    }

    @Override
    public String toString() {
        if (!isSimple()) {
            return "(" + left + " " + connector + " " + right + ")";
        }
        return columnName + " " + operator.getSymbol() + " " + targetValue;
    }
}
