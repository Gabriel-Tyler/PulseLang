/*
 * Post-order traversal of the syntax tree.
 *  - Each node evaluates its children before doing its own work
 */

package pulse;

import java.util.ArrayList;
import java.util.List;

class Interpreter implements Expr.Visitor<Object>,
                             Stmt.Visitor<Void> {
    final Environment globals = new Environment();
    // The current environment (innermost scope):
    private Environment environment = globals;

    Interpreter() {
        globals.define("clock", new PulseCallable() {
            @Override
            public int arity() { return 0; }

            @Override
            public Object call(Interpreter interpreter,
                               List<Object> arguments) {
                return (double)System.currentTimeMillis()/1000.0;
            }

            @Override
            public String toString() { return "<native fn>"; }
        });
    }

    void interpret(List<Stmt> statements) {
        try {
            for (Stmt statement : statements) {
                execute(statement);
            }
        } catch (RuntimeError error) {
            Pulse.runtimeError(error);
        }
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);
        environment.assign(expr.name, value);
        return value;
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case BANG_EQUAL -> {
                return !isEqual(left, right);
            }
            case EQUAL_EQUAL -> {
                return isEqual(left, right);
            }
            case GREATER -> {
                // add support for strings?
                checkNumberOperands(expr.operator, left, right);
                return (double)left > (double)right;
            }
            case GREATER_EQUAL -> {
                checkNumberOperands(expr.operator, left, right);
                return (double)left >= (double)right;
            }
            case LESS -> {
                checkNumberOperands(expr.operator, left, right);
                return (double)left < (double)right;
            }
            case LESS_EQUAL -> {
                checkNumberOperands(expr.operator, left, right);
                return (double)left <= (double)right;
            }
            case SLASH -> {
                checkNumberOperands(expr.operator, left, right);
                if ((double)right == 0)
                    throw new RuntimeError(expr.operator,
                        "Division by zero");
                return (double)left / (double)right;
            }
            case STAR -> {
                checkNumberOperands(expr.operator, left, right);
                return (double)left * (double)right;
            }
            case MINUS -> {
                checkNumberOperands(expr.operator, left, right);
                return (double)left - (double)right;
            }
            case PLUS -> {
                if (left instanceof Double) {
                    if (right instanceof Double)
                        return (double)left + (double)right;
                    else if (right instanceof String)
                        return left.toString() + right;
                } else if (left instanceof String) {
                    if (right instanceof Double)
                        return left + right.toString();
                    else if (right instanceof String)
                        return (String)left + right;
                }
                throw new RuntimeError(expr.operator,
                    "Operands must be two numbers or two strings.");
            }
        }

        return null;
    }

    @Override
    public Object visitCallExpr(Expr.Call expr) {
        Object callee = evaluate(expr.callee);

        List<Object> arguments = new ArrayList<>();
        for (Expr argument : expr.arguments)
            arguments.add(evaluate(argument));

        if (!(callee instanceof PulseCallable function)) {
            throw new RuntimeError(expr.paren,
                "Can only call functions and classes.");
        }

        if (arguments.size() != function.arity()) {
            throw new RuntimeError(expr.paren,
                "Expected " + function.arity()
                    + " arguments but got " + arguments.size() + ".");
        }

        return function.call(this, arguments);
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }
    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object left = evaluate(expr.left);

        // Short circuit?
        if (expr.operator.type == TokenType.OR) {
            // OR
            if (isTruthy(left))
                return left;
        } else {
            // AND
            if (!isTruthy(left))
                return left;
        }
        return evaluate(expr.right);
    }

    @Override
    public Object visitSetExpr(Expr.Set expr) {
        if (expr.object instanceof Expr.Subscript) {

            List<Object> list = (List<Object>)evaluate(((Expr.Subscript)expr.object).object);

            Object indexObject = evaluate(((Expr.Subscript)expr.object).value);

            int index = ((Double)indexObject).intValue();
            if (index >= list.size()) {
                throw new RuntimeError(expr.name,
                    "Array index out of range.");
            }

            list.set(index, evaluate(expr.right));
        }
        return null;
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case BANG -> {
                return !isTruthy(right);
            }
            case MINUS -> {
                checkNumberOperand(expr.operator, right);
                return -(double)right;
            }
        }

        // Unreachable.
        return null;
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        return environment.get(expr.name);
    }

    @Override
    public Object visitArrayExpr(Expr.Array expr) {
        List<Object> values = new ArrayList<>();
        if (expr.values != null) {
            for (Expr value : expr.values)
                values.add(evaluate(value));
        }
        return values;
    }

    @Override
    public Object visitSubscriptExpr(Expr.Subscript expr) {
        List<Object> list = null;
        try {
            list = (List<Object>)evaluate(expr.object);
        } catch (Exception e) {
            throw new RuntimeError(expr.name, "Only arrays can be subscripted");
        }

        Object indexObject = evaluate(expr.value);
        if (!(indexObject instanceof Double)) {
            throw new RuntimeError(expr.name,
                "Only numbers can be used to index an array.");
        }

        int index = ((Double)indexObject).intValue();
        if (index >= list.size()) {
            throw new RuntimeError(expr.name,
                "Array index out of range.");
        }

        return list.get(index);
    }

    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double)
            return;
        throw new RuntimeError(operator, "Operand must be a number.");
    }
    private void checkNumberOperands(Token operator, Object left, Object right) {
        if (left instanceof Double && right instanceof Double)
            return;
        throw new RuntimeError(operator, "Operands must be numbers.");
    }

    private Object evaluate(Expr expr) {
        // dispatch on the type of expr
        return expr.accept(this);
    }

    private void execute(Stmt stmt) {
        // dispatch on the type of stmt
        stmt.accept(this);
    }

    void executeBlock(List<Stmt> statements, Environment environment) {
        Environment previous = this.environment;
        try {
            this.environment = environment;
            for (Stmt statement : statements)
                execute(statement);
        } finally {
            this.environment = previous;
        }
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        PulseFunction function = new PulseFunction(stmt);
        environment.define(stmt.name.lexeme, function);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        if (isTruthy(evaluate(stmt.condition)))
            execute(stmt.thenBranch);
        else if (stmt.elseBranch != null)
            execute(stmt.elseBranch);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        Object value = null;
        if (stmt.value != null)
            value = evaluate(stmt.value);
        throw new Return(value);
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value = null;
        if (stmt.initializer != null)
            value = evaluate(stmt.initializer);
        environment.define(stmt.name.lexeme, value);
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        while (isTruthy(evaluate(stmt.condition)))
            execute(stmt.body);
        return null;
    }

    private boolean isTruthy(Object object) {
        if (object == null)
            return false;
        if (object instanceof Boolean)
            return (boolean)object;
        return true;
    }
    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null)
            return true;
        if (a == null)
            return false;
        return a.equals(b);
    }
    private String stringify(Object object) {
        if (object == null)
            return "nil";

        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0"))
                text = text.substring(0, text.length() - 2);
            return text;
        }

        return object.toString();
    }
}
