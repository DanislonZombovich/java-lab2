package ru.danis.dbms.storage;

import ru.danis.dbms.index.Index;
import ru.danis.dbms.model.DataType;
import ru.danis.dbms.model.Row;
import ru.danis.dbms.model.TableSchema;

import java.io.IOException;
import java.util.List;

/**
 * Storage engine interface responsible for on-disk persistence, WAL logging, and index serialization.
 */
public interface StorageEngine {

    /**
     * Initializes storage directories and WAL state.
     *
     * @throws IOException if storage initialization fails
     */
    void initialize() throws IOException;

    /**
     * Atomically persists table schema and row data to disk.
     *
     * @param schema table schema
     * @param rows   rows to store
     * @throws IOException if disk write fails
     */
    void saveTable(TableSchema schema, List<Row> rows) throws IOException;

    /**
     * Loads a table schema from disk.
     *
     * @param tableName table name
     * @return loaded TableSchema or null if absent
     * @throws IOException if disk read fails
     */
    TableSchema loadSchema(String tableName) throws IOException;

    /**
     * Loads all table rows from disk and casts them to schema types.
     *
     * @param tableName table name
     * @param schema    table schema
     * @return list of loaded rows
     * @throws IOException if disk read fails
     */
    List<Row> loadRows(String tableName, TableSchema schema) throws IOException;

    /**
     * Deletes table schema, data, and associated index files from disk.
     *
     * @param tableName table name
     * @throws IOException if deletion fails
     */
    void dropTable(String tableName) throws IOException;

    /**
     * Lists persisted table names.
     *
     * @return list of table names
     * @throws IOException if directory listing fails
     */
    List<String> listTables() throws IOException;

    /**
     * Atomically serializes an index to disk.
     *
     * @param index index instance
     * @throws IOException if serialization fails
     */
    void saveIndex(Index index) throws IOException;

    /**
     * Loads an index from disk.
     *
     * @param tableName  table name
     * @param indexName  index name
     * @param columnName optional column name override
     * @param keyType    optional key data type override
     * @return loaded Index or null if absent
     * @throws IOException if read fails
     */
    Index loadIndex(String tableName, String indexName, String columnName, DataType keyType) throws IOException;

    /**
     * Loads an index from disk using stored metadata.
     *
     * @param tableName table name
     * @param indexName index name
     * @return loaded Index or null if absent
     * @throws IOException if read fails
     */
    default Index loadIndex(String tableName, String indexName) throws IOException {
        return loadIndex(tableName, indexName, null, null);
    }

    /**
     * Deletes an index file from disk.
     *
     * @param tableName table name
     * @param indexName index name
     * @throws IOException if deletion fails
     */
    void dropIndex(String tableName, String indexName) throws IOException;

    /**
     * Lists persisted index names belonging to a table.
     *
     * @param tableName table name
     * @return list of index names
     * @throws IOException if listing fails
     */
    List<String> listIndexesForTable(String tableName) throws IOException;

    /**
     * Appends a record to the Write-Ahead Log.
     *
     * @param operation operation label
     * @param tableName target table name
     * @param details   operation details
     * @throws IOException if WAL write fails
     */
    void appendWal(String operation, String tableName, String details) throws IOException;

    /**
     * Truncates the Write-Ahead Log after a consistent checkpoint.
     *
     * @throws IOException if truncation fails
     */
    void checkpoint() throws IOException;

    /**
     * Reads all currently persisted WAL entries.
     *
     * @return list of WAL entries
     * @throws IOException if WAL read fails
     */
    List<WalEntry> readWalEntries() throws IOException;

    /**
     * Closes the storage engine cleanly with a final checkpoint.
     *
     * @throws IOException if checkpoint fails
     */
    void close() throws IOException;
}
