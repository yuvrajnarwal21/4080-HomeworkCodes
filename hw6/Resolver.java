import java.util.*;

// checks names and scopes before running anything
class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
  final Interpreter terp;
  final List<Map<String, Local>> scopes = new ArrayList<>();

  enum FunctionKind { NONE, FUNCTION, INITIALIZER }
  FunctionKind function = FunctionKind.NONE;
  int loops = 0;

  static class Local {
    final Token name;
    final int index;
    boolean ready = false, used = false;

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

    if (current().containsKey(name.text)) {
      Lox.error(name, "Already a variable with this name in this scope.");
      return;
    }

    Local local = new Local(name, current().size());
    current().put(name.text, local);
    terp.slots.put(name, local.index);
  }

  void define(Token name) {
    if (!scopes.isEmpty()) current().get(name.text).ready = true;
  }

  // this and inner have fixed slots and aren't user-declared variables
  void hidden(String name, int index) {
    Local local = new Local(null, index);
    local.ready = true;
    local.used = true;
    current().put(name, local);
  }

  boolean resolveLocal(Expr e, Token name, boolean read) {
    for (int i = scopes.size() - 1; i >= 0; i--) {
      Local local = scopes.get(i).get(name.text);

      if (local != null) {
        if (read) local.used = true;
        terp.resolve(e, scopes.size() - 1 - i, local.index);
        return true;
      }
    }

    return false;   // globals are checked at runtime
  }

  void resolveFunction(Expr.Function fn, FunctionKind kind) {
    FunctionKind old = function;
    int oldLoops = loops;

    function = kind;
    loops = 0;   // a function can't break out of somebody else's loop
    beginScope();

    for (Token param : fn.params) {
      declare(param);
      define(param);
    }

    resolve(fn.body);
    endScope();

    function = old;
    loops = oldLoops;
  }

  public Void visitClassStmt(Stmt.Class s) {
    declare(s.name);
    define(s.name);

    if (s.parent != null) {
      if (s.name.text.equals(s.parent.name.text)) {
        Lox.error(s.parent.name, "A class can't inherit from itself.");
      }
      resolve(s.parent);
    }

    beginScope();
    hidden("this", 0);
    hidden("inner", 1);

    resolveMethods(s.methods, false);
    resolveMethods(s.statics, true);

    endScope();
    return null;
  }

  void resolveMethods(List<Stmt.Function> methods, boolean isStatic) {
    Set<String> names = new HashSet<>();

    for (Stmt.Function method : methods) {
      if (!names.add(method.name.text)) {
        Lox.error(method.name, "Already a method with this name.");
      }

      boolean init = !isStatic && method.name.text.equals("init");

      if (init && method.getter) {
        Lox.error(method.name, "An initializer needs a parameter list.");
      }

      resolveFunction(method.fn,
          init ? FunctionKind.INITIALIZER : FunctionKind.FUNCTION);
    }
  }

  public Void visitThisExpr(Expr.This e) {
    if (!resolveLocal(e, e.keyword, true)) {
      Lox.error(e.keyword, "Can't use 'this' outside a method.");
    }
    return null;
  }

  public Void visitInnerExpr(Expr.Inner e) {
    if (!resolveLocal(e, e.keyword, true)) {
      Lox.error(e.keyword, "Can't use 'inner' outside a method.");
    }
    return null;
  }

  public Void visitGetExpr(Expr.Get e) {
    resolve(e.obj);
    return null;
  }

  public Void visitSetExpr(Expr.Set e) {
    resolve(e.obj);
    resolve(e.val);
    return null;
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
    resolveFunction(s.fn, FunctionKind.FUNCTION);
    return null;
  }

  public Void visitFunctionExpr(Expr.Function e) {
    resolveFunction(e, FunctionKind.FUNCTION);
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
    if (function == FunctionKind.NONE) {
      Lox.error(s.keyword, "Can't return from top-level code.");
    }

    if (s.val != null && function == FunctionKind.INITIALIZER) {
      Lox.error(s.keyword, "Can't return a value from an initializer.");
    }

    resolve(s.val);
    return null;
  }

  public Void visitBreakStmt(Stmt.Break s) {
    if (loops == 0) {
      Lox.error(s.keyword, "Can't use 'break' outside a loop.");
    }
    return null;
  }

  public Void visitWhileStmt(Stmt.While s) {
    resolve(s.cond);
    loops++;
    resolve(s.body);
    loops--;
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