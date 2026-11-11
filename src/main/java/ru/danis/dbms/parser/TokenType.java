package ru.danis.dbms.parser;

/**
 * Enumeration of token types identified during SQL lexical analysis.
 */
public enum TokenType {
    /** SQL keyword such as SELECT, FROM, WHERE, CREATE. */
    KEYWORD,
    /** Unquoted table, column, or index identifier. */
    IDENTIFIER,
    /** Single- or double-quoted string literal. */
    STRING_LITERAL,
    /** Numeric integer or floating-point literal. */
    NUMBER_LITERAL,
    /** Boolean literal (TRUE or FALSE). */
    BOOLEAN_LITERAL,
    /** Comparison operator (=, !=, &lt;&gt;, &lt;, &lt;=, &gt;, &gt;=). */
    OPERATOR,
    /** Comma separator (,). */
    COMMA,
    /** Statement terminator (;). */
    SEMICOLON,
    /** Opening parenthesis ((). */
    LPAREN,
    /** Closing parenthesis ()). */
    RPAREN,
    /** Asterisk wildcard (*). */
    STAR,
    /** End-of-input marker token. */
    EOF
}
