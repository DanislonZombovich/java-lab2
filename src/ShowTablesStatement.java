public class ShowTablesStatement implements Statement {

    public ShowTablesStatement() {
    }

    @Override
    public StatementType getType() {
        return StatementType.SHOW_TABLES;
    }
}
