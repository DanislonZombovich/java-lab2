package ru.danis.dbms.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Enumeration of supported database column data types.
 */
public enum DataType {
    INT,
    DOUBLE,
    VARCHAR,
    DATE,
    BOOLEAN;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * Resolves a string identifier to DataType enum.
     *
     * @param typeName string name of the type
     * @return corresponding DataType
     */
    public static DataType fromString(String typeName) {
        if (typeName == null) {
            throw new IllegalArgumentException("Type name cannot be null");
        }
        String normalized = typeName.trim().toUpperCase();
        return switch (normalized) {
            case "INT", "INTEGER", "BIGINT", "SMALLINT" -> INT;
            case "DOUBLE", "FLOAT", "DECIMAL", "NUMERIC", "REAL" -> DOUBLE;
            case "VARCHAR", "CHAR", "TEXT", "STRING" -> VARCHAR;
            case "DATE" -> DATE;
            case "BOOLEAN", "BOOL" -> BOOLEAN;
            default -> throw new IllegalArgumentException("Unsupported data type: " + typeName);
        };
    }

    /**
     * Parses a string literal into an instance of this type.
     *
     * @param raw input string value
     * @return typed object instance
     */
    public Object parse(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.equalsIgnoreCase("null")) {
            return null;
        }

        try {
            return switch (this) {
                case INT -> Long.parseLong(trimmed);
                case DOUBLE -> Double.parseDouble(trimmed);
                case VARCHAR -> {
                    if ((trimmed.startsWith("'") && trimmed.endsWith("'")) ||
                        (trimmed.startsWith("\"") && trimmed.endsWith("\""))) {
                        if (trimmed.length() >= 2) {
                            yield trimmed.substring(1, trimmed.length() - 1);
                        }
                    }
                    yield trimmed;
                }
                case DATE -> {
                    String cleanDate = trimmed;
                    if ((cleanDate.startsWith("'") && cleanDate.endsWith("'")) ||
                        (cleanDate.startsWith("\"") && cleanDate.endsWith("\""))) {
                        cleanDate = cleanDate.substring(1, cleanDate.length() - 1);
                    }
                    yield LocalDate.parse(cleanDate, DATE_FORMATTER);
                }
                case BOOLEAN -> {
                    if (trimmed.equalsIgnoreCase("true") || trimmed.equalsIgnoreCase("1")) {
                        yield Boolean.TRUE;
                    }
                    if (trimmed.equalsIgnoreCase("false") || trimmed.equalsIgnoreCase("0")) {
                        yield Boolean.FALSE;
                    }
                    throw new IllegalArgumentException("Invalid boolean literal: " + raw);
                }
            };
        } catch (NumberFormatException | DateTimeParseException e) {
            throw new IllegalArgumentException("Cannot convert value '" + raw + "' to " + this.name(), e);
        }
    }

    /**
     * Casts or converts an existing object to match this column's type.
     *
     * @param value source value
     * @return converted typed object
     */
    public Object cast(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String str) {
            return parse(str);
        }
        return switch (this) {
            case INT -> {
                if (value instanceof Number n) {
                    yield n.longValue();
                }
                yield parse(value.toString());
            }
            case DOUBLE -> {
                if (value instanceof Number n) {
                    yield n.doubleValue();
                }
                yield parse(value.toString());
            }
            case VARCHAR -> value.toString();
            case DATE -> {
                if (value instanceof LocalDate d) {
                    yield d;
                }
                yield parse(value.toString());
            }
            case BOOLEAN -> {
                if (value instanceof Boolean b) {
                    yield b;
                }
                yield parse(value.toString());
            }
        };
    }

    /**
     * Compares two objects of this data type.
     *
     * @param o1 first object
     * @param o2 second object
     * @return comparison result (-1, 0, 1)
     */
    @SuppressWarnings("unchecked")
    public int compareValues(Object o1, Object o2) {
        if (o1 == null && o2 == null) {
            return 0;
        }
        if (o1 == null) {
            return -1;
        }
        if (o2 == null) {
            return 1;
        }

        Object c1 = cast(o1);
        Object c2 = cast(o2);

        if (c1 instanceof Comparable comp1 && c2 instanceof Comparable comp2) {
            return comp1.compareTo(comp2);
        }

        return c1.toString().compareTo(c2.toString());
    }
}
