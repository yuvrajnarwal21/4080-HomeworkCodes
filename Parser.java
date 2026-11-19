import java.util.ArrayList;
import java.util.List;

// takes tokens and builds statements.
// each expression rule handles its own level, then asks the next one down.
class Parser {
  static class ParseError extends RuntimeException {}

  final List<Token> toks;
  int spot = 0;   // next token we haven't eaten

  Parser(List<Token> toks) { this.toks = toks; }

  // keep reading statements till we're out
  List<Stmt> parse() {
    List<Stmt> body = new ArrayList<>();
    while (!outOfToks()) {
      Stmt stmt = declaration();
      if (stmt != null) body.add(stmt);
    }
    return body;
  }

  Stmt declaration() {
    try {
      // fun + name = declaration. fun + "(" = anonymous function
      if (isIt(TokenType.FUN) && nextIs(TokenType.IDENTIFIER)) {
        eat();
        Token name = mustEat(TokenType.IDENTIFIER, "Expect function name.");
        return new Stmt.Function(name, functionBody());
      }

      if (snag(TokenType.VAR)) return varDeclaration();
      return statement();
    } catch (ParseError err) {
      synchronize();
      return null;
    }
  }

  Stmt varDeclaration() {
    Token name = mustEat(TokenType.IDENTIFIER, "Expect variable name.");
    Expr val = null;
    if (snag(TokenType.EQUAL)) val = expression();
    mustEat(TokenType.SEMICOLON, "Expect ';' after variable declaration.");
    return new Stmt.Var(name, val);
  }

  Stmt statement() {
    if (snag(TokenType.FOR)) return forStatement();
    if (snag(TokenType.IF)) return ifStatement();
    if (snag(TokenType.WHILE)) return whileStatement();
    if (snag(TokenType.PRINT)) return printStatement();
    if (snag(TokenType.RETURN)) return returnStatement();
    if (snag(TokenType.LEFT_BRACE)) return new Stmt.Block(block());
    return expressionStatement();
  }

  // turn for into a while with setup before it and an update after each round
  Stmt forStatement() {
    mustEat(TokenType.LEFT_PAREN, "Expect '(' after 'for'.");

    Stmt setup;
    if (snag(TokenType.SEMICOLON)) setup = null;
    else if (snag(TokenType.VAR)) setup = varDeclaration();
    else setup = expressionStatement();

    Expr cond = null;
    if (!isIt(TokenType.SEMICOLON)) cond = expression();
    mustEat(TokenType.SEMICOLON, "Expect ';' after loop condition.");

    Expr step = null;
    if (!isIt(TokenType.RIGHT_PAREN)) step = expression();
    mustEat(TokenType.RIGHT_PAREN, "Expect ')' after for clauses.");

    Stmt body = statement();

    if (step != null) {
      List<Stmt> round = new ArrayList<>();
      round.add(body);
      round.add(new Stmt.Expression(step));
      body = new Stmt.Block(round);
    }

    if (cond == null) cond = new Expr.Literal(true);
    body = new Stmt.While(cond, body);

    if (setup != null) {
      List<Stmt> whole = new ArrayList<>();
      whole.add(setup);
      whole.add(body);
      body = new Stmt.Block(whole);
    }

    return body;
  }

  Stmt ifStatement() {
    mustEat(TokenType.LEFT_PAREN, "Expect '(' after 'if'.");
    Expr cond = expression();
    mustEat(TokenType.RIGHT_PAREN, "Expect ')' after condition.");

    Stmt yep = statement();
    Stmt nope = null;
    if (snag(TokenType.ELSE)) nope = statement();

    return new Stmt.If(cond, yep, nope);
  }

  Stmt whileStatement() {
    mustEat(TokenType.LEFT_PAREN, "Expect '(' after 'while'.");
    Expr cond = expression();
    mustEat(TokenType.RIGHT_PAREN, "Expect ')' after condition.");
    return new Stmt.While(cond, statement());
  }

  Stmt printStatement() {
    Expr val = expression();
    mustEat(TokenType.SEMICOLON, "Expect ';' after value.");
    return new Stmt.Print(val);
  }

  Stmt returnStatement() {
    Token keyword = last();
    Expr val = null;
    if (!isIt(TokenType.SEMICOLON)) val = expression();
    mustEat(TokenType.SEMICOLON, "Expect ';' after return value.");
    return new Stmt.Return(keyword, val);
  }

  Stmt expressionStatement() {
    Expr expr = expression();
    mustEat(TokenType.SEMICOLON, "Expect ';' after expression.");
    return new Stmt.Expression(expr);
  }

  // opening brace was already eaten
  List<Stmt> block() {
    List<Stmt> body = new ArrayList<>();

    while (!isIt(TokenType.RIGHT_BRACE) && !outOfToks()) {
      Stmt stmt = declaration();
      if (stmt != null) body.add(stmt);
    }

    mustEat(TokenType.RIGHT_BRACE, "Expect '}' after block.");
    return body;
  }

  // named and anonymous functions use the same params and body
  Expr.Function functionBody() {
    mustEat(TokenType.LEFT_PAREN, "Expect '(' before parameters.");
    List<Token> params = new ArrayList<>();

    if (!isIt(TokenType.RIGHT_PAREN)) {
      do {
        if (params.size() >= 255) {
          yell(peek(), "Can't have more than 255 parameters.");
        }
        params.add(mustEat(TokenType.IDENTIFIER, "Expect parameter name."));
      } while (snag(TokenType.COMMA));
    }

    mustEat(TokenType.RIGHT_PAREN, "Expect ')' after parameters.");
    mustEat(TokenType.LEFT_BRACE, "Expect '{' before function body.");
    return new Expr.Function(params, block());
  }

  Expr expression() { return comma(); }

  // comma stays weakest. run the left, keep the right
  Expr comma() {
    Expr lhs = assignment();
    while (snag(TokenType.COMMA)) {
      Token op = last();
      lhs = new Expr.Binary(lhs, op, assignment());
    }
    return lhs;
  }

  // goes right to left, so a = b = 3 works
  Expr assignment() {
    Expr expr = conditional();

    if (snag(TokenType.EQUAL)) {
      Token equals = last();
      Expr val = assignment();

      if (expr instanceof Expr.Variable) {
        Token name = ((Expr.Variable)expr).name;
        return new Expr.Assign(name, val);
      }

      yell(equals, "Invalid assignment target.");
    }

    return expr;
  }

  // a ? b : c. middle allows anything, right side goes right to left
  Expr conditional() {
    Expr cond = or();

    if (snag(TokenType.QUESTION)) {
      Expr yep = expression();
      mustEat(TokenType.COLON, "Expect ':' after then branch of conditional expression.");
      cond = new Expr.Conditional(cond, yep, conditional());
    }

    return cond;
  }

  Expr or() {
    Expr lhs = and();
    while (snag(TokenType.OR)) {
      Token op = last();
      lhs = new Expr.Logical(lhs, op, and());
    }
    return lhs;
  }

  Expr and() {
    Expr lhs = equality();
    while (snag(TokenType.AND)) {
      Token op = last();
      lhs = new Expr.Logical(lhs, op, equality());
    }
    return lhs;
  }

  // get the left, then keep stacking "op right" onto it
  Expr equality() {
    Expr lhs = comparison();
    while (snag(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL)) {
      Token op = last();
      lhs = new Expr.Binary(lhs, op, comparison());
    }
    return lhs;
  }

  Expr comparison() {
    Expr lhs = term();
    while (snag(TokenType.GREATER, TokenType.GREATER_EQUAL,
                TokenType.LESS, TokenType.LESS_EQUAL)) {
      Token op = last();
      lhs = new Expr.Binary(lhs, op, term());
    }
    return lhs;
  }

  Expr term() {
    Expr lhs = factor();
    while (snag(TokenType.MINUS, TokenType.PLUS)) {
      Token op = last();
      lhs = new Expr.Binary(lhs, op, factor());
    }
    return lhs;
  }

  Expr factor() {
    Expr lhs = unary();
    while (snag(TokenType.SLASH, TokenType.STAR)) {
      Token op = last();
      lhs = new Expr.Binary(lhs, op, unary());
    }
    return lhs;
  }

  // handles things like -x and !!x
  Expr unary() {
    if (snag(TokenType.BANG, TokenType.MINUS)) {
      Token op = last();
      return new Expr.Unary(op, unary());
    }
    return call();
  }

  // loop lets a returned function get called too: makeFn()()
  Expr call() {
    Expr expr = primary();

    while (snag(TokenType.LEFT_PAREN)) {
      expr = finishCall(expr);
    }

    return expr;
  }

  Expr finishCall(Expr callee) {
    List<Expr> args = new ArrayList<>();

    if (!isIt(TokenType.RIGHT_PAREN)) {
      do {
        if (args.size() >= 255) {
          yell(peek(), "Can't have more than 255 arguments.");
        }

        // don't let the comma operator eat the argument separators
        args.add(assignment());
      } while (snag(TokenType.COMMA));
    }

    Token paren = mustEat(TokenType.RIGHT_PAREN, "Expect ')' after arguments.");
    return new Expr.Call(callee, paren, args);
  }

  // plain values, names, anonymous functions, and ( stuff )
  Expr primary() {
    if (snag(TokenType.FALSE)) return new Expr.Literal(false);
    if (snag(TokenType.TRUE)) return new Expr.Literal(true);
    if (snag(TokenType.NIL)) return new Expr.Literal(null);

    if (snag(TokenType.NUMBER, TokenType.STRING)) {
      return new Expr.Literal(last().val);
    }

    if (snag(TokenType.IDENTIFIER)) return new Expr.Variable(last());
    if (snag(TokenType.FUN)) return functionBody();

    if (snag(TokenType.LEFT_PAREN)) {
      Expr inside = expression();
      mustEat(TokenType.RIGHT_PAREN, "Expect ')' after expression.");
      return new Expr.Grouping(inside);
    }

    // earlier challenge: an operator with nothing on its left
    // no "-" here, since unary minus is fine
    if (snag(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL)) {
      yell(last(), "Missing left-hand operand.");
      equality();
      return null;
    }

    if (snag(TokenType.GREATER, TokenType.GREATER_EQUAL,
             TokenType.LESS, TokenType.LESS_EQUAL)) {
      yell(last(), "Missing left-hand operand.");
      comparison();
      return null;
    }

    if (snag(TokenType.PLUS)) {
      yell(last(), "Missing left-hand operand.");
      term();
      return null;
    }

    if (snag(TokenType.SLASH, TokenType.STAR)) {
      yell(last(), "Missing left-hand operand.");
      factor();
      return null;
    }

    throw yell(peek(), "Expect expression.");
  }

  // token helpers
  boolean snag(TokenType... kinds) {
    for (TokenType k : kinds) {
      if (isIt(k)) {
        eat();
        return true;
      }
    }
    return false;
  }

  Token mustEat(TokenType kind, String msg) {
    if (isIt(kind)) return eat();
    throw yell(peek(), msg);
  }

  boolean isIt(TokenType kind) {
    return !outOfToks() && peek().kind == kind;
  }

  boolean nextIs(TokenType kind) {
    return spot + 1 < toks.size() && toks.get(spot + 1).kind == kind;
  }

  Token eat() {
    if (!outOfToks()) spot++;
    return last();
  }

  boolean outOfToks() { return peek().kind == TokenType.EOF; }
  Token peek() { return toks.get(spot); }
  Token last() { return toks.get(spot - 1); }

  // report it. throwing this also stops the current parse
  ParseError yell(Token tok, String msg) {
    Lox.error(tok, msg);
    return new ParseError();
  }

  // something broke, skip ahead to the next likely statement
  void synchronize() {
    if (outOfToks()) return;
    eat();

    while (!outOfToks()) {
      if (last().kind == TokenType.SEMICOLON) return;

      switch (peek().kind) {
        case CLASS:
        case FUN:
        case VAR:
        case FOR:
        case IF:
        case WHILE:
        case PRINT:
        case RETURN:
          return;
        default:
          break;
      }

      eat();
    }
  }
}