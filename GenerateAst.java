import java.io.*;
import java.util.*;

// writes Expr.java for us so we don't type it all by hand
// run it like:  java GenerateAst .     (the dot = put Expr.java right here)
public class GenerateAst {
  public static void main(String[] args) throws IOException {
    if (args.length != 1) { System.err.println("Usage: generate_ast <output directory>"); System.exit(64); }

    // "Name : fields" -- one line for each kind of node
    makeAst(args[0], "Expr", Arrays.asList(
      "Binary      : Expr lhs, Token op, Expr rhs",
      "Conditional : Expr cond, Expr yep, Expr nope",   // new, for  cond ? yep : nope
      "Grouping    : Expr inside",
      "Literal     : Object val",
      "Unary       : Token op, Expr rhs"
    ));
  }

  static void makeAst(String dir, String base, List<String> kinds) throws IOException {
    PrintWriter pen = new PrintWriter(dir + "/" + base + ".java", "UTF-8");
    pen.println("// made by GenerateAst, don't edit by hand");
    pen.println("abstract class " + base + " {");

    // the visitor: one visit method per kind
    pen.println("  interface Visitor<R> {");
    for (String k : kinds) {
      String name = k.split(":")[0].trim();
      pen.println("    R visit" + name + base + "(" + name + " " + base.toLowerCase() + ");");
    }
    pen.println("  }");

    // one little class per kind
    for (String k : kinds) {
      String name = k.split(":")[0].trim(), fields = k.split(":")[1].trim();
      pen.println();
      pen.println("  static class " + name + " extends " + base + " {");
      for (String f : fields.split(", ")) pen.println("    final " + f + ";");

      // constructor, just saves everything
      String saves = "";
      for (String f : fields.split(", ")) { String n = f.split(" ")[1]; saves += " this." + n + " = " + n + ";"; }
      pen.println("    " + name + "(" + fields + ") {" + saves + " }");

      pen.println("    <R> R accept(Visitor<R> v) { return v.visit" + name + base + "(this); }");
      pen.println("  }");
    }

    pen.println();
    pen.println("  abstract <R> R accept(Visitor<R> v);");
    pen.println("}");
    pen.close();
  }
}
