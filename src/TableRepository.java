

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Repository interface providing CRUD operations and index management for a table.
 */
public interface TableRepository {

    /**
     * Returns the schema definition of this table.
     *
     * @return table schema
     */
    TableSchema getSchema();

    /**
     * Inserts a single row after validating constraints.
     *
     * @param row row to insert
     * @throws IOException if persistence fails
     */
    void insert(Row row) throws IOException;

    /**
     * Atomically validates and inserts a batch of rows.
     *
     * @param rows list of rows to insert
     * @throws IOException if persistence fails
     */
    void insertBatch(List<Row> rows) throws IOException;

    /**
     * Finds a row in O(1) by primary key value.
     *
     * @param pk primary key value
     * @return matching Row or null
     */
    Row findByPrimaryKey(Object pk);

    /**
     * Returns an unmodifiable list containing all rows in the table.
     *
     * @return unmodifiable list of all rows
     */
    List<Row> findAll();

    /**
     * Returns a sequential RowIterator over all records in this table.
     *
     * @return sequential RowIterator over table records
     */
    RowIterator iterator();

    /**
     * Updates rows matching a condition.
     *
     * @param condition filter predicate or null for all rows
     * @param newValues column-to-value assignments
     * @return number of updated rows
     * @throws IOException if persistence fails
     */
    int update(Condition condition, Map<String, Object> newValues) throws IOException;

    /**
     * Deletes rows matching a condition.
     *
     * @param condition filter predicate or null for all rows
     * @return number of deleted rows
     * @throws IOException if persistence fails
     */
    int delete(Condition condition) throws IOException;

    /**
     * Returns the total number of rows currently stored in this table.
     *
     * @return total row count
     */
    int count();

    /**
     * Registers and builds an index on this table.
     *
     * @param index index instance
     * @throws IOException if persistence fails
     */
    void addIndex(Index index) throws IOException;

    /**
     * Drops an index from this table.
     *
     * @param indexName index name
     * @throws IOException if persistence fails
     */
    void removeIndex(String indexName) throws IOException;

    /**
     * Looks up an index by name.
     *
     * @param indexName index name
     * @return Index or null
     */
    Index getIndex(String indexName);

    /**
     * Looks up an index covering a specific column.
     *
     * @param columnName column name
     * @return Index or null
     */
    Index getIndexForColumn(String columnName);

    /**
     * Returns all indexes currently attached to this table.
     *
     * @return list of all indexes on this table
     */
    List<Index> getIndexes();

    /**
     * Flushes table data and indexes to disk.
     *
     * @throws IOException if persistence fails
     */
    void flush() throws IOException;

    /**
     * Verifies and repairs secondary indexes against current table rows.
     *
     * @param deepVerify true to verify full key-to-id mapping rather than counts only
     * @throws IOException if saving repaired indexes fails
     */
    void reconcileIndexes(boolean deepVerify) throws IOException;
}
