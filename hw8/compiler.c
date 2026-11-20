#include <stdio.h>
#include <stdlib.h>

#include "compiler.h"
#include "scanner.h"

static struct {
  Token current;
  Token previous;
  bool hadError;
  bool panicMode;
} parser;

static Chunk* currentChunk;

typedef enum {
  PREC_NONE, PREC_ASSIGNMENT, PREC_CONDITIONAL,
  PREC_OR, PREC_AND, PREC_EQUALITY, PREC_COMPARISON,
  PREC_TERM, PREC_FACTOR, PREC_UNARY, PREC_CALL, PREC_PRIMARY
} Precedence;

typedef void (*ParseFn)(void);

typedef struct {
  ParseFn prefix;
  ParseFn infix;
  Precedence precedence;
} ParseRule;

static void expression(void);
static void parsePrecedence(Precedence precedence);
static ParseRule* getRule(TokenType type);

static void errorAt(Token* token, const char* msg) {
  if (parser.panicMode) return;
  parser.panicMode = true;

  fprintf(stderr, "[line %d] Error", token->line);

  if (token->type == TOKEN_EOF) fprintf(stderr, " at end");
  else if (token->type != TOKEN_ERROR) {
    fprintf(stderr, " at '%.*s'", token->length, token->start);
  }

  fprintf(stderr, ": %s\n", msg);
  parser.hadError = true;
}

static void advance(void) {
  parser.previous = parser.current;

  for (;;) {
    parser.current = scanToken();
    if (parser.current.type != TOKEN_ERROR) break;
    errorAt(&parser.current, parser.current.start);
  }
}

static void consume(TokenType type, const char* msg) {
  if (parser.current.type == type) advance();
  else errorAt(&parser.current, msg);
}

static void emitAt(uint8_t byte, int line) {
  writeChunk(currentChunk, byte, line);
}

static void emitByte(uint8_t byte) {
  emitAt(byte, parser.previous.line);
}

static void number(void) {
  if (currentChunk->constants.count > 0xffffff) {
    errorAt(&parser.previous, "Too many constants in one chunk.");
    return;
  }

  double value = strtod(parser.previous.start, NULL);
  writeConstant(currentChunk, NUMBER_VAL(value), parser.previous.line);
}

static void grouping(void) {
  expression();
  consume(TOKEN_RIGHT_PAREN, "Expect ')' after expression.");
}

static void literal(void) {
  switch (parser.previous.type) {
    case TOKEN_FALSE: emitByte(OP_FALSE); break;
    case TOKEN_TRUE: emitByte(OP_TRUE); break;
    case TOKEN_NIL: emitByte(OP_NIL); break;
    default: return;
  }
}

static void unary(void) {
  Token op = parser.previous;
  parsePrecedence(PREC_UNARY);
  emitAt(op.type == TOKEN_MINUS ? OP_NEGATE : OP_NOT, op.line);
}

static void binary(void) {
  Token op = parser.previous;
  ParseRule* rule = getRule(op.type);
  parsePrecedence((Precedence)(rule->precedence + 1));

  switch (op.type) {
    case TOKEN_PLUS: emitAt(OP_ADD, op.line); break;
    case TOKEN_MINUS: emitAt(OP_SUBTRACT, op.line); break;
    case TOKEN_STAR: emitAt(OP_MULTIPLY, op.line); break;
    case TOKEN_SLASH: emitAt(OP_DIVIDE, op.line); break;
    case TOKEN_EQUAL_EQUAL: emitAt(OP_EQUAL, op.line); break;

    case TOKEN_BANG_EQUAL:
      emitAt(OP_EQUAL, op.line); emitAt(OP_NOT, op.line); break;

    case TOKEN_GREATER: emitAt(OP_GREATER, op.line); break;
    case TOKEN_LESS: emitAt(OP_LESS, op.line); break;

    case TOKEN_GREATER_EQUAL:
      emitAt(OP_LESS, op.line); emitAt(OP_NOT, op.line); break;

    case TOKEN_LESS_EQUAL:
      emitAt(OP_GREATER, op.line); emitAt(OP_NOT, op.line); break;

    default: return;
  }
}

// leave room for the distance. fill it in once we know where to land
static int emitJump(uint8_t op, int line) {
  emitAt(op, line);
  emitAt(0xff, line);
  emitAt(0xff, line);
  return currentChunk->count - 2;
}

static void patchJump(int offset) {
  int distance = currentChunk->count - offset - 2;

  if (distance > UINT16_MAX) {
    errorAt(&parser.previous, "Too much code to jump over.");
    return;
  }

  currentChunk->code[offset] = (uint8_t)(distance >> 8);
  currentChunk->code[offset + 1] = (uint8_t)(distance & 0xff);
}

// ch17 q3. right side uses the same precedence so it groups right to left
static void conditional(void) {
  int line = parser.previous.line;

  int otherwise = emitJump(OP_JUMP_IF_FALSE, line);
  emitAt(OP_POP, line);
  expression();
  consume(TOKEN_COLON, "Expect ':' after then branch.");

  int end = emitJump(OP_JUMP, line);
  patchJump(otherwise);
  emitAt(OP_POP, line);
  parsePrecedence(PREC_CONDITIONAL);
  patchJump(end);
}

// omitted entries are all zero: no prefix, no infix, PREC_NONE
static ParseRule rules[TOKEN_COUNT] = {
  [TOKEN_LEFT_PAREN]    = {grouping, NULL, PREC_NONE},
  [TOKEN_MINUS]         = {unary, binary, PREC_TERM},
  [TOKEN_PLUS]          = {NULL, binary, PREC_TERM},
  [TOKEN_SLASH]         = {NULL, binary, PREC_FACTOR},
  [TOKEN_STAR]          = {NULL, binary, PREC_FACTOR},
  [TOKEN_QUESTION]      = {NULL, conditional, PREC_CONDITIONAL},
  [TOKEN_BANG]          = {unary, NULL, PREC_NONE},
  [TOKEN_BANG_EQUAL]    = {NULL, binary, PREC_EQUALITY},
  [TOKEN_EQUAL_EQUAL]   = {NULL, binary, PREC_EQUALITY},
  [TOKEN_GREATER]       = {NULL, binary, PREC_COMPARISON},
  [TOKEN_GREATER_EQUAL] = {NULL, binary, PREC_COMPARISON},
  [TOKEN_LESS]          = {NULL, binary, PREC_COMPARISON},
  [TOKEN_LESS_EQUAL]    = {NULL, binary, PREC_COMPARISON},
  [TOKEN_NUMBER]        = {number, NULL, PREC_NONE},
  [TOKEN_FALSE]         = {literal, NULL, PREC_NONE},
  [TOKEN_TRUE]          = {literal, NULL, PREC_NONE},
  [TOKEN_NIL]           = {literal, NULL, PREC_NONE}
};

static ParseRule* getRule(TokenType type) {
  return &rules[type];
}

static void parsePrecedence(Precedence precedence) {
  advance();
  ParseFn prefix = getRule(parser.previous.type)->prefix;

  if (prefix == NULL) {
    errorAt(&parser.previous, "Expect expression.");
    return;
  }

  prefix();

  while (precedence <= getRule(parser.current.type)->precedence) {
    advance();
    getRule(parser.previous.type)->infix();
  }
}

static void expression(void) {
  parsePrecedence(PREC_ASSIGNMENT);
}

bool compile(const char* source, Chunk* chunk) {
  initScanner(source);
  currentChunk = chunk;
  parser.hadError = false;
  parser.panicMode = false;
  parser.current = (Token){TOKEN_EOF, source, 0, 1};
  parser.previous = parser.current;

  advance();
  expression();
  consume(TOKEN_EOF, "Expect end of expression.");
  emitByte(OP_RETURN);
  return !parser.hadError;
}