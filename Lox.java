import java.io.*;
import java.nio.file.*;
import java.util.List;

// start here. run a file, or type code in one line at a time
public class Lox {
  static final Interpreter terp = new Interpreter();   // one for the whole session, keeps stuff around later
  static boolean oops = false;      // true if the code was written wrong (scanner/parser)
  static boolean oopsRun = false;   // true if it broke while running

  public static void main(String[] args) throws IOException {
    if (args.length > 1) { System.out.println("Usage: jlox [script]"); System.exit(64); }
    else if (args.length == 1) runFile(args[0]);
    else runPrompt();
  }

  static void runFile(String path) throws IOException {
    run(new String(Files.readAllBytes(Paths.get(path))));
    if (oops) System.exit(65);
    if (oopsRun) System.exit(70);
  }

  static void runPrompt() throws IOException {
    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    while (true) {
      System.out.print("> ");
      String ln = in.readLine();
      if (ln == null) break;   // ctrl-D, bye
      run(ln);
      oops = false;   // one mistake shouldn't end everything
    }
  }

  // code -> tokens -> tree -> print it
  static void run(String src) {
    List<Token> toks = new Scanner(src).scanTokens();
    Expr tree = new Parser(toks).parse();
    if (oops) return;   // something broke, we already said so
    terp.interpret(tree);
  }

  // scanner errors, we only know the line
  static void error(int line, String msg) { report(line, "", msg); }

  // parser errors, we know the exact token
  static void error(Token tok, String msg) {
    if (tok.kind == TokenType.EOF) report(tok.line, " at end", msg);
    else report(tok.line, " at '" + tok.text + "'", msg);
  }

  // runtime errors, say the message then the line
  static void runtimeError(RuntimeError err) {
    System.err.println(err.getMessage() + "\n[line " + err.tok.line + "]");
    oopsRun = true;
  }
  
  static void report(int line, String where, String msg) {
    System.err.println("[line " + line + "] Error" + where + ": " + msg);
    oops = true;
  }
}
