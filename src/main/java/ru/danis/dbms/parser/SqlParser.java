package ru.danis.dbms.parser;

import ru.danis.dbms.model.*;
import ru.danis.dbms.parser.statement.*;

import java.util.*;

/**
 * Recursive descent SQL parser supporting DDL, DML, DCL, and query analysis statements.
 */
public class SqlParser {

    private final List<Token> tokens;
    private int index;

    /**
     * Constructs a SQL parser for the given query text.
     *
     * @param sql raw SQL statement string
     */
    public SqlParser(String sql) {
        SqlLexer lexer = new SqlLexer(sql);
        this.tokens = lexer.tokenize();
        this.index = 0;
    }

    /**
     * Parses a single SQL statement and ensures no unexpected trailing tokens remain.
     *
     * @return parsed AST {@link Statement}
     */
    public Statement parse() {
        Statement stmt = parseStatement();
        match(TokenType.SEMICOLON);
        if (!check(TokenType.EOF)) {
            throw new IllegalArgumentException("Unexpected trailing token at position " + peek().getPosition() + ": " + peek().getValue());
        }
        return stmt;
    }

    private Statement parseStatement() {
        if (check(TokenType.EOF)) {
            throw new IllegalArgumentException("Empty SQL query");
        }

        if (matchKeyword("EXPLAIN")) {
            return parseExplain();
        } else if (matchKeyword("CREATE")) {
            return parseCreate();
        } else if (matchKeyword("DROP")) {
            return parseDrop();
        } else if (matchKeyword("INSERT")) {
            return parseInsert();
        } else if (matchKeyword("SELECT")) {
            return parseSelect();
        } else if (matchKeyword("UPDATE")) {
            return parseUpdate();
        } else if (matchKeyword("DELETE")) {
            return parseDelete();
        } else if (matchKeyword("SHOW")) {
            return parseShow();
        } else if (matchKeyword("DESCRIBE") || matchKeyword("DESC")) {
            return parseDescribe();
        } else {
            throw new IllegalArgumentException("Unrecognized SQL statement starting with: " + peek().getValue());
        }
    }

    private Statement parseExplain() {
        Statement inner = parseStatement();
        return new ExplainStatement(inner);
    }

    private Statement parseCreate() {
        if (matchKeyword("TABLE")) {
            return parseCreateTable();
        }
        if (matchKeyword("INDEX")) {
            return parseCreateIndex();
        }
        throw new IllegalArgumentException("Expected TABLE or INDEX after CREATE, found: " + peek().getValue());
    }

    private Statement parseDrop() {
        if (matchKeyword("TABLE")) {
            boolean ifExists = false;
            if (matchKeyword("IF")) {
                consumeKeyword("EXISTS");
                ifExists = true;
            }
            String tableName = consumeIdentifierOrKeyword();
            return new DropTableStatement(tableName, ifExists);
        }
        if (matchKeyword("INDEX")) {
            boolean ifExists = false;
            if (matchKeyword("IF")) {
                consumeKeyword("EXISTS");
                ifExists = true;
            }
            String indexName = consumeIdentifierOrKeyword();
            return new DropIndexStatement(indexName, ifExists);
        }
        throw new IllegalArgumentException("Expected TABLE or INDEX after DROP, found: " + peek().getValue());
    }

    private Statement parseCreateTable() {
        boolean ifNotExists = false;
        if (matchKeyword("IF")) {
            consumeKeyword("NOT");
            consumeKeyword("EXISTS");
            ifNotExists = true;
        }

        String tableName = consumeIdentifierOrKeyword();
        consume(TokenType.LPAREN, "Expected '(' after table name");

        List<Column> columns = new ArrayList<>();

        do {
            String colName = consumeIdentifierOrKeyword();
            String typeName = consumeIdentifierOrKeyword();
            DataType type = DataType.fromString(typeName);

            if (match(TokenType.LPAREN)) {
                consume(TokenType.NUMBER_LITERAL, "Expected type length or precision");
                if (match(TokenType.COMMA)) {
                    consume(TokenType.NUMBER_LITERAL, "Expected type scale");
                }
                consume(TokenType.RPAREN, "Expected ')' after type length");
            }

            boolean primaryKey = false;
            boolean notNull = false;
            ForeignKey fk = null;

            while (true) {
                if (matchKeyword("PRIMARY")) {
                    consumeKeyword("KEY");
                    primaryKey = true;
                } else if (matchKeyword("NOT")) {
                    consumeKeyword("NULL");
                    notNull = true;
                } else if (matchKeyword("REFERENCES")) {
                    String refTable = consumeIdentifierOrKeyword();
                    consume(TokenType.LPAREN, "Expected '(' after referenced table name");
                    String refCol = consumeIdentifierOrKeyword();
                    consume(TokenType.RPAREN, "Expected ')' after referenced column name");
                    fk = new ForeignKey(refTable, refCol);
                } else {
                    break;
                }
            }

            columns.add(new Column(colName, type, primaryKey, !notNull && !primaryKey, fk));
        } while (match(TokenType.COMMA));

        consume(TokenType.RPAREN, "Expected ')' at end of column definitions");
        return new CreateTableStatement(new TableSchema(tableName, columns), ifNotExists);
    }

    private Statement parseCreateIndex() {
        boolean ifNotExists = false;
        if (matchKeyword("IF")) {
            consumeKeyword("NOT");
            consumeKeyword("EXISTS");
            ifNotExists = true;
        }

        String indexName = consumeIdentifierOrKeyword();
        consumeKeyword("ON");
        String tableName = consumeIdentifierOrKeyword();

        consume(TokenType.LPAREN, "Expected '(' before index column name");
        String columnName = consumeIdentifierOrKeyword();
        consume(TokenType.RPAREN, "Expected ')' after index column name");

        return new CreateIndexStatement(indexName, tableName, columnName, ifNotExists);
    }

    private Statement parseInsert() {
        consumeKeyword("INTO");
        String tableName = consumeIdentifierOrKeyword();

        List<String> columns = new ArrayList<>();
        if (match(TokenType.LPAREN)) {
            do {
                columns.add(consumeIdentifierOrKeyword());
            } while (match(TokenType.COMMA));
            consume(TokenType.RPAREN, "Expected ')' after column list");
        }

        consumeKeyword("VALUES");
        List<List<String>> rows = new ArrayList<>();

        do {
            consume(TokenType.LPAREN, "Expected '(' before row values");
            List<String> values = new ArrayList<>();
            if (!check(TokenType.RPAREN)) {
                do {
                    values.add(consumeLiteralOrWord());
                } while (match(TokenType.COMMA));
            }
            consume(TokenType.RPAREN, "Expected ')' after row values");
            rows.add(values);
        } while (match(TokenType.COMMA));

        return new InsertStatement(tableName, columns, rows);
    }

    private Statement parseSelect() {
        boolean selectAll = false;
        List<String> columns = new ArrayList<>();

        if (match(TokenType.STAR)) {
            selectAll = true;
        } else {
            while (!check(TokenType.EOF)) {
                columns.add(consumeIdentifierOrKeyword());
                if (!match(TokenType.COMMA)) {
                    break;
                }
            }
        }

        consumeKeyword("FROM");
        String tableName = consumeIdentifierOrKeyword();

        Condition condition = null;
        if (matchKeyword("WHERE")) {
            condition = parseCondition();
        }

        return new SelectStatement(tableName, columns, selectAll, condition);
    }

    private Statement parseUpdate() {
        String tableName = consumeIdentifierOrKeyword();
        consumeKeyword("SET");

        Map<String, String> updates = new LinkedHashMap<>();
        while (!check(TokenType.EOF)) {
            String col = consumeIdentifierOrKeyword();
            Token eqToken = consume(TokenType.OPERATOR, "Expected '=' in SET clause");
            if (!"=".equals(eqToken.getValue())) {
                throw new IllegalArgumentException("Expected '=' in SET clause at position " + eqToken.getPosition() + ", found: " + eqToken.getValue());
            }
            String val = consumeLiteralOrWord();
            updates.put(col, val);

            if (!match(TokenType.COMMA)) {
                break;
            }
        }

        Condition condition = null;
        if (matchKeyword("WHERE")) {
            condition = parseCondition();
        }

        return new UpdateStatement(tableName, updates, condition);
    }

    private Statement parseDelete() {
        consumeKeyword("FROM");
        String tableName = consumeIdentifierOrKeyword();

        Condition condition = null;
        if (matchKeyword("WHERE")) {
            condition = parseCondition();
        }

        return new DeleteStatement(tableName, condition);
    }

    private Statement parseShow() {
        if (matchKeyword("TABLES")) {
            return new ShowTablesStatement();
        }
        if (matchKeyword("INDEXES")) {
            String targetTable = null;
            if (matchKeyword("FROM") || matchKeyword("IN")) {
                targetTable = consumeIdentifierOrKeyword();
            }
            return new ShowIndexesStatement(targetTable);
        }
        throw new IllegalArgumentException("Expected TABLES or INDEXES after SHOW, found: " + peek().getValue());
    }

    private Statement parseDescribe() {
        String tableName = consumeIdentifierOrKeyword();
        return new DescribeTableStatement(tableName);
    }

    private Condition parseCondition() {
        return parseOrExpression();
    }

    private Condition parseOrExpression() {
        Condition left = parseAndExpression();
        while (matchKeyword("OR")) {
            Condition right = parseAndExpression();
            left = new Condition(left, Condition.LogicalConnector.OR, right);
        }
        return left;
    }

    private Condition parseAndExpression() {
        Condition left = parsePrimaryCondition();
        while (matchKeyword("AND")) {
            Condition right = parsePrimaryCondition();
            left = new Condition(left, Condition.LogicalConnector.AND, right);
        }
        return left;
    }

    private Condition parsePrimaryCondition() {
        if (match(TokenType.LPAREN)) {
            Condition inner = parseOrExpression();
            consume(TokenType.RPAREN, "Expected ')' after parenthesized condition");
            return inner;
        }
        return parseSimpleCondition();
    }

    private Condition parseSimpleCondition() {
        String columnName = consumeIdentifierOrKeyword();
        if (matchKeyword("IS")) {
            if (matchKeyword("NOT")) {
                consumeKeyword("NULL");
                return new Condition(columnName, ComparisonOperator.NOT_EQUALS, null);
            }
            consumeKeyword("NULL");
            return new Condition(columnName, ComparisonOperator.EQUALS, null);
        }
        Token opToken = consume(TokenType.OPERATOR, "Expected comparison operator");
        ComparisonOperator op = ComparisonOperator.fromString(opToken.getValue());
        String literal = consumeLiteralOrWord();

        return new Condition(columnName, op, literal);
    }

    private String consumeLiteralOrWord() {
        Token t = peek();
        if (t.getType() == TokenType.KEYWORD && t.getValue().equalsIgnoreCase("NULL")) {
            advance();
            return null;
        }
        if (t.getType() == TokenType.STRING_LITERAL ||
            t.getType() == TokenType.NUMBER_LITERAL ||
            t.getType() == TokenType.BOOLEAN_LITERAL ||
            t.getType() == TokenType.IDENTIFIER ||
            t.getType() == TokenType.KEYWORD) {
            advance();
            return t.getValue();
        }
        throw new IllegalArgumentException("Expected literal or identifier at position " + t.getPosition() + ", found " + t.getValue());
    }

    private String consumeIdentifierOrKeyword() {
        Token t = peek();
        if (t.getType() == TokenType.IDENTIFIER || t.getType() == TokenType.KEYWORD) {
            advance();
            return t.getValue();
        }
        throw new IllegalArgumentException("Expected identifier at position " + t.getPosition() + ", found: " + t.getValue());
    }

    private boolean matchKeyword(String keyword) {
        if (check(TokenType.KEYWORD) && peek().getValue().equalsIgnoreCase(keyword)) {
            advance();
            return true;
        }
        return false;
    }

    private void consumeKeyword(String keyword) {
        if (!matchKeyword(keyword)) {
            throw new IllegalArgumentException("Expected keyword '" + keyword + "' at position " + peek().getPosition() + ", found: " + peek().getValue());
        }
    }

    private boolean match(TokenType type) {
        if (check(type)) {
            advance();
            return true;
        }
        return false;
    }

    private Token consume(TokenType type, String errorMsg) {
        if (check(type)) {
            return advance();
        }
        throw new IllegalArgumentException(errorMsg + " at position " + peek().getPosition() + ", found: " + peek().getValue());
    }

    private boolean check(TokenType type) {
        return peek().getType() == type;
    }

    private Token peek() {
        if (index < tokens.size()) {
            return tokens.get(index);
        }
        return tokens.get(tokens.size() - 1);
    }

    private Token advance() {
        Token current = peek();
        if (index < tokens.size()) {
            index++;
        }
        return current;
    }
}
