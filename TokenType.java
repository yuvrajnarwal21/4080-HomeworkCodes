// all the kinds of tokens. that's it, just a list
enum TokenType {
  // 1 char
  LEFT_PAREN, RIGHT_PAREN, LEFT_BRACE, RIGHT_BRACE, COMMA, DOT, MINUS, PLUS,
  SEMICOLON, SLASH, STAR, QUESTION, COLON,   // ? and : are new, for  a ? b : c
  // 1 or 2 chars
  BANG, BANG_EQUAL, EQUAL, EQUAL_EQUAL, GREATER, GREATER_EQUAL, LESS, LESS_EQUAL,
  // stuff with a value
  IDENTIFIER, STRING, NUMBER,
  // words lox owns
  AND, CLASS, ELSE, FALSE, FUN, FOR, IF, NIL, OR,
  PRINT, RETURN, SUPER, THIS, TRUE, VAR, WHILE,
  EOF   // the end
}
