package ru.danis.dbms.index;

import ru.danis.dbms.model.DataType;

import java.util.*;

/**
 * Balanced B-Tree index implementation for database columns.
 */
public class BTreeIndex implements Index {

    private static final int DEGREE = 8;

    private final String name;
    private final String tableName;
    private final String columnName;
    private final DataType indexedType;
    private BTreeNode root;
    private int size;

    public BTreeIndex(String name, String tableName, String columnName, DataType indexedType) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.tableName = Objects.requireNonNull(tableName, "tableName cannot be null");
        this.columnName = Objects.requireNonNull(columnName, "columnName cannot be null");
        this.indexedType = Objects.requireNonNull(indexedType, "indexedType cannot be null");
        this.root = new BTreeNode(true);
        this.size = 0;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getTableName() {
        return tableName;
    }

    @Override
    public String getColumnName() {
        return columnName;
    }

    @Override
    public DataType getIndexedType() {
        return indexedType;
    }

    @Override
    public synchronized void insert(Comparable<?> rawKey, Object recordId) {
        if (rawKey == null || recordId == null) {
            return;
        }
        Comparable<?> key = (Comparable<?>) indexedType.cast(rawKey);

        BTreeNode r = root;
        if (r.keys.size() == 2 * DEGREE - 1) {
            BTreeNode s = new BTreeNode(false);
            root = s;
            s.children.add(r);
            splitChild(s, 0, r);
            insertNonFull(s, key, recordId);
        } else {
            insertNonFull(r, key, recordId);
        }
        size++;
    }

    private void insertNonFull(BTreeNode node, Comparable<?> key, Object recordId) {
        int i = node.keys.size() - 1;

        if (node.isLeaf) {
            while (i >= 0 && compare(key, node.keys.get(i)) < 0) {
                i--;
            }
            if (i >= 0 && compare(key, node.keys.get(i)) == 0) {
                List<Object> ids = node.values.get(i);
                if (!ids.contains(recordId)) {
                    ids.add(recordId);
                }
                return;
            }
            node.keys.add(i + 1, key);
            List<Object> ids = new ArrayList<>();
            ids.add(recordId);
            node.values.add(i + 1, ids);
        } else {
            while (i >= 0 && compare(key, node.keys.get(i)) < 0) {
                i--;
            }
            if (i >= 0 && compare(key, node.keys.get(i)) == 0) {
                List<Object> ids = node.values.get(i);
                if (!ids.contains(recordId)) {
                    ids.add(recordId);
                }
                return;
            }
            i++;
            BTreeNode child = node.children.get(i);
            if (child.keys.size() == 2 * DEGREE - 1) {
                splitChild(node, i, child);
                if (compare(key, node.keys.get(i)) > 0) {
                    i++;
                } else if (compare(key, node.keys.get(i)) == 0) {
                    List<Object> ids = node.values.get(i);
                    if (!ids.contains(recordId)) {
                        ids.add(recordId);
                    }
                    return;
                }
            }
            insertNonFull(node.children.get(i), key, recordId);
        }
    }

    private void splitChild(BTreeNode parent, int index, BTreeNode fullChild) {
        BTreeNode z = new BTreeNode(fullChild.isLeaf);
        int mid = DEGREE - 1;

        Comparable<?> medianKey = fullChild.keys.get(mid);
        List<Object> medianVal = fullChild.values.get(mid);

        for (int j = 0; j < mid; j++) {
            z.keys.add(fullChild.keys.get(mid + 1 + j));
            z.values.add(fullChild.values.get(mid + 1 + j));
        }

        if (!fullChild.isLeaf) {
            for (int j = 0; j < DEGREE; j++) {
                z.children.add(fullChild.children.get(DEGREE + j));
            }
        }

        for (int j = 2 * DEGREE - 2; j >= mid; j--) {
            fullChild.keys.remove(j);
            fullChild.values.remove(j);
        }
        if (!fullChild.isLeaf) {
            for (int j = 2 * DEGREE - 1; j >= DEGREE; j--) {
                fullChild.children.remove(j);
            }
        }

        parent.children.add(index + 1, z);
        parent.keys.add(index, medianKey);
        parent.values.add(index, medianVal);
    }

    @Override
    public synchronized List<Object> search(Comparable<?> rawKey) {
        if (rawKey == null) {
            return Collections.emptyList();
        }
        Comparable<?> key = (Comparable<?>) indexedType.cast(rawKey);
        return searchInNode(root, key);
    }

    private List<Object> searchInNode(BTreeNode node, Comparable<?> key) {
        int i = 0;
        while (i < node.keys.size() && compare(key, node.keys.get(i)) > 0) {
            i++;
        }
        if (i < node.keys.size() && compare(key, node.keys.get(i)) == 0) {
            return Collections.unmodifiableList(new ArrayList<>(node.values.get(i)));
        }
        if (node.isLeaf) {
            return Collections.emptyList();
        }
        return searchInNode(node.children.get(i), key);
    }

    @Override
    public synchronized List<Object> searchRange(Comparable<?> min, Comparable<?> max, boolean includeMin, boolean includeMax) {
        List<Object> result = new ArrayList<>();
        Comparable<?> castMin = min != null ? (Comparable<?>) indexedType.cast(min) : null;
        Comparable<?> castMax = max != null ? (Comparable<?>) indexedType.cast(max) : null;

        collectRange(root, castMin, castMax, includeMin, includeMax, result);
        return result;
    }

    private void collectRange(BTreeNode node, Comparable<?> min, Comparable<?> max, boolean includeMin, boolean includeMax, List<Object> acc) {
        for (int i = 0; i < node.keys.size(); i++) {
            Comparable<?> k = node.keys.get(i);

            if (!node.isLeaf) {
                boolean visitLeft = (min == null) || (compare(k, min) >= 0);
                if (visitLeft) {
                    collectRange(node.children.get(i), min, max, includeMin, includeMax, acc);
                }
            }

            boolean greaterThanMin = (min == null) || (includeMin ? compare(k, min) >= 0 : compare(k, min) > 0);
            boolean lessThanMax = (max == null) || (includeMax ? compare(k, max) <= 0 : compare(k, max) < 0);

            if (greaterThanMin && lessThanMax) {
                acc.addAll(node.values.get(i));
            }

            if (max != null && compare(k, max) > 0) {
                return;
            }
        }

        if (!node.isLeaf) {
            Comparable<?> lastKey = node.keys.get(node.keys.size() - 1);
            if (max == null || compare(lastKey, max) <= 0) {
                collectRange(node.children.get(node.keys.size()), min, max, includeMin, includeMax, acc);
            }
        }
    }

    @Override
    public synchronized void delete(Comparable<?> rawKey, Object recordId) {
        if (rawKey == null || recordId == null) {
            return;
        }
        Comparable<?> key = (Comparable<?>) indexedType.cast(rawKey);
        NavigableMap<Comparable<?>, List<Object>> map = getEntries();
        List<Object> list = map.get(key);
        if (list != null && list.remove(recordId)) {
            if (list.isEmpty()) {
                map.remove(key);
            }
            loadEntries(map);
        }
    }

    @Override
    public synchronized void clear() {
        this.root = new BTreeNode(true);
        this.size = 0;
    }

    @Override
    public synchronized int size() {
        return size;
    }

    @Override
    public synchronized NavigableMap<Comparable<?>, List<Object>> getEntries() {
        NavigableMap<Comparable<?>, List<Object>> map = new TreeMap<>((k1, k2) -> compare(k1, k2));
        collectAll(root, map);
        return map;
    }

    private void collectAll(BTreeNode node, Map<Comparable<?>, List<Object>> map) {
        for (int i = 0; i < node.keys.size(); i++) {
            if (!node.isLeaf) {
                collectAll(node.children.get(i), map);
            }
            map.put(node.keys.get(i), new ArrayList<>(node.values.get(i)));
        }
        if (!node.isLeaf) {
            collectAll(node.children.get(node.keys.size()), map);
        }
    }

    @Override
    public synchronized void loadEntries(NavigableMap<Comparable<?>, List<Object>> data) {
        clear();
        if (data == null) {
            return;
        }
        for (Map.Entry<Comparable<?>, List<Object>> entry : data.entrySet()) {
            for (Object id : entry.getValue()) {
                insert(entry.getKey(), id);
            }
        }
    }

    private int compare(Comparable<?> k1, Comparable<?> k2) {
        return indexedType.compareValues(k1, k2);
    }

    private static class BTreeNode {
        final boolean isLeaf;
        final List<Comparable<?>> keys = new ArrayList<>();
        final List<List<Object>> values = new ArrayList<>();
        final List<BTreeNode> children = new ArrayList<>();

        BTreeNode(boolean isLeaf) {
            this.isLeaf = isLeaf;
        }
    }
}
