#include <stdio.h>

#include "debug.h"
#include "value.h"

static int simpleInstruction(const char* name, int offset) {
  printf("%s\n", name);
  return offset + 1;
}

static int constantInstruction(
    const char* name, Chunk* chunk, int offset, int bytes) {
  if (chunk->count - offset - 1 < bytes) {
    printf("%s <missing operand>\n", name);
    return chunk->count;
  }

  uint32_t index = 0;
  for (int i = 0; i < bytes; i++) {
    index |= (uint32_t)chunk->code[offset + 1 + i] << (8 * i);
  }

  printf("%-18s %6u '", name, (unsigned)index);

  if (index < (uint32_t)chunk->constants.count) {
    printValue(chunk->constants.values[index]);
  } else {
    printf("invalid constant");
  }

  printf("'\n");
  return offset + 1 + bytes;
}

static int jumpInstruction(const char* name, Chunk* chunk, int offset) {
  if (chunk->count - offset - 1 < 2) {
    printf("%s <missing operand>\n", name);
    return chunk->count;
  }

  int distance = (int)chunk->code[offset + 1] << 8 |
      chunk->code[offset + 2];

  printf("%-18s %4d -> %d\n", name, offset, offset + 3 + distance);
  return offset + 3;
}

void disassembleChunk(Chunk* chunk, const char* name) {
  printf("== %s ==\n", name);

  for (int offset = 0; offset < chunk->count;) {
    offset = disassembleInstruction(chunk, offset);
  }
}

int disassembleInstruction(Chunk* chunk, int offset) {
  printf("%04d ", offset);

  int line = getLine(chunk, offset);
  if (offset > 0 && line == getLine(chunk, offset - 1)) {
    printf("   | ");
  } else {
    printf("%4d ", line);
  }

  switch (chunk->code[offset]) {
    case OP_CONSTANT:
      return constantInstruction("OP_CONSTANT", chunk, offset, 1);
    case OP_CONSTANT_LONG:
      return constantInstruction("OP_CONSTANT_LONG", chunk, offset, 3);

    case OP_NIL: return simpleInstruction("OP_NIL", offset);
    case OP_TRUE: return simpleInstruction("OP_TRUE", offset);
    case OP_FALSE: return simpleInstruction("OP_FALSE", offset);
    case OP_EQUAL: return simpleInstruction("OP_EQUAL", offset);
    case OP_GREATER: return simpleInstruction("OP_GREATER", offset);
    case OP_LESS: return simpleInstruction("OP_LESS", offset);
    case OP_NOT: return simpleInstruction("OP_NOT", offset);
    case OP_POP: return simpleInstruction("OP_POP", offset);

    case OP_JUMP:
      return jumpInstruction("OP_JUMP", chunk, offset);
    case OP_JUMP_IF_FALSE:
      return jumpInstruction("OP_JUMP_IF_FALSE", chunk, offset);

    case OP_ADD:
      return simpleInstruction("OP_ADD", offset);
    case OP_SUBTRACT:
      return simpleInstruction("OP_SUBTRACT", offset);
    case OP_MULTIPLY:
      return simpleInstruction("OP_MULTIPLY", offset);
    case OP_DIVIDE:
      return simpleInstruction("OP_DIVIDE", offset);
    case OP_NEGATE:
      return simpleInstruction("OP_NEGATE", offset);
    case OP_RETURN:
      return simpleInstruction("OP_RETURN", offset);

    default:
      printf("Unknown opcode %u\n", (unsigned)chunk->code[offset]);
      return offset + 1;
  }
}