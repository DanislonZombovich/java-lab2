package ru.danis.dbms.repository;

import ru.danis.dbms.index.Index;
import ru.danis.dbms.model.Row;
import ru.danis.dbms.model.TableSchema;
import ru.danis.dbms.parser.Condition;
import ru.danis.dbms.storage.RowIterator;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Repository interface providing CRUD operations and index management for a table.
 */
public interface TableRepository {

    TableSchema getSchema();

    void insert(Row row) throws IOException;

    void insertBatch(List<Row> rows) throws IOException;

    Row findByPrimaryKey(Object pk);

    List<Row> findAll();

    RowIterator iterator();

    int update(Condition condition, Map<String, Object> newValues) throws IOException;

    int delete(Condition condition) throws IOException;

    int count();

    void addIndex(Index index) throws IOException;

    void removeIndex(String indexName) throws IOException;

    Index getIndex(String indexName);

    Index getIndexForColumn(String columnName);

    List<Index> getIndexes();

    void flush() throws IOException;
}
