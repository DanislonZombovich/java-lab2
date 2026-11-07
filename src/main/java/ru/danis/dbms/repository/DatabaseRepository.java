package ru.danis.dbms.repository;

import ru.danis.dbms.index.BTreeIndex;
import ru.danis.dbms.index.Index;
import ru.danis.dbms.model.*;
import ru.danis.dbms.parser.Condition;
import ru.danis.dbms.storage.StorageEngine;

import java.io.IOException;
import java.util.*;

/**
 * Database-level repository coordinating tables, schemas, relations, and transactional integrity.
 */
public class DatabaseRepository {

    private final StorageEngine storageEngine;
    private final Map<String, TableRepository> tables;

    public DatabaseRepository(StorageEngine storageEngine) {
        this.storageEngine = Objects.requireNonNull(storageEngine, "storageEngine cannot be null");
        this.tables = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    }

    public synchronized void initialize() throws IOException {
        storageEngine.initialize();
        List<String> tableNames = storageEngine.listTables();

        for (String tbl : tableNames) {
            TableSchema schema = storageEngine.loadSchema(tbl);
            if (schema != null) {
                List<Row> rows = storageEngine.loadRows(tbl, schema);
                List<Index> indexes = new ArrayList<>();
                for (String idxName : storageEngine.listIndexesForTable(tbl)) {
                    Index idx = storageEngine.loadIndex(tbl, idxName, null, null);
                    if (idx != null) {
                        indexes.add(idx);
                    }
                }
                FileTableRepository repo = new FileTableRepository(schema, storageEngine, rows, indexes);
                tables.put(schema.getTableName(), repo);
            }
        }
    }

    public synchronized boolean hasTable(String tableName) {
        return tables.containsKey(tableName);
    }

    public synchronized TableRepository getTable(String tableName) {
        TableRepository repo = tables.get(tableName);
        if (repo == null) {
            throw new IllegalArgumentException("Table not found: " + tableName);
        }
        return repo;
    }

    public synchronized List<String> getTableNames() {
        return new ArrayList<>(tables.keySet());
    }

    public synchronized void createTable(TableSchema schema) throws IOException {
        String name = schema.getTableName();
        if (hasTable(name)) {
            throw new IllegalArgumentException("Table already exists: " + name);
        }

        for (Column col : schema.getColumns()) {
            if (col.hasForeignKey()) {
                ForeignKey fk = col.getForeignKey();
                if (!hasTable(fk.getTargetTable())) {
                    throw new IllegalArgumentException("Referenced table '" + fk.getTargetTable() + "' does not exist");
                }
                TableSchema targetSchema = getTable(fk.getTargetTable()).getSchema();
                if (!targetSchema.hasColumn(fk.getTargetColumn())) {
                    throw new IllegalArgumentException("Referenced column '" + fk.getTargetColumn() + "' does not exist in table '" + fk.getTargetTable() + "'");
                }
            }
        }

        FileTableRepository repo = new FileTableRepository(schema, storageEngine, Collections.emptyList(), Collections.emptyList());
        tables.put(name, repo);
        storageEngine.saveTable(schema, Collections.emptyList());
        storageEngine.appendWal("CREATE_TABLE", name, schema.toString());
    }

    public synchronized void dropTable(String tableName) throws IOException {
        TableRepository target = getTable(tableName);

        for (Map.Entry<String, TableRepository> entry : tables.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(tableName)) {
                continue;
            }
            TableSchema otherSchema = entry.getValue().getSchema();
            for (Column col : otherSchema.getColumns()) {
                if (col.hasForeignKey() && col.getForeignKey().getTargetTable().equalsIgnoreCase(tableName)) {
                    throw new IllegalStateException("Cannot drop table '" + tableName + "' because it is referenced by table '" + otherSchema.getTableName() + "'");
                }
            }
        }

        tables.remove(tableName);
        storageEngine.dropTable(tableName);
        storageEngine.appendWal("DROP_TABLE", tableName, "DROPPED");
    }

    public synchronized void createIndex(String indexName, String tableName, String columnName) throws IOException {
        TableRepository repo = getTable(tableName);
        TableSchema schema = repo.getSchema();
        if (!schema.hasColumn(columnName)) {
            throw new IllegalArgumentException("Column '" + columnName + "' does not exist in table '" + tableName + "'");
        }
        if (repo.getIndex(indexName) != null) {
            throw new IllegalArgumentException("Index '" + indexName + "' already exists");
        }

        Column col = schema.getColumn(columnName);
        Index index = new BTreeIndex(indexName, tableName, col.getName(), col.getType());
        repo.addIndex(index);
        storageEngine.appendWal("CREATE_INDEX", tableName, indexName + " ON (" + columnName + ")");
    }

    public synchronized void dropIndex(String indexName) throws IOException {
        boolean found = false;
        for (TableRepository repo : tables.values()) {
            if (repo.getIndex(indexName) != null) {
                repo.removeIndex(indexName);
                storageEngine.appendWal("DROP_INDEX", repo.getSchema().getTableName(), indexName);
                found = true;
                break;
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Index not found: " + indexName);
        }
    }

    public synchronized void validateForeignKeysOnInsert(String tableName, Row row) {
        TableRepository repo = getTable(tableName);
        TableSchema schema = repo.getSchema();

        for (Column col : schema.getColumns()) {
            if (col.hasForeignKey()) {
                Object val = row.get(col.getName());
                if (val != null) {
                    ForeignKey fk = col.getForeignKey();
                    TableRepository targetRepo = getTable(fk.getTargetTable());
                    TableSchema targetSchema = targetRepo.getSchema();

                    boolean exists = false;
                    if (targetSchema.hasPrimaryKey() && targetSchema.getPrimaryKeyColumn().equalsIgnoreCase(fk.getTargetColumn())) {
                        exists = targetRepo.findByPrimaryKey(val) != null;
                    } else {
                        DataType targetType = targetSchema.getColumn(fk.getTargetColumn()).getType();
                        Object castVal = targetType.cast(val);
                        for (Row targetRow : targetRepo.findAll()) {
                            Object targetVal = targetRow.get(fk.getTargetColumn());
                            if (targetType.compareValues(targetVal, castVal) == 0) {
                                exists = true;
                                break;
                            }
                        }
                    }

                    if (!exists) {
                        throw new IllegalArgumentException("Foreign key violation in table '" + tableName + "': Referenced value " + val + " not found in " + fk);
                    }
                }
            }
        }
    }

    public synchronized void validateForeignKeysOnUpdate(String tableName, Condition condition, Map<String, Object> newValues) {
        TableRepository repo = getTable(tableName);
        TableSchema schema = repo.getSchema();

        List<Row> matchingRows = new ArrayList<>();
        for (Row r : repo.findAll()) {
            if (condition == null || condition.test(r, schema)) {
                matchingRows.add(r);
            }
        }
        if (matchingRows.isEmpty()) {
            return;
        }

        for (Column col : schema.getColumns()) {
            if (col.hasForeignKey() && newValues.containsKey(col.getName())) {
                Object rawVal = newValues.get(col.getName());
                if (rawVal != null) {
                    ForeignKey fk = col.getForeignKey();
                    TableRepository targetRepo = getTable(fk.getTargetTable());
                    TableSchema targetSchema = targetRepo.getSchema();

                    boolean exists = false;
                    if (targetSchema.hasPrimaryKey() && targetSchema.getPrimaryKeyColumn().equalsIgnoreCase(fk.getTargetColumn())) {
                        exists = targetRepo.findByPrimaryKey(rawVal) != null;
                    } else {
                        DataType targetType = targetSchema.getColumn(fk.getTargetColumn()).getType();
                        Object castVal = targetType.cast(rawVal);
                        for (Row targetRow : targetRepo.findAll()) {
                            Object targetVal = targetRow.get(fk.getTargetColumn());
                            if (targetType.compareValues(targetVal, castVal) == 0) {
                                exists = true;
                                break;
                            }
                        }
                    }

                    if (!exists) {
                        throw new IllegalArgumentException("Foreign key violation in table '" + tableName + "': Referenced value " + rawVal + " not found in " + fk);
                    }
                }
            }
        }

        for (TableRepository childRepo : tables.values()) {
            if (childRepo.getSchema().getTableName().equalsIgnoreCase(tableName)) {
                continue;
            }
            TableSchema childSchema = childRepo.getSchema();
            for (Column childCol : childSchema.getColumns()) {
                if (childCol.hasForeignKey() && childCol.getForeignKey().getTargetTable().equalsIgnoreCase(tableName)) {
                    String referencedCol = childCol.getForeignKey().getTargetColumn();
                    if (newValues.containsKey(referencedCol)) {
                        DataType refType = schema.getColumn(referencedCol).getType();
                        Object newTargetVal = refType.cast(newValues.get(referencedCol));

                        for (Row parentRow : matchingRows) {
                            Object oldVal = parentRow.get(referencedCol);
                            if (oldVal != null && refType.compareValues(oldVal, newTargetVal) != 0) {
                                for (Row childRow : childRepo.findAll()) {
                                    Object childVal = childRow.get(childCol.getName());
                                    if (childVal != null && refType.compareValues(oldVal, childVal) == 0) {
                                        throw new IllegalStateException("Foreign key violation: Cannot update " + referencedCol + " in table '" + tableName + "' because child table '" + childSchema.getTableName() + "' references it");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public synchronized void validateForeignKeysOnDelete(String tableName, Condition condition) {
        TableRepository repo = getTable(tableName);
        TableSchema schema = repo.getSchema();
        List<Row> rowsToDelete = new ArrayList<>();

        for (Row r : repo.findAll()) {
            if (condition == null || condition.test(r, schema)) {
                rowsToDelete.add(r);
            }
        }

        if (rowsToDelete.isEmpty()) {
            return;
        }

        for (TableRepository childRepo : tables.values()) {
            if (childRepo.getSchema().getTableName().equalsIgnoreCase(tableName)) {
                continue;
            }
            TableSchema childSchema = childRepo.getSchema();
            for (Column childCol : childSchema.getColumns()) {
                if (childCol.hasForeignKey() && childCol.getForeignKey().getTargetTable().equalsIgnoreCase(tableName)) {
                    String referencedCol = childCol.getForeignKey().getTargetColumn();
                    DataType refType = schema.getColumn(referencedCol).getType();

                    for (Row parentRow : rowsToDelete) {
                        Object parentVal = parentRow.get(referencedCol);
                        if (parentVal != null) {
                            for (Row childRow : childRepo.findAll()) {
                                Object childVal = childRow.get(childCol.getName());
                                if (childVal != null && refType.compareValues(parentVal, childVal) == 0) {
                                    throw new IllegalStateException("Foreign key violation: Cannot delete record with " + referencedCol + "=" + parentVal + " from table '" + tableName + "' because child table '" + childSchema.getTableName() + "' references it");
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
