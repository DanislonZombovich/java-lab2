
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NavigableMap;

import static org.junit.jupiter.api.Assertions.*;

public class BTreeIndexTest {

    private BTreeIndex index;

    @BeforeEach
    void setUp() {
        index = new BTreeIndex("idx_brand", "cars", "brand", DataType.VARCHAR);
    }

    @Test
    @DisplayName("Test basic insert and search in B-Tree index")
    void testInsertAndSearch() {
        index.insert("Toyota", 1L);
        index.insert("BMW", 2L);
        index.insert("Audi", 3L);
        index.insert("Toyota", 4L);

        List<Object> toyotaIds = index.search("Toyota");
        assertEquals(2, toyotaIds.size());
        assertTrue(toyotaIds.contains(1L));
        assertTrue(toyotaIds.contains(4L));

        List<Object> bmwIds = index.search("BMW");
        assertEquals(List.of(2L), bmwIds);

        List<Object> missing = index.search("Ferrari");
        assertTrue(missing.isEmpty());
    }

    @Test
    @DisplayName("Test range search on numeric index")
    void testNumericRangeSearch() {
        BTreeIndex priceIndex = new BTreeIndex("idx_price", "cars", "price", DataType.DOUBLE);

        priceIndex.insert(10000.0, 1L);
        priceIndex.insert(20000.0, 2L);
        priceIndex.insert(30000.0, 3L);
        priceIndex.insert(40000.0, 4L);
        priceIndex.insert(50000.0, 5L);

        List<Object> greaterThan25k = priceIndex.searchRange(25000.0, null, false, false);
        assertEquals(3, greaterThan25k.size());
        assertTrue(greaterThan25k.contains(3L));
        assertTrue(greaterThan25k.contains(4L));
        assertTrue(greaterThan25k.contains(5L));

        List<Object> between20kAnd40kInclusive = priceIndex.searchRange(20000.0, 40000.0, true, true);
        assertEquals(3, between20kAnd40kInclusive.size());
        assertTrue(between20kAnd40kInclusive.contains(2L));
        assertTrue(between20kAnd40kInclusive.contains(3L));
        assertTrue(between20kAnd40kInclusive.contains(4L));
    }

    @Test
    @DisplayName("Test deletion from B-Tree index")
    void testDelete() {
        index.insert("Toyota", 1L);
        index.insert("Toyota", 2L);
        index.insert("BMW", 3L);

        assertEquals(2, index.search("Toyota").size());

        index.delete("Toyota", 1L);
        List<Object> remaining = index.search("Toyota");
        assertEquals(1, remaining.size());
        assertEquals(2L, remaining.get(0));

        index.delete("Toyota", 2L);
        assertTrue(index.search("Toyota").isEmpty());
    }

    @Test
    @DisplayName("Test B-Tree index persistence serialization format")
    void testSerializationRoundTrip() {
        index.insert("Toyota", 1L);
        index.insert("BMW", 2L);
        index.insert("Audi", 3L);

        NavigableMap<Comparable<?>, List<Object>> entries = index.getEntries();
        assertEquals(3, entries.size());

        BTreeIndex newIndex = new BTreeIndex("idx_brand", "cars", "brand", DataType.VARCHAR);
        newIndex.loadEntries(entries);

        assertEquals(List.of(1L), newIndex.search("Toyota"));
        assertEquals(List.of(2L), newIndex.search("BMW"));
        assertEquals(List.of(3L), newIndex.search("Audi"));
    }

    @Test
    @DisplayName("Test large insertion and balance splits")
    void testLargeDataset() {
        BTreeIndex intIndex = new BTreeIndex("idx_id", "items", "id", DataType.INT);
        int total = 2000;
        for (int i = 1; i <= total; i++) {
            intIndex.insert((long) i, (long) (i * 10));
        }

        assertEquals(total, intIndex.size());

        for (int i = 1; i <= total; i += 50) {
            List<Object> found = intIndex.search((long) i);
            assertEquals(1, found.size());
            assertEquals((long) (i * 10), found.get(0));
        }
    }
}
