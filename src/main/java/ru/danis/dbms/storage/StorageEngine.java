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

    void initialize() throws IOException;

    void saveTable(TableSchema schema, List<Row> rows) throws IOException;

    TableSchema loadSchema(String tableName) throws IOException;

    List<Row> loadRows(String tableName, TableSchema schema) throws IOException;

    void dropTable(String tableName) throws IOException;

    List<String> listTables() throws IOException;

    void saveIndex(Index index) throws IOException;

    Index loadIndex(String tableName, String indexName, String columnName, DataType keyType) throws IOException;

    void dropIndex(String tableName, String indexName) throws IOException;

    List<String> listIndexesForTable(String tableName) throws IOException;

    void appendWal(String operation, String tableName, String details) throws IOException;

    void checkpoint() throws IOException;

    List<WalEntry> readWalEntries() throws IOException;

    void close() throws IOException;
}
