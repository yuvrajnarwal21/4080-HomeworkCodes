#include <assert.h>
#include <stdio.h>
#include <string.h>
#include <time.h>

#include "chunk.h"
#include "debug.h"
#include "vm.h"

// finish a test, check its answer, then reset the chunk
static void finish(Chunk* chunk, const char* name, double expected) {
  writeChunk(chunk, OP_RETURN, 1);
  printf("%s = ", name);

  InterpretResult status = interpret(chunk);
  assert(status == INTERPRET_OK);

  double diff = vm.result - expected;
  assert(diff > -0.000001 && diff < 0.000001);
  assert(vm.stackTop == vm.stack);

  freeChunk(chunk);
  initChunk(chunk);
}

static void testLines(void) {
  Chunk chunk;
  initChunk(&chunk);

  int lines[] = {10, 10, 10, 20, 20, 10};

  for (int i = 0; i < 6; i++) {
    writeChunk(&chunk, OP_RETURN, lines[i]);
  }

  for (int i = 0; i < 6; i++) {
    assert(getLine(&chunk, i) == lines[i]);
  }

  assert(getLine(&chunk, -1) == -1);
  assert(getLine(&chunk, 6) == -1);
  assert(chunk.lineCount == 3);

  printf("line encoding: 3 runs for 6 bytes\n");
  freeChunk(&chunk);
}

static void testConstants(void) {
  Chunk chunk;
  initChunk(&chunk);

  for (int i = 0; i < 255; i++) addConstant(&chunk, i);

  writeConstant(&chunk, 255, 8);
  writeConstant(&chunk, 256, 8);
  writeChunk(&chunk, OP_ADD, 8);
  writeChunk(&chunk, OP_RETURN, 9);

  assert(chunk.code[0] == OP_CONSTANT);
  assert(chunk.code[1] == 255);
  assert(chunk.code[2] == OP_CONSTANT_LONG);
  assert(chunk.code[3] == 0);
  assert(chunk.code[4] == 1);
  assert(chunk.code[5] == 0);

  for (int i = 0; i < 7; i++) {
    assert(getLine(&chunk, i) == 8);
  }
  assert(getLine(&chunk, 7) == 9);

  disassembleChunk(&chunk, "short and long constants");

  InterpretResult status = interpret(&chunk);
  assert(status == INTERPRET_OK && vm.result == 511);

  freeChunk(&chunk);

  // make sure the third index byte works too
  initChunk(&chunk);

  for (int i = 0; i < 65536; i++) addConstant(&chunk, i);
  writeConstant(&chunk, 42, 1);

  assert(chunk.code[1] == 0);
  assert(chunk.code[2] == 0);
  assert(chunk.code[3] == 1);

  finish(&chunk, "constant index 65536", 42);
  freeChunk(&chunk);
}

static void testArithmetic(void) {
  Chunk chunk;
  initChunk(&chunk);

#define NUM(n) writeConstant(&chunk, (n), 1)
#define OP(code) writeChunk(&chunk, (code), 1)

  // ch15 q1
  NUM(1);
  NUM(2);
  OP(OP_MULTIPLY);
  NUM(3);
  OP(OP_ADD);
  finish(&chunk, "1 * 2 + 3", 5);

  NUM(1);
  NUM(2);
  NUM(3);
  OP(OP_MULTIPLY);
  OP(OP_ADD);
  finish(&chunk, "1 + 2 * 3", 7);

  NUM(3);
  NUM(2);
  OP(OP_SUBTRACT);
  NUM(1);
  OP(OP_SUBTRACT);
  finish(&chunk, "3 - 2 - 1", 0);

  NUM(1);
  NUM(2);
  NUM(3);
  OP(OP_MULTIPLY);
  OP(OP_ADD);
  NUM(4);
  NUM(5);
  OP(OP_NEGATE);
  OP(OP_DIVIDE);
  OP(OP_SUBTRACT);
  finish(&chunk, "1 + 2 * 3 - 4 / -5", 7.8);

  // ch15 q2: 4 - 3 * -2, without OP_NEGATE
  NUM(4);
  NUM(3);
  NUM(0);
  NUM(2);
  OP(OP_SUBTRACT);
  OP(OP_MULTIPLY);
  OP(OP_SUBTRACT);
  finish(&chunk, "without negate", 10);

  // same expression, without OP_SUBTRACT
  NUM(4);
  NUM(3);
  NUM(2);
  OP(OP_NEGATE);
  OP(OP_MULTIPLY);
  OP(OP_NEGATE);
  OP(OP_ADD);
  finish(&chunk, "without subtract", 10);

#undef NUM
#undef OP

  freeChunk(&chunk);
}

static void testStack(void) {
  Chunk chunk;
  initChunk(&chunk);

  // push everything first, so the stack really has to grow
  for (int i = 0; i < 1024; i++) {
    writeConstant(&chunk, 1, 1);
  }

  for (int i = 1; i < 1024; i++) {
    writeChunk(&chunk, OP_ADD, 1);
  }

  finish(&chunk, "1024 values on the stack", 1024);
  assert(vm.stackCapacity >= 1024);

  printf("stack capacity: %d\n", vm.stackCapacity);
  freeChunk(&chunk);
}

static void benchmark(void) {
  Chunk chunk;
  initChunk(&chunk);

  writeConstant(&chunk, 1, 1);

  for (int i = 0; i < 200000; i++) {
    writeChunk(&chunk, OP_NEGATE, 1);
  }

  writeChunk(&chunk, OP_RETURN, 1);
  vm.quiet = true;

  // warm up before timing
  InterpretResult status = interpret(&chunk);
  assert(status == INTERPRET_OK);

  for (int sample = 1; sample <= 5; sample++) {
    clock_t start = clock();

    for (int round = 0; round < 200; round++) {
      status = interpret(&chunk);
      assert(status == INTERPRET_OK && vm.result == 1);
    }

    double seconds = (double)(clock() - start) / CLOCKS_PER_SEC;
    printf("sample %d: %.6f seconds\n", sample, seconds);
  }

  vm.quiet = false;
  freeChunk(&chunk);
}

int main(int argc, char** argv) {
  initVM();

  if (argc == 2 && strcmp(argv[1], "--bench") == 0) {
    benchmark();
  } else if (argc == 1) {
    testLines();
    testConstants();
    testArithmetic();
    testStack();
    printf("all tests passed\n");
  } else {
    fprintf(stderr, "Usage: %s [--bench]\n", argv[0]);
    freeVM();
    return 64;
  }

  freeVM();
  return 0;
}