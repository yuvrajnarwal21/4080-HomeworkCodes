import java.util.*;

// globals use names. locals use array slots
class Environment {
  final Environment outer;
  final Map<String, Object> vals;
  Object[] values = new Object[0];

  Environment() { this(null); }

  Environment(Environment outer) {
    this.outer = outer;
    vals = outer == null ? new HashMap<>() : null;
  }

  void define(String name, Object val) {
    vals.put(name, val);
  }

  Object get(Token name) {
    if (vals.containsKey(name.text)) return vals.get(name.text);
    throw new RuntimeError(name, "Undefined variable '" + name.text + "'.");
  }

  void assign(Token name, Object val) {
    if (vals.containsKey(name.text)) {
      vals.put(name.text, val);
      return;
    }
    throw new RuntimeError(name, "Undefined variable '" + name.text + "'.");
  }

  Environment ancestor(int depth) {
    Environment scope = this;
    for (int i = 0; i < depth; i++) scope = scope.outer;
    return scope;
  }

  void put(int index, Object val) {
    if (index >= values.length) {
      values = Arrays.copyOf(values,
          Math.max(index + 1, Math.max(8, values.length * 2)));
    }
    values[index] = val;
  }

  Object getAt(int depth, int index) {
    Object[] slots = ancestor(depth).values;
    return index < slots.length ? slots[index] : null;
  }

  void assignAt(int depth, int index, Object val) {
    ancestor(depth).put(index, val);
  }
}