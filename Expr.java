// made by GenerateAst, don't edit by hand
import java.util.List;

abstract class Expr {
  interface Visitor<R> {
    R visitAssignExpr(Assign expr);
    R visitCallExpr(Call expr);
    R visitFunctionExpr(Function expr);
    R visitLogicalExpr(Logical expr);
    R visitVariableExpr(Variable expr);
    R visitBinaryExpr(Binary expr);
    R visitConditionalExpr(Conditional expr);
    R visitGroupingExpr(Grouping expr);
    R visitLiteralExpr(Literal expr);
    R visitUnaryExpr(Unary expr);
  }

  static class Assign extends Expr {
    final Token name;
    final Expr val;
    Assign(Token name, Expr val) { this.name = name; this.val = val; }
    <R> R accept(Visitor<R> v) { return v.visitAssignExpr(this); }
  }

  static class Call extends Expr {
    final Expr callee;
    final Token paren;
    final List<Expr> args;
    Call(Expr callee, Token paren, List<Expr> args) { this.callee = callee; this.paren = paren; this.args = args; }
    <R> R accept(Visitor<R> v) { return v.visitCallExpr(this); }
  }

  static class Function extends Expr {
    final List<Token> params;
    final List<Stmt> body;
    Function(List<Token> params, List<Stmt> body) { this.params = params; this.body = body; }
    <R> R accept(Visitor<R> v) { return v.visitFunctionExpr(this); }
  }

  static class Logical extends Expr {
    final Expr lhs;
    final Token op;
    final Expr rhs;
    Logical(Expr lhs, Token op, Expr rhs) { this.lhs = lhs; this.op = op; this.rhs = rhs; }
    <R> R accept(Visitor<R> v) { return v.visitLogicalExpr(this); }
  }

  static class Variable extends Expr {
    final Token name;
    Variable(Token name) { this.name = name; }
    <R> R accept(Visitor<R> v) { return v.visitVariableExpr(this); }
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
