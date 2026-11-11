package ru.danis.dbms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.danis.dbms.benchmark.BenchmarkRunner;
import ru.danis.dbms.cli.ConsoleApp;
import ru.danis.dbms.engine.QueryEngine;
import ru.danis.dbms.index.BTreeIndex;
import ru.danis.dbms.model.DataType;
import ru.danis.dbms.model.QueryResult;
import ru.danis.dbms.parser.SqlParser;
import ru.danis.dbms.parser.statement.SelectStatement;
import ru.danis.dbms.parser.statement.Statement;
import ru.danis.dbms.repository.DatabaseRepository;
import ru.danis.dbms.storage.FileStorageEngine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class EdgeCaseAndIntegrityTest {

    @Test
    @DisplayName("UPDATE changing primary key updates secondary index lookups")
    void testUpdatePrimaryKeyPreservesIndexLookup(@TempDir Path tempDir) throws IOException {
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
    @DisplayName("UPDATE enforces Foreign Key constraints on child and parent tables")
    void testUpdateEnforcesForeignKeys(@TempDir Path tempDir) throws IOException {
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
    @DisplayName("Table names sharing prefix with underscore isolate their persisted indexes")
    void testTableNamesWithUnderscoresIndexIsolation(@TempDir Path tempDir) throws IOException {
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
                "Table 'car' should not have loaded 'car_parts' index");
        assertNotNull(reloadedRepo.getTable("car_parts").getIndex("idx_part"));
    }

    @Test
    @DisplayName("Index restoration preserves target column when index name does not contain column name")
    void testIndexRestorationPreservesColumnAndName(@TempDir Path tempDir) throws IOException {
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
                "Index should not be assigned to column 'sku'");
    }

    @Test
    @DisplayName("Batch insert fails atomically without leaving partial rows")
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
        assertEquals(1, res.getRows().size(), "Failed batch insert must not leave partial rows");
    }

    @Test
    @DisplayName("Quoted string literal 'null' is stored as text rather than SQL NULL")
    void testStringLiteralNullInNotNullColumn(@TempDir Path tempDir) throws IOException {
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
    @DisplayName("DOUBLE primary key is preserved across disk persistence for index lookups")
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
    @DisplayName("Optimizer selects index scan across multiple AND conjuncts regardless of order")
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

    @Test
    @DisplayName("B-Tree randomized insert/delete oracle matches TreeMap across point and range queries")
    void testBTreeRandomizedDeleteOracle() {
        BTreeIndex btree = new BTreeIndex("idx_rand", "bench", "val", DataType.INT);
        TreeMap<Long, Set<Long>> oracle = new TreeMap<>();
        Random rng = new Random(12345L);

        List<long[]> inserted = new ArrayList<>(5000);
        for (int i = 0; i < 5000; i++) {
            long key = rng.nextInt(1200);
            long id = i + 1L;
            btree.insert(key, id);
            oracle.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(id);
            inserted.add(new long[]{key, id});
        }
        assertEquals(5000, btree.size());

        Collections.shuffle(inserted, rng);
        for (int i = 0; i < 4500; i++) {
            long[] pair = inserted.get(i);
            btree.delete(pair[0], pair[1]);
            Set<Long> ids = oracle.get(pair[0]);
            ids.remove(pair[1]);
            if (ids.isEmpty()) {
                oracle.remove(pair[0]);
            }

            if (i % 500 == 0) {
                assertEquals(5000 - i - 1, btree.size());
                long probe = rng.nextInt(1200);
                Set<Long> expectedPoint = oracle.getOrDefault(probe, Collections.emptySet());
                assertEquals(expectedPoint, toLongSet(btree.search(probe)));

                long low = 200L;
                long high = 650L;
                Set<Long> expectedRange = new LinkedHashSet<>();
                for (Map.Entry<Long, Set<Long>> e : oracle.subMap(low, false, high, true).entrySet()) {
                    expectedRange.addAll(e.getValue());
                }
                assertEquals(expectedRange, toLongSet(btree.searchRange(low, high, false, true)));
            }
        }

        assertEquals(500, btree.size());
        for (int k = 0; k < 1200; k++) {
            long key = k;
            assertEquals(oracle.getOrDefault(key, Collections.emptySet()), toLongSet(btree.search(key)));
        }

        for (int i = 4500; i < 5000; i++) {
            long[] pair = inserted.get(i);
            btree.delete(pair[0], pair[1]);
        }
        assertEquals(0, btree.size());
        assertTrue(btree.searchRange(null, null, false, false).isEmpty());
    }

    private Set<Long> toLongSet(List<Object> list) {
        Set<Long> set = new LinkedHashSet<>(list.size());
        for (Object o : list) {
            set.add(((Number) o).longValue());
        }
        return set;
    }

    @Test
    @DisplayName("Crash recovery cleans leftover tmp files, reconciles stale indexes, and checkpoints WAL")
    void testCrashRecoveryCleansTmpAndReconcilesStaleIndex(@TempDir Path tempDir) throws IOException {
        FileStorageEngine storage = new FileStorageEngine(tempDir);
        DatabaseRepository repo = new DatabaseRepository(storage);
        repo.initialize();
        QueryEngine engine = new QueryEngine(repo);

        engine.execute("CREATE TABLE cars (id INT PRIMARY KEY, brand VARCHAR, price DOUBLE);");
        engine.execute("CREATE INDEX idx_brand ON cars (brand);");
        engine.execute("INSERT INTO cars VALUES (1, 'Toyota', 20000.0);");
        engine.execute("INSERT INTO cars VALUES (2, 'Honda', 22000.0);");

        BTreeIndex staleIndex = new BTreeIndex("idx_brand", "cars", "brand", DataType.VARCHAR);
        staleIndex.insert("Toyota", 1L);
        staleIndex.insert("Ghost", 99L);
        storage.saveIndex(staleIndex);

        Path leftoverTmp = tempDir.resolve("tables").resolve("cars.json.tmp.99999");
        Files.writeString(leftoverTmp, "{corrupt_partial");

        FileStorageEngine recoveredStorage = new FileStorageEngine(tempDir);
        DatabaseRepository recoveredRepo = new DatabaseRepository(recoveredStorage);
        recoveredRepo.initialize();
        QueryEngine recoveredEngine = new QueryEngine(recoveredRepo);

        assertFalse(Files.exists(leftoverTmp), "Stale temporary files must be removed during initialization");
        assertTrue(recoveredStorage.readWalEntries().isEmpty(), "WAL should be checkpointed after recovery");

        QueryResult hondaRes = recoveredEngine.execute("SELECT * FROM cars WHERE brand = 'Honda';");
        assertEquals(1, hondaRes.getRows().size());
        assertEquals(2L, hondaRes.getRows().get(0).get("id"));

        QueryResult ghostRes = recoveredEngine.execute("SELECT * FROM cars WHERE brand = 'Ghost';");
        assertTrue(ghostRes.getRows().isEmpty(), "Stale index entry must be reconciled away");
        assertEquals(2, recoveredRepo.getTable("cars").getIndex("idx_brand").size());
        recoveredRepo.close();
    }

    @Test
    @DisplayName("SqlParser handles AND/OR precedence, parentheses, VARCHAR(N), IS [NOT] NULL, and rejects trailing tokens")
    void testParserPrecedenceParenthesesIsNullAndValidation(@TempDir Path tempDir) throws IOException {
        FileStorageEngine storage = new FileStorageEngine(tempDir);
        DatabaseRepository repo = new DatabaseRepository(storage);
        repo.initialize();
        QueryEngine engine = new QueryEngine(repo);

        engine.execute("CREATE TABLE items (id INT PRIMARY KEY, tag VARCHAR(50), score DOUBLE);");
        engine.execute("INSERT INTO items VALUES (1, 'alpha', 5.0), (2, NULL, 90.0), (3, 'beta', 90.0);");

        QueryResult precRes = engine.execute("SELECT id FROM items WHERE tag = 'alpha' OR tag = 'beta' AND score > 50;");
        assertEquals(2, precRes.getRows().size(), "AND must bind tighter than OR (matches id=1 and id=3)");

        QueryResult parenRes = engine.execute("SELECT id FROM items WHERE (tag = 'alpha' OR tag = 'beta') AND score > 50;");
        assertEquals(1, parenRes.getRows().size());
        assertEquals(3L, parenRes.getRows().get(0).get("id"));

        QueryResult isNullRes = engine.execute("SELECT id FROM items WHERE tag IS NULL;");
        assertEquals(1, isNullRes.getRows().size());
        assertEquals(2L, isNullRes.getRows().get(0).get("id"));

        QueryResult isNotNullRes = engine.execute("SELECT id FROM items WHERE tag IS NOT NULL;");
        assertEquals(2, isNotNullRes.getRows().size());

        assertThrows(IllegalArgumentException.class, () -> {
            new SqlParser("SELECT * FROM items EXTRA_TOKEN;").parse();
        });
        assertThrows(IllegalArgumentException.class, () -> {
            new SqlParser("EXPLAIN SELECT * FROM items EXTRA;").parse();
        });
        assertThrows(IllegalArgumentException.class, () -> {
            new SqlParser("UPDATE items SET score > 10;").parse();
        });

        Statement commented = new SqlParser("SELECT * FROM items WHERE id > -10 -- inline SQL comment\n;").parse();
        assertInstanceOf(SelectStatement.class, commented);
    }

    @Test
    @DisplayName("UPDATE setting NOT NULL column to NULL is rejected cleanly")
    void testUpdateNotNullToNullRejected(@TempDir Path tempDir) throws IOException {
        FileStorageEngine storage = new FileStorageEngine(tempDir);
        DatabaseRepository repo = new DatabaseRepository(storage);
        repo.initialize();
        QueryEngine engine = new QueryEngine(repo);

        engine.execute("CREATE TABLE users (id INT PRIMARY KEY, username VARCHAR NOT NULL);");
        engine.execute("INSERT INTO users VALUES (1, 'alice');");

        assertThrows(IllegalArgumentException.class, () -> {
            engine.execute("UPDATE users SET username = NULL WHERE id = 1;");
        });

        QueryResult res = engine.execute("SELECT username FROM users WHERE id = 1;");
        assertEquals("alice", res.getRows().get(0).get("username"));
    }

    @Test
    @DisplayName("NULL predicates on indexed columns yield identical results between SeqScan and IndexScan")
    void testNullPredicateParityBetweenSeqAndIndexScan(@TempDir Path tempDir) throws IOException {
        FileStorageEngine storage = new FileStorageEngine(tempDir);
        DatabaseRepository repo = new DatabaseRepository(storage);
        repo.initialize();
        QueryEngine engine = new QueryEngine(repo);

        engine.execute("CREATE TABLE plain_cars (id INT PRIMARY KEY, brand VARCHAR);");
        engine.execute("CREATE TABLE idx_cars (id INT PRIMARY KEY, brand VARCHAR);");
        engine.execute("CREATE INDEX idx_brand ON idx_cars (brand);");

        String[] inserts = {
                "(1, 'Toyota'), (2, NULL), (3, 'BMW'), (4, NULL)"
        };
        engine.execute("INSERT INTO plain_cars VALUES " + inserts[0] + ";");
        engine.execute("INSERT INTO idx_cars VALUES " + inserts[0] + ";");

        assertEquals(
                engine.execute("SELECT id FROM plain_cars WHERE brand = NULL;").getRows().size(),
                engine.execute("SELECT id FROM idx_cars WHERE brand = NULL;").getRows().size()
        );
        assertEquals(2, engine.execute("SELECT id FROM idx_cars WHERE brand IS NULL;").getRows().size());
        assertEquals(2, engine.execute("SELECT id FROM idx_cars WHERE brand IS NOT NULL;").getRows().size());
        assertEquals(
                engine.execute("SELECT id FROM plain_cars WHERE brand != NULL;").getRows().size(),
                engine.execute("SELECT id FROM idx_cars WHERE brand != NULL;").getRows().size()
        );
    }

    @Test
    @DisplayName("Queries referencing nonexistent columns in WHERE fail explicitly")
    void testUnknownColumnInWhereRejected(@TempDir Path tempDir) throws IOException {
        FileStorageEngine storage = new FileStorageEngine(tempDir);
        DatabaseRepository repo = new DatabaseRepository(storage);
        repo.initialize();
        QueryEngine engine = new QueryEngine(repo);

        engine.execute("CREATE TABLE cars (id INT PRIMARY KEY, brand VARCHAR);");
        engine.execute("INSERT INTO cars VALUES (1, 'Toyota');");

        assertThrows(IllegalArgumentException.class, () -> engine.execute("SELECT * FROM cars WHERE unknown_col = 1;"));
        assertThrows(IllegalArgumentException.class, () -> engine.execute("UPDATE cars SET brand = 'BMW' WHERE unknown_col = 1;"));
        assertThrows(IllegalArgumentException.class, () -> engine.execute("DELETE FROM cars WHERE unknown_col = 1;"));
        assertThrows(IllegalArgumentException.class, () -> engine.execute("EXPLAIN SELECT * FROM cars WHERE unknown_col = 1;"));
    }

    @Test
    @DisplayName("VARCHAR preserves leading/trailing whitespace and embedded quotes verbatim")
    void testVarcharWhitespaceAndQuotesPreserved(@TempDir Path tempDir) throws IOException {
        FileStorageEngine storage = new FileStorageEngine(tempDir);
        DatabaseRepository repo = new DatabaseRepository(storage);
        repo.initialize();
        QueryEngine engine = new QueryEngine(repo);

        engine.execute("CREATE TABLE texts (id INT PRIMARY KEY, val VARCHAR);");
        engine.execute("INSERT INTO texts VALUES (1, '  padded  '), (2, '\"quoted\"');");

        QueryResult r1 = engine.execute("SELECT val FROM texts WHERE id = 1;");
        assertEquals("  padded  ", r1.getRows().get(0).get("val"));

        QueryResult r2 = engine.execute("SELECT val FROM texts WHERE id = 2;");
        assertEquals("\"quoted\"", r2.getRows().get(0).get("val"));

        engine.execute("UPDATE texts SET val = '  updated  ' WHERE id = 1;");
        QueryResult r3 = engine.execute("SELECT val FROM texts WHERE id = 1;");
        assertEquals("  updated  ", r3.getRows().get(0).get("val"));
    }

    @Test
    @DisplayName("BenchmarkRunner demonstrates IndexScan speedup on 10,000 rows")
    void testBenchmarkRunnerShowsIndexSpeedup(@TempDir Path tempDir) throws IOException {
        FileStorageEngine storage = new FileStorageEngine(tempDir);
        DatabaseRepository repo = new DatabaseRepository(storage);
        repo.initialize();

        BenchmarkRunner runner = new BenchmarkRunner(repo);
        List<BenchmarkRunner.BenchmarkRow> results = runner.runSuite();
        assertEquals(4, results.size());

        BenchmarkRunner.BenchmarkRow largest = results.get(results.size() - 1);
        assertEquals(10000, largest.recordCount);
        assertTrue(largest.indexScanMs < largest.seqScanMs,
                "Expected IndexScan (" + largest.indexScanMs + " ms) < SeqScan (" + largest.seqScanMs + " ms)");
        assertTrue(largest.speedup > 1.0, "Expected speedup > 1.0, was " + largest.speedup);
    }

    @Test
    @DisplayName("ConsoleApp executes REPL commands with optional semicolons and idempotent init-demo")
    void testConsoleReplCommandsAndDemoIdempotency(@TempDir Path tempDir) throws IOException {
        FileStorageEngine storage = new FileStorageEngine(tempDir);
        DatabaseRepository repo = new DatabaseRepository(storage);
        repo.initialize();
        QueryEngine engine = new QueryEngine(repo);

        String script = String.join("\n",
                "init-demo;",
                "init-demo",
                "tables;",
                "SELECT brand FROM cars WHERE brand = 'Toyota';",
                "EXPLAIN SELECT * FROM cars WHERE brand = 'Toyota';",
                "exit;"
        ) + "\n";

        ByteArrayInputStream in = new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuf, true, StandardCharsets.UTF_8);

        ConsoleApp console = new ConsoleApp(engine, in, out);
        console.start();

        String output = outBuf.toString(StandardCharsets.UTF_8);
        assertFalse(output.contains("warning:"), "Repeated init-demo should not fail with duplicate key warning");
        assertTrue(output.contains("Toyota"));
        assertTrue(output.contains("IndexScan"));
        assertTrue(output.contains("Goodbye."));
        repo.close();
    }
}
