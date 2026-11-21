#ifndef clox_vm_h
#define clox_vm_h

#include "chunk.h"
#include "table.h"

typedef struct {
  Chunk* chunk;
  uint8_t* ip;

  Value* stack;
  Value* stackTop;
  int stackCapacity;

  Table globals;
  Value result;
  bool quiet;
} VM;

typedef enum {
  INTERPRET_OK,
  INTERPRET_COMPILE_ERROR,
  INTERPRET_RUNTIME_ERROR
} InterpretResult;

extern VM vm;

void initVM(void);
void freeVM(void);
void push(Value value);
Value pop(void);
InterpretResult interpret(const char* source);

#endif