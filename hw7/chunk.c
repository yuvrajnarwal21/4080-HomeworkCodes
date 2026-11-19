#include <stdio.h>
#include <stdlib.h>

#include "chunk.h"
#include "memory.h"

void initChunk(Chunk* chunk) {
  chunk->count = 0;
  chunk->capacity = 0;
  chunk->code = NULL;

  chunk->lineCount = 0;
  chunk->lineCapacity = 0;
  chunk->lines = NULL;

  initValueArray(&chunk->constants);
}

void freeChunk(Chunk* chunk) {
  FREE_ARRAY(uint8_t, chunk->code, chunk->capacity);
  FREE_ARRAY(LineRun, chunk->lines, chunk->lineCapacity);

  freeValueArray(&chunk->constants);
  initChunk(chunk);
}

void writeChunk(Chunk* chunk, uint8_t byte, int line) {
  if (chunk->count == chunk->capacity) {
    int old = chunk->capacity;
    chunk->capacity = GROW_CAPACITY(old);

    chunk->code = GROW_ARRAY(uint8_t, chunk->code,
        old, chunk->capacity);
  }

  chunk->code[chunk->count++] = byte;

  // same line as the last byte? just extend that run
  if (chunk->lineCount > 0 &&
      chunk->lines[chunk->lineCount - 1].line == line) {
    chunk->lines[chunk->lineCount - 1].count++;
    return;
  }

  if (chunk->lineCount == chunk->lineCapacity) {
    int old = chunk->lineCapacity;
    chunk->lineCapacity = GROW_CAPACITY(old);

    chunk->lines = GROW_ARRAY(LineRun, chunk->lines,
        old, chunk->lineCapacity);
  }

  chunk->lines[chunk->lineCount++] = (LineRun){line, 1};
}

int getLine(const Chunk* chunk, int offset) {
  if (offset < 0 || offset >= chunk->count) return -1;

  for (int i = 0; i < chunk->lineCount; i++) {
    if (offset < chunk->lines[i].count) {
      return chunk->lines[i].line;
    }
    offset -= chunk->lines[i].count;
  }

  return -1;
}

int addConstant(Chunk* chunk, Value value) {
  writeValueArray(&chunk->constants, value);
  return chunk->constants.count - 1;
}

void writeConstant(Chunk* chunk, Value value, int line) {
  if (chunk->constants.count > 0xffffff) {
    fprintf(stderr, "Too many constants in one chunk.\n");
    exit(1);
  }

  int index = addConstant(chunk, value);

  if (index <= UINT8_MAX) {
    writeChunk(chunk, OP_CONSTANT, line);
    writeChunk(chunk, (uint8_t)index, line);
  } else {
    // low byte first. the VM and disassembler use the same order
    writeChunk(chunk, OP_CONSTANT_LONG, line);
    writeChunk(chunk, (uint8_t)(index & 0xff), line);
    writeChunk(chunk, (uint8_t)((index >> 8) & 0xff), line);
    writeChunk(chunk, (uint8_t)((index >> 16) & 0xff), line);
  }
}