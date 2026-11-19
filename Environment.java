import java.util.*;

// one scope. if a name isn't here, check the one outside it
class Environment {
  final Environment outer;
  final Map<String, Object> vals = new HashMap<>();

  Environment() { this(null); }
  Environment(Environment outer) { this.outer = outer; }

  void define(String name, Object val) { vals.put(name, val); }

  Object get(Token name) {
    if (vals.containsKey(name.text)) return vals.get(name.text);
    if (outer != null) return outer.get(name);
    throw new RuntimeError(name, "Undefined variable '" + name.text + "'.");
  }

  void assign(Token name, Object val) {
    if (vals.containsKey(name.text)) { vals.put(name.text, val); return; }
    if (outer != null) { outer.assign(name, val); return; }
    throw new RuntimeError(name, "Undefined variable '" + name.text + "'.");
  }
}