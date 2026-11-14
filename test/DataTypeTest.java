
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class DataTypeTest {

    @Test
    @DisplayName("Test INT type parsing, casting, and comparison")
    void testIntType() {
        DataType dt = DataType.INT;
        assertEquals(42L, dt.parse("42"));
        assertEquals(-10L, dt.parse("-10"));
        assertEquals(100L, dt.cast(100));
        assertEquals(100L, dt.cast("100"));

        assertTrue(dt.compareValues(10L, 20L) < 0);
        assertTrue(dt.compareValues(20L, 10L) > 0);
        assertEquals(0, dt.compareValues(10L, 10L));

        assertThrows(IllegalArgumentException.class, () -> dt.parse("not_a_number"));
    }

    @Test
    @DisplayName("Test DOUBLE type parsing, casting, and comparison")
    void testDoubleType() {
        DataType dt = DataType.DOUBLE;
        assertEquals(25000.0, (Double) dt.parse("25000.0"), 0.0001);
        assertEquals(-3.14, (Double) dt.parse("-3.14"), 0.0001);
        assertEquals(12.5, (Double) dt.cast(12.5), 0.0001);

        assertTrue(dt.compareValues(15.5, 20.0) < 0);
        assertEquals(0, dt.compareValues(5.0, 5.0));

        assertThrows(IllegalArgumentException.class, () -> dt.parse("invalid_float"));
    }

    @Test
    @DisplayName("Test VARCHAR type parsing with quotes and comparison")
    void testVarcharType() {
        DataType dt = DataType.VARCHAR;
        assertEquals("Toyota", dt.parse("'Toyota'"));
        assertEquals("BMW", dt.parse("\"BMW\""));
        assertEquals("PlainString", dt.parse("PlainString"));

        assertTrue(dt.compareValues("Audi", "BMW") < 0);
        assertEquals(0, dt.compareValues("Toyota", "Toyota"));
    }

    @Test
    @DisplayName("Test DATE type parsing and comparison")
    void testDateType() {
        DataType dt = DataType.DATE;
        LocalDate d1 = LocalDate.of(2024, 1, 15);
        LocalDate d2 = LocalDate.of(2024, 6, 20);

        assertEquals(d1, dt.parse("'2024-01-15'"));
        assertEquals(d1, dt.parse("2024-01-15"));

        assertTrue(dt.compareValues(d1, d2) < 0);
        assertEquals(0, dt.compareValues(d1, LocalDate.of(2024, 1, 15)));

        assertThrows(IllegalArgumentException.class, () -> dt.parse("2024-99-99"));
    }

    @Test
    @DisplayName("Test BOOLEAN type parsing and comparison")
    void testBooleanType() {
        DataType dt = DataType.BOOLEAN;
        assertEquals(Boolean.TRUE, dt.parse("TRUE"));
        assertEquals(Boolean.TRUE, dt.parse("true"));
        assertEquals(Boolean.TRUE, dt.parse("1"));

        assertEquals(Boolean.FALSE, dt.parse("FALSE"));
        assertEquals(Boolean.FALSE, dt.parse("false"));
        assertEquals(Boolean.FALSE, dt.parse("0"));

        assertTrue(dt.compareValues(false, true) < 0);
        assertEquals(0, dt.compareValues(true, true));

        assertThrows(IllegalArgumentException.class, () -> dt.parse("maybe"));
    }
}
