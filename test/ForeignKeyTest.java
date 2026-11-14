
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ForeignKeyTest {

    private QueryEngine engine;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        FileStorageEngine storage = new FileStorageEngine(tempDir);
        DatabaseRepository repo = new DatabaseRepository(storage);
        repo.initialize();
        engine = new QueryEngine(repo);

        engine.execute("CREATE TABLE manufacturers (id INT PRIMARY KEY, name VARCHAR, country VARCHAR);");
        engine.execute("CREATE TABLE cars (id INT PRIMARY KEY, brand VARCHAR, price DOUBLE, manufacturer_id INT REFERENCES manufacturers(id));");

        engine.execute("INSERT INTO manufacturers (id, name, country) VALUES (1, 'Toyota Motor Corp', 'Japan');");
        engine.execute("INSERT INTO manufacturers (id, name, country) VALUES (2, 'Bayerische Motoren Werke', 'Germany');");
    }

    @Test
    @DisplayName("Test valid foreign key insertion succeeds")
    void testValidForeignKeyInsert() {
        assertDoesNotThrow(() -> {
            engine.execute("INSERT INTO cars (id, brand, price, manufacturer_id) VALUES (101, 'Corolla', 22000.0, 1);");
        });
        assertEquals(1, engine.execute("SELECT * FROM cars;").getRows().size());
    }

    @Test
    @DisplayName("Test invalid foreign key insertion is rejected")
    void testInvalidForeignKeyInsertThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            engine.execute("INSERT INTO cars (id, brand, price, manufacturer_id) VALUES (102, 'UnknownCar', 15000.0, 999);");
        });
        assertTrue(ex.getMessage().contains("Foreign key violation"));
    }

    @Test
    @DisplayName("Test deleting referenced parent row is blocked by foreign key constraint")
    void testDeleteParentBlockedByForeignKey() {
        engine.execute("INSERT INTO cars (id, brand, price, manufacturer_id) VALUES (101, 'Corolla', 22000.0, 1);");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            engine.execute("DELETE FROM manufacturers WHERE id = 1;");
        });
        assertTrue(ex.getMessage().contains("Foreign key violation"));
    }

    @Test
    @DisplayName("Test deleting unreferenced parent row succeeds")
    void testDeleteUnreferencedParentSucceeds() {
        engine.execute("INSERT INTO cars (id, brand, price, manufacturer_id) VALUES (101, 'Corolla', 22000.0, 1);");

        assertDoesNotThrow(() -> {
            engine.execute("DELETE FROM manufacturers WHERE id = 2;");
        });
    }

    @Test
    @DisplayName("Test dropping referenced table is blocked")
    void testDropParentTableBlocked() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            engine.execute("DROP TABLE manufacturers;");
        });
        assertTrue(ex.getMessage().contains("Cannot drop table"));
    }
}
