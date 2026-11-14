
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SqlParserTest {

    @Test
    @DisplayName("Test parsing CREATE TABLE statement with PK and Foreign Key")
    void testParseCreateTable() {
        String sql = "CREATE TABLE cars (id INT PRIMARY KEY, brand VARCHAR, price DOUBLE, manufacturer_id INT REFERENCES manufacturers(id));";
        SqlParser parser = new SqlParser(sql);
        Statement stmt = parser.parse();

        assertInstanceOf(CreateTableStatement.class, stmt);
        CreateTableStatement create = (CreateTableStatement) stmt;
        assertEquals("cars", create.getSchema().getTableName());
        assertEquals("id", create.getSchema().getPrimaryKeyColumn());
        assertEquals(4, create.getSchema().getColumns().size());
        assertEquals(DataType.INT, create.getSchema().getColumn("id").getType());
        assertEquals(DataType.VARCHAR, create.getSchema().getColumn("brand").getType());
        assertEquals(DataType.DOUBLE, create.getSchema().getColumn("price").getType());

        assertTrue(create.getSchema().getColumn("manufacturer_id").hasForeignKey());
        assertEquals("manufacturers", create.getSchema().getColumn("manufacturer_id").getForeignKey().getTargetTable());
        assertEquals("id", create.getSchema().getColumn("manufacturer_id").getForeignKey().getTargetColumn());
    }

    @Test
    @DisplayName("Test parsing INSERT INTO statement full values and partial columns")
    void testParseInsert() {
        String sql1 = "INSERT INTO cars VALUES (1, 'Toyota', 25000.0, TRUE);";
        SqlParser parser1 = new SqlParser(sql1);
        Statement stmt1 = parser1.parse();

        assertInstanceOf(InsertStatement.class, stmt1);
        InsertStatement ins1 = (InsertStatement) stmt1;
        assertEquals("cars", ins1.getTableName());
        assertFalse(ins1.hasExplicitColumns());
        assertEquals(1, ins1.getRowsValues().size());
        assertEquals(List.of("1", "Toyota", "25000.0", "TRUE"), ins1.getRowsValues().get(0));

        String sql2 = "INSERT INTO cars (id, brand) VALUES (2, 'BMW');";
        SqlParser parser2 = new SqlParser(sql2);
        Statement stmt2 = parser2.parse();

        assertInstanceOf(InsertStatement.class, stmt2);
        InsertStatement ins2 = (InsertStatement) stmt2;
        assertEquals("cars", ins2.getTableName());
        assertTrue(ins2.hasExplicitColumns());
        assertEquals(List.of("id", "brand"), ins2.getColumns());
        assertEquals(List.of("2", "BMW"), ins2.getRowsValues().get(0));
    }

    @Test
    @DisplayName("Test parsing multi-row INSERT INTO statement")
    void testParseMultiRowInsert() {
        String sql = "INSERT INTO cars VALUES (1, 'Toyota'), (2, 'Honda');";
        SqlParser parser = new SqlParser(sql);
        Statement stmt = parser.parse();

        assertInstanceOf(InsertStatement.class, stmt);
        InsertStatement ins = (InsertStatement) stmt;
        assertEquals(2, ins.getRowsValues().size());
        assertEquals(List.of("1", "Toyota"), ins.getRowsValues().get(0));
        assertEquals(List.of("2", "Honda"), ins.getRowsValues().get(1));
    }

    @Test
    @DisplayName("Test parsing SELECT statements with and without WHERE")
    void testParseSelect() {
        String sql1 = "SELECT * FROM cars;";
        SqlParser p1 = new SqlParser(sql1);
        SelectStatement sel1 = (SelectStatement) p1.parse();
        assertEquals("cars", sel1.getTableName());
        assertTrue(sel1.isSelectAll());
        assertFalse(sel1.hasCondition());

        String sql2 = "SELECT brand, price FROM cars WHERE price > 20000;";
        SqlParser p2 = new SqlParser(sql2);
        SelectStatement sel2 = (SelectStatement) p2.parse();
        assertEquals("cars", sel2.getTableName());
        assertFalse(sel2.isSelectAll());
        assertEquals(List.of("brand", "price"), sel2.getColumns());
        assertTrue(sel2.hasCondition());
        assertEquals("price", sel2.getCondition().getColumnName());
        assertEquals(ComparisonOperator.GREATER_THAN, sel2.getCondition().getOperator());
        assertEquals("20000", sel2.getCondition().getTargetValue());
    }

    @Test
    @DisplayName("Test parsing UPDATE and DELETE statements")
    void testParseUpdateAndDelete() {
        String updateSql = "UPDATE cars SET price = 30000 WHERE id = 2;";
        SqlParser p1 = new SqlParser(updateSql);
        UpdateStatement upd = (UpdateStatement) p1.parse();
        assertEquals("cars", upd.getTableName());
        assertEquals("30000", upd.getRawUpdates().get("price"));
        assertEquals("id", upd.getCondition().getColumnName());

        String deleteSql = "DELETE FROM cars WHERE brand = 'Toyota';";
        SqlParser p2 = new SqlParser(deleteSql);
        DeleteStatement del = (DeleteStatement) p2.parse();
        assertEquals("cars", del.getTableName());
        assertEquals("brand", del.getCondition().getColumnName());
        assertEquals("Toyota", del.getCondition().getTargetValue());
    }

    @Test
    @DisplayName("Test parsing CREATE INDEX, DROP INDEX, and EXPLAIN")
    void testParseIndexAndExplain() {
        String createIdxSql = "CREATE INDEX idx_cars_brand ON cars (brand);";
        SqlParser p1 = new SqlParser(createIdxSql);
        CreateIndexStatement cidx = (CreateIndexStatement) p1.parse();
        assertEquals("idx_cars_brand", cidx.getIndexName());
        assertEquals("cars", cidx.getTableName());
        assertEquals("brand", cidx.getColumnName());

        String explainSql = "EXPLAIN SELECT * FROM cars WHERE brand = 'BMW';";
        SqlParser p2 = new SqlParser(explainSql);
        ExplainStatement exp = (ExplainStatement) p2.parse();
        assertInstanceOf(SelectStatement.class, exp.getInnerStatement());
    }
}
