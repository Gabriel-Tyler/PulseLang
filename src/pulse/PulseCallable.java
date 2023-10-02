package pulse;

import java.util.List;

interface PulseCallable {
    int arity();
    Object call(Interpreter interpreter, List<Object> arguments);
}

