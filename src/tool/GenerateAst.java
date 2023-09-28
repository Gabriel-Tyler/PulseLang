package tool;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class GenerateAst {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: generate_ast <output directory>");
            System.exit(64);
        }
        String outputDir = args[0];
        defineAst(outputDir, "Expr", Arrays.asList(
            "Binary   : Expr left, Token operator, Expr right",
            "Grouping : Expr expression",
            "Literal  : Object value",
            "Unary    : Token operator, Expr right",
            "Variable : Token name"));

        defineAst(outputDir, "Stmt", Arrays.asList(
            "Expression : Expr expression",
            "Print      : Expr expression",
            "Var        : Token name, Expr initializer"));
    }

    private static void defineAst(
            String outputDir, String baseName, List<String> types)
            throws IOException {

        String path = outputDir + "/" + baseName + ".java";
        try (PrintWriter writer = new PrintWriter(path, StandardCharsets.UTF_8)) {
            writer.println("package pulse;");
            writer.println();
            writer.println("import java.util.List;");
            writer.println();
            writer.println("abstract class " + baseName + " {");

            writer.println("    abstract <R> R accept(Visitor<R> visitor);");
            writer.println();

            // the visitor
            defineVisitor(writer, baseName, types);
            writer.println();

            // the AST classes
            for (String type : types) {
                String className = type.split(":")[0].trim();
                String fields = type.split(":")[1].trim();
                defineType(writer, baseName, className, fields);
            }


            writer.println("}");
        }
    }

    private static void defineVisitor(
            PrintWriter writer, String baseName, List<String> types) {

        writer.println("    interface Visitor<R> {");

        for (String type : types) {
            String typeName = type.split(":")[0].trim();
            writer.println("        R visit" + typeName + baseName + "("
                + typeName + " " + baseName.toLowerCase() + ");");
        }

        writer.println("    }");
    }

    private static void defineType(
            PrintWriter writer, String baseName, String className, String fieldList) {

        // subclass declaration
        writer.println("    static class " + className + " extends " + baseName + " {");

        // visitor pattern
        writer.println("        @Override");
        writer.println("        <R> R accept(Visitor<R> visitor) {");
        writer.println("            return visitor.visit" + className + baseName
            + "(this);");
        writer.println("        }");
        writer.println();

        // separate the fields
        String[] fields = fieldList.split(", ");

        // fields
        for (String field : fields) {
            writer.println("        final " + field + ";");
        }

        // constructor
        writer.println();
        writer.println("        " + className + "(" + fieldList + ") {");
        // store parameters in fields.
        for (String field : fields) {
            String name = field.split(" ")[1];
            writer.println("            this." + name + " = " + name + ";");
        }
        writer.println("        }");
        writer.println("    }");
        writer.println();
    }
}

