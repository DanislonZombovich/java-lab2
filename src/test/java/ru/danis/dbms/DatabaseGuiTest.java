package ru.danis.dbms;

import ru.danis.dbms.engine.QueryEngine;
import ru.danis.dbms.repository.DatabaseRepository;
import ru.danis.dbms.storage.FileStorageEngine;
import ru.danis.dbms.ui.DatabaseGui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

public class DatabaseGuiTest {

    @Test
    @DisplayName("DatabaseGui initializes and integrates with QueryEngine without exceptions")
    void testDatabaseGuiInitialization(@TempDir Path tempDir) throws IOException {
        if (GraphicsEnvironment.isHeadless()) {
            // In headless environment without X server, JFrame cannot create native peer, skip graphical rendering
            return;
        }

        FileStorageEngine storage = new FileStorageEngine(tempDir);
        DatabaseRepository repo = new DatabaseRepository(storage);
        repo.initialize();
        QueryEngine engine = new QueryEngine(repo);

        assertDoesNotThrow(() -> {
            DatabaseGui gui = new DatabaseGui(engine);
            assertNotNull(gui.getTitle());
            assertTrue(gui.getTitle().contains("DBMS"));
            gui.dispose();
        });

        repo.close();
    }
}
