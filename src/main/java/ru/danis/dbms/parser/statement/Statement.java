package ru.danis.dbms.parser.statement;

/**
 * Common interface for parsed SQL statements.
 */
public interface Statement {

    enum StatementType {
        CREATE_TABLE,
        DROP_TABLE,
        CREATE_INDEX,
        DROP_INDEX,
        INSERT,
        SELECT,
        UPDATE,
        DELETE,
        SHOW_TABLES,
        DESCRIBE_TABLE,
        SHOW_INDEXES,
        EXPLAIN
    }

    StatementType getType();
}
