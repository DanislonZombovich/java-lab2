
/**
 * Common interface for parsed SQL statements.
 */
public interface Statement {

    /**
     * Supported SQL statement kinds.
     */
    enum StatementType {
        /** CREATE TABLE statement. */
        CREATE_TABLE,
        /** DROP TABLE statement. */
        DROP_TABLE,
        /** CREATE INDEX statement. */
        CREATE_INDEX,
        /** DROP INDEX statement. */
        DROP_INDEX,
        /** INSERT INTO statement. */
        INSERT,
        /** SELECT query statement. */
        SELECT,
        /** UPDATE statement. */
        UPDATE,
        /** DELETE statement. */
        DELETE,
        /** SHOW TABLES metadata query. */
        SHOW_TABLES,
        /** DESCRIBE table metadata query. */
        DESCRIBE_TABLE,
        /** SHOW INDEXES metadata query. */
        SHOW_INDEXES,
        /** EXPLAIN execution plan query. */
        EXPLAIN
    }

    /**
     * Returns the type of this SQL statement.
     *
     * @return statement type constant
     */
    StatementType getType();
}
