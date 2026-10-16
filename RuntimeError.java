// a lox error that happens while the code is running (like  -"muffin")
// keeps the token so we can say what line it was on
class RuntimeError extends RuntimeException {
  final Token tok;
  RuntimeError(Token tok, String msg) { super(msg); this.tok = tok; }
}