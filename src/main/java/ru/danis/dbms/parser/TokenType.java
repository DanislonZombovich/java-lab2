package ru.danis.dbms.parser;

/**
 * Enumeration of token types identified during SQL lexical analysis.
 */
public enum TokenType {
    KEYWORD,
    IDENTIFIER,
    STRING_LITERAL,
    NUMBER_LITERAL,
    BOOLEAN_LITERAL,
    OPERATOR,
    COMMA,
    SEMICOLON,
    LPAREN,
    RPAREN,
    STAR,
    EOF
}
