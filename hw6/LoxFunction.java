import java.util.*;

// keeps the code, the closure, and method info when there is any
class LoxFunction implements LoxCallable {
  final String name;
  final Expr.Function fn;
  final Environment closure;

  final boolean getter, initializer, isStatic;
  final LoxClass owner;
  final LoxInstance receiver;

  // regular named or anonymous function
  LoxFunction(String name, Expr.Function fn, Environment closure) {
    this(name, fn, closure, false, false, false, null, null);
  }

  LoxFunction(String name, Expr.Function fn, Environment closure,
              boolean getter, boolean initializer, boolean isStatic,
              LoxClass owner, LoxInstance receiver) {
    this.name = name;
    this.fn = fn;
    this.closure = closure;
    this.getter = getter;
    this.initializer = initializer;
    this.isStatic = isStatic;
    this.owner = owner;
    this.receiver = receiver;
  }

  // matches Resolver: this at slot 0, current method at slot 1
  LoxFunction bind(LoxInstance obj) {
    Environment bound = new Environment(closure);

    LoxFunction method = new LoxFunction(name, fn, bound,
        getter, initializer, isStatic, owner, obj);

    bound.put(0, obj);
    bound.put(1, method);
    return method;
  }

  LoxCallable inner() {
    LoxClass actual = isStatic ? (LoxClass)receiver : receiver.klass;
    LoxFunction next = actual.findInner(owner, name, isStatic);

    if (next != null) return next.bind(receiver);

    // no matching subclass method, so inner does nothing
    return new LoxCallable() {
      public int arity() { return -1; }

      public Object call(Interpreter terp, List<Object> args) {
        return null;
      }

      public String toString() {
        return "<empty inner>";
      }
    };
  }

  public int arity() {
    return fn.params.size();
  }

  public Object call(Interpreter terp, List<Object> args) {
    Environment local = new Environment(closure);

    for (int i = 0; i < fn.params.size(); i++) {
      terp.define(fn.params.get(i), args.get(i), local);
    }

    try {
      terp.runBlock(fn.body, local);
    } catch (Return answer) {
      return initializer ? receiver : answer.val;
    }

    return initializer ? receiver : null;
  }

  public String toString() {
    return name == null ? "<fn anonymous>" : "<fn " + name + ">";
  }
}