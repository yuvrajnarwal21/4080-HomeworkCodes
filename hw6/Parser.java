import java.util.*;

// turns tokens into statements, one rule at a time
class Parser {
  static class ParseError extends RuntimeException {}

  final List<Token> toks;
  int spot = 0;

  Parser(List<Token> toks) { this.toks = toks; }

  List<Stmt> parse() {
    List<Stmt> body = new ArrayList<>();
    while (!outOfToks()) {
      Stmt s = declaration();
      if (s != null) body.add(s);
    }
    return body;
  }

  Stmt declaration() {
    try {
      if (snag(TokenType.CLASS)) return classDeclaration();

      if (isIt(TokenType.FUN) && nextIs(TokenType.IDENTIFIER)) {
        eat();
        Token name = mustEat(TokenType.IDENTIFIER, "Expect function name.");
        return new Stmt.Function(name, functionBody(false), false);
      }

      if (snag(TokenType.VAR)) return varDeclaration();
      return statement();
    } catch (ParseError err) {
      synchronize();
      return null;
    }
  }

  Stmt classDeclaration() {
    Token name = mustEat(TokenType.IDENTIFIER, "Expect class name.");
    Expr.Variable parent = null;

    if (snag(TokenType.LESS)) {
      parent = new Expr.Variable(
          mustEat(TokenType.IDENTIFIER, "Expect superclass name."));
    }

    mustEat(TokenType.LEFT_BRACE, "Expect '{' before class body.");
    List<Stmt.Function> methods = new ArrayList<>();
    List<Stmt.Function> statics = new ArrayList<>();

    while (!isIt(TokenType.RIGHT_BRACE) && !outOfToks()) {
      boolean isStatic = snag(TokenType.CLASS);
      Token method = mustEat(TokenType.IDENTIFIER, "Expect method name.");
      boolean getter = isIt(TokenType.LEFT_BRACE);

      Stmt.Function fn = new Stmt.Function(
          method, functionBody(getter), getter);

      (isStatic ? statics : methods).add(fn);
    }

    mustEat(TokenType.RIGHT_BRACE, "Expect '}' after class body.");
    return new Stmt.Class(name, parent, methods, statics);
  }

  // getters skip the parameter list
  Expr.Function functionBody(boolean getter) {
    List<Token> params = new ArrayList<>();

    if (!getter) {
      mustEat(TokenType.LEFT_PAREN, "Expect '(' before parameters.");

      if (!isIt(TokenType.RIGHT_PAREN)) {
        do {
          if (params.size() >= 255) {
            yell(peek(), "Can't have more than 255 parameters.");
          }
          params.add(mustEat(TokenType.IDENTIFIER, "Expect parameter name."));
        } while (snag(TokenType.COMMA));
      }

      mustEat(TokenType.RIGHT_PAREN, "Expect ')' after parameters.");
    }

    mustEat(TokenType.LEFT_BRACE, "Expect '{' before function body.");
    return new Expr.Function(params, block());
  }

  Stmt varDeclaration() {
    Token name = mustEat(TokenType.IDENTIFIER, "Expect variable name.");
    Expr val = snag(TokenType.EQUAL) ? expression() : null;
    mustEat(TokenType.SEMICOLON, "Expect ';' after variable declaration.");
    return new Stmt.Var(name, val);
  }

  Stmt statement() {
    if (snag(TokenType.FOR)) return forStatement();
    if (snag(TokenType.IF)) return ifStatement();
    if (snag(TokenType.WHILE)) return whileStatement();
    if (snag(TokenType.LEFT_BRACE)) return new Stmt.Block(block());

    if (snag(TokenType.BREAK)) {
      Token keyword = last();
      mustEat(TokenType.SEMICOLON, "Expect ';' after 'break'.");
      return new Stmt.Break(keyword);
    }

    if (snag(TokenType.PRINT)) {
      Expr val = expression();
      mustEat(TokenType.SEMICOLON, "Expect ';' after value.");
      return new Stmt.Print(val);
    }

    if (snag(TokenType.RETURN)) {
      Token keyword = last();
      Expr val = isIt(TokenType.SEMICOLON) ? null : expression();
      mustEat(TokenType.SEMICOLON, "Expect ';' after return value.");
      return new Stmt.Return(keyword, val);
    }

    return expressionStatement();
  }

  Stmt forStatement() {
    mustEat(TokenType.LEFT_PAREN, "Expect '(' after 'for'.");

    Stmt setup;
    if (snag(TokenType.SEMICOLON)) setup = null;
    else if (snag(TokenType.VAR)) setup = varDeclaration();
    else setup = expressionStatement();

    Expr cond = isIt(TokenType.SEMICOLON) ? null : expression();
    mustEat(TokenType.SEMICOLON, "Expect ';' after loop condition.");

    Expr step = isIt(TokenType.RIGHT_PAREN) ? null : expression();
    mustEat(TokenType.RIGHT_PAREN, "Expect ')' after for clauses.");

    Stmt body = statement();

    // turn for into while. break skips the update too
    if (step != null) {
      body = new Stmt.Block(Arrays.asList(body, new Stmt.Expression(step)));
    }
    if (cond == null) cond = new Expr.Literal(true);

    body = new Stmt.While(cond, body);
    if (setup != null) body = new Stmt.Block(Arrays.asList(setup, body));

    return body;
  }

  Stmt ifStatement() {
    mustEat(TokenType.LEFT_PAREN, "Expect '(' after 'if'.");
    Expr cond = expression();
    mustEat(TokenType.RIGHT_PAREN, "Expect ')' after condition.");

    Stmt yep = statement();
    Stmt nope = snag(TokenType.ELSE) ? statement() : null;
    return new Stmt.If(cond, yep, nope);
  }

  Stmt whileStatement() {
    mustEat(TokenType.LEFT_PAREN, "Expect '(' after 'while'.");
    Expr cond = expression();
    mustEat(TokenType.RIGHT_PAREN, "Expect ')' after condition.");
    return new Stmt.While(cond, statement());
  }

  Stmt expressionStatement() {
    Expr expr = expression();
    mustEat(TokenType.SEMICOLON, "Expect ';' after expression.");
    return new Stmt.Expression(expr);
  }

  List<Stmt> block() {
    List<Stmt> body = new ArrayList<>();

    while (!isIt(TokenType.RIGHT_BRACE) && !outOfToks()) {
      Stmt s = declaration();
      if (s != null) body.add(s);
    }

    mustEat(TokenType.RIGHT_BRACE, "Expect '}' after block.");
    return body;
  }

  Expr expression() { return comma(); }

  Expr comma() {
    Expr lhs = assignment();
    while (snag(TokenType.COMMA)) {
      Token op = last();
      lhs = new Expr.Binary(lhs, op, assignment());
    }
    return lhs;
  }

  Expr assignment() {
    Expr expr = conditional();

    if (snag(TokenType.EQUAL)) {
      Token equals = last();
      Expr val = assignment();

      if (expr instanceof Expr.Variable) {
        return new Expr.Assign(((Expr.Variable)expr).name, val);
      }

      if (expr instanceof Expr.Get) {
        Expr.Get get = (Expr.Get)expr;
        return new Expr.Set(get.obj, get.name, val);
      }

      yell(equals, "Invalid assignment target.");
    }

    return expr;
  }

  Expr conditional() {
    Expr cond = or();

    if (snag(TokenType.QUESTION)) {
      Expr yep = expression();
      mustEat(TokenType.COLON, "Expect ':' after then branch.");
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

  Expr unary() {
    if (snag(TokenType.BANG, TokenType.MINUS)) {
      return new Expr.Unary(last(), unary());
    }
    return call();
  }

  Expr call() {
    Expr expr = primary();

    while (true) {
      if (snag(TokenType.LEFT_PAREN)) {
        List<Expr> args = new ArrayList<>();

        if (!isIt(TokenType.RIGHT_PAREN)) {
          do {
            if (args.size() >= 255) {
              yell(peek(), "Can't have more than 255 arguments.");
            }
            args.add(assignment());   // commas here separate arguments
          } while (snag(TokenType.COMMA));
        }

        Token paren = mustEat(TokenType.RIGHT_PAREN, "Expect ')' after arguments.");
        expr = new Expr.Call(expr, paren, args);
      } else if (snag(TokenType.DOT)) {
        expr = new Expr.Get(expr,
            mustEat(TokenType.IDENTIFIER, "Expect property name after '.'."));
      } else {
        break;
      }
    }

    return expr;
  }

  Expr primary() {
    if (snag(TokenType.FALSE)) return new Expr.Literal(false);
    if (snag(TokenType.TRUE)) return new Expr.Literal(true);
    if (snag(TokenType.NIL)) return new Expr.Literal(null);
    if (snag(TokenType.NUMBER, TokenType.STRING)) return new Expr.Literal(last().val);
    if (snag(TokenType.IDENTIFIER)) return new Expr.Variable(last());
    if (snag(TokenType.THIS)) return new Expr.This(last());
    if (snag(TokenType.INNER)) return new Expr.Inner(last());
    if (snag(TokenType.FUN)) return functionBody(false);

    if (snag(TokenType.SUPER)) {
      throw yell(last(), "Use inner() instead of super in this version.");
    }

    if (snag(TokenType.LEFT_PAREN)) {
      Expr inside = expression();
      mustEat(TokenType.RIGHT_PAREN, "Expect ')' after expression.");
      return new Expr.Grouping(inside);
    }

    // earlier challenge: an operator with nothing on its left
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

  boolean isIt(TokenType kind) { return !outOfToks() && peek().kind == kind; }
  boolean nextIs(TokenType kind) {
    return spot + 1 < toks.size() && toks.get(spot + 1).kind == kind;
  }
  Token eat() { if (!outOfToks()) spot++; return last(); }
  boolean outOfToks() { return peek().kind == TokenType.EOF; }
  Token peek() { return toks.get(spot); }
  Token last() { return toks.get(spot - 1); }

  ParseError yell(Token tok, String msg) {
    Lox.error(tok, msg);
    return new ParseError();
  }

  void synchronize() {
    if (outOfToks()) return;
    eat();

    while (!outOfToks()) {
      if (last().kind == TokenType.SEMICOLON) return;

      switch (peek().kind) {
        case CLASS: case FUN: case VAR: case FOR: case IF:
        case WHILE: case PRINT: case RETURN: case BREAK:
          return;
        default:
          break;
      }

      eat();
    }
  }
}