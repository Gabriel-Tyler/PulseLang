package pulse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.nio.charset.Charset;

public class Pulse {

    // static so successive calls in run() use the same interpreter
    private static final Interpreter interpreter = new Interpreter();

    static boolean hadError = false; // used in runFile and the REPL
    static boolean hadRuntimeError = false; // only used in runFile

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            System.out.println("Usage: jpls [script]");
            System.exit(64);
        } else if (args.length == 1) {
            runFile(args[0]);
        } else {
            runPrompt();
        }
    }

    private static void runFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Path.of(path));
        run(new String(bytes, Charset.defaultCharset()));
        if (hadError)
            System.exit(65);
        if (hadRuntimeError)
            System.exit(70);
    }
    private static void runPrompt() throws IOException {
        InputStreamReader input = new InputStreamReader(System.in);
        BufferedReader reader = new BufferedReader(input);

        while (true) {
            System.out.print("> ");
            String line = reader.readLine();
            if (line == null)
                break;
            run(line);
            hadError = false;
        }
    }
    private static void run(String source) {
        // scanning, parsing, and execution

        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();

        Parser parser = new Parser(tokens);
        Expr expression = parser.parse();

        // stop if there was a syntax error
        if (hadError)
            return;

        // print the lisp-like syntax tree
        // System.out.println(new AstPrinter().print(expression));

        // "You are my creator, but I am your master; Obey!"
        // EVALUATE THE EXPRESSION!
        interpreter.interpret(expression); // "It's alive!"
    }

    private static void report(int line, String where, String message) {
        // System.out.flush();
        System.err.println(
            "[Line " + line + "] Error" + where + ": " + message);
        // System.err.flush();
        hadError = true;
    }
    static void error(int line, String message) {
        report(line, "", message);
    }
    static void error(Token token, String message) {
        if (token.type == TokenType.EOF) {
            report(token.line, " at end", message);
        } else {
            report(token.line, " at '" + token.lexeme + "'", message);
        }
    }

    static void runtimeError(RuntimeError error) {
        System.err.println("[line " + error.token.line + "] Runtime Error: "
            + error.getMessage());
        hadRuntimeError = true;
    }
}