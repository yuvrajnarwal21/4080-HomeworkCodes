#ifndef clox_chunk_h
#define clox_chunk_h

#include "common.h"
#include "value.h"

typedef enum {
  OP_CONSTANT,
  OP_CONSTANT_LONG,
  OP_ADD,
  OP_SUBTRACT,
  OP_MULTIPLY,
  OP_DIVIDE,
  OP_NEGATE,
  OP_RETURN
} OpCode;

// one entry for a whole run of bytes on the same source line
// count includes operand bytes too
typedef struct {
  int line;
  int count;
} LineRun;

typedef struct {
  int count;
  int capacity;
  uint8_t* code;

  ValueArray constants;

  int lineCount;
  int lineCapacity;
  LineRun* lines;
} Chunk;

void initChunk(Chunk* chunk);
void freeChunk(Chunk* chunk);
void writeChunk(Chunk* chunk, uint8_t byte, int line);
int addConstant(Chunk* chunk, Value value);
void writeConstant(Chunk* chunk, Value value, int line);
int getLine(const Chunk* chunk, int offset);

#endif