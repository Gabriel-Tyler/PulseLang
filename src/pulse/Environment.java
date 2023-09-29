package pulse;

import java.util.HashMap;
import java.util.Map;

// A node in a linked list of environments
class Environment {
    private final Map<String, Object> values = new HashMap<>();
    final Environment enclosing; // next

    Environment() {
        this.enclosing = null; // global scope
    }

    Environment(Environment enclosing) {
        this.enclosing = enclosing; // nested local scope
    }

    Object get(Token name) {
        if (values.containsKey(name.lexeme))
            return values.get(name.lexeme);

        if (enclosing != null)
            return enclosing.get(name);

        throw new RuntimeError(name,
            "Undefined variable '" + name.lexeme + "'.");
    }

    void assign(Token name, Object value) {
        if (values.containsKey(name.lexeme)) {
            values.put(name.lexeme, value);
            return;
        }

        if (enclosing != null) {
            enclosing.assign(name, value);
            return;
        }

        throw new RuntimeError(name,
            "Undefined variable '" + name.lexeme + "'.");
    }

    void define(String name, Object value) {
        values.put(name, value);
    }
}
