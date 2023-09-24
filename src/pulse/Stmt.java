package pulse;

import java.util.List;

abstract class Stmt {
    abstract <R> R accept(Visitor<R> visitor);

    interface Visitor<R> {
        R visitExpressionStmt(Expression stmt);
        R visitPrintStmt(Print stmt);
    }

    static class Expression extends Stmt {
        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitExpressionStmt(this);
        }

        final Expr expression;

        Expression(Expr expression) {
            this.expression = expression;
        }
    }

    static class Print extends Stmt {
        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitPrintStmt(this);
        }

        final Expr expression;

        Print(Expr expression) {
            this.expression = expression;
        }
    }

}
