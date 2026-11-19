// turns the tree back into text so we can check what the parser made
class AstPrinter implements Expr.Visitor<String>, Stmt.Visitor<String> {
  String print(Expr e) { return e == null ? "nil" : e.accept(this); }
  String print(Stmt s) { return s == null ? "" : s.accept(this); }

  public String visitAssignExpr(Expr.Assign e) {
    return wrap("= " + e.name.text, e.val);
  }

  public String visitBinaryExpr(Expr.Binary e) {
    return wrap(e.op.text, e.lhs, e.rhs);
  }

  public String visitConditionalExpr(Expr.Conditional e) {
    return wrap("?:", e.cond, e.yep, e.nope);
  }

  public String visitGroupingExpr(Expr.Grouping e) {
    return wrap("group", e.inside);
  }

  public String visitLiteralExpr(Expr.Literal e) {
    return e.val == null ? "nil" : e.val.toString();
  }

  public String visitUnaryExpr(Expr.Unary e) {
    return wrap(e.op.text, e.rhs);
  }

  public String visitLogicalExpr(Expr.Logical e) {
    return wrap(e.op.text, e.lhs, e.rhs);
  }

  public String visitVariableExpr(Expr.Variable e) {
    return e.name.text;
  }

  public String visitThisExpr(Expr.This e) { return "this"; }
  public String visitInnerExpr(Expr.Inner e) { return "inner"; }

  public String visitGetExpr(Expr.Get e) {
    return wrap("get " + e.name.text, e.obj);
  }

  public String visitSetExpr(Expr.Set e) {
    return wrap("set " + e.name.text, e.obj, e.val);
  }

  public String visitCallExpr(Expr.Call e) {
    String s = "(call " + print(e.callee);
    for (Expr arg : e.args) s += " " + print(arg);
    return s + ")";
  }

  public String visitFunctionExpr(Expr.Function e) {
    String s = "(fun (";

    for (int i = 0; i < e.params.size(); i++) {
      if (i > 0) s += " ";
      s += e.params.get(i).text;
    }

    s += ")";
    for (Stmt stmt : e.body) s += " " + print(stmt);
    return s + ")";
  }

  public String visitBlockStmt(Stmt.Block s) {
    String text = "(block";
    for (Stmt stmt : s.body) text += " " + print(stmt);
    return text + ")";
  }

  public String visitClassStmt(Stmt.Class s) {
    String text = "(class " + s.name.text;

    if (s.parent != null) text += " < " + print(s.parent);
    for (Stmt.Function method : s.methods) text += " " + print(method);
    for (Stmt.Function method : s.statics) {
      text += " (static " + print(method) + ")";
    }

    return text + ")";
  }

  public String visitFunctionStmt(Stmt.Function s) {
    return "(" + (s.getter ? "getter " : "define ")
        + s.name.text + " " + print(s.fn) + ")";
  }

  public String visitBreakStmt(Stmt.Break s) { return "(break)"; }

  public String visitExpressionStmt(Stmt.Expression s) {
    return wrap("expr", s.expr);
  }

  public String visitPrintStmt(Stmt.Print s) {
    return wrap("print", s.expr);
  }

  public String visitReturnStmt(Stmt.Return s) {
    return wrap("return", s.val);
  }

  public String visitVarStmt(Stmt.Var s) {
    return wrap("var " + s.name.text, s.val);
  }

  public String visitIfStmt(Stmt.If s) {
    return "(if " + print(s.cond) + " " + print(s.yep)
        + (s.nope == null ? "" : " " + print(s.nope)) + ")";
  }

  public String visitWhileStmt(Stmt.While s) {
    return "(while " + print(s.cond) + " " + print(s.body) + ")";
  }

  String wrap(String name, Expr... kids) {
    String s = "(" + name;
    for (Expr kid : kids) s += " " + print(kid);
    return s + ")";
  }
}