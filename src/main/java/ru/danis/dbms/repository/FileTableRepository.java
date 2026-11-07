package ru.danis.dbms.repository;

import ru.danis.dbms.index.Index;
import ru.danis.dbms.model.Column;
import ru.danis.dbms.model.DataType;
import ru.danis.dbms.model.Row;
import ru.danis.dbms.model.TableSchema;
import ru.danis.dbms.parser.Condition;
import ru.danis.dbms.storage.RowIterator;
import ru.danis.dbms.storage.StorageEngine;

import java.io.IOException;
import java.util.*;

/**
 * FileTableRepository implements TableRepository with in-memory caching and disk synchronization.
 */
public class FileTableRepository implements TableRepository {

    private final TableSchema schema;
    private final StorageEngine storageEngine;
    private final List<Row> rows;
    private final Map<Object, Row> primaryKeyIndex;
    private final Map<String, Index> indexesByName;
    private final Map<String, Index> indexesByColumn;

    public FileTableRepository(TableSchema schema, StorageEngine storageEngine, List<Row> initialRows, List<Index> initialIndexes) {
        this.schema = Objects.requireNonNull(schema, "schema cannot be null");
        this.storageEngine = Objects.requireNonNull(storageEngine, "storageEngine cannot be null");
        this.rows = new ArrayList<>(initialRows != null ? initialRows : Collections.emptyList());
        this.primaryKeyIndex = new HashMap<>();
        this.indexesByName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        this.indexesByColumn = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        if (initialIndexes != null) {
            for (Index idx : initialIndexes) {
                indexesByName.put(idx.getName(), idx);
                indexesByColumn.put(idx.getColumnName(), idx);
            }
        }

        rebuildPrimaryKeyIndex();
    }

    private void rebuildPrimaryKeyIndex() {
        primaryKeyIndex.clear();
        String pkCol = schema.getPrimaryKeyColumn();
        if (pkCol == null) {
            return;
        }

        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            Object pkVal = r.get(pkCol);
            if (pkVal != null) {
                if (primaryKeyIndex.containsKey(pkVal)) {
                    throw new IllegalStateException("Duplicate primary key found during table load: " + pkVal);
                }
                primaryKeyIndex.put(pkVal, r);
            }
        }
    }

    @Override
    public TableSchema getSchema() {
        return schema;
    }

    @Override
    public synchronized void insert(Row row) throws IOException {
        validateRow(row);
        validateUniqueConstraint(row);

        rows.add(row);
        Object rowId = getRecordIdentifier(row, rows.size() - 1);

        String pkCol = schema.getPrimaryKeyColumn();
        if (pkCol != null) {
            primaryKeyIndex.put(row.get(pkCol), row);
        }

        for (Index idx : indexesByName.values()) {
            Object val = row.get(idx.getColumnName());
            if (val instanceof Comparable<?> comp) {
                idx.insert(comp, rowId);
            }
        }

        flush();
    }

    @Override
    public synchronized void insertBatch(List<Row> batchRows) throws IOException {
        for (Row row : batchRows) {
            validateRow(row);
            validateUniqueConstraint(row);
            rows.add(row);
            Object rowId = getRecordIdentifier(row, rows.size() - 1);

            String pkCol = schema.getPrimaryKeyColumn();
            if (pkCol != null) {
                primaryKeyIndex.put(row.get(pkCol), row);
            }

            for (Index idx : indexesByName.values()) {
                Object val = row.get(idx.getColumnName());
                if (val instanceof Comparable<?> comp) {
                    idx.insert(comp, rowId);
                }
            }
        }

        flush();
    }

    @Override
    public synchronized Row findByPrimaryKey(Object pk) {
        if (pk == null || !schema.hasPrimaryKey()) {
            return null;
        }
        DataType pkType = schema.getColumn(schema.getPrimaryKeyColumn()).getType();
        Object castPk = pkType.cast(pk);
        return primaryKeyIndex.get(castPk);
    }

    @Override
    public synchronized List<Row> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(rows));
    }

    @Override
    public synchronized RowIterator iterator() {
        return new RowIterator(new ArrayList<>(rows));
    }

    @Override
    public synchronized int update(Condition condition, Map<String, Object> newValues) throws IOException {
        int updated = 0;
        String pkCol = schema.getPrimaryKeyColumn();

        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (condition == null || condition.test(r, schema)) {
                Object oldRowId = getRecordIdentifier(r, i);

                for (Index idx : indexesByName.values()) {
                    if (newValues.containsKey(idx.getColumnName())) {
                        Object oldVal = r.get(idx.getColumnName());
                        if (oldVal instanceof Comparable<?> comp) {
                            idx.delete(comp, oldRowId);
                        }
                    }
                }

                if (pkCol != null && newValues.containsKey(pkCol)) {
                    Object newPk = schema.getColumn(pkCol).getType().cast(newValues.get(pkCol));
                    Object oldPk = r.get(pkCol);
                    if (!Objects.equals(oldPk, newPk) && primaryKeyIndex.containsKey(newPk)) {
                        throw new IllegalArgumentException("Primary key violation: Key " + newPk + " already exists");
                    }
                    primaryKeyIndex.remove(oldPk);
                    primaryKeyIndex.put(newPk, r);
                }

                for (Map.Entry<String, Object> entry : newValues.entrySet()) {
                    String col = entry.getKey();
                    if (schema.hasColumn(col)) {
                        DataType type = schema.getColumn(col).getType();
                        r.set(col, type.cast(entry.getValue()));
                    }
                }

                Object newRowId = getRecordIdentifier(r, i);
                for (Index idx : indexesByName.values()) {
                    if (newValues.containsKey(idx.getColumnName())) {
                        Object newVal = r.get(idx.getColumnName());
                        if (newVal instanceof Comparable<?> comp) {
                            idx.insert(comp, newRowId);
                        }
                    }
                }

                updated++;
            }
        }

        if (updated > 0) {
            flush();
        }
        return updated;
    }

    @Override
    public synchronized int delete(Condition condition) throws IOException {
        List<Row> toRemove = new ArrayList<>();
        String pkCol = schema.getPrimaryKeyColumn();

        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (condition == null || condition.test(r, schema)) {
                toRemove.add(r);
                Object rowId = getRecordIdentifier(r, i);

                for (Index idx : indexesByName.values()) {
                    Object val = r.get(idx.getColumnName());
                    if (val instanceof Comparable<?> comp) {
                        idx.delete(comp, rowId);
                    }
                }

                if (pkCol != null) {
                    primaryKeyIndex.remove(r.get(pkCol));
                }
            }
        }

        rows.removeAll(toRemove);

        if (!toRemove.isEmpty()) {
            rebuildAllIndexes();
            flush();
        }

        return toRemove.size();
    }

    @Override
    public synchronized int count() {
        return rows.size();
    }

    @Override
    public synchronized void addIndex(Index index) throws IOException {
        indexesByName.put(index.getName(), index);
        indexesByColumn.put(index.getColumnName(), index);

        index.clear();
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            Object val = r.get(index.getColumnName());
            if (val instanceof Comparable<?> comp) {
                index.insert(comp, getRecordIdentifier(r, i));
            }
        }

        storageEngine.saveIndex(index);
    }

    @Override
    public synchronized void removeIndex(String indexName) throws IOException {
        Index idx = indexesByName.remove(indexName);
        if (idx != null) {
            indexesByColumn.remove(idx.getColumnName());
            storageEngine.dropIndex(schema.getTableName(), indexName);
        }
    }

    @Override
    public synchronized Index getIndex(String indexName) {
        return indexesByName.get(indexName);
    }

    @Override
    public synchronized Index getIndexForColumn(String columnName) {
        return indexesByColumn.get(columnName);
    }

    @Override
    public synchronized List<Index> getIndexes() {
        return new ArrayList<>(indexesByName.values());
    }

    @Override
    public synchronized void flush() throws IOException {
        storageEngine.saveTable(schema, rows);
        for (Index idx : indexesByName.values()) {
            storageEngine.saveIndex(idx);
        }
    }

    private void rebuildAllIndexes() {
        for (Index idx : indexesByName.values()) {
            idx.clear();
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                Object val = r.get(idx.getColumnName());
                if (val instanceof Comparable<?> comp) {
                    idx.insert(comp, getRecordIdentifier(r, i));
                }
            }
        }
    }

    private Object getRecordIdentifier(Row row, int index) {
        if (schema.hasPrimaryKey()) {
            return row.get(schema.getPrimaryKeyColumn());
        }
        return (long) index;
    }

    private void validateRow(Row row) {
        for (Column col : schema.getColumns()) {
            Object val = row.get(col.getName());
            if (val == null && !col.isNullable()) {
                throw new IllegalArgumentException("Column '" + col.getName() + "' cannot be null");
            }
        }
    }

    private void validateUniqueConstraint(Row row) {
        if (!schema.hasPrimaryKey()) {
            return;
        }
        String pkCol = schema.getPrimaryKeyColumn();
        Object pkVal = row.get(pkCol);
        if (pkVal == null) {
            throw new IllegalArgumentException("Primary key column '" + pkCol + "' cannot be null");
        }
        if (primaryKeyIndex.containsKey(pkVal)) {
            throw new IllegalArgumentException("Primary key violation: Key " + pkVal + " already exists in table " + schema.getTableName());
        }
    }
}
