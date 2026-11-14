
/**
 * Statement representing SHOW TABLES command.
 */
public class ShowTablesStatement implements Statement {

    /**
     * Creates a SHOW TABLES statement.
     */
    public ShowTablesStatement() {
    }

    @Override
    public StatementType getType() {
        return StatementType.SHOW_TABLES;
    }
}
