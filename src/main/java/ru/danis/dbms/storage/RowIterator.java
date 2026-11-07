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

    public int getCursor() {
        return cursor;
    }

    public int getTotalCount() {
        return totalCount;
    }

    @Override
    public void close() {
    }
}
