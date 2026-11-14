

import java.util.List;
import java.util.NavigableMap;

/**
 * Interface representing a database index for accelerated lookups.
 */
public interface Index {

    /**
     * Returns the unique index name.
     *
     * @return unique index name
     */
    String getName();

    /**
     * Returns the indexed table name.
     *
     * @return indexed table name
     */
    String getTableName();

    /**
     * Returns the indexed column name.
     *
     * @return indexed column name
     */
    String getColumnName();

    /**
     * Returns the indexed column data type.
     *
     * @return indexed column data type
     */
    DataType getIndexedType();

    /**
     * Inserts a key-to-record-identifier mapping into the index.
     *
     * @param key              indexed column value
     * @param recordIdentifier row primary key or positional identifier
     */
    void insert(Comparable<?> key, Object recordIdentifier);

    /**
     * Removes a key-to-record-identifier mapping from the index.
     *
     * @param key              indexed column value
     * @param recordIdentifier row primary key or positional identifier
     */
    void delete(Comparable<?> key, Object recordIdentifier);

    /**
     * Performs an exact-match key lookup.
     *
     * @param key target key value
     * @return matching record identifiers
     */
    List<Object> search(Comparable<?> key);

    /**
     * Performs an ordered range lookup.
     *
     * @param min        lower bound or null for unbounded
     * @param max        upper bound or null for unbounded
     * @param includeMin true if lower bound is inclusive
     * @param includeMax true if upper bound is inclusive
     * @return matching record identifiers in key order
     */
    List<Object> searchRange(Comparable<?> min, Comparable<?> max, boolean includeMin, boolean includeMax);

    /** Removes all entries from the index. */
    void clear();

    /**
     * Returns total number of indexed record references.
     *
     * @return total number of indexed record references
     */
    int size();

    /**
     * Returns an ordered snapshot of all indexed keys and record identifier lists.
     *
     * @return ordered snapshot of all indexed keys and record identifier lists
     */
    NavigableMap<Comparable<?>, List<Object>> getEntries();

    /**
     * Replaces current index contents with entries from the provided map.
     *
     * @param data ordered map of keys to record identifier lists
     */
    void loadEntries(NavigableMap<Comparable<?>, List<Object>> data);
}
