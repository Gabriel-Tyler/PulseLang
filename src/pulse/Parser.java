package pulse;

import java.util.ArrayList;
import java.util.Arrays;
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

    // declaration -> funDecl | varDecl | statement ;
    private Stmt declaration() {
        try {
            if (match(FUN))
                return function("function");
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

    // statement -> exprStmt | ifStmt | printStmt
    //            | whileStmt | forStmt | block | returnStmt;
    private Stmt statement() {
        if (match(IF))         return ifStatement();
        if (match(PRINT))      return printStatement();
        if (match(WHILE))      return whileStatement();
        if (match(FOR))        return forStatement();
        if (match(RETURN))     return returnStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());
        return expressionStatement();
    }

    // ifStmt -> "if" "(" expression ")" statement ( "else" statement )? ;
    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'if'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after if condition.");

        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if (match(ELSE))
            elseBranch = statement();

        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    // printStmt -> "print" expression ";" ;
    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    // whileStmt  -> "while" "(" expression ")" statement ;
    private Stmt whileStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'while'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after condition.");
        Stmt body = statement();

        return new Stmt.While(condition, body);
    }
    // forStmt    -> "for" "(" ( varDecl | exprStmt | ";" ) expression? ";"
    //                expression? ")" statement ;
    private Stmt forStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'for'.");

        Stmt initializer;
        if (match(SEMICOLON))
            initializer = null;
        else if (match(VAR))
            initializer = varDeclaration();
        else
            initializer = expressionStatement();

        Expr condition = null;
        if (!check(SEMICOLON))
            condition = expression();
        consume(SEMICOLON, "Expect ';' after loop condition.");

        Expr increment = null;
        if (!check(RIGHT_PAREN))
            increment = expression();
        consume(RIGHT_PAREN, "Expect ')' after for clauses.");

        Stmt body = statement();

        if (increment != null)
            body = new Stmt.Block(
                Arrays.asList(
                    body,
                    new Stmt.Expression(increment)));

        if (condition == null)
            condition = new Expr.Literal(true);
        body = new Stmt.While(condition, body);

        if (initializer != null)
            body = new Stmt.Block(
                Arrays.asList(initializer, body));

        return body;
    }

    // returnStmt -> "return" expression? ";" ;
    private Stmt returnStatement() {
        Token keyword = previous();
        Expr value = null;
        if (!check(SEMICOLON))
            value = expression();

        consume(SEMICOLON, "Expect ';' after return value.");
        return new Stmt.Return(keyword, value);
    }

    // exprStmt -> expression ";" ;
    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after expression.");
        return new Stmt.Expression(expr);
    }

    // function -> IDENTIFIER "(" parameters? ")" block ;
    // parameters -> IDENTIFIER ( "," IDENTIFIER )* ;
    private Stmt.Function function(String kind) {
        Token name = consume(IDENTIFIER, "Expect " + kind + " name.");

        consume(LEFT_PAREN, "Expect '(' after " + kind + " name.");

        List<Token> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (parameters.size() >= 255)
                    error(peek(), "Can't have more than 255 parameters.");
                parameters.add(
                    consume(IDENTIFIER, "Expect parameter name."));
            } while (match(COMMA));
        }

        consume(RIGHT_PAREN, "Expect ')' after parameters.");

        consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
        List<Stmt> body = block();
        return new Stmt.Function(name, parameters, body);
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

    // assignment -> IDENTIFIER "=" assignment | logic_or ;
    private Expr assignment() {
        Expr expr = or();

        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            }

            if (expr instanceof Expr.Subscript) {
                Expr.Subscript set = (Expr.Subscript)expr;
                return new Expr.Set(set, set.name, value);
            }

            error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    // logic_or -> logic_and ( "or" logic_and)* ;
    private Expr or() {
        Expr expr = and();
        while (match(OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }
        return expr;
    }
    // logic_and -> equality ( "and" equality)* ;
    private Expr and() {
        Expr expr = equality();
        while (match(AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
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
    // unary -> ( "!" | "-" ) unary | call ; (right associative)
    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return call();
    }

    private Expr finishCall(Expr callee) {
        List<Expr> arguments = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (arguments.size() >= 255)
                    error(peek(), "Can't have more than 255 arguments");
                arguments.add(expression());
            } while (match(COMMA));
        }
        Token paren = consume(RIGHT_PAREN, "Expect ')' after arguments.");
        return new Expr.Call(callee, paren, arguments);
    }
    // call -> primary ( "(" arguments? ")" )* ;
    private Expr call() {
        Expr callee = primary();
        while (true) {
            if (match(LEFT_PAREN)) {
                callee = finishCall(callee);
            } else if (match(LEFT_SQUARE)) {
                Expr index = expression();
                Token closeBracket = consume(RIGHT_SQUARE,
                    "Expected ']' after subscript index.");
                callee = new Expr.Subscript(callee, closeBracket, index);
            } else {
                break;
            }
        }
        return callee;
    }
    // primary -> NUMBER | STRING | "true" | "false" | "nil"
    //          | array | "(" expression ")" ;
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

        if (match(LEFT_SQUARE)) {
            List<Expr> values = new ArrayList<>();
            if (match(RIGHT_SQUARE))
                return new Expr.Array(null);
            while (!match(RIGHT_SQUARE)) {
                Expr value = expression();
                values.add(value);
                if (peek().type != RIGHT_SQUARE)
                    consume(COMMA,
                        "Expected a comma before next expression.");
            }
            return new Expr.Array(values);
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
