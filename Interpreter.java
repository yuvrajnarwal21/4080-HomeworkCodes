// runs the tree and gets the answer. same visitor idea as AstPrinter,
// but instead of making text we do the actual math
class Interpreter implements Expr.Visitor<Object> {

  // start here. run it, print the answer. if it blows up, say why and keep going
  void interpret(Expr e) {
    try { System.out.println(stringify(eval(e))); }
    catch (RuntimeError err) { Lox.runtimeError(err); }
  }

  Object eval(Expr e) { return e.accept(this); }   // just sends it back into the visitor

  public Object visitLiteralExpr(Expr.Literal e)   { return e.val; }   // value's already in there, just grab it
  public Object visitGroupingExpr(Expr.Grouping e) { return eval(e.inside); }

  // a ? b : c  -- only run the side we need
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
      case COMMA: return rhs;   // ch6 comma: ran the left already, toss it, keep the right

      case GREATER:       numsCheck(e.op, lhs, rhs); return (double)lhs >  (double)rhs;
      case GREATER_EQUAL: numsCheck(e.op, lhs, rhs); return (double)lhs >= (double)rhs;
      case LESS:          numsCheck(e.op, lhs, rhs); return (double)lhs <  (double)rhs;
      case LESS_EQUAL:    numsCheck(e.op, lhs, rhs); return (double)lhs <= (double)rhs;
      case BANG_EQUAL:    return !isEqual(lhs, rhs);
      case EQUAL_EQUAL:   return isEqual(lhs, rhs);

      case MINUS: numsCheck(e.op, lhs, rhs); return (double)lhs - (double)rhs;
      case STAR:  numsCheck(e.op, lhs, rhs); return (double)lhs * (double)rhs;

      // challenge 3: java would quietly give Infinity / -Infinity / NaN. we yell instead
      case SLASH:
        numsCheck(e.op, lhs, rhs);
        if ((double)rhs == 0) throw new RuntimeError(e.op, "Division by zero.");
        return (double)lhs / (double)rhs;

      case PLUS:
        // challenge 2: if EITHER side is a string, turn both into strings and glue them. "scone" + 4 -> "scone4"
        if (lhs instanceof String || rhs instanceof String) return stringify(lhs) + stringify(rhs);
        if (lhs instanceof Double && rhs instanceof Double) return (double)lhs + (double)rhs;
        throw new RuntimeError(e.op, "Operands must be two numbers or two strings.");
    }
    return null;   // can't get here
  }

  // ---- helpers ----

  // lox rule: false and nil are false. everything else is true
  boolean isTruthy(Object o) {
    if (o == null) return false;
    if (o instanceof Boolean) return (boolean)o;
    return true;
  }

  boolean isEqual(Object a, Object b) {
    if (a == null) return b == null;   // stops java from crashing on null.equals()
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

  // lox value -> text. nil instead of null, and 3 instead of 3.0
  String stringify(Object o) {
    if (o == null) return "nil";
    String s = o.toString();
    if (o instanceof Double && s.endsWith(".0")) s = s.substring(0, s.length() - 2);
    return s;
  }
}