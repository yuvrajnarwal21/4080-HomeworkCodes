#include <assert.h>
#include <stdio.h>

#include "memory.h"
#include "vm.h"

VM vm;

void initVM(void) {
  vm.chunk = NULL;
  vm.ip = NULL;

  vm.stackCapacity = 8;
  vm.stack = GROW_ARRAY(Value, NULL, 0, vm.stackCapacity);
  vm.stackTop = vm.stack;

  vm.result = 0;
  vm.quiet = false;
}

void freeVM(void) {
  FREE_ARRAY(Value, vm.stack, vm.stackCapacity);

  vm.stack = NULL;
  vm.stackTop = NULL;
  vm.stackCapacity = 0;
  vm.chunk = NULL;
  vm.ip = NULL;
}

void push(Value value) {
  int used = (int)(vm.stackTop - vm.stack);

  if (used == vm.stackCapacity) {
    int old = vm.stackCapacity;
    vm.stackCapacity = GROW_CAPACITY(old);

    vm.stack = GROW_ARRAY(Value, vm.stack,
        old, vm.stackCapacity);

    // realloc may have moved the whole stack
    vm.stackTop = vm.stack + used;
  }

  *vm.stackTop++ = value;
}

Value pop(void) {
  assert(vm.stackTop > vm.stack);
  return *--vm.stackTop;
}

static InterpretResult runtimeError(const char* msg, int offset) {
  fprintf(stderr, "%s\n[line %d] in script\n",
      msg, getLine(vm.chunk, offset));

  vm.stackTop = vm.stack;
  return INTERPRET_RUNTIME_ERROR;
}

static InterpretResult run(void) {
#define REQUIRE(n) \
  do { \
    if (vm.stackTop - vm.stack < (n)) { \
      return runtimeError("Stack underflow.", offset); \
    } \
  } while (false)

#define BINARY_OP(op) \
  do { \
    REQUIRE(2); \
    double b = pop(); \
    double a = pop(); \
    push(a op b); \
  } while (false)

  for (;;) {
    int offset = (int)(vm.ip - vm.chunk->code);

    if (offset >= vm.chunk->count) {
      return runtimeError("Missing OP_RETURN.", offset - 1);
    }

    uint8_t instruction = *vm.ip++;

    switch (instruction) {
      case OP_CONSTANT:
      case OP_CONSTANT_LONG: {
        int bytes = instruction == OP_CONSTANT ? 1 : 3;

        if (vm.chunk->count - offset - 1 < bytes) {
          return runtimeError("Missing constant operand.", offset);
        }

        uint32_t index = 0;
        for (int i = 0; i < bytes; i++) {
          index |= (uint32_t)(*vm.ip++) << (8 * i);
        }

        if (index >= (uint32_t)vm.chunk->constants.count) {
          return runtimeError("Invalid constant index.", offset);
        }

        push(vm.chunk->constants.values[index]);
        break;
      }

      case OP_ADD:
        BINARY_OP(+);
        break;
      case OP_SUBTRACT:
        BINARY_OP(-);
        break;
      case OP_MULTIPLY:
        BINARY_OP(*);
        break;
      case OP_DIVIDE:
        BINARY_OP(/);
        break;

      case OP_NEGATE:
        REQUIRE(1);
#ifdef NEGATE_POP_PUSH
        push(-pop());   // original version, just for comparison
#else
        vm.stackTop[-1] = -vm.stackTop[-1];
#endif
        break;

      case OP_RETURN:
        REQUIRE(1);
        vm.result = pop();

        if (!vm.quiet) {
          printValue(vm.result);
          printf("\n");
        }

        return INTERPRET_OK;

      default:
        return runtimeError("Unknown opcode.", offset);
    }
  }

#undef REQUIRE
#undef BINARY_OP
}

InterpretResult interpret(Chunk* chunk) {
  vm.chunk = chunk;
  vm.ip = chunk->code;
  vm.stackTop = vm.stack;

  if (chunk->count == 0) {
    return runtimeError("Empty chunk.", 0);
  }

  return run();
}