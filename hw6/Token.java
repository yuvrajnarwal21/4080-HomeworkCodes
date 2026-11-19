// one token. what it is, what it says, its value, what line
class Token {
  final TokenType kind;  final String text;  final Object val;  final int line;

  Token(TokenType kind, String text, Object val, int line) {
    this.kind = kind;  this.text = text;  this.val = val;  this.line = line;
  }

  public String toString() { return kind + " " + text + " " + val; }
}
