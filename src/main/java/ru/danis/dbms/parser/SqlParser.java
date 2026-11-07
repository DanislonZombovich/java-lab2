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

    public SqlParser(String sql) {
        SqlLexer lexer = new SqlLexer(sql);
        this.tokens = lexer.tokenize();
        this.index = 0;
    }

    public Statement parse() {
        if (check(TokenType.EOF)) {
            throw new IllegalArgumentException("Empty SQL query");
        }

        Statement stmt;
        if (matchKeyword("EXPLAIN")) {
            stmt = parseExplain();
        } else if (matchKeyword("CREATE")) {
            stmt = parseCreate();
        } else if (matchKeyword("DROP")) {
            stmt = parseDrop();
        } else if (matchKeyword("INSERT")) {
            stmt = parseInsert();
        } else if (matchKeyword("SELECT")) {
            stmt = parseSelect();
        } else if (matchKeyword("UPDATE")) {
            stmt = parseUpdate();
        } else if (matchKeyword("DELETE")) {
            stmt = parseDelete();
        } else if (matchKeyword("SHOW")) {
            stmt = parseShow();
        } else if (matchKeyword("DESCRIBE") || matchKeyword("DESC")) {
            stmt = parseDescribe();
        } else {
            throw new IllegalArgumentException("Unrecognized SQL statement starting with: " + peek().getValue());
        }

        match(TokenType.SEMICOLON);
        return stmt;
    }

    private Statement parseExplain() {
        Statement inner = parse();
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

        while (!check(TokenType.RPAREN) && !check(TokenType.EOF)) {
            String colName = consumeIdentifierOrKeyword();
            String typeName = consumeIdentifierOrKeyword();
            DataType type = DataType.fromString(typeName);

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

            if (!match(TokenType.COMMA)) {
                break;
            }
        }

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
            while (!check(TokenType.RPAREN) && !check(TokenType.EOF)) {
                columns.add(consumeIdentifierOrKeyword());
                if (!match(TokenType.COMMA)) {
                    break;
                }
            }
            consume(TokenType.RPAREN, "Expected ')' after column list");
        }

        consumeKeyword("VALUES");
        List<List<String>> rows = new ArrayList<>();

        while (!check(TokenType.EOF) && !check(TokenType.SEMICOLON)) {
            consume(TokenType.LPAREN, "Expected '(' before row values");
            List<String> values = new ArrayList<>();
            while (!check(TokenType.RPAREN) && !check(TokenType.EOF)) {
                values.add(consumeLiteralOrWord());
                if (!match(TokenType.COMMA)) {
                    break;
                }
            }
            consume(TokenType.RPAREN, "Expected ')' after row values");
            rows.add(values);

            if (!match(TokenType.COMMA)) {
                break;
            }
        }

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
            consume(TokenType.OPERATOR, "=");
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
        Condition left = parseSimpleCondition();

        while (true) {
            if (matchKeyword("AND")) {
                Condition right = parseSimpleCondition();
                left = new Condition(left, Condition.LogicalConnector.AND, right);
            } else if (matchKeyword("OR")) {
                Condition right = parseSimpleCondition();
                left = new Condition(left, Condition.LogicalConnector.OR, right);
            } else {
                break;
            }
        }

        return left;
    }

    private Condition parseSimpleCondition() {
        String columnName = consumeIdentifierOrKeyword();
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
