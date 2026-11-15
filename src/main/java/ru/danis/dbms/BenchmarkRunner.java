package ru.danis.dbms;

import ru.danis.dbms.engine.QueryEngine;
import ru.danis.dbms.index.Index;
import ru.danis.dbms.model.QueryResult;
import ru.danis.dbms.repository.DatabaseRepository;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class BenchmarkRunner {

    private final DatabaseRepository repository;
    private final QueryEngine engine;

    public BenchmarkRunner(DatabaseRepository repository) {
        this.repository = repository;
        this.engine = new QueryEngine(repository);
    }

    public static class BenchmarkRow {

        public final int recordCount;

        public final double seqScanMs;

        public final double indexScanMs;

        public final double speedup;

        public BenchmarkRow(int recordCount, double seqScanMs, double indexScanMs) {
            this.recordCount = recordCount;
            this.seqScanMs = seqScanMs;
            this.indexScanMs = indexScanMs;
            this.speedup = indexScanMs > 0 ? seqScanMs / indexScanMs : 1.0;
        }
    }

    public List<BenchmarkRow> runSuite() {
        int[] sizes = {500, 2000, 5000, 10000};
        List<BenchmarkRow> results = new ArrayList<>();

        for (int size : sizes) {
            results.add(runForSize(size));
        }

        return results;
    }

    public BenchmarkRow runForSize(int size) {
        String tableName = "bench_" + size;
        String indexName = "idx_" + tableName + "_brand";

        try {
            if (repository.hasTable(tableName)) {
                repository.dropTable(tableName);
            }

            engine.execute("CREATE TABLE " + tableName + " (id INT PRIMARY KEY, brand VARCHAR, price DOUBLE, is_available BOOLEAN);");

            int cardinality = 100;
            String[] brands = new String[cardinality];
            for (int c = 0; c < cardinality; c++) {
                brands[c] = String.format(Locale.US, "Brand_%03d", c);
            }
            String targetBrand = brands[42];
            Random random = new Random(42);

            int batchSize = 2000;
            for (int i = 0; i < size; i += batchSize) {
                StringBuilder insertSql = new StringBuilder("INSERT INTO ").append(tableName).append(" VALUES ");
                int end = Math.min(i + batchSize, size);
                for (int j = i; j < end; j++) {
                    String b = brands[random.nextInt(cardinality)];
                    double p = 15000.0 + random.nextInt(40000);
                    boolean avail = random.nextBoolean();
                    insertSql.append(String.format(Locale.US, "(%d, '%s', %.1f, %s)", j + 1, b, p, avail ? "TRUE" : "FALSE"));
                    if (j < end - 1) {
                        insertSql.append(", ");
                    }
                }
                insertSql.append(";");
                engine.execute(insertSql.toString());
            }

            String query = "SELECT * FROM " + tableName + " WHERE brand = '" + targetBrand + "';";
            for (int w = 0; w < 20; w++) {
                engine.execute(query);
            }

            int testRuns = 50;
            long seqTotalNs = 0;
            for (int r = 0; r < testRuns; r++) {
                long st = System.nanoTime();
                QueryResult res = engine.execute(query);
                seqTotalNs += (System.nanoTime() - st);
            }
            double seqScanAvgMs = (seqTotalNs / (double) testRuns) / 1_000_000.0;

            engine.execute("CREATE INDEX " + indexName + " ON " + tableName + " (brand);");

            for (int w = 0; w < 20; w++) {
                engine.execute(query);
            }

            long idxTotalNs = 0;
            for (int r = 0; r < testRuns; r++) {
                long st = System.nanoTime();
                QueryResult res = engine.execute(query);
                idxTotalNs += (System.nanoTime() - st);
            }
            double idxScanAvgMs = (idxTotalNs / (double) testRuns) / 1_000_000.0;

            repository.dropTable(tableName);

            return new BenchmarkRow(size, seqScanAvgMs, idxScanAvgMs);
        } catch (Exception e) {
            throw new RuntimeException("Benchmark failed for size " + size + ": " + e.getMessage(), e);
        }
    }

    public void runAndPrint(PrintStream out) {
        out.println("\n--- Benchmark: Full Table Scan (SeqScan) vs B-Tree Index (IndexScan) ---");
        out.printf("%-12s | %-16s | %-16s | %-10s%n", "Records (N)", "SeqScan (ms)", "IndexScan (ms)", "Speedup");
        out.println("-------------+------------------+------------------+-----------");

        List<BenchmarkRow> results = runSuite();
        for (BenchmarkRow row : results) {
            String speedupStr = String.format(Locale.US, "%.2fx", row.speedup);
            out.printf(Locale.US, "%-12d | %-16.4f | %-16.4f | %-10s%n",
                    row.recordCount,
                    row.seqScanMs,
                    row.indexScanMs,
                    speedupStr);
        }
        out.println("--------------------------------------------------------------------\n");
    }
}
