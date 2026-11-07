package ru.danis.dbms;

import ru.danis.dbms.benchmark.BenchmarkRunner;
import ru.danis.dbms.cli.ConsoleApp;
import ru.danis.dbms.engine.QueryEngine;
import ru.danis.dbms.gui.DatabaseGui;
import ru.danis.dbms.repository.DatabaseRepository;
import ru.danis.dbms.storage.FileStorageEngine;
import ru.danis.dbms.storage.StorageEngine;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Main application entry point for the Simple Relational DBMS.
 */
public class Main {

    public static void main(String[] args) {
        Path dataDir = Paths.get("data");
        boolean launchGui = false;
        boolean runBenchmark = false;
        boolean initDemoOnly = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--gui", "-g" -> launchGui = true;
                case "--benchmark", "-b" -> runBenchmark = true;
                case "--init-demo" -> initDemoOnly = true;
                case "--data", "-d" -> {
                    if (i + 1 < args.length) {
                        dataDir = Paths.get(args[++i]);
                    }
                }
            }
        }

        try {
            StorageEngine storageEngine = new FileStorageEngine(dataDir);
            DatabaseRepository databaseRepository = new DatabaseRepository(storageEngine);
            databaseRepository.initialize();
            QueryEngine engine = new QueryEngine(databaseRepository);

            if (initDemoOnly) {
                ConsoleApp console = new ConsoleApp(engine, System.in, System.out);
                console.initDemoData();
                System.out.println("Demo initialized. Exiting.");
                return;
            }

            if (runBenchmark) {
                BenchmarkRunner runner = new BenchmarkRunner(databaseRepository);
                runner.runAndPrint(System.out);
                return;
            }

            if (launchGui) {
                SwingUtilities.invokeLater(() -> {
                    try {
                        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                    } catch (Exception ignored) {
                    }
                    DatabaseGui gui = new DatabaseGui(engine);
                    gui.setVisible(true);
                });
            } else {
                ConsoleApp console = new ConsoleApp(engine, System.in, System.out);
                console.start();
            }

        } catch (IOException e) {
            System.err.println("Fatal storage engine error: " + e.getMessage());
            System.exit(1);
        }
    }
}
