package ru.danis.dbms.index;

import ru.danis.dbms.model.DataType;

import java.util.List;
import java.util.NavigableMap;

/**
 * Interface representing a database index for accelerated lookups.
 */
public interface Index {

    String getName();

    String getTableName();

    String getColumnName();

    DataType getIndexedType();

    void insert(Comparable<?> key, Object recordIdentifier);

    void delete(Comparable<?> key, Object recordIdentifier);

    List<Object> search(Comparable<?> key);

    List<Object> searchRange(Comparable<?> min, Comparable<?> max, boolean includeMin, boolean includeMax);

    void clear();

    int size();

    NavigableMap<Comparable<?>, List<Object>> getEntries();

    void loadEntries(NavigableMap<Comparable<?>, List<Object>> data);
}
