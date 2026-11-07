package ru.danis.dbms.parser.statement;

/**
 * Statement representing SHOW TABLES command.
 */
public class ShowTablesStatement implements Statement {
    @Override
    public StatementType getType() {
        return StatementType.SHOW_TABLES;
    }
}
