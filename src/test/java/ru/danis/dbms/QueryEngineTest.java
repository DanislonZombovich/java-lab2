package ru.danis.dbms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.danis.dbms.engine.QueryEngine;
import ru.danis.dbms.model.QueryResult;
import ru.danis.dbms.model.Row;
import ru.danis.dbms.repository.DatabaseRepository;
import ru.danis.dbms.storage.FileStorageEngine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class QueryEngineTest {

    private Path dbDir;
    private QueryEngine engine;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        this.dbDir = tempDir;
        FileStorageEngine storage = new FileStorageEngine(tempDir);
        DatabaseRepository repo = new DatabaseRepository(storage);
        repo.initialize();
        this.engine = new QueryEngine(repo);

        engine.execute("CREATE TABLE cars (id INT PRIMARY KEY, brand VARCHAR, price DOUBLE, is_available BOOLEAN);");
    }

    @Test
    @DisplayName("Execute full suite of prompt example SQL statements")
    void testPromptSpecificationQueries() {
        QueryResult r1 = engine.execute("INSERT INTO cars VALUES (1, 'Toyota', 25000.0, TRUE);");
        assertEquals(1, r1.getRowsAffected());

        QueryResult r2 = engine.execute("INSERT INTO cars (id, brand) VALUES (2, 'BMW');");
        assertEquals(1, r2.getRowsAffected());

        QueryResult r3 = engine.execute("SELECT * FROM cars;");
        assertEquals(2, r3.getRows().size());

        QueryResult r4 = engine.execute("SELECT brand, price FROM cars WHERE price > 20000;");
        assertEquals(1, r4.getRows().size());
        assertEquals("Toyota", r4.getRows().get(0).get("brand"));
        assertEquals(25000.0, (Double) r4.getRows().get(0).get("price"), 0.001);

        QueryResult r5 = engine.execute("UPDATE cars SET price = 30000 WHERE id = 2;");
        assertEquals(1, r5.getRowsAffected());

        QueryResult r5Check = engine.execute("SELECT brand, price FROM cars WHERE price > 20000;");
        assertEquals(2, r5Check.getRows().size());

        QueryResult r6 = engine.execute("DELETE FROM cars WHERE brand = 'Toyota';");
        assertEquals(1, r6.getRowsAffected());

        QueryResult r7 = engine.execute("SELECT * FROM cars;");
        assertEquals(1, r7.getRows().size());
        assertEquals("BMW", r7.getRows().get(0).get("brand"));
    }

    @Test
    @DisplayName("Primary key uniqueness is enforced on insert")
    void testPrimaryKeyUniqueness() {
        engine.execute("INSERT INTO cars VALUES (1, 'Toyota', 25000.0, TRUE);");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            engine.execute("INSERT INTO cars VALUES (1, 'Honda', 18000.0, FALSE);");
        });
        assertTrue(ex.getMessage().contains("Primary key violation"));
    }

    @Test
    @DisplayName("Index creation and optimizer selection of IndexScan vs SeqScan")
    void testIndexScanOptimization() {
        engine.execute("INSERT INTO cars VALUES (1, 'Toyota', 25000.0, TRUE);");
        engine.execute("INSERT INTO cars VALUES (2, 'BMW', 35000.0, TRUE);");
        engine.execute("INSERT INTO cars VALUES (3, 'Audi', 30000.0, FALSE);");

        QueryResult beforePlan = engine.execute("EXPLAIN SELECT * FROM cars WHERE brand = 'BMW';");
        assertTrue(beforePlan.getRows().get(0).get("PLAN").toString().contains("SeqScan"));

        engine.execute("CREATE INDEX idx_cars_brand ON cars (brand);");

        QueryResult afterPlan = engine.execute("EXPLAIN SELECT * FROM cars WHERE brand = 'BMW';");
        String planText = afterPlan.getRows().get(0).get("PLAN").toString();
        assertTrue(planText.contains("IndexScan"));
        assertTrue(planText.contains("idx_cars_brand"));

        QueryResult selectRes = engine.execute("SELECT * FROM cars WHERE brand = 'BMW';");
        assertEquals(1, selectRes.getRows().size());
        assertEquals(2L, selectRes.getRows().get(0).get("id"));
    }

    @Test
    @DisplayName("Data and index survive database reload from disk")
    void testPersistenceAfterReload() throws IOException {
        engine.execute("INSERT INTO cars VALUES (1, 'Toyota', 25000.0, TRUE);");
        engine.execute("INSERT INTO cars VALUES (2, 'BMW', 35000.0, TRUE);");
        engine.execute("CREATE INDEX idx_cars_brand ON cars (brand);");

        FileStorageEngine newStorage = new FileStorageEngine(dbDir);
        DatabaseRepository newRepo = new DatabaseRepository(newStorage);
        newRepo.initialize();
        QueryEngine reloadedEngine = new QueryEngine(newRepo);

        QueryResult res = reloadedEngine.execute("SELECT * FROM cars;");
        assertEquals(2, res.getRows().size());

        QueryResult idxRes = reloadedEngine.execute("SELECT * FROM cars WHERE brand = 'Toyota';");
        assertEquals(1, idxRes.getRows().size());
        assertEquals(1L, idxRes.getRows().get(0).get("id"));
    }
}
