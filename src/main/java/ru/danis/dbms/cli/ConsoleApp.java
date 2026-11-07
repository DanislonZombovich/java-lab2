package ru.danis.dbms.cli;

import ru.danis.dbms.benchmark.BenchmarkRunner;
import ru.danis.dbms.engine.QueryEngine;
import ru.danis.dbms.model.QueryResult;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.Scanner;

/**
 * Interactive Command-Line Interface (REPL) for the database management system.
 */
public class ConsoleApp {

    private final QueryEngine engine;
    private final InputStream in;
    private final PrintStream out;

    public ConsoleApp(QueryEngine engine, InputStream in, PrintStream out) {
        this.engine = engine;
        this.in = in;
        this.out = out;
    }

    public void start() {
        printBanner();
        Scanner scanner = new Scanner(in);
        StringBuilder buffer = new StringBuilder();

        while (true) {
            if (buffer.length() == 0) {
                out.print("simpledb> ");
            } else {
                out.print("      -> ");
            }

            if (!scanner.hasNextLine()) {
                break;
            }

            String line = scanner.nextLine().trim();
            if (line.isEmpty()) {
                continue;
            }

            if (buffer.length() == 0) {
                if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
                    out.println("Goodbye.");
                    break;
                }
                if (line.equalsIgnoreCase("help")) {
                    printHelp();
                    continue;
                }
                if (line.equalsIgnoreCase("tables")) {
                    executeQuery("SHOW TABLES;");
                    continue;
                }
                if (line.equalsIgnoreCase("indexes")) {
                    executeQuery("SHOW INDEXES;");
                    continue;
                }
                if (line.toLowerCase().startsWith("schema ")) {
                    String table = line.substring(7).trim().replace(";", "");
                    executeQuery("DESCRIBE " + table + ";");
                    continue;
                }
                if (line.equalsIgnoreCase("init-demo")) {
                    initDemoData();
                    continue;
                }
                if (line.equalsIgnoreCase("benchmark")) {
                    out.println("Running benchmark...");
                    BenchmarkRunner runner = new BenchmarkRunner(engine.getDatabaseRepository());
                    runner.runAndPrint(out);
                    continue;
                }
            }

            buffer.append(line).append(" ");
            if (line.endsWith(";")) {
                String query = buffer.toString().trim();
                buffer.setLength(0);
                executeQuery(query);
            }
        }
    }

    private void executeQuery(String sql) {
        try {
            QueryResult result = engine.execute(sql);
            TablePrinter.print(result, out);
        } catch (Exception e) {
            out.printf("ERROR: %s%n", e.getMessage());
        }
    }

    public void initDemoData() {
        out.println("Initializing sample relational tables and demo data...");
        try {
            engine.execute("CREATE TABLE IF NOT EXISTS manufacturers (id INT PRIMARY KEY, name VARCHAR, country VARCHAR);");
            engine.execute("CREATE TABLE IF NOT EXISTS cars (id INT PRIMARY KEY, brand VARCHAR, price DOUBLE, is_available BOOLEAN, created_at DATE, manufacturer_id INT REFERENCES manufacturers(id));");

            engine.execute("INSERT INTO manufacturers (id, name, country) VALUES (1, 'Toyota Motor Corp', 'Japan'), (2, 'Bayerische Motoren Werke AG', 'Germany');");
            engine.execute("INSERT INTO cars VALUES (1, 'Toyota', 25000.0, TRUE, '2024-01-15', 1);");
            engine.execute("INSERT INTO cars (id, brand, price, is_available, manufacturer_id) VALUES (2, 'BMW', 35000.0, TRUE, 2);");

            engine.execute("CREATE INDEX IF NOT EXISTS idx_cars_brand ON cars (brand);");
            out.println("Demo tables 'manufacturers' and 'cars' initialized with foreign key relation and index.");
        } catch (Exception e) {
            out.printf("Demo initialization warning: %s%n", e.getMessage());
        }
    }

    private void printBanner() {
        out.println("====================================================================");
        out.println("                   Simple Relational DBMS (Java)                    ");
        out.println("  Features: B-Tree Indexes, Disk Persistence, SQL Parser, Foreign Keys");
        out.println("====================================================================");
        out.println("Type 'help' for instructions, 'init-demo' to populate sample tables,");
        out.println("or 'exit' to terminate.\n");
    }

    private void printHelp() {
        out.println("\nSupported SQL Queries:");
        out.println("  CREATE TABLE <name> (<col> <TYPE> [PRIMARY KEY] [REFERENCES <tbl>(<col>)], ...);");
        out.println("  DROP TABLE <name>;");
        out.println("  CREATE INDEX <idx_name> ON <tbl> (<col>);");
        out.println("  DROP INDEX <idx_name>;");
        out.println("  INSERT INTO <tbl> [(<col1>, ...)] VALUES (<val1>, ...);");
        out.println("  SELECT * | <col1>, <col2> FROM <tbl> [WHERE <condition>];");
        out.println("  UPDATE <tbl> SET <col1> = <val1> [WHERE <condition>];");
        out.println("  DELETE FROM <tbl> [WHERE <condition>];");
        out.println("  EXPLAIN SELECT ...;");
        out.println("  SHOW TABLES; | DESCRIBE <tbl>; | SHOW INDEXES;\n");
        out.println("Commands:");
        out.println("  help       - Print this guide");
        out.println("  init-demo  - Initialize sample 'manufacturers' & 'cars' tables");
        out.println("  benchmark  - Execute performance benchmark (Index vs SeqScan)");
        out.println("  tables     - Show all created tables");
        out.println("  exit       - Exit interactive console\n");
    }
}
