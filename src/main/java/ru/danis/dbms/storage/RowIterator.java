package ru.danis.dbms.storage;

import ru.danis.dbms.model.Row;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Iterator pattern implementation for streaming sequential row scans.
 */
public class RowIterator implements Iterator<Row>, AutoCloseable {

    private final Iterator<Row> internalIterator;
    private final int totalCount;
    private int cursor;

    /**
     * Creates a sequential row iterator over a row collection.
     *
     * @param rows rows to iterate
     */
    public RowIterator(List<Row> rows) {
        Objects.requireNonNull(rows, "rows list cannot be null");
        this.internalIterator = rows.iterator();
        this.totalCount = rows.size();
        this.cursor = 0;
    }

    @Override
    public boolean hasNext() {
        return internalIterator.hasNext();
    }

    @Override
    public Row next() {
        cursor++;
        return internalIterator.next();
    }

    /**
     * Returns the current zero-based cursor offset.
     *
     * @return current zero-based cursor offset
     */
    public int getCursor() {
        return cursor;
    }

    /**
     * Returns the total number of rows covered by this iterator.
     *
     * @return total number of rows in the iterator
     */
    public int getTotalCount() {
        return totalCount;
    }

    @Override
    public void close() {
    }
}
