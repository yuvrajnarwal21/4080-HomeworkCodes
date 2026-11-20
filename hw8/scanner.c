#include <string.h>

#include "common.h"
#include "scanner.h"

static struct {
  const char* start;
  const char* current;
  int line;
  int tokenLine;
} scanner;

void initScanner(const char* source) {
  scanner.start = source;
  scanner.current = source;
  scanner.line = 1;
  scanner.tokenLine = 1;
}

static bool done(void) { return *scanner.current == '\0'; }
static char advance(void) { return *scanner.current++; }
static char peek(void) { return *scanner.current; }
static char peekNext(void) { return done() ? '\0' : scanner.current[1]; }
static bool digit(char c) { return c >= '0' && c <= '9'; }

static bool alpha(char c) {
  return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
}

static bool match(char c) {
  if (peek() != c) return false;
  scanner.current++;
  return true;
}

static Token makeToken(TokenType type) {
  return (Token){type, scanner.start,
      (int)(scanner.current - scanner.start), scanner.tokenLine};
}

static Token errorToken(const char* msg) {
  return (Token){TOKEN_ERROR, msg, (int)strlen(msg), scanner.tokenLine};
}

static void skipWhitespace(void) {
  for (;;) {
    switch (peek()) {
      case ' ': case '\r': case '\t': advance(); break;
      case '\n': scanner.line++; advance(); break;
      case '/':
        if (peekNext() != '/') return;
        while (peek() != '\n' && !done()) advance();
        break;
      default: return;
    }
  }
}

static Token identifier(void) {
  while (alpha(peek()) || digit(peek())) advance();

  static const struct {
    const char* word;
    TokenType type;
  } keywords[] = {
    {"and", TOKEN_AND}, {"class", TOKEN_CLASS}, {"else", TOKEN_ELSE},
    {"false", TOKEN_FALSE}, {"for", TOKEN_FOR}, {"fun", TOKEN_FUN},
    {"if", TOKEN_IF}, {"nil", TOKEN_NIL}, {"or", TOKEN_OR},
    {"print", TOKEN_PRINT}, {"return", TOKEN_RETURN}, {"super", TOKEN_SUPER},
    {"this", TOKEN_THIS}, {"true", TOKEN_TRUE}, {"var", TOKEN_VAR},
    {"while", TOKEN_WHILE}
  };

  size_t length = (size_t)(scanner.current - scanner.start);
  for (size_t i = 0; i < sizeof(keywords) / sizeof(keywords[0]); i++) {
    if (strlen(keywords[i].word) == length &&
        memcmp(scanner.start, keywords[i].word, length) == 0) {
      return makeToken(keywords[i].type);
    }
  }
  return makeToken(TOKEN_IDENTIFIER);
}

static Token number(void) {
  while (digit(peek())) advance();

  if (peek() == '.' && digit(peekNext())) {
    advance();
    while (digit(peek())) advance();
  }
  return makeToken(TOKEN_NUMBER);
}

static Token string(void) {
  while (peek() != '"' && !done()) {
    if (peek() == '\n') scanner.line++;
    advance();
  }

  if (done()) return errorToken("Unterminated string.");
  advance();
  return makeToken(TOKEN_STRING);
}

Token scanToken(void) {
  skipWhitespace();
  scanner.start = scanner.current;
  scanner.tokenLine = scanner.line;

  if (done()) return makeToken(TOKEN_EOF);

  char c = advance();
  if (alpha(c)) return identifier();
  if (digit(c)) return number();

  switch (c) {
    case '(': return makeToken(TOKEN_LEFT_PAREN);
    case ')': return makeToken(TOKEN_RIGHT_PAREN);
    case '{': return makeToken(TOKEN_LEFT_BRACE);
    case '}': return makeToken(TOKEN_RIGHT_BRACE);
    case ',': return makeToken(TOKEN_COMMA);
    case '.': return makeToken(TOKEN_DOT);
    case '-': return makeToken(TOKEN_MINUS);
    case '+': return makeToken(TOKEN_PLUS);
    case ';': return makeToken(TOKEN_SEMICOLON);
    case '/': return makeToken(TOKEN_SLASH);
    case '*': return makeToken(TOKEN_STAR);
    case '?': return makeToken(TOKEN_QUESTION);
    case ':': return makeToken(TOKEN_COLON);
    case '!': return makeToken(match('=') ? TOKEN_BANG_EQUAL : TOKEN_BANG);
    case '=': return makeToken(match('=') ? TOKEN_EQUAL_EQUAL : TOKEN_EQUAL);
    case '<': return makeToken(match('=') ? TOKEN_LESS_EQUAL : TOKEN_LESS);
    case '>': return makeToken(match('=') ? TOKEN_GREATER_EQUAL : TOKEN_GREATER);
    case '"': return string();
    default: return errorToken("Unexpected character.");
  }
}