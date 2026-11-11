package ru.danis.dbms.engine;

import ru.danis.dbms.index.Index;
import ru.danis.dbms.model.DataType;
import ru.danis.dbms.model.Row;
import ru.danis.dbms.model.TableSchema;
import ru.danis.dbms.parser.ComparisonOperator;
import ru.danis.dbms.parser.Condition;
import ru.danis.dbms.repository.TableRepository;

import java.util.*;

/**
 * Execution plan leveraging B-Tree indexes for sub-linear record retrieval.
 */
public class IndexScanPlan implements ExecutionPlan {

    private final TableRepository repository;
    private final Index index;
    private final Condition indexedCondition;
    private final Condition remainingCondition;

    /**
     * Creates an index-backed execution plan.
     *
     * @param repository target table repository
     * @param index B-Tree index to query
     * @param indexedCondition condition applied against the indexed column
     * @param remainingCondition optional residual filter evaluated on fetched rows
     */
    public IndexScanPlan(TableRepository repository, Index index, Condition indexedCondition, Condition remainingCondition) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.index = Objects.requireNonNull(index, "index cannot be null");
        this.indexedCondition = Objects.requireNonNull(indexedCondition, "indexedCondition cannot be null");
        this.remainingCondition = remainingCondition;
    }

    @Override
    public String describe() {
        return "IndexScan(table=" + repository.getSchema().getTableName() +
               ", index=" + index.getName() +
               ", keyCondition=" + indexedCondition +
               (remainingCondition != null ? ", residualFilter=" + remainingCondition : "") + ")";
    }

    @Override
    public List<Row> execute() {
        TableSchema schema = repository.getSchema();
        DataType keyType = index.getIndexedType();
        Object targetVal = indexedCondition.getTargetValue();
        Comparable<?> castKey = (Comparable<?>) keyType.cast(targetVal);
        ComparisonOperator op = indexedCondition.getOperator();

        List<Object> matchingIds;
        switch (op) {
            case EQUALS -> matchingIds = index.search(castKey);
            case GREATER_THAN -> matchingIds = index.searchRange(castKey, null, false, false);
            case GREATER_THAN_OR_EQUAL -> matchingIds = index.searchRange(castKey, null, true, false);
            case LESS_THAN -> matchingIds = index.searchRange(null, castKey, false, false);
            case LESS_THAN_OR_EQUAL -> matchingIds = index.searchRange(null, castKey, false, true);
            default -> {
                matchingIds = new ArrayList<>();
            }
        }

        List<Row> rows = new ArrayList<>(matchingIds.size());
        List<Row> allRows = null;

        for (Object id : matchingIds) {
            Row row = null;
            if (schema.hasPrimaryKey()) {
                row = repository.findByPrimaryKey(id);
            } else if (id instanceof Number n) {
                if (allRows == null) {
                    allRows = repository.findAll();
                }
                int idx = n.intValue();
                if (idx >= 0 && idx < allRows.size()) {
                    row = allRows.get(idx);
                }
            }

            if (row != null) {
                if (remainingCondition == null || remainingCondition.test(row, schema)) {
                    rows.add(row);
                }
            }
        }

        return rows;
    }
}
