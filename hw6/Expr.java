import java.util.List;
// made by GenerateAst, don't edit by hand
abstract class Expr {
  interface Visitor<R> {
    R visitAssignExpr(Assign expr);
    R visitBinaryExpr(Binary expr);
    R visitCallExpr(Call expr);
    R visitConditionalExpr(Conditional expr);
    R visitFunctionExpr(Function expr);
    R visitGetExpr(Get expr);
    R visitGroupingExpr(Grouping expr);
    R visitInnerExpr(Inner expr);
    R visitLiteralExpr(Literal expr);
    R visitLogicalExpr(Logical expr);
    R visitSetExpr(Set expr);
    R visitThisExpr(This expr);
    R visitUnaryExpr(Unary expr);
    R visitVariableExpr(Variable expr);
  }

  static class Assign extends Expr {
    final Token name;
    final Expr val;
    Assign(Token name, Expr val) { this.name = name; this.val = val; }
    <R> R accept(Visitor<R> v) { return v.visitAssignExpr(this); }
  }

  static class Binary extends Expr {
    final Expr lhs;
    final Token op;
    final Expr rhs;
    Binary(Expr lhs, Token op, Expr rhs) { this.lhs = lhs; this.op = op; this.rhs = rhs; }
    <R> R accept(Visitor<R> v) { return v.visitBinaryExpr(this); }
  }

  static class Call extends Expr {
    final Expr callee;
    final Token paren;
    final List<Expr> args;
    Call(Expr callee, Token paren, List<Expr> args) { this.callee = callee; this.paren = paren; this.args = args; }
    <R> R accept(Visitor<R> v) { return v.visitCallExpr(this); }
  }

  static class Conditional extends Expr {
    final Expr cond;
    final Expr yep;
    final Expr nope;
    Conditional(Expr cond, Expr yep, Expr nope) { this.cond = cond; this.yep = yep; this.nope = nope; }
    <R> R accept(Visitor<R> v) { return v.visitConditionalExpr(this); }
  }

  static class Function extends Expr {
    final List<Token> params;
    final List<Stmt> body;
    Function(List<Token> params, List<Stmt> body) { this.params = params; this.body = body; }
    <R> R accept(Visitor<R> v) { return v.visitFunctionExpr(this); }
  }

  static class Get extends Expr {
    final Expr obj;
    final Token name;
    Get(Expr obj, Token name) { this.obj = obj; this.name = name; }
    <R> R accept(Visitor<R> v) { return v.visitGetExpr(this); }
  }

  static class Grouping extends Expr {
    final Expr inside;
    Grouping(Expr inside) { this.inside = inside; }
    <R> R accept(Visitor<R> v) { return v.visitGroupingExpr(this); }
  }

  static class Inner extends Expr {
    final Token keyword;
    Inner(Token keyword) { this.keyword = keyword; }
    <R> R accept(Visitor<R> v) { return v.visitInnerExpr(this); }
  }

  static class Literal extends Expr {
    final Object val;
    Literal(Object val) { this.val = val; }
    <R> R accept(Visitor<R> v) { return v.visitLiteralExpr(this); }
  }

  static class Logical extends Expr {
    final Expr lhs;
    final Token op;
    final Expr rhs;
    Logical(Expr lhs, Token op, Expr rhs) { this.lhs = lhs; this.op = op; this.rhs = rhs; }
    <R> R accept(Visitor<R> v) { return v.visitLogicalExpr(this); }
  }

  static class Set extends Expr {
    final Expr obj;
    final Token name;
    final Expr val;
    Set(Expr obj, Token name, Expr val) { this.obj = obj; this.name = name; this.val = val; }
    <R> R accept(Visitor<R> v) { return v.visitSetExpr(this); }
  }

  static class This extends Expr {
    final Token keyword;
    This(Token keyword) { this.keyword = keyword; }
    <R> R accept(Visitor<R> v) { return v.visitThisExpr(this); }
  }

  static class Unary extends Expr {
    final Token op;
    final Expr rhs;
    Unary(Token op, Expr rhs) { this.op = op; this.rhs = rhs; }
    <R> R accept(Visitor<R> v) { return v.visitUnaryExpr(this); }
  }

  static class Variable extends Expr {
    final Token name;
    Variable(Token name) { this.name = name; }
    <R> R accept(Visitor<R> v) { return v.visitVariableExpr(this); }
  }

  abstract <R> R accept(Visitor<R> v);
}
