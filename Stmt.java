// made by GenerateAst, don't edit by hand
import java.util.List;

abstract class Stmt {
  interface Visitor<R> {
    R visitBlockStmt(Block stmt);
    R visitExpressionStmt(Expression stmt);
    R visitFunctionStmt(Function stmt);
    R visitIfStmt(If stmt);
    R visitPrintStmt(Print stmt);
    R visitReturnStmt(Return stmt);
    R visitVarStmt(Var stmt);
    R visitWhileStmt(While stmt);
  }

  static class Block extends Stmt {
    final List<Stmt> body;
    Block(List<Stmt> body) { this.body = body; }
    <R> R accept(Visitor<R> v) { return v.visitBlockStmt(this); }
  }

  static class Expression extends Stmt {
    final Expr expr;
    Expression(Expr expr) { this.expr = expr; }
    <R> R accept(Visitor<R> v) { return v.visitExpressionStmt(this); }
  }

  static class Function extends Stmt {
    final Token name;
    final Expr.Function fn;
    Function(Token name, Expr.Function fn) { this.name = name; this.fn = fn; }
    <R> R accept(Visitor<R> v) { return v.visitFunctionStmt(this); }
  }

  static class If extends Stmt {
    final Expr cond;
    final Stmt yep;
    final Stmt nope;
    If(Expr cond, Stmt yep, Stmt nope) { this.cond = cond; this.yep = yep; this.nope = nope; }
    <R> R accept(Visitor<R> v) { return v.visitIfStmt(this); }
  }

  static class Print extends Stmt {
    final Expr expr;
    Print(Expr expr) { this.expr = expr; }
    <R> R accept(Visitor<R> v) { return v.visitPrintStmt(this); }
  }

  static class Return extends Stmt {
    final Token keyword;
    final Expr val;
    Return(Token keyword, Expr val) { this.keyword = keyword; this.val = val; }
    <R> R accept(Visitor<R> v) { return v.visitReturnStmt(this); }
  }

  static class Var extends Stmt {
    final Token name;
    final Expr val;
    Var(Token name, Expr val) { this.name = name; this.val = val; }
    <R> R accept(Visitor<R> v) { return v.visitVarStmt(this); }
  }

  static class While extends Stmt {
    final Expr cond;
    final Stmt body;
    While(Expr cond, Stmt body) { this.cond = cond; this.body = body; }
    <R> R accept(Visitor<R> v) { return v.visitWhileStmt(this); }
  }

  abstract <R> R accept(Visitor<R> v);
}
