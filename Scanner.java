import java.util.*;
// reads the code letter by letter and turns it into tokens
class Scanner {
  // words lox owns
  static final Map<String, TokenType> dibs = new HashMap<>();
  static {
    dibs.put("and", TokenType.AND);       dibs.put("class", TokenType.CLASS);
    dibs.put("else", TokenType.ELSE);     dibs.put("false", TokenType.FALSE);
    dibs.put("for", TokenType.FOR);       dibs.put("fun", TokenType.FUN);
    dibs.put("if", TokenType.IF);         dibs.put("nil", TokenType.NIL);
    dibs.put("or", TokenType.OR);         dibs.put("print", TokenType.PRINT);
    dibs.put("return", TokenType.RETURN); dibs.put("super", TokenType.SUPER);
    dibs.put("this", TokenType.THIS);     dibs.put("true", TokenType.TRUE);
    dibs.put("var", TokenType.VAR);       dibs.put("while", TokenType.WHILE);
  }
  final String src;
  final List<Token> toks = new ArrayList<>();
  int from = 0, at = 0, line = 1;   // token start, where we are, what line

  Scanner(String src) { this.src = src; }

  // keep making tokens till we run out, then add EOF
  List<Token> scanTokens() {
    while (!done()) { from = at; scanOne(); }
    toks.add(new Token(TokenType.EOF, "", null, line));
    return toks;
  }

  void scanOne() {
    char c = nom();
    switch (c) {
      case '(': add(TokenType.LEFT_PAREN); break;   case ')': add(TokenType.RIGHT_PAREN); break;
      case '{': add(TokenType.LEFT_BRACE); break;   case '}': add(TokenType.RIGHT_BRACE); break;
      case ',': add(TokenType.COMMA); break;        case '.': add(TokenType.DOT); break;
      case '-': add(TokenType.MINUS); break;        case '+': add(TokenType.PLUS); break;
      case ';': add(TokenType.SEMICOLON); break;    case '*': add(TokenType.STAR); break;
      case '?': add(TokenType.QUESTION); break;     case ':': add(TokenType.COLON); break;   // new

      // could be 1 or 2 chars, so check the next one
      case '!': add(nomIf('=') ? TokenType.BANG_EQUAL : TokenType.BANG); break;
      case '=': add(nomIf('=') ? TokenType.EQUAL_EQUAL : TokenType.EQUAL); break;
      case '<': add(nomIf('=') ? TokenType.LESS_EQUAL : TokenType.LESS); break;
      case '>': add(nomIf('=') ? TokenType.GREATER_EQUAL : TokenType.GREATER); break;

      case '/':
        if (nomIf('/')) { while (peek() != '\n' && !done()) nom(); }   // comment, skip the line
        else add(TokenType.SLASH);
        break;

      case ' ': case '\r': case '\t': break;   // spaces, don't care
      case '\n': line++; break;
      case '"': str(); break;

      default:
        if (digit(c)) num();
        else if (alpha(c)) word();    }
  }

  // a word. lox's word or just a name?
  void word() {
    while (alpha(peek()) || digit(peek())) nom();
    TokenType kind = dibs.get(src.substring(from, at));
    add(kind == null ? TokenType.IDENTIFIER : kind);
  }

  // a number like 12 or 3.5
  void num() {
    while (digit(peek())) nom();
    if (peek() == '.' && digit(peekPeek())) { nom(); while (digit(peek())) nom(); }
    add(TokenType.NUMBER, Double.parseDouble(src.substring(from, at)));
  }

  // a string, "like this"
  void str() {
    while (peek() != '"' && !done()) { if (peek() == '\n') line++; nom(); }
    nom();   // the closing "
    add(TokenType.STRING, src.substring(from + 1, at - 1));   // drop the quotes
  }

  // little helpers
  boolean nomIf(char want) {   // eat the next char only if it's the one we want
    if (done() || src.charAt(at) != want) return false;
    at++; return true;
  }
  char peek()     { return done() ? '\0' : src.charAt(at); }
  char peekPeek() { return at + 1 >= src.length() ? '\0' : src.charAt(at + 1); }
  boolean alpha(char c) { return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_'; }
  boolean digit(char c) { return c >= '0' && c <= '9'; }
  boolean done()  { return at >= src.length(); }
  char nom()      { return src.charAt(at++); }

  void add(TokenType kind) { add(kind, null); }
  void add(TokenType kind, Object val) { toks.add(new Token(kind, src.substring(from, at), val, line)); }
}
