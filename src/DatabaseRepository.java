

import java.io.IOException;
import java.util.*;

/**
 * Database-level repository coordinating tables, schemas, relations, and transactional integrity.
 */
public class DatabaseRepository {

    private final StorageEngine storageEngine;
    private final Map<String, TableRepository> tables;

    /**
     * Creates a database coordinator over the given storage engine.
     *
     * @param storageEngine persistence engine
     */
    public DatabaseRepository(StorageEngine storageEngine) {
        this.storageEngine = Objects.requireNonNull(storageEngine, "storageEngine cannot be null");
        this.tables = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    }

    /**
     * Initializes storage, loads schemas/rows/indexes, and reconciles indexes after unclean shutdown.
     *
     * @throws IOException if storage loading fails
     */
    public synchronized void initialize() throws IOException {
        storageEngine.initialize();
        List<WalEntry> pendingWal = storageEngine.readWalEntries();
        boolean hasPendingWal = !pendingWal.isEmpty();

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
                repo.reconcileIndexes(hasPendingWal);
                tables.put(schema.getTableName(), repo);
            }
        }

        if (hasPendingWal) {
            storageEngine.checkpoint();
        }
    }

    /**
     * Checks if a table exists.
     *
     * @param tableName table name
     * @return true if present
     */
    public synchronized boolean hasTable(String tableName) {
        return tables.containsKey(tableName);
    }

    /**
     * Retrieves a table repository or throws if absent.
     *
     * @param tableName table name
     * @return TableRepository instance
     */
    public synchronized TableRepository getTable(String tableName) {
        TableRepository repo = tables.get(tableName);
        if (repo == null) {
            throw new IllegalArgumentException("Table not found: " + tableName);
        }
        return repo;
    }

    /**
     * Returns a list of all registered table names.
     *
     * @return list of registered table names
     */
    public synchronized List<String> getTableNames() {
        return new ArrayList<>(tables.keySet());
    }

    /**
     * Creates a new table and persists its empty state.
     *
     * @param schema table schema definition
     * @throws IOException if disk persistence fails
     */
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
        storageEngine.appendWal("CREATE_TABLE", name, schema.toString());
        tables.put(name, repo);
        storageEngine.saveTable(schema, Collections.emptyList());
    }

    /**
     * Drops an existing table if no child table references it.
     *
     * @param tableName table name
     * @throws IOException if disk cleanup fails
     */
    public synchronized void dropTable(String tableName) throws IOException {
        getTable(tableName);

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

        storageEngine.appendWal("DROP_TABLE", tableName, "DROPPED");
        tables.remove(tableName);
        storageEngine.dropTable(tableName);
    }

    /**
     * Creates and persists a B-Tree index on a table column.
     *
     * @param indexName  unique index name
     * @param tableName  target table name
     * @param columnName target column name
     * @throws IOException if persistence fails
     */
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
        storageEngine.appendWal("CREATE_INDEX", tableName, indexName + " ON (" + columnName + ")");
        repo.addIndex(index);
    }

    /**
     * Drops an index by name across registered tables.
     *
     * @param indexName index name
     * @throws IOException if disk cleanup fails
     */
    public synchronized void dropIndex(String indexName) throws IOException {
        boolean found = false;
        for (TableRepository repo : tables.values()) {
            if (repo.getIndex(indexName) != null) {
                storageEngine.appendWal("DROP_INDEX", repo.getSchema().getTableName(), indexName);
                repo.removeIndex(indexName);
                found = true;
                break;
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Index not found: " + indexName);
        }
    }

    /**
     * Validates foreign key references before inserting a row.
     *
     * @param tableName target table name
     * @param row       row being inserted
     */
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

    /**
     * Validates foreign key integrity for both child references and parent mutations on update.
     *
     * @param tableName target table name
     * @param condition filter predicate or null
     * @param newValues proposed column updates
     */
    public synchronized void validateForeignKeysOnUpdate(String tableName, Condition condition, Map<String, Object> newValues) {
        TableRepository repo = getTable(tableName);
        TableSchema schema = repo.getSchema();

        Map<String, Object> caseInsensitiveUpdates = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        caseInsensitiveUpdates.putAll(newValues);

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
            if (col.hasForeignKey() && caseInsensitiveUpdates.containsKey(col.getName())) {
                Object rawVal = caseInsensitiveUpdates.get(col.getName());
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
                    if (caseInsensitiveUpdates.containsKey(referencedCol)) {
                        DataType refType = schema.getColumn(referencedCol).getType();
                        Object newTargetVal = refType.cast(caseInsensitiveUpdates.get(referencedCol));

                        for (Row parentRow : matchingRows) {
                            Object oldVal = parentRow.get(referencedCol);
                            if (oldVal != null && (newTargetVal == null || refType.compareValues(oldVal, newTargetVal) != 0)) {
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

    /**
     * Validates that deleting matching rows will not violate child table foreign keys.
     *
     * @param tableName target table name
     * @param condition filter predicate or null
     */
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

    /**
     * Performs a clean shutdown checkpoint on the underlying storage engine.
     *
     * @throws IOException if checkpoint fails
     */
    public synchronized void close() throws IOException {
        storageEngine.close();
    }
}
