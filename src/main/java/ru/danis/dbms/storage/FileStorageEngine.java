package ru.danis.dbms.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ru.danis.dbms.index.BTreeIndex;
import ru.danis.dbms.index.Index;
import ru.danis.dbms.model.DataType;
import ru.danis.dbms.model.Row;
import ru.danis.dbms.model.TableSchema;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * File-based storage engine implementing atomic disk persistence, index serialization, and WAL.
 */
public class FileStorageEngine implements StorageEngine {

    private final Path baseDirectory;
    private final Path tablesDirectory;
    private final Path indexesDirectory;
    private final Path walDirectory;
    private final Path walFile;

    private final ObjectMapper mapper;
    private final AtomicLong walSequence = new AtomicLong(0);

    public FileStorageEngine(Path baseDirectory) {
        this.baseDirectory = Objects.requireNonNull(baseDirectory, "baseDirectory cannot be null");
        this.tablesDirectory = baseDirectory.resolve("tables");
        this.indexesDirectory = baseDirectory.resolve("indexes");
        this.walDirectory = baseDirectory.resolve("wal");
        this.walFile = walDirectory.resolve("wal.log");

        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public synchronized void initialize() throws IOException {
        Files.createDirectories(tablesDirectory);
        Files.createDirectories(indexesDirectory);
        Files.createDirectories(walDirectory);
        if (!Files.exists(walFile)) {
            Files.createFile(walFile);
        } else {
            List<WalEntry> existing = readWalEntries();
            if (!existing.isEmpty()) {
                walSequence.set(existing.get(existing.size() - 1).getSequenceNumber() + 1);
            }
        }
    }

    @Override
    public synchronized void saveTable(TableSchema schema, List<Row> rows) throws IOException {
        String tableName = schema.getTableName().toLowerCase();
        Path schemaTarget = tablesDirectory.resolve(tableName + ".schema.json");
        Path dataTarget = tablesDirectory.resolve(tableName + ".data.json");

        writeAtomically(schemaTarget, schema);

        List<Map<String, Object>> serializedRows = new ArrayList<>(rows.size());
        for (Row row : rows) {
            serializedRows.add(row.getValues());
        }
        writeAtomically(dataTarget, serializedRows);
    }

    @Override
    public synchronized TableSchema loadSchema(String tableName) throws IOException {
        Path schemaTarget = tablesDirectory.resolve(tableName.toLowerCase() + ".schema.json");
        if (!Files.exists(schemaTarget)) {
            return null;
        }
        return mapper.readValue(schemaTarget.toFile(), TableSchema.class);
    }

    @Override
    public synchronized List<Row> loadRows(String tableName, TableSchema schema) throws IOException {
        Path dataTarget = tablesDirectory.resolve(tableName.toLowerCase() + ".data.json");
        if (!Files.exists(dataTarget)) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> rawRows = mapper.readValue(dataTarget.toFile(), new TypeReference<List<Map<String, Object>>>() {});
        List<Row> result = new ArrayList<>(rawRows.size());

        for (Map<String, Object> rawMap : rawRows) {
            Row row = new Row();
            for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
                String colName = entry.getKey();
                Object rawVal = entry.getValue();
                if (schema.hasColumn(colName) && rawVal != null) {
                    DataType type = schema.getColumn(colName).getType();
                    row.set(colName, type.cast(rawVal));
                } else {
                    row.set(colName, rawVal);
                }
            }
            result.add(row);
        }

        return result;
    }

    @Override
    public synchronized void dropTable(String tableName) throws IOException {
        String lower = tableName.toLowerCase();
        Files.deleteIfExists(tablesDirectory.resolve(lower + ".schema.json"));
        Files.deleteIfExists(tablesDirectory.resolve(lower + ".data.json"));

        for (String idxFile : listIndexesForTable(lower)) {
            dropIndex(lower, idxFile);
        }
    }

    @Override
    public synchronized List<String> listTables() throws IOException {
        List<String> list = new ArrayList<>();
        if (!Files.exists(tablesDirectory)) {
            return list;
        }
        try (var stream = Files.list(tablesDirectory)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".schema.json"))
                  .forEach(p -> {
                      String name = p.getFileName().toString();
                      list.add(name.substring(0, name.length() - ".schema.json".length()));
                  });
        }
        return list;
    }

    @Override
    public synchronized void saveIndex(Index index) throws IOException {
        String fileName = index.getTableName().toLowerCase() + "_" + index.getName().toLowerCase() + ".idx.json";
        Path target = indexesDirectory.resolve(fileName);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", index.getName());
        payload.put("tableName", index.getTableName());
        payload.put("columnName", index.getColumnName());
        payload.put("keyType", index.getIndexedType().name());

        TableSchema schema = loadSchema(index.getTableName());
        if (schema != null && schema.hasPrimaryKey()) {
            payload.put("idType", schema.getColumn(schema.getPrimaryKeyColumn()).getType().name());
        } else {
            payload.put("idType", DataType.INT.name());
        }

        List<Map<String, Object>> entriesList = new ArrayList<>();
        for (Map.Entry<Comparable<?>, List<Object>> entry : index.getEntries().entrySet()) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("key", entry.getKey());
            node.put("ids", entry.getValue());
            entriesList.add(node);
        }
        payload.put("entries", entriesList);

        writeAtomically(target, payload);
    }

    @Override
    public synchronized Index loadIndex(String tableName, String indexName, String columnName, DataType keyType) throws IOException {
        String fileName = tableName.toLowerCase() + "_" + indexName.toLowerCase() + ".idx.json";
        Path target = indexesDirectory.resolve(fileName);
        if (!Files.exists(target)) {
            return null;
        }

        Map<String, Object> data = mapper.readValue(target.toFile(), new TypeReference<Map<String, Object>>() {});
        String name = (String) data.get("name");
        String col = (String) data.get("columnName");
        DataType type = DataType.valueOf((String) data.get("keyType"));
        DataType idType = data.containsKey("idType") ? DataType.valueOf((String) data.get("idType")) : DataType.INT;

        BTreeIndex index = new BTreeIndex(name, tableName, col, type);
        List<Map<String, Object>> entriesList = (List<Map<String, Object>>) data.get("entries");

        NavigableMap<Comparable<?>, List<Object>> map = new TreeMap<>((a, b) -> type.compareValues(a, b));
        if (entriesList != null) {
            for (Map<String, Object> node : entriesList) {
                Object rawKey = node.get("key");
                Comparable<?> castKey = (Comparable<?>) type.cast(rawKey);
                List<Object> rawIds = (List<Object>) node.get("ids");
                List<Object> normalizedIds = new ArrayList<>();
                if (rawIds != null) {
                    for (Object id : rawIds) {
                        if (id instanceof Number n) {
                            if (idType == DataType.DOUBLE) {
                                normalizedIds.add(n.doubleValue());
                            } else {
                                normalizedIds.add(n.longValue());
                            }
                        } else if (idType != null) {
                            normalizedIds.add(idType.cast(id));
                        } else {
                            normalizedIds.add(id);
                        }
                    }
                }
                map.put(castKey, normalizedIds);
            }
        }
        index.loadEntries(map);
        return index;
    }

    @Override
    public synchronized void dropIndex(String tableName, String indexName) throws IOException {
        String fileName = tableName.toLowerCase() + "_" + indexName.toLowerCase() + ".idx.json";
        Files.deleteIfExists(indexesDirectory.resolve(fileName));
    }

    @Override
    public synchronized List<String> listIndexesForTable(String tableName) throws IOException {
        List<String> list = new ArrayList<>();
        if (!Files.exists(indexesDirectory)) {
            return list;
        }
        String prefix = tableName.toLowerCase() + "_";
        try (var stream = Files.list(indexesDirectory)) {
            stream.filter(p -> p.getFileName().toString().startsWith(prefix) && p.getFileName().toString().endsWith(".idx.json"))
                  .forEach(p -> {
                      try {
                          Map<String, Object> data = mapper.readValue(p.toFile(), new TypeReference<Map<String, Object>>() {});
                          String actualTable = (String) data.get("tableName");
                          if (actualTable != null && actualTable.equalsIgnoreCase(tableName)) {
                              String idxName = (String) data.get("name");
                              list.add(idxName);
                          }
                      } catch (Exception ignored) {
                      }
                  });
        }
        return list;
    }

    @Override
    public synchronized void appendWal(String operation, String tableName, String details) throws IOException {
        long seq = walSequence.incrementAndGet();
        WalEntry entry = new WalEntry(seq, Instant.now(), operation, tableName, details);
        String json = mapper.writeValueAsString(entry).replace("\n", " ") + "\n";
        Files.writeString(walFile, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    @Override
    public synchronized void checkpoint() throws IOException {
        Files.writeString(walFile, "", StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
    }

    @Override
    public synchronized List<WalEntry> readWalEntries() throws IOException {
        if (!Files.exists(walFile)) {
            return Collections.emptyList();
        }
        List<WalEntry> entries = new ArrayList<>();
        List<String> lines = Files.readAllLines(walFile, StandardCharsets.UTF_8);
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                entries.add(mapper.readValue(trimmed, WalEntry.class));
            }
        }
        return entries;
    }

    @Override
    public synchronized void close() throws IOException {
        checkpoint();
    }

    private void writeAtomically(Path targetFile, Object data) throws IOException {
        Path tempFile = targetFile.resolveSibling(targetFile.getFileName() + ".tmp." + System.nanoTime());
        try {
            mapper.writeValue(tempFile.toFile(), data);
            Files.move(tempFile, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
