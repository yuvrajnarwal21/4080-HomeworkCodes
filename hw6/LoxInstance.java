import java.util.*;

// each object has its own fields, but shares methods with its class
class LoxInstance {
  final LoxClass klass;
  final Map<String, Object> fields = new HashMap<>();

  LoxInstance(LoxClass klass) {
    this.klass = klass;
  }

  Object get(Token name, Interpreter terp) {
    if (fields.containsKey(name.text)) return fields.get(name.text);
    return methodValue(klass.findMethod(name.text, false), name, terp);
  }

  Object methodValue(LoxFunction method, Token name, Interpreter terp) {
    if (method == null) {
      throw new RuntimeError(name, "Undefined property '" + name.text + "'.");
    }

    LoxFunction bound = method.bind(this);

    // getters run right away, regular methods wait for a call
    return method.getter ? bound.call(terp, Collections.emptyList()) : bound;
  }

  void set(Token name, Object val) {
    fields.put(name.text, val);
  }

  public String toString() {
    return klass.name + " instance";
  }
}