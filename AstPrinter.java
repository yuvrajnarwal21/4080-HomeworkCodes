// turns a tree back into text so we can see what the parser made
class AstPrinter implements Expr.Visitor<String> {
  String print(Expr e) { return e.accept(this); }

  public String visitBinaryExpr(Expr.Binary e)           { return wrap(e.op.text, e.lhs, e.rhs); }
  public String visitConditionalExpr(Expr.Conditional e) { return wrap("?:", e.cond, e.yep, e.nope); }
  public String visitGroupingExpr(Expr.Grouping e)       { return wrap("group", e.inside); }
  public String visitLiteralExpr(Expr.Literal e)         { return e.val == null ? "nil" : e.val.toString(); }
  public String visitUnaryExpr(Expr.Unary e)             { return wrap(e.op.text, e.rhs); }
  public String visitAssignExpr(Expr.Assign e)           { return wrap("= " + e.name.text, e.val); }
  public String visitVariableExpr(Expr.Variable e)       { return e.name.text; }
  public String visitLogicalExpr(Expr.Logical e)         { return wrap(e.op.text, e.lhs, e.rhs); }

  public String visitCallExpr(Expr.Call e) {
    String s = "(call " + print(e.callee);
    for (Expr arg : e.args) s += " " + print(arg);
    return s + ")";
  }

  public String visitFunctionExpr(Expr.Function e) {
    String s = "(fun (";
    for (int i = 0; i < e.params.size(); i++) {
      s += (i == 0 ? "" : " ") + e.params.get(i).text;
    }
    s += ")";
    for (Stmt stmt : e.body) s += " " + print(stmt);
    return s + ")";
  }

  // statement trees, for checking function bodies too
  String print(Stmt stmt) {
    if (stmt instanceof Stmt.Block) {
      String s = "(block";
      for (Stmt kid : ((Stmt.Block)stmt).body) s += " " + print(kid);
      return s + ")";
    }

    if (stmt instanceof Stmt.Expression) return wrap(";", ((Stmt.Expression)stmt).expr);
    if (stmt instanceof Stmt.Print) return wrap("print", ((Stmt.Print)stmt).expr);

    if (stmt instanceof Stmt.Var) {
      Stmt.Var s = (Stmt.Var)stmt;
      return s.val == null ? "(var " + s.name.text + ")" : wrap("var " + s.name.text, s.val);
    }

    if (stmt instanceof Stmt.Function) {
      Stmt.Function s = (Stmt.Function)stmt;
      return "(define " + s.name.text + " " + print(s.fn) + ")";
    }

    if (stmt instanceof Stmt.Return) {
      Stmt.Return s = (Stmt.Return)stmt;
      return s.val == null ? "(return)" : wrap("return", s.val);
    }

    if (stmt instanceof Stmt.If) {
      Stmt.If s = (Stmt.If)stmt;
      return "(if " + print(s.cond) + " " + print(s.yep)
          + (s.nope == null ? "" : " " + print(s.nope)) + ")";
    }

    Stmt.While s = (Stmt.While)stmt;
    return "(while " + print(s.cond) + " " + print(s.body) + ")";
  }

  // makes "(name kid kid kid)"
  String wrap(String name, Expr... kids) {
    String s = "(" + name;
    for (Expr kid : kids) s += " " + kid.accept(this);
    return s + ")";
  }
}