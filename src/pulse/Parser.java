/*
program    -> declaration* EOF ;

declaration -> varDecl | statement ;
varDecl    -> "var" IDENTIFIER ( "=" expression )? ";" ;
statement  -> exprStmt | printStmt | block;

exprStmt   -> expression ";" ;
printStmt  -> "print" expression ";" ;
block      -> "{" declaration* "}" ;

expression -> assignment ;

assignment -> IDENTIFIER "=" assignment | equality ;
equality   -> comparison ( ( "!=" | "==" ) comparison )* ;
comparison -> term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
term       -> factor ( ( "-" | "+" ) factor )* ;
factor     -> unary ( ( "/" | "*" ) unary )* ;
unary      -> ( "!" | "-" ) unary | primary ;

primary    -> NUMBER | STRING | "true" | "false" | "nil"
            | "(" expression ")"
            | IDENTIFIER ;
 */

package pulse;

import java.util.ArrayList;
import java.util.List;

import static pulse.TokenType.*;

public class Parser {

    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    // program -> declaration* EOF ;
    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd())
            statements.add(declaration());
        return statements;
    }

    // declaration -> varDecl | statement ;
    private Stmt declaration() {
        try {
            if (match(VAR))
                return varDeclaration();
            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    // varDecl -> "var" IDENTIFIER ( "=" expression )? ";" ;
    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expect variable name.");

        Expr initializer = null;
        if (match(EQUAL))
            initializer = expression();

        consume(SEMICOLON, "Expect ';' after variable declaration.");
        return new Stmt.Var(name, initializer);
    }

    // statement -> exprStmt | printStmt | block;
    private Stmt statement() {
        if (match(PRINT))
            return printStatement();
        if (match(LEFT_BRACE))
            return new Stmt.Block(block());
        return expressionStatement();
    }

    // printStmt -> "print" expression ";" ;
    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    // exprStmt -> expression ";" ;
    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after expression.");
        return new Stmt.Expression(expr);
    }

    // block -> "{" declaration* "}" ;
    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd())
            statements.add(declaration());

        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    // expression -> assignment ;
    private Expr expression() {
        return assignment();
    }

    // assignment -> IDENTIFIER "=" assignment | equality ;
    private Expr assignment() {
        Expr expr = equality();

        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            }

            error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    // equality -> comparison ( ( "!=" | "==" ) comparison )* ;
    private Expr equality() {
        Expr expr = comparison();

        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }
    // comparison -> term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
    private Expr comparison() {
        Expr expr = term();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }
    // term -> factor ( ( "-" | "+" ) factor )* ;
    private Expr term() {
        Expr expr = factor();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }
    // factor -> unary ( ( "/" | "*" ) unary )* ;
    private Expr factor() {
        Expr expr = unary();

        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }
    // unary -> ( "!" | "-" ) unary | primary ; (right associative)
    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return primary();
    }
    // primary -> NUMBER | STRING | "true" | "false" | "nil" | "(" expression ")" ;
    private Expr primary() {
        if (match(FALSE))
            return new Expr.Literal(false);
        if (match(TRUE))
            return new Expr.Literal(true);
        if (match(NIL))
            return new Expr.Literal(null);

        if (match(NUMBER, STRING))
            return new Expr.Literal(previous().literal);

        if (match(IDENTIFIER))
            return new Expr.Variable(previous());

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        throw error(peek(), "Expect expression.");
    }

    // does the current token has any of the given types? consume/return true if so
    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }
    // check that the next token is the expected type, then consume it
    private Token consume(TokenType type, String message) {
        if (check(type))
            return advance();
        throw error(peek(), message);
    }
    // return if the current token is of the given type. doesn't consume
    private boolean check(TokenType type) {
        if (isAtEnd())
            return false;
        return peek().type == type;
    }
    // consume the current token and return it
    private Token advance() {
        if (!isAtEnd())
            ++current;
        return previous();
    }
    // check if we've run  out of tokens to parse
    private boolean isAtEnd() {
        return peek().type == EOF;
    }
    // return the current token not yet consumed
    private Token peek() {
        return tokens.get(current);
    }
    // return the most recently consumed token, for match()
    private Token previous() {
        return tokens.get(current - 1);
    }

    // unexpected token, print message, throw ParseError
    private ParseError error(Token token, String message) {
        Pulse.error(token, message);
        return new ParseError();
    }

    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            if (previous().type == SEMICOLON)
                return;

            switch (peek().type) {
                case CLASS, FUN, VAR, FOR, IF, WHILE, PRINT, RETURN
                    -> { return; }
            }

            advance();
        }
    }
}
