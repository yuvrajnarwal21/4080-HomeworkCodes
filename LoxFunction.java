import java.util.List;

// saves the function's code and the scope where it was made
class LoxFunction implements LoxCallable {
  final String name;
  final Expr.Function fn;
  final Environment closure;

  LoxFunction(String name, Expr.Function fn, Environment closure) {
    this.name = name;
    this.fn = fn;
    this.closure = closure;
  }

  public int arity() { return fn.params.size(); }

  public Object call(Interpreter terp, List<Object> args) {
    Environment local = new Environment(closure);

    // params go into the slots picked by the resolver
    for (int i = 0; i < fn.params.size(); i++) {
      terp.define(fn.params.get(i), args.get(i), local);
    }

    try {
      terp.runBlock(fn.body, local);
    } catch (Return answer) {
      return answer.val;
    }

    return null;   // no return value means nil
  }

  public String toString() {
    return name == null ? "<fn anonymous>" : "<fn " + name + ">";
  }
}