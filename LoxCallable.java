import java.util.List;

// anything we can call needs these two things
interface LoxCallable {
  int arity();
  Object call(Interpreter terp, List<Object> args);
}