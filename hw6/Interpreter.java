import java.util.*;

// runs statements and gets values from expressions
class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
  final Environment globals = new Environment();
  Environment env = globals;

  final Map<Expr, int[]> locals = new IdentityHashMap<>();
  final Map<Token, Integer> slots = new IdentityHashMap<>();

  Interpreter() {
    globals.define("clock", new LoxCallable() {
      public int arity() { return 0; }

      public Object call(Interpreter terp, List<Object> args) {
        return System.currentTimeMillis() / 1000.0;
      }

      public String toString() { return "<native fn>"; }
    });
  }

  void resolve(Expr e, int depth, int index) {
    locals.put(e, new int[] {depth, index});
  }

  void define(Token name, Object val, Environment scope) {
    Integer index = slots.get(name);
    if (index == null) globals.define(name.text, val);
    else scope.put(index, val);
  }

  Object lookup(Expr e, Token name) {
    int[] where = locals.get(e);
    return where == null
        ? globals.get(name)
        : env.getAt(where[0], where[1]);
  }

  void interpret(List<Stmt> body) {
    try {
      for (Stmt s : body) run(s);
    } catch (RuntimeError err) {
      Lox.runtimeError(err);
    }
  }

  void run(Stmt s) { s.accept(this); }
  Object eval(Expr e) { return e.accept(this); }

  void runBlock(List<Stmt> body, Environment local) {
    Environment old = env;

    try {
      env = local;
      for (Stmt s : body) run(s);
    } finally {
      env = old;
    }
  }

  public Void visitClassStmt(Stmt.Class s) {
    LoxClass parent = null;

    if (s.parent != null) {
      Object val = eval(s.parent);

      if (!(val instanceof LoxClass)) {
        throw new RuntimeError(s.parent.name, "Superclass must be a class.");
      }

      parent = (LoxClass)val;
    }

    LoxClass klass = new LoxClass(s.name.text, parent);
    define(s.name, klass, env);

    addMethods(klass, s.methods, false);
    addMethods(klass, s.statics, true);
    return null;
  }

  void addMethods(LoxClass klass, List<Stmt.Function> methods, boolean isStatic) {
    for (Stmt.Function s : methods) {
      LoxFunction method = new LoxFunction(
          s.name.text, s.fn, env, s.getter,
          !isStatic && s.name.text.equals("init"), isStatic, klass, null);

      (isStatic ? klass.statics : klass.methods).put(s.name.text, method);
    }
  }

  public Void visitBlockStmt(Stmt.Block s) {
    runBlock(s.body, new Environment(env));
    return null;
  }

  public Void visitExpressionStmt(Stmt.Expression s) {
    eval(s.expr);
    return null;
  }

  public Void visitPrintStmt(Stmt.Print s) {
    System.out.println(stringify(eval(s.expr)));
    return null;
  }

  public Void visitVarStmt(Stmt.Var s) {
    define(s.name, s.val == null ? null : eval(s.val), env);
    return null;
  }

  public Void visitReturnStmt(Stmt.Return s) {
    throw new Return(s.keyword, s.val == null ? null : eval(s.val));
  }

  public Void visitBreakStmt(Stmt.Break s) {
    throw new Break();
  }

  public Void visitFunctionStmt(Stmt.Function s) {
    define(s.name, new LoxFunction(s.name.text, s.fn, env), env);
    return null;
  }

  public Void visitIfStmt(Stmt.If s) {
    if (isTruthy(eval(s.cond))) run(s.yep);
    else if (s.nope != null) run(s.nope);
    return null;
  }

  public Void visitWhileStmt(Stmt.While s) {
    try {
      while (isTruthy(eval(s.cond))) run(s.body);
    } catch (Break stop) {
      // done with this loop
    }
    return null;
  }

  public Object visitVariableExpr(Expr.Variable e) {
    return lookup(e, e.name);
  }

  public Object visitThisExpr(Expr.This e) {
    return lookup(e, e.keyword);
  }

  public Object visitInnerExpr(Expr.Inner e) {
    return ((LoxFunction)lookup(e, e.keyword)).inner();
  }

  public Object visitFunctionExpr(Expr.Function e) {
    return new LoxFunction(null, e, env);
  }

  public Object visitGetExpr(Expr.Get e) {
    Object obj = eval(e.obj);

    if (obj instanceof LoxInstance) {
      return ((LoxInstance)obj).get(e.name, this);
    }

    throw new RuntimeError(e.name, "Only instances and classes have properties.");
  }

  public Object visitSetExpr(Expr.Set e) {
    Object obj = eval(e.obj);

    if (!(obj instanceof LoxInstance)) {
      throw new RuntimeError(e.name, "Only instances and classes have fields.");
    }

    Object val = eval(e.val);
    ((LoxInstance)obj).set(e.name, val);
    return val;
  }

  public Object visitAssignExpr(Expr.Assign e) {
    Object val = eval(e.val);
    int[] where = locals.get(e);

    if (where == null) globals.assign(e.name, val);
    else env.assignAt(where[0], where[1], val);

    return val;
  }

  public Object visitLogicalExpr(Expr.Logical e) {
    Object lhs = eval(e.lhs);

    if (e.op.kind == TokenType.OR) {
      if (isTruthy(lhs)) return lhs;
    } else if (!isTruthy(lhs)) {
      return lhs;
    }

    return eval(e.rhs);
  }

  public Object visitCallExpr(Expr.Call e) {
    Object callee = eval(e.callee);
    List<Object> args = new ArrayList<>();
    for (Expr arg : e.args) args.add(eval(arg));

    if (!(callee instanceof LoxCallable)) {
      throw new RuntimeError(e.paren, "Can only call functions and classes.");
    }

    LoxCallable fn = (LoxCallable)callee;

    // -1 is just for an inner call with no next method
    if (fn.arity() >= 0 && args.size() != fn.arity()) {
      throw new RuntimeError(e.paren,
          "Expected " + fn.arity() + " arguments but got " + args.size() + ".");
    }

    return fn.call(this, args);
  }

  public Object visitLiteralExpr(Expr.Literal e) {
    return e.val;
  }

  public Object visitGroupingExpr(Expr.Grouping e) {
    return eval(e.inside);
  }

  // only run the side we need
  public Object visitConditionalExpr(Expr.Conditional e) {
    return isTruthy(eval(e.cond)) ? eval(e.yep) : eval(e.nope);
  }

  public Object visitUnaryExpr(Expr.Unary e) {
    Object rhs = eval(e.rhs);

    switch (e.op.kind) {
      case BANG:
        return !isTruthy(rhs);
      case MINUS:
        numCheck(e.op, rhs);
        return -(double)rhs;
    }

    return null;
  }

  public Object visitBinaryExpr(Expr.Binary e) {
    Object lhs = eval(e.lhs), rhs = eval(e.rhs);   // left first, then right

    switch (e.op.kind) {
      case COMMA:
        return rhs;

      case GREATER:
        numsCheck(e.op, lhs, rhs);
        return (double)lhs > (double)rhs;
      case GREATER_EQUAL:
        numsCheck(e.op, lhs, rhs);
        return (double)lhs >= (double)rhs;
      case LESS:
        numsCheck(e.op, lhs, rhs);
        return (double)lhs < (double)rhs;
      case LESS_EQUAL:
        numsCheck(e.op, lhs, rhs);
        return (double)lhs <= (double)rhs;
      case BANG_EQUAL:
        return !isEqual(lhs, rhs);
      case EQUAL_EQUAL:
        return isEqual(lhs, rhs);

      case MINUS:
        numsCheck(e.op, lhs, rhs);
        return (double)lhs - (double)rhs;
      case STAR:
        numsCheck(e.op, lhs, rhs);
        return (double)lhs * (double)rhs;

      // earlier challenge: yell instead of giving Infinity or NaN
      case SLASH:
        numsCheck(e.op, lhs, rhs);
        if ((double)rhs == 0) {
          throw new RuntimeError(e.op, "Division by zero.");
        }
        return (double)lhs / (double)rhs;

      case PLUS:
        if (lhs instanceof String || rhs instanceof String) {
          return stringify(lhs) + stringify(rhs);
        }
        if (lhs instanceof Double && rhs instanceof Double) {
          return (double)lhs + (double)rhs;
        }
        throw new RuntimeError(e.op,
            "Operands must be two numbers or two strings.");
    }

    return null;
  }

  boolean isTruthy(Object o) {
    if (o == null) return false;
    if (o instanceof Boolean) return (boolean)o;
    return true;
  }

  boolean isEqual(Object a, Object b) {
    if (a == null) return b == null;
    return a.equals(b);
  }

  void numCheck(Token op, Object o) {
    if (o instanceof Double) return;
    throw new RuntimeError(op, "Operand must be a number.");
  }

  void numsCheck(Token op, Object a, Object b) {
    if (a instanceof Double && b instanceof Double) return;
    throw new RuntimeError(op, "Operands must be numbers.");
  }

  String stringify(Object o) {
    if (o == null) return "nil";

    String s = o.toString();
    if (o instanceof Double && s.endsWith(".0")) {
      s = s.substring(0, s.length() - 2);
    }

    return s;
  }
}