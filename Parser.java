import java.util.List;

/*
 * the parser. takes the tokens and builds a tree.
 *
 * the rules (top = weakest, bottom = strongest):
 *   expression  -> comma
 *   comma       -> conditional ( "," conditional )*
 *   conditional -> equality ( "?" expression ":" conditional )?
 *   equality    -> comparison ( ( "!=" | "==" ) comparison )*
 *   comparison  -> term ( ( ">" | ">=" | "<" | "<=" ) term )*
 *   term        -> factor ( ( "-" | "+" ) factor )*
 *   factor      -> unary ( ( "/" | "*" ) unary )*
 *   unary       -> ( "!" | "-" ) unary | primary
 *   primary     -> NUMBER | STRING | "true" | "false" | "nil" | "(" expression ")"
 *                | ( "!=" | "==" ) equality                <- these 4 are
 *                | ( ">" | ">=" | "<" | "<=" ) comparison  <- error rules,
 *                | ( "+" ) term                            <- for when the left
 *                | ( "/" | "*" ) factor                    <- side is missing
 *
 * one rule = one method. each one does its own level, and asks the next one down for the rest.
 */
class Parser {
  static class ParseError extends RuntimeException {}   // we throw this to bail out fast

  final List<Token> toks;
  int spot = 0;   // the next token we haven't eaten

  Parser(List<Token> toks) { this.toks = toks; }

  // start here. if it breaks, we already said why, so just give back null
  Expr parse() {
    try { return expression(); }
    catch (ParseError err) { return null; }
  }

  Expr expression() { return comma(); }

  // challenge 1: comma. weakest one of all, goes left to right: a, b, c = (a, b), c
  // we just reuse Binary for it. later: run the left, throw it away, keep the right.
  // careful later: foo(1, 2) would read as foo((1, 2)). when we add calls, read each arg with conditional()
  Expr comma() {
    Expr lhs = conditional();
    while (snag(TokenType.COMMA)) { Token op = last(); lhs = new Expr.Binary(lhs, op, conditional()); }
    return lhs;
  }

  // challenge 2: a ? b : c
  //   middle part uses expression(), so anything goes in there (? and : act like brackets)
  //   right part calls conditional() again, so it goes right to left: a ? b : c ? d : e = a ? b : (c ? d : e)
  Expr conditional() {
    Expr cond = equality();
    if (snag(TokenType.QUESTION)) {
      Expr yep = expression();
      mustEat(TokenType.COLON, "Expect ':' after then branch of conditional expression.");
      cond = new Expr.Conditional(cond, yep, conditional());
    }
    return cond;
  }

  // these 4 are all the same idea: get the left side, then keep grabbing "op right" and stacking it on.
  // stacking onto the left is what makes them go left to right
  Expr equality() {
    Expr lhs = comparison();
    while (snag(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL)) { Token op = last(); lhs = new Expr.Binary(lhs, op, comparison()); }
    return lhs;
  }

  Expr comparison() {
    Expr lhs = term();
    while (snag(TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL)) {
      Token op = last(); lhs = new Expr.Binary(lhs, op, term());
    }
    return lhs;
  }

  Expr term() {
    Expr lhs = factor();
    while (snag(TokenType.MINUS, TokenType.PLUS)) { Token op = last(); lhs = new Expr.Binary(lhs, op, factor()); }
    return lhs;
  }

  Expr factor() {
    Expr lhs = unary();
    while (snag(TokenType.SLASH, TokenType.STAR)) { Token op = last(); lhs = new Expr.Binary(lhs, op, unary()); }
    return lhs;
  }

  // ! or - in front? eat it and read the rest (so !!x works). if not, go to primary
  Expr unary() {
    if (snag(TokenType.BANG, TokenType.MINUS)) { Token op = last(); return new Expr.Unary(op, unary()); }
    return primary();
  }

  // the bottom. plain values and ( stuff )
  Expr primary() {
    if (snag(TokenType.FALSE)) return new Expr.Literal(false);
    if (snag(TokenType.TRUE))  return new Expr.Literal(true);
    if (snag(TokenType.NIL))   return new Expr.Literal(null);
    if (snag(TokenType.NUMBER, TokenType.STRING)) return new Expr.Literal(last().val);
    if (snag(TokenType.LEFT_PAREN)) {
      Expr inside = expression();
      mustEat(TokenType.RIGHT_PAREN, "Expect ')' after expression.");
      return new Expr.Grouping(inside);
    }

    // challenge 3: an op with nothing on its left, like "== 3" or "* 2".
    // say it's wrong (no throw, we know what happened), then read the right side and toss it.
    // we call the SAME level, so "+ a - b + c" eats the +, then term() eats all of "a - b + c".
    // no "-" here because "-x" is fine, unary() already took it
    if (snag(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL)) { yell(last(), "Missing left-hand operand."); equality(); return null; }
    if (snag(TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL)) {
      yell(last(), "Missing left-hand operand."); comparison(); return null;
    }
    if (snag(TokenType.PLUS))                    { yell(last(), "Missing left-hand operand."); term();   return null; }
    if (snag(TokenType.SLASH, TokenType.STAR))   { yell(last(), "Missing left-hand operand."); factor(); return null; }

    throw yell(peek(), "Expect expression.");   // nothing fits. we're lost, bail
  }

  // ---- token helpers ----
  boolean snag(TokenType... kinds) {   // is it one of these? then eat it and say yes
    for (TokenType k : kinds) if (isIt(k)) { eat(); return true; }
    return false;
  }
  Token mustEat(TokenType kind, String msg) {   // it HAS to be this, or it's an error
    if (isIt(kind)) return eat();
    throw yell(peek(), msg);
  }
  boolean isIt(TokenType kind) { return !outOfToks() && peek().kind == kind; }   // just look, don't eat
  Token eat()        { if (!outOfToks()) spot++; return last(); }
  boolean outOfToks(){ return peek().kind == TokenType.EOF; }
  Token peek()       { return toks.get(spot); }
  Token last()       { return toks.get(spot - 1); }   // the one we just ate

  // ---- errors ----
  // says the error and hands back a ParseError. "throw yell()" = bail. just "yell()" = keep going
  ParseError yell(Token tok, String msg) { Lox.error(tok, msg); return new ParseError(); }

  // skip tokens till we're probably at the next statement. not used yet, no statements till ch 8
  void synchronize() {
    eat();
    while (!outOfToks()) {
      if (last().kind == TokenType.SEMICOLON) return;
      switch (peek().kind) { case CLASS: case FUN: case VAR: case FOR: case IF: case WHILE: case PRINT: case RETURN: return; }
      eat();
    }
  }
}
