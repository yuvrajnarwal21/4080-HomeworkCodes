import java.util.*;

// runs the tree. expressions give back values, statements do stuff
class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
  final Environment globals = new Environment();
  Environment env = globals;

  Interpreter() {
    globals.define("clock", new LoxCallable() {
      public int arity() { return 0; }
      public Object call(Interpreter terp, List<Object> args) {
        return System.currentTimeMillis() / 1000.0;
      }
      public String toString() { return "<native fn>"; }
    });
  }

  void interpret(List<Stmt> body) {
    try { for (Stmt s : body) run(s); }
    catch (RuntimeError err) { Lox.runtimeError(err); }
    catch (Return answer) {
      Lox.runtimeError(new RuntimeError(answer.keyword, "Can't return from top-level code."));
    }
  }

  void run(Stmt s) { s.accept(this); }
  Object eval(Expr e) { return e.accept(this); }

  // always put the old scope back, even if a return jumps out
  void runBlock(List<Stmt> body, Environment local) {
    Environment old = env;
    try {
      env = local;
      for (Stmt s : body) run(s);
    } finally { env = old; }
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
    env.define(s.name.text, s.val == null ? null : eval(s.val));
    return null;
  }

  public Void visitReturnStmt(Stmt.Return s) {
    throw new Return(s.keyword, s.val == null ? null : eval(s.val));
  }

  public Void visitFunctionStmt(Stmt.Function s) {
    env.define(s.name.text, new LoxFunction(s.name.text, s.fn, env));
    return null;
  }

  public Void visitIfStmt(Stmt.If s) {
    if (isTruthy(eval(s.cond))) run(s.yep);
    else if (s.nope != null) run(s.nope);
    return null;
  }

  public Void visitWhileStmt(Stmt.While s) {
    while (isTruthy(eval(s.cond))) run(s.body);
    return null;
  }

  public Object visitVariableExpr(Expr.Variable e) { return env.get(e.name); }
  public Object visitFunctionExpr(Expr.Function e) { return new LoxFunction(null, e, env); }
  public Object visitLiteralExpr(Expr.Literal e)   { return e.val; }
  public Object visitGroupingExpr(Expr.Grouping e) { return eval(e.inside); }

  public Object visitAssignExpr(Expr.Assign e) {
    Object val = eval(e.val);
    env.assign(e.name, val);
    return val;
  }

  public Object visitLogicalExpr(Expr.Logical e) {
    Object lhs = eval(e.lhs);

    if (e.op.kind == TokenType.OR) {
      if (isTruthy(lhs)) return lhs;
    } else if (!isTruthy(lhs)) return lhs;

    return eval(e.rhs);   // only run the right side if we need it
  }

  public Object visitCallExpr(Expr.Call e) {
    Object callee = eval(e.callee);
    List<Object> args = new ArrayList<>();
    for (Expr arg : e.args) args.add(eval(arg));

    if (!(callee instanceof LoxCallable)) {
      throw new RuntimeError(e.paren, "Can only call functions.");
    }

    LoxCallable fn = (LoxCallable)callee;
    if (args.size() != fn.arity()) {
      throw new RuntimeError(e.paren,
          "Expected " + fn.arity() + " arguments but got " + args.size() + ".");
    }

    return fn.call(this, args);
  }

  // a ? b : c. only run the side we need
  public Object visitConditionalExpr(Expr.Conditional e) {
    return isTruthy(eval(e.cond)) ? eval(e.yep) : eval(e.nope);
  }

  public Object visitUnaryExpr(Expr.Unary e) {
    Object rhs = eval(e.rhs);

    switch (e.op.kind) {
      case BANG:  return !isTruthy(rhs);
      case MINUS: numCheck(e.op, rhs); return -(double)rhs;
    }

    return null;   // can't get here
  }

  public Object visitBinaryExpr(Expr.Binary e) {
    Object lhs = eval(e.lhs), rhs = eval(e.rhs);   // left first, then right

    switch (e.op.kind) {
      case COMMA: return rhs;   // ran the left already, toss it, keep the right

      case GREATER:       numsCheck(e.op, lhs, rhs); return (double)lhs >  (double)rhs;
      case GREATER_EQUAL: numsCheck(e.op, lhs, rhs); return (double)lhs >= (double)rhs;
      case LESS:          numsCheck(e.op, lhs, rhs); return (double)lhs <  (double)rhs;
      case LESS_EQUAL:    numsCheck(e.op, lhs, rhs); return (double)lhs <= (double)rhs;
      case BANG_EQUAL:    return !isEqual(lhs, rhs);
      case EQUAL_EQUAL:   return isEqual(lhs, rhs);

      case MINUS: numsCheck(e.op, lhs, rhs); return (double)lhs - (double)rhs;
      case STAR:  numsCheck(e.op, lhs, rhs); return (double)lhs * (double)rhs;

      // java would quietly give Infinity / NaN. we yell instead
      case SLASH:
        numsCheck(e.op, lhs, rhs);
        if ((double)rhs == 0) throw new RuntimeError(e.op, "Division by zero.");
        return (double)lhs / (double)rhs;

      case PLUS:
        // if either side is a string, turn both into strings and glue them
        if (lhs instanceof String || rhs instanceof String) return stringify(lhs) + stringify(rhs);
        if (lhs instanceof Double && rhs instanceof Double) return (double)lhs + (double)rhs;
        throw new RuntimeError(e.op, "Operands must be two numbers or two strings.");
    }

    return null;   // can't get here
  }

  // false and nil are false. everything else is true
  boolean isTruthy(Object o) {
    if (o == null) return false;
    if (o instanceof Boolean) return (boolean)o;
    return true;
  }

  boolean isEqual(Object a, Object b) {
    if (a == null) return b == null;
    return a.equals(b);
  }

  // make sure stuff is a number before we do math on it
  void numCheck(Token op, Object o) {
    if (o instanceof Double) return;
    throw new RuntimeError(op, "Operand must be a number.");
  }

  void numsCheck(Token op, Object a, Object b) {
    if (a instanceof Double && b instanceof Double) return;
    throw new RuntimeError(op, "Operands must be numbers.");
  }

  // nil instead of null, and 3 instead of 3.0
  String stringify(Object o) {
    if (o == null) return "nil";
    String s = o.toString();
    if (o instanceof Double && s.endsWith(".0")) s = s.substring(0, s.length() - 2);
    return s;
  }
}