
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class StorageEngineTest {

    @Test
    @DisplayName("Test persistence of schemas, data rows, and B-Tree indexes on disk")
    void testStoragePersistence(@TempDir Path tempDir) throws IOException {
        FileStorageEngine engine = new FileStorageEngine(tempDir);
        engine.initialize();

        List<Column> columns = List.of(
                new Column("id", DataType.INT, true),
                new Column("brand", DataType.VARCHAR),
                new Column("price", DataType.DOUBLE)
        );
        TableSchema schema = new TableSchema("cars", columns);

        Row row1 = new Row();
        row1.set("id", 1L);
        row1.set("brand", "Toyota");
        row1.set("price", 25000.0);

        Row row2 = new Row();
        row2.set("id", 2L);
        row2.set("brand", "BMW");
        row2.set("price", 35000.0);

        engine.saveTable(schema, List.of(row1, row2));

        BTreeIndex idx = new BTreeIndex("idx_brand", "cars", "brand", DataType.VARCHAR);
        idx.insert("Toyota", 1L);
        idx.insert("BMW", 2L);
        engine.saveIndex(idx);

        engine.appendWal("INSERT", "cars", "id=1, brand=Toyota");
        List<WalEntry> wal = engine.readWalEntries();
        assertEquals(1, wal.size());
        assertEquals("INSERT", wal.get(0).getOperation());

        FileStorageEngine reloadedEngine = new FileStorageEngine(tempDir);
        reloadedEngine.initialize();

        TableSchema loadedSchema = reloadedEngine.loadSchema("cars");
        assertNotNull(loadedSchema);
        assertEquals("cars", loadedSchema.getTableName());
        assertEquals(3, loadedSchema.getColumns().size());

        List<Row> loadedRows = reloadedEngine.loadRows("cars", loadedSchema);
        assertEquals(2, loadedRows.size());
        assertEquals("Toyota", loadedRows.get(0).get("brand"));
        assertEquals(25000.0, (Double) loadedRows.get(0).get("price"), 0.001);

        Index loadedIdx = reloadedEngine.loadIndex("cars", "idx_brand", "brand", DataType.VARCHAR);
        assertNotNull(loadedIdx);
        assertEquals(List.of(1L), loadedIdx.search("Toyota"));
        assertEquals(List.of(2L), loadedIdx.search("BMW"));
    }
}
