#include <stdio.h>
#include <stdlib.h>

#include "compiler.h"
#include "object.h"
#include "scanner.h"
#include "table.h"

static struct {
  Token current;
  Token previous;
  bool hadError;
  bool panicMode;
} parser;

static Chunk* currentChunk;
static Table nameConstants;

typedef enum {
  PREC_NONE, PREC_ASSIGNMENT, PREC_CONDITIONAL,
  PREC_OR, PREC_AND, PREC_EQUALITY, PREC_COMPARISON,
  PREC_TERM, PREC_FACTOR, PREC_UNARY, PREC_CALL, PREC_PRIMARY
} Precedence;

typedef void (*ParseFn)(bool canAssign);

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

static bool match(TokenType type) {
  if (parser.current.type != type) return false;
  advance();
  return true;
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

// same low-byte-first format as our long constants
static void emitIndex(uint8_t shortOp, uint8_t longOp,
    int index, int line) {
  if (index <= UINT8_MAX) {
    emitAt(shortOp, line);
    emitAt((uint8_t)index, line);
  } else {
    emitAt(longOp, line);
    emitAt((uint8_t)(index & 0xff), line);
    emitAt((uint8_t)((index >> 8) & 0xff), line);
    emitAt((uint8_t)((index >> 16) & 0xff), line);
  }
}

static int makeConstant(Value value) {
  if (currentChunk->constants.count > 0xffffff) {
    errorAt(&parser.previous, "Too many constants in one chunk.");
    return 0;
  }

  return addConstant(currentChunk, value);
}

static void emitConstant(Value value) {
  int index = makeConstant(value);
  emitIndex(OP_CONSTANT, OP_CONSTANT_LONG, index, parser.previous.line);
}

// q1: each global name gets one constant slot in this chunk
static int identifierConstant(Token name) {
  Value key = OBJ_VAL(copyString(name.start, name.length));
  Value existing;

  if (tableGet(&nameConstants, key, &existing)) {
    return (int)AS_NUMBER(existing);
  }

  int index = makeConstant(key);
  tableSet(&nameConstants, key, NUMBER_VAL(index));
  return index;
}

static void number(bool canAssign) {
  (void)canAssign;
  emitConstant(NUMBER_VAL(strtod(parser.previous.start, NULL)));
}

static void string(bool canAssign) {
  (void)canAssign;

  emitConstant(OBJ_VAL(borrowString(
      parser.previous.start + 1, parser.previous.length - 2)));
}

static void grouping(bool canAssign) {
  (void)canAssign;
  expression();
  consume(TOKEN_RIGHT_PAREN, "Expect ')' after expression.");
}

static void literal(bool canAssign) {
  (void)canAssign;

  switch (parser.previous.type) {
    case TOKEN_FALSE: emitByte(OP_FALSE); break;
    case TOKEN_TRUE: emitByte(OP_TRUE); break;
    case TOKEN_NIL: emitByte(OP_NIL); break;
    default: return;
  }
}

static void variable(bool canAssign) {
  Token name = parser.previous;
  int index = identifierConstant(name);

  if (canAssign && match(TOKEN_EQUAL)) {
    expression();
    emitIndex(OP_SET_GLOBAL, OP_SET_GLOBAL_LONG, index, name.line);
  } else {
    emitIndex(OP_GET_GLOBAL, OP_GET_GLOBAL_LONG, index, name.line);
  }
}

static void unary(bool canAssign) {
  (void)canAssign;
  Token op = parser.previous;

  parsePrecedence(PREC_UNARY);
  emitAt(op.type == TOKEN_MINUS ? OP_NEGATE : OP_NOT, op.line);
}

static void binary(bool canAssign) {
  (void)canAssign;
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
      emitAt(OP_EQUAL, op.line);
      emitAt(OP_NOT, op.line);
      break;

    case TOKEN_GREATER: emitAt(OP_GREATER, op.line); break;
    case TOKEN_LESS: emitAt(OP_LESS, op.line); break;

    case TOKEN_GREATER_EQUAL:
      emitAt(OP_LESS, op.line);
      emitAt(OP_NOT, op.line);
      break;

    case TOKEN_LESS_EQUAL:
      emitAt(OP_GREATER, op.line);
      emitAt(OP_NOT, op.line);
      break;

    default: return;
  }
}

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

static void conditional(bool canAssign) {
  (void)canAssign;
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
  [TOKEN_IDENTIFIER]   = {variable, NULL, PREC_NONE},
  [TOKEN_NUMBER]        = {number, NULL, PREC_NONE},
  [TOKEN_STRING]        = {string, NULL, PREC_NONE},
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

  bool canAssign = precedence <= PREC_ASSIGNMENT;
  prefix(canAssign);

  while (precedence <= getRule(parser.current.type)->precedence) {
    advance();
    getRule(parser.previous.type)->infix(canAssign);
  }

  if (canAssign && match(TOKEN_EQUAL)) {
    errorAt(&parser.previous, "Invalid assignment target.");
  }
}

static void expression(void) {
  parsePrecedence(PREC_ASSIGNMENT);
}

static void varDeclaration(void) {
  consume(TOKEN_IDENTIFIER, "Expect variable name.");
  Token name = parser.previous;
  int index = identifierConstant(name);

  if (match(TOKEN_EQUAL)) expression();
  else emitByte(OP_NIL);

  consume(TOKEN_SEMICOLON, "Expect ';' after variable declaration.");
  emitIndex(OP_DEFINE_GLOBAL, OP_DEFINE_GLOBAL_LONG, index, name.line);
}

static void statement(void) {
  if (match(TOKEN_PRINT)) {
    expression();
    consume(TOKEN_SEMICOLON, "Expect ';' after value.");
    emitByte(OP_PRINT);
  } else {
    expression();
    consume(TOKEN_SEMICOLON, "Expect ';' after expression.");
    emitByte(OP_POP);
  }
}

static void synchronize(void) {
  parser.panicMode = false;

  while (parser.current.type != TOKEN_EOF) {
    if (parser.previous.type == TOKEN_SEMICOLON) return;

    if (parser.current.type == TOKEN_VAR ||
        parser.current.type == TOKEN_PRINT) return;

    advance();
  }
}

bool compile(const char* source, Chunk* chunk) {
  initScanner(source);
  initTable(&nameConstants);
  currentChunk = chunk;

  parser.hadError = false;
  parser.panicMode = false;
  parser.current = (Token){TOKEN_EOF, source, 0, 1};
  parser.previous = parser.current;
  advance();

  while (!match(TOKEN_EOF)) {
    if (match(TOKEN_VAR)) varDeclaration();
    else statement();

    if (parser.panicMode) synchronize();
  }

  emitByte(OP_RETURN);

#ifdef DEBUG_CONSTANTS
  if (!parser.hadError) {
    printf("constant slots: %d\n", chunk->constants.count);
  }
#endif

  freeTable(&nameConstants);
  return !parser.hadError;
}