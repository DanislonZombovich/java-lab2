import java.io.IOException;
import java.util.List;
import java.util.Map;

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

    void reconcileIndexes(boolean deepVerify) throws IOException;
}
