package ru.danis.dbms.engine;

import ru.danis.dbms.index.Index;
import ru.danis.dbms.model.*;
import ru.danis.dbms.parser.*;
import ru.danis.dbms.parser.statement.*;
import ru.danis.dbms.repository.DatabaseRepository;
import ru.danis.dbms.repository.TableRepository;

import java.io.IOException;
import java.util.*;

/**
 * Core query evaluation engine orchestrating parsing, optimization, transaction safety, and execution.
 */
public class QueryEngine {

    private final DatabaseRepository databaseRepository;

    public QueryEngine(DatabaseRepository databaseRepository) {
        this.databaseRepository = Objects.requireNonNull(databaseRepository, "databaseRepository cannot be null");
    }

    public DatabaseRepository getDatabaseRepository() {
        return databaseRepository;
    }

    public QueryResult execute(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL query cannot be empty");
        }
        SqlParser parser = new SqlParser(sql);
        Statement stmt = parser.parse();
        return execute(stmt);
    }

    public QueryResult execute(Statement stmt) {
        long startNs = System.nanoTime();
        try {
            return switch (stmt.getType()) {
                case CREATE_TABLE -> handleCreateTable((CreateTableStatement) stmt, startNs);
                case DROP_TABLE -> handleDropTable((DropTableStatement) stmt, startNs);
                case CREATE_INDEX -> handleCreateIndex((CreateIndexStatement) stmt, startNs);
                case DROP_INDEX -> handleDropIndex((DropIndexStatement) stmt, startNs);
                case INSERT -> handleInsert((InsertStatement) stmt, startNs);
                case SELECT -> handleSelect((SelectStatement) stmt, startNs);
                case UPDATE -> handleUpdate((UpdateStatement) stmt, startNs);
                case DELETE -> handleDelete((DeleteStatement) stmt, startNs);
                case SHOW_TABLES -> handleShowTables(startNs);
                case DESCRIBE_TABLE -> handleDescribe((DescribeTableStatement) stmt, startNs);
                case SHOW_INDEXES -> handleShowIndexes((ShowIndexesStatement) stmt, startNs);
                case EXPLAIN -> handleExplain((ExplainStatement) stmt, startNs);
            };
        } catch (IOException e) {
            throw new RuntimeException("Storage I/O failure during statement execution: " + e.getMessage(), e);
        }
    }

    private QueryResult handleCreateTable(CreateTableStatement stmt, long startNs) throws IOException {
        String name = stmt.getSchema().getTableName();
        if (databaseRepository.hasTable(name)) {
            if (stmt.isIfNotExists()) {
                return QueryResult.forMutation(0, System.nanoTime() - startNs, "Table '" + name + "' already exists (skipped)");
            }
            throw new IllegalArgumentException("Table already exists: " + name);
        }
        databaseRepository.createTable(stmt.getSchema());
        return QueryResult.forMutation(0, System.nanoTime() - startNs, "Table '" + name + "' created successfully");
    }

    private QueryResult handleDropTable(DropTableStatement stmt, long startNs) throws IOException {
        String name = stmt.getTableName();
        if (!databaseRepository.hasTable(name)) {
            if (stmt.isIfExists()) {
                return QueryResult.forMutation(0, System.nanoTime() - startNs, "Table '" + name + "' does not exist (skipped)");
            }
            throw new IllegalArgumentException("Table not found: " + name);
        }
        databaseRepository.dropTable(name);
        return QueryResult.forMutation(0, System.nanoTime() - startNs, "Table '" + name + "' dropped successfully");
    }

    private QueryResult handleCreateIndex(CreateIndexStatement stmt, long startNs) throws IOException {
        TableRepository repo = databaseRepository.getTable(stmt.getTableName());
        if (repo.getIndex(stmt.getIndexName()) != null) {
            if (stmt.isIfNotExists()) {
                return QueryResult.forMutation(0, System.nanoTime() - startNs, "Index '" + stmt.getIndexName() + "' already exists (skipped)");
            }
            throw new IllegalArgumentException("Index '" + stmt.getIndexName() + "' already exists");
        }
        databaseRepository.createIndex(stmt.getIndexName(), stmt.getTableName(), stmt.getColumnName());
        return QueryResult.forMutation(0, System.nanoTime() - startNs, "Index '" + stmt.getIndexName() + "' created on " + stmt.getTableName() + "(" + stmt.getColumnName() + ")");
    }

    private QueryResult handleDropIndex(DropIndexStatement stmt, long startNs) throws IOException {
        try {
            databaseRepository.dropIndex(stmt.getIndexName());
            return QueryResult.forMutation(0, System.nanoTime() - startNs, "Index '" + stmt.getIndexName() + "' dropped successfully");
        } catch (IllegalArgumentException e) {
            if (stmt.isIfExists()) {
                return QueryResult.forMutation(0, System.nanoTime() - startNs, "Index '" + stmt.getIndexName() + "' does not exist (skipped)");
            }
            throw e;
        }
    }

    private QueryResult handleInsert(InsertStatement stmt, long startNs) throws IOException {
        TableRepository repo = databaseRepository.getTable(stmt.getTableName());
        TableSchema schema = repo.getSchema();
        List<Row> rowsToInsert = new ArrayList<>();

        for (List<String> rawValues : stmt.getRowsValues()) {
            Row row = new Row();
            if (stmt.hasExplicitColumns()) {
                List<String> explicitCols = stmt.getColumns();
                if (explicitCols.size() != rawValues.size()) {
                    throw new IllegalArgumentException("Column count (" + explicitCols.size() + ") does not match value count (" + rawValues.size() + ")");
                }
                for (int i = 0; i < explicitCols.size(); i++) {
                    String colName = explicitCols.get(i);
                    Column col = schema.getColumn(colName);
                    if (col == null) {
                        throw new IllegalArgumentException("Unknown column: " + colName);
                    }
                    row.set(col.getName(), col.getType().parse(rawValues.get(i)));
                }
                for (Column col : schema.getColumns()) {
                    if (!row.has(col.getName())) {
                        if (!col.isNullable() && !col.isPrimaryKey()) {
                            throw new IllegalArgumentException("Non-nullable column '" + col.getName() + "' omitted without default value");
                        }
                    }
                }
            } else {
                List<Column> schemaCols = schema.getColumns();
                if (schemaCols.size() != rawValues.size()) {
                    throw new IllegalArgumentException("Expected " + schemaCols.size() + " values for table " + schema.getTableName() + ", found " + rawValues.size());
                }
                for (int i = 0; i < schemaCols.size(); i++) {
                    Column col = schemaCols.get(i);
                    row.set(col.getName(), col.getType().parse(rawValues.get(i)));
                }
            }

            databaseRepository.validateForeignKeysOnInsert(stmt.getTableName(), row);
            rowsToInsert.add(row);
        }

        repo.insertBatch(rowsToInsert);
        return QueryResult.forMutation(rowsToInsert.size(), System.nanoTime() - startNs, "Inserted " + rowsToInsert.size() + " row(s)");
    }

    private QueryResult handleSelect(SelectStatement stmt, long startNs) {
        TableRepository repo = databaseRepository.getTable(stmt.getTableName());
        TableSchema schema = repo.getSchema();

        List<String> projectedColNames = resolveProjectedColumns(schema, stmt);
        ExecutionPlan plan = buildOptimizedPlan(repo, stmt.getCondition());
        List<Row> rawResults = plan.execute();

        List<Row> projectedRows = new ArrayList<>(rawResults.size());
        for (Row original : rawResults) {
            Row projected = new Row();
            for (String col : projectedColNames) {
                projected.set(col, original.get(col));
            }
            projectedRows.add(projected);
        }

        return QueryResult.forSelect(projectedColNames, projectedRows, System.nanoTime() - startNs, plan.describe());
    }

    private QueryResult handleUpdate(UpdateStatement stmt, long startNs) throws IOException {
        TableRepository repo = databaseRepository.getTable(stmt.getTableName());
        TableSchema schema = repo.getSchema();

        Map<String, Object> castUpdates = new HashMap<>();
        for (Map.Entry<String, String> entry : stmt.getRawUpdates().entrySet()) {
            String colName = entry.getKey();
            Column col = schema.getColumn(colName);
            if (col == null) {
                throw new IllegalArgumentException("Unknown column in UPDATE: " + colName);
            }
            castUpdates.put(col.getName(), col.getType().parse(entry.getValue()));
        }

        databaseRepository.validateForeignKeysOnUpdate(stmt.getTableName(), stmt.getCondition(), castUpdates);
        int count = repo.update(stmt.getCondition(), castUpdates);
        return QueryResult.forMutation(count, System.nanoTime() - startNs, "Updated " + count + " row(s)");
    }

    private QueryResult handleDelete(DeleteStatement stmt, long startNs) throws IOException {
        databaseRepository.validateForeignKeysOnDelete(stmt.getTableName(), stmt.getCondition());
        TableRepository repo = databaseRepository.getTable(stmt.getTableName());
        int count = repo.delete(stmt.getCondition());
        return QueryResult.forMutation(count, System.nanoTime() - startNs, "Deleted " + count + " row(s)");
    }

    private QueryResult handleShowTables(long startNs) {
        List<String> names = databaseRepository.getTableNames();
        List<Row> rows = new ArrayList<>();
        for (String name : names) {
            Row r = new Row();
            r.set("TABLE_NAME", name);
            rows.add(r);
        }
        return QueryResult.forSelect(List.of("TABLE_NAME"), rows, System.nanoTime() - startNs, "ShowTablesPlan");
    }

    private QueryResult handleDescribe(DescribeTableStatement stmt, long startNs) {
        TableRepository repo = databaseRepository.getTable(stmt.getTableName());
        TableSchema schema = repo.getSchema();

        List<String> cols = List.of("COLUMN_NAME", "DATA_TYPE", "PRIMARY_KEY", "NULLABLE", "FOREIGN_KEY");
        List<Row> rows = new ArrayList<>();

        for (Column c : schema.getColumns()) {
            Row r = new Row();
            r.set("COLUMN_NAME", c.getName());
            r.set("DATA_TYPE", c.getType().name());
            r.set("PRIMARY_KEY", c.isPrimaryKey());
            r.set("NULLABLE", c.isNullable());
            r.set("FOREIGN_KEY", c.hasForeignKey() ? c.getForeignKey().toString() : "NONE");
            rows.add(r);
        }

        return QueryResult.forSelect(cols, rows, System.nanoTime() - startNs, "DescribeTablePlan");
    }

    private QueryResult handleShowIndexes(ShowIndexesStatement stmt, long startNs) {
        List<String> cols = List.of("INDEX_NAME", "TABLE_NAME", "COLUMN_NAME", "TYPE", "ENTRIES_COUNT");
        List<Row> rows = new ArrayList<>();

        List<String> tables = stmt.hasTableFilter() ? List.of(stmt.getTableName()) : databaseRepository.getTableNames();
        for (String tbl : tables) {
            if (databaseRepository.hasTable(tbl)) {
                TableRepository repo = databaseRepository.getTable(tbl);
                for (Index idx : repo.getIndexes()) {
                    Row r = new Row();
                    r.set("INDEX_NAME", idx.getName());
                    r.set("TABLE_NAME", idx.getTableName());
                    r.set("COLUMN_NAME", idx.getColumnName());
                    r.set("TYPE", idx.getClass().getSimpleName());
                    r.set("ENTRIES_COUNT", idx.size());
                    rows.add(r);
                }
            }
        }

        return QueryResult.forSelect(cols, rows, System.nanoTime() - startNs, "ShowIndexesPlan");
    }

    private QueryResult handleExplain(ExplainStatement stmt, long startNs) {
        if (stmt.getInnerStatement() instanceof SelectStatement sel) {
            TableRepository repo = databaseRepository.getTable(sel.getTableName());
            ExecutionPlan plan = buildOptimizedPlan(repo, sel.getCondition());
            return QueryResult.forExplain(plan.describe(), System.nanoTime() - startNs);
        }
        return QueryResult.forExplain("Execution plan available primarily for SELECT queries", System.nanoTime() - startNs);
    }

    public ExecutionPlan buildOptimizedPlan(TableRepository repository, Condition condition) {
        if (condition == null) {
            return new SeqScanPlan(repository, null);
        }

        if (condition.isSimple()) {
            Index index = repository.getIndexForColumn(condition.getColumnName());
            if (index != null && isSupportedIndexOperator(condition.getOperator())) {
                return new IndexScanPlan(repository, index, condition, null);
            }
            return new SeqScanPlan(repository, condition);
        }

        List<Condition> conjuncts = new ArrayList<>();
        if (extractAndConjuncts(condition, conjuncts)) {
            for (int i = 0; i < conjuncts.size(); i++) {
                Condition candidate = conjuncts.get(i);
                if (candidate.isSimple()) {
                    Index index = repository.getIndexForColumn(candidate.getColumnName());
                    if (index != null && isSupportedIndexOperator(candidate.getOperator())) {
                        List<Condition> rest = new ArrayList<>(conjuncts);
                        rest.remove(i);
                        Condition residual = combineConjuncts(rest);
                        return new IndexScanPlan(repository, index, candidate, residual);
                    }
                }
            }
        }

        return new SeqScanPlan(repository, condition);
    }

    private boolean extractAndConjuncts(Condition cond, List<Condition> list) {
        if (cond.isSimple()) {
            list.add(cond);
            return true;
        }
        if (cond.getConnector() == Condition.LogicalConnector.AND) {
            return extractAndConjuncts(cond.getLeft(), list) && extractAndConjuncts(cond.getRight(), list);
        }
        return false;
    }

    private Condition combineConjuncts(List<Condition> list) {
        if (list.isEmpty()) {
            return null;
        }
        Condition result = list.get(0);
        for (int i = 1; i < list.size(); i++) {
            result = new Condition(result, Condition.LogicalConnector.AND, list.get(i));
        }
        return result;
    }

    private boolean isSupportedIndexOperator(ComparisonOperator op) {
        return op == ComparisonOperator.EQUALS ||
               op == ComparisonOperator.GREATER_THAN ||
               op == ComparisonOperator.GREATER_THAN_OR_EQUAL ||
               op == ComparisonOperator.LESS_THAN ||
               op == ComparisonOperator.LESS_THAN_OR_EQUAL;
    }

    private List<String> resolveProjectedColumns(TableSchema schema, SelectStatement stmt) {
        if (stmt.isSelectAll()) {
            return schema.getColumns().stream().map(Column::getName).toList();
        }
        for (String reqCol : stmt.getColumns()) {
            if (!schema.hasColumn(reqCol)) {
                throw new IllegalArgumentException("Unknown projection column '" + reqCol + "' in table '" + schema.getTableName() + "'");
            }
        }
        return stmt.getColumns();
    }
}
