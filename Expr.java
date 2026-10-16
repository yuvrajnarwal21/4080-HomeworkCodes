// made by GenerateAst, don't edit by hand
abstract class Expr {
  interface Visitor<R> {
    R visitBinaryExpr(Binary expr);
    R visitConditionalExpr(Conditional expr);
    R visitGroupingExpr(Grouping expr);
    R visitLiteralExpr(Literal expr);
    R visitUnaryExpr(Unary expr);
  }

  static class Binary extends Expr {
    final Expr lhs;
    final Token op;
    final Expr rhs;
    Binary(Expr lhs, Token op, Expr rhs) { this.lhs = lhs; this.op = op; this.rhs = rhs; }
    <R> R accept(Visitor<R> v) { return v.visitBinaryExpr(this); }
  }

  static class Conditional extends Expr {
    final Expr cond;
    final Expr yep;
    final Expr nope;
    Conditional(Expr cond, Expr yep, Expr nope) { this.cond = cond; this.yep = yep; this.nope = nope; }
    <R> R accept(Visitor<R> v) { return v.visitConditionalExpr(this); }
  }

  static class Grouping extends Expr {
    final Expr inside;
    Grouping(Expr inside) { this.inside = inside; }
    <R> R accept(Visitor<R> v) { return v.visitGroupingExpr(this); }
  }

  static class Literal extends Expr {
    final Object val;
    Literal(Object val) { this.val = val; }
    <R> R accept(Visitor<R> v) { return v.visitLiteralExpr(this); }
  }

  static class Unary extends Expr {
    final Token op;
    final Expr rhs;
    Unary(Token op, Expr rhs) { this.op = op; this.rhs = rhs; }
    <R> R accept(Visitor<R> v) { return v.visitUnaryExpr(this); }
  }

  abstract <R> R accept(Visitor<R> v);
}
