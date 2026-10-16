// turns a tree back into text so we can see what the parser made
//   1 + 2 * 3  ->  (+ 1.0 (* 2.0 3.0))       a ? b : c  ->  (?: a b c)
class AstPrinter implements Expr.Visitor<String> {
  String print(Expr e) { return e.accept(this); }

  public String visitBinaryExpr(Expr.Binary e)           { return wrap(e.op.text, e.lhs, e.rhs); }   // commas show up here too
  public String visitConditionalExpr(Expr.Conditional e) { return wrap("?:", e.cond, e.yep, e.nope); }
  public String visitGroupingExpr(Expr.Grouping e)       { return wrap("group", e.inside); }
  public String visitLiteralExpr(Expr.Literal e)         { return e.val == null ? "nil" : e.val.toString(); }
  public String visitUnaryExpr(Expr.Unary e)             { return wrap(e.op.text, e.rhs); }

  // makes "(name kid kid kid)"
  String wrap(String name, Expr... kids) {
    String s = "(" + name;
    for (Expr kid : kids) s += " " + kid.accept(this);
    return s + ")";
  }
}
