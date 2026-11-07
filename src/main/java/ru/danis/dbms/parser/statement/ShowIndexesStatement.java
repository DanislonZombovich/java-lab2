package ru.danis.dbms.parser.statement;

/**
 * Statement representing SHOW INDEXES command.
 */
public class ShowIndexesStatement implements Statement {
    private final String tableName;

    public ShowIndexesStatement(String tableName) {
        this.tableName = tableName;
    }

    public String getTableName() {
        return tableName;
    }

    public boolean hasTableFilter() {
        return tableName != null && !tableName.isBlank();
    }

    @Override
    public StatementType getType() {
        return StatementType.SHOW_INDEXES;
    }
}
