import java.util.*;

// checks scopes before anything runs
class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
  final Interpreter terp;
  final List<Map<String, Local>> scopes = new ArrayList<>();
  boolean inFunction = false;

  static class Local {
    final Token name;
    final int index;
    boolean ready = false;
    boolean used = false;

    Local(Token name, int index) {
      this.name = name;
      this.index = index;
    }
  }

  Resolver(Interpreter terp) { this.terp = terp; }

  void resolve(List<Stmt> body) {
    for (Stmt s : body) resolve(s);
  }

  void resolve(Stmt s) { s.accept(this); }
  void resolve(Expr e) { if (e != null) e.accept(this); }

  Map<String, Local> current() {
    return scopes.get(scopes.size() - 1);
  }

  void beginScope() {
    scopes.add(new LinkedHashMap<>());
  }

  // q3: assigning a value doesn't count as reading it
  void endScope() {
    for (Local local : current().values()) {
      if (!local.used) {
        Lox.error(local.name,
            "Local variable '" + local.name.text + "' is never used.");
      }
    }
    scopes.remove(scopes.size() - 1);
  }

  void declare(Token name) {
    if (scopes.isEmpty()) return;

    Map<String, Local> scope = current();
    if (scope.containsKey(name.text)) {
      Lox.error(name, "Already a variable with this name in this scope.");
      return;
    }

    // q4: each declaration gets its own slot in this scope
    Local local = new Local(name, scope.size());
    scope.put(name.text, local);
    terp.slots.put(name, local.index);
  }

  void define(Token name) {
    if (!scopes.isEmpty()) current().get(name.text).ready = true;
  }

  void resolveLocal(Expr e, Token name, boolean read) {
    for (int i = scopes.size() - 1; i >= 0; i--) {
      Local local = scopes.get(i).get(name.text);

      if (local != null) {
        if (read) local.used = true;
        terp.resolve(e, scopes.size() - 1 - i, local.index);
        return;
      }
    }

    // not in a local scope, so it's global
  }

  void resolveFunction(Expr.Function fn) {
    boolean old = inFunction;
    inFunction = true;
    beginScope();

    for (Token param : fn.params) {
      declare(param);
      define(param);
    }

    resolve(fn.body);
    endScope();
    inFunction = old;
  }

  public Void visitBlockStmt(Stmt.Block s) {
    beginScope();
    resolve(s.body);
    endScope();
    return null;
  }

  public Void visitVarStmt(Stmt.Var s) {
    declare(s.name);
    resolve(s.val);
    define(s.name);
    return null;
  }

  public Void visitFunctionStmt(Stmt.Function s) {
    declare(s.name);
    define(s.name);
    resolveFunction(s.fn);
    return null;
  }

  public Void visitFunctionExpr(Expr.Function e) {
    resolveFunction(e);
    return null;
  }

  public Void visitVariableExpr(Expr.Variable e) {
    if (!scopes.isEmpty()) {
      Local local = current().get(e.name.text);
      if (local != null && !local.ready) {
        Lox.error(e.name, "Can't read local variable in its own initializer.");
      }
    }

    resolveLocal(e, e.name, true);
    return null;
  }

  public Void visitAssignExpr(Expr.Assign e) {
    resolve(e.val);
    resolveLocal(e, e.name, false);
    return null;
  }

  public Void visitReturnStmt(Stmt.Return s) {
    if (!inFunction) {
      Lox.error(s.keyword, "Can't return from top-level code.");
    }

    resolve(s.val);
    return null;
  }

  public Void visitExpressionStmt(Stmt.Expression s) {
    resolve(s.expr);
    return null;
  }

  public Void visitPrintStmt(Stmt.Print s) {
    resolve(s.expr);
    return null;
  }

  public Void visitIfStmt(Stmt.If s) {
    resolve(s.cond);
    resolve(s.yep);
    if (s.nope != null) resolve(s.nope);
    return null;
  }

  public Void visitWhileStmt(Stmt.While s) {
    resolve(s.cond);
    resolve(s.body);
    return null;
  }

  public Void visitBinaryExpr(Expr.Binary e) {
    resolve(e.lhs);
    resolve(e.rhs);
    return null;
  }

  public Void visitLogicalExpr(Expr.Logical e) {
    resolve(e.lhs);
    resolve(e.rhs);
    return null;
  }

  public Void visitConditionalExpr(Expr.Conditional e) {
    resolve(e.cond);
    resolve(e.yep);
    resolve(e.nope);
    return null;
  }

  public Void visitCallExpr(Expr.Call e) {
    resolve(e.callee);
    for (Expr arg : e.args) resolve(arg);
    return null;
  }

  public Void visitGroupingExpr(Expr.Grouping e) {
    resolve(e.inside);
    return null;
  }

  public Void visitUnaryExpr(Expr.Unary e) {
    resolve(e.rhs);
    return null;
  }

  public Void visitLiteralExpr(Expr.Literal e) {
    return null;
  }
}