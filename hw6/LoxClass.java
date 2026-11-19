import java.util.*;

// a class is callable, and also holds its own static methods and fields
class LoxClass extends LoxInstance implements LoxCallable {
  final String name;
  final LoxClass parent;

  final Map<String, LoxFunction> methods = new HashMap<>();
  final Map<String, LoxFunction> statics = new HashMap<>();

  LoxClass(String name, LoxClass parent) {
    super(null);
    this.name = name;
    this.parent = parent;
  }

  LoxFunction ownMethod(String name, boolean isStatic) {
    return (isStatic ? statics : methods).get(name);
  }

  // ch13 q2: the highest ancestor gets first say
  LoxFunction findMethod(String name, boolean isStatic) {
    if (parent != null) {
      LoxFunction found = parent.findMethod(name, isStatic);
      if (found != null) return found;
    }
    return ownMethod(name, isStatic);
  }

  // keep the matching method closest below the class containing inner
  LoxFunction findInner(LoxClass owner, String name, boolean isStatic) {
    LoxFunction next = null;

    for (LoxClass c = this; c != null && c != owner; c = c.parent) {
      LoxFunction found = c.ownMethod(name, isStatic);
      if (found != null) next = found;
    }

    return next;
  }

  Object get(Token name, Interpreter terp) {
    if (fields.containsKey(name.text)) return fields.get(name.text);
    return methodValue(findMethod(name.text, true), name, terp);
  }

  public int arity() {
    LoxFunction init = findMethod("init", false);
    return init == null ? 0 : init.arity();
  }

  public Object call(Interpreter terp, List<Object> args) {
    LoxInstance obj = new LoxInstance(this);
    LoxFunction init = findMethod("init", false);

    if (init != null) init.bind(obj).call(terp, args);
    return obj;
  }

  public String toString() {
    return name;
  }
}