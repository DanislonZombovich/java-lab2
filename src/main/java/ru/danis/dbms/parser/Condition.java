package ru.danis.dbms.parser;

import ru.danis.dbms.model.DataType;
import ru.danis.dbms.model.Row;
import ru.danis.dbms.model.TableSchema;

import java.util.Objects;
import java.util.Set;

/**
 * Predicate condition for filtering records in WHERE clauses.
 */
public class Condition {

    /**
     * Logical operator combining two child conditions.
     */
    public enum LogicalConnector {
        /** Logical conjunction. */
        AND,
        /** Logical disjunction. */
        OR
    }

    private final String columnName;
    private final ComparisonOperator operator;
    private final Object targetValue;

    private final Condition left;
    private final LogicalConnector connector;
    private final Condition right;

    /**
     * Creates a simple column comparison condition.
     *
     * @param columnName target column name
     * @param operator comparison operator
     * @param targetValue literal value to compare against, or null for NULL checks
     */
    public Condition(String columnName, ComparisonOperator operator, Object targetValue) {
        this.columnName = Objects.requireNonNull(columnName, "columnName cannot be null");
        this.operator = Objects.requireNonNull(operator, "operator cannot be null");
        this.targetValue = targetValue;
        this.left = null;
        this.connector = null;
        this.right = null;
    }

    /**
     * Creates a compound logical condition combining two sub-conditions.
     *
     * @param left left-hand condition
     * @param connector logical connector (AND or OR)
     * @param right right-hand condition
     */
    public Condition(Condition left, LogicalConnector connector, Condition right) {
        this.left = Objects.requireNonNull(left, "left condition cannot be null");
        this.connector = Objects.requireNonNull(connector, "connector cannot be null");
        this.right = Objects.requireNonNull(right, "right condition cannot be null");
        this.columnName = null;
        this.operator = null;
        this.targetValue = null;
    }

    /**
     * Returns whether this condition is a leaf comparison rather than a compound node.
     *
     * @return true if this is a simple comparison condition
     */
    public boolean isSimple() {
        return left == null && right == null;
    }

    /**
     * Returns the target column name for a simple condition.
     *
     * @return column name, or null for compound conditions
     */
    public String getColumnName() {
        return columnName;
    }

    /**
     * Returns the comparison operator for a simple condition.
     *
     * @return operator, or null for compound conditions
     */
    public ComparisonOperator getOperator() {
        return operator;
    }

    /**
     * Returns the right-hand literal value for a simple condition.
     *
     * @return target value, or null
     */
    public Object getTargetValue() {
        return targetValue;
    }

    /**
     * Returns the left-hand sub-condition for a compound condition.
     *
     * @return left condition, or null for simple conditions
     */
    public Condition getLeft() {
        return left;
    }

    /**
     * Returns the logical connector for a compound condition.
     *
     * @return logical connector, or null for simple conditions
     */
    public LogicalConnector getConnector() {
        return connector;
    }

    /**
     * Returns the right-hand sub-condition for a compound condition.
     *
     * @return right condition, or null for simple conditions
     */
    public Condition getRight() {
        return right;
    }

    /**
     * Collects all column names referenced by this condition tree into the given set.
     *
     * @param columns destination set for referenced column names
     */
    public void collectColumns(Set<String> columns) {
        if (isSimple()) {
            columns.add(columnName);
        } else {
            left.collectColumns(columns);
            right.collectColumns(columns);
        }
    }

    /**
     * Evaluates this condition against a table row using column types from the schema.
     *
     * @param row row to evaluate
     * @param schema owning table schema
     * @return true if the row satisfies the condition
     */
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
