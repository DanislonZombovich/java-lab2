package ru.danis.dbms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.danis.dbms.engine.QueryEngine;
import ru.danis.dbms.model.QueryResult;
import ru.danis.dbms.repository.DatabaseRepository;
import ru.danis.dbms.storage.FileStorageEngine;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class SkepticalReviewTest {

    @Test
    @DisplayName("Bug 1: UPDATE changing primary key corrupts secondary index lookups")
    void testUpdatePrimaryKeyCorruptsIndex(@TempDir Path tempDir) throws IOException {
        FileStorageEngine storage = new FileStorageEngine(tempDir);
        DatabaseRepository repo = new DatabaseRepository(storage);
        repo.initialize();
        QueryEngine engine = new QueryEngine(repo);

        engine.execute("CREATE TABLE cars (id INT PRIMARY KEY, brand VARCHAR, price DOUBLE);");
        engine.execute("CREATE INDEX idx_cars_brand ON cars (brand);");
        engine.execute("INSERT INTO cars VALUES (1, 'Toyota', 25000.0);");

        QueryResult resBefore = engine.execute("SELECT * FROM cars WHERE brand = 'Toyota';");
        assertEquals(1, resBefore.getRows().size());

        engine.execute("UPDATE cars SET id = 10 WHERE id = 1;");

        QueryResult resAfter = engine.execute("SELECT * FROM cars WHERE brand = 'Toyota';");
        assertEquals(1, resAfter.getRows().size(), "Index scan should find updated row with id 10");
        assertEquals(10L, resAfter.getRows().get(0).get("id"));
    }

    @Test
    @DisplayName("Bug 2: UPDATE ignores Foreign Key constraints")
    void testUpdateForeignKeysIgnored(@TempDir Path tempDir) throws IOException {
        FileStorageEngine storage = new FileStorageEngine(tempDir);
        DatabaseRepository repo = new DatabaseRepository(storage);
        repo.initialize();
        QueryEngine engine = new QueryEngine(repo);

        engine.execute("CREATE TABLE manufacturers (id INT PRIMARY KEY, name VARCHAR);");
        engine.execute("CREATE TABLE cars (id INT PRIMARY KEY, brand VARCHAR, manufacturer_id INT REFERENCES manufacturers(id));");
        engine.execute("INSERT INTO manufacturers VALUES (1, 'Toyota');");
        engine.execute("INSERT INTO cars VALUES (10, 'Corolla', 1);");

        assertThrows(Exception.class, () -> {
            engine.execute("UPDATE cars SET manufacturer_id = 999 WHERE id = 10;");
        }, "Updating foreign key to non-existent parent must be rejected");

        assertThrows(Exception.class, () -> {
            engine.execute("UPDATE manufacturers SET id = 2 WHERE id = 1;");
        }, "Updating parent primary key referenced by child must be rejected");
    }

    @Test
    @DisplayName("Bug 3: Table names with underscores conflict in index loading")
    void testTableNamesWithUnderscoresIndexConflict(@TempDir Path tempDir) throws IOException {
        FileStorageEngine storage = new FileStorageEngine(tempDir);
        DatabaseRepository repo = new DatabaseRepository(storage);
        repo.initialize();
        QueryEngine engine = new QueryEngine(repo);

        engine.execute("CREATE TABLE car (id INT PRIMARY KEY, name VARCHAR);");
        engine.execute("CREATE TABLE car_parts (id INT PRIMARY KEY, part_name VARCHAR);");
        engine.execute("CREATE INDEX idx_part ON car_parts (part_name);");

        FileStorageEngine reloadedStorage = new FileStorageEngine(tempDir);
        DatabaseRepository reloadedRepo = new DatabaseRepository(reloadedStorage);
        reloadedRepo.initialize();

        assertTrue(reloadedRepo.getTable("car").getIndexes().isEmpty(),
                "Table 'car' should not have loaded 'car_parts' index!");
    }

    @Test
    @DisplayName("Bug 4: Index restoration with arbitrary index name picks wrong column")
    void testIndexRestorationWithArbitraryName(@TempDir Path tempDir) throws IOException {
        FileStorageEngine storage = new FileStorageEngine(tempDir);
        DatabaseRepository repo = new DatabaseRepository(storage);
        repo.initialize();
        QueryEngine engine = new QueryEngine(repo);

        engine.execute("CREATE TABLE products (sku VARCHAR, price DOUBLE);");
        engine.execute("CREATE INDEX idx_fast ON products (price);");

        FileStorageEngine reloadedStorage = new FileStorageEngine(tempDir);
        DatabaseRepository reloadedRepo = new DatabaseRepository(reloadedStorage);
        reloadedRepo.initialize();

        assertNotNull(reloadedRepo.getTable("products").getIndexForColumn("price"),
                "Index on 'price' should be restored for column 'price'");
        assertNull(reloadedRepo.getTable("products").getIndexForColumn("sku"),
                "Index should NOT be assigned to column 'sku'");
    }

    @Test
    @DisplayName("Bug 5: Batch insert atomicity failure")
    void testBatchInsertAtomicity(@TempDir Path tempDir) throws IOException {
        FileStorageEngine storage = new FileStorageEngine(tempDir);
        DatabaseRepository repo = new DatabaseRepository(storage);
        repo.initialize();
        QueryEngine engine = new QueryEngine(repo);

        engine.execute("CREATE TABLE items (id INT PRIMARY KEY, name VARCHAR);");
        engine.execute("INSERT INTO items VALUES (1, 'Existing');");

        assertThrows(IllegalArgumentException.class, () -> {
            engine.execute("INSERT INTO items VALUES (2, 'Second'), (1, 'Duplicate');");
        });

        QueryResult res = engine.execute("SELECT * FROM items;");
        assertEquals(1, res.getRows().size(), "Failed batch insert should not leave partial row (id=2) in memory");
    }

    @Test
    @DisplayName("Bug 6: Inserting string literal 'null' in NOT NULL column fails")
    void testStringLiteralNull(@TempDir Path tempDir) throws IOException {
        FileStorageEngine storage = new FileStorageEngine(tempDir);
        DatabaseRepository repo = new DatabaseRepository(storage);
        repo.initialize();
        QueryEngine engine = new QueryEngine(repo);

        engine.execute("CREATE TABLE notes (id INT PRIMARY KEY, content VARCHAR NOT NULL);");
        assertDoesNotThrow(() -> {
            engine.execute("INSERT INTO notes VALUES (1, 'null');");
        });

        QueryResult res = engine.execute("SELECT * FROM notes WHERE id = 1;");
        assertEquals("null", res.getRows().get(0).get("content"));
    }

    @Test
    @DisplayName("Bug 7: Double Primary Key is preserved without truncation during index restoration")
    void testDoublePrimaryKeyIndexScanAfterReload(@TempDir Path tempDir) throws IOException {
        FileStorageEngine storage = new FileStorageEngine(tempDir);
        DatabaseRepository repo = new DatabaseRepository(storage);
        repo.initialize();
        QueryEngine engine = new QueryEngine(repo);

        engine.execute("CREATE TABLE measurements (val DOUBLE PRIMARY KEY, sensor VARCHAR);");
        engine.execute("CREATE INDEX idx_sensor ON measurements (sensor);");
        engine.execute("INSERT INTO measurements VALUES (12.75, 'temp1');");

        FileStorageEngine reloadedStorage = new FileStorageEngine(tempDir);
        DatabaseRepository reloadedRepo = new DatabaseRepository(reloadedStorage);
        reloadedRepo.initialize();
        QueryEngine reloadedEngine = new QueryEngine(reloadedRepo);

        QueryResult res = reloadedEngine.execute("SELECT * FROM measurements WHERE sensor = 'temp1';");
        assertEquals(1, res.getRows().size());
        assertEquals(12.75, (Double) res.getRows().get(0).get("val"), 0.0001);
    }

    @Test
    @DisplayName("Optimizer: Multi-conjunct AND query selects index scan regardless of condition order")
    void testMultiConjunctAndOptimizer(@TempDir Path tempDir) throws IOException {
        FileStorageEngine storage = new FileStorageEngine(tempDir);
        DatabaseRepository repo = new DatabaseRepository(storage);
        repo.initialize();
        QueryEngine engine = new QueryEngine(repo);

        engine.execute("CREATE TABLE inventory (id INT PRIMARY KEY, category VARCHAR, price DOUBLE, active BOOLEAN);");
        engine.execute("CREATE INDEX idx_inv_cat ON inventory (category);");
        engine.execute("INSERT INTO inventory VALUES (1, 'tools', 50.0, TRUE);");

        QueryResult exp = engine.execute("EXPLAIN SELECT * FROM inventory WHERE active = TRUE AND price > 10.0 AND category = 'tools';");
        String plan = exp.getRows().get(0).get("PLAN").toString();
        assertTrue(plan.contains("IndexScan"));
        assertTrue(plan.contains("idx_inv_cat"));
    }
}
