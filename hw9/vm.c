#include <assert.h>
#include <stdio.h>

#include "compiler.h"
#include "memory.h"
#include "vm.h"
#include "object.h"

VM vm;

void initVM(void) {
  vm.chunk = NULL;
  vm.ip = NULL;
  initTable(&vm.globals);

  vm.stackCapacity = 8;
  vm.stack = GROW_ARRAY(Value, NULL, 0, vm.stackCapacity);
  vm.stackTop = vm.stack;

  vm.result = NIL_VAL;
  vm.quiet = false;
}

void freeVM(void) {
  freeTable(&vm.globals);
  freeObjects();

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

static bool isFalsey(Value value) {
  return IS_NIL(value) || (IS_BOOL(value) && !AS_BOOL(value));
}

static InterpretResult run(void) {
#define REQUIRE(n) \
  do { \
    if (vm.stackTop - vm.stack < (n)) { \
      return runtimeError("Stack underflow.", offset); \
    } \
  } while (false)

#define BINARY_OP(wrap, op) \
  do { \
    REQUIRE(2); \
    if (!IS_NUMBER(vm.stackTop[-1]) || !IS_NUMBER(vm.stackTop[-2])) { \
      return runtimeError("Operands must be numbers.", offset); \
    } \
    double b = AS_NUMBER(pop()); \
    double a = AS_NUMBER(pop()); \
    push(wrap(a op b)); \
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
      case OP_DEFINE_GLOBAL:
      case OP_DEFINE_GLOBAL_LONG:
      case OP_GET_GLOBAL:
      case OP_GET_GLOBAL_LONG:
      case OP_SET_GLOBAL:
      case OP_SET_GLOBAL_LONG: {
        bool longIndex =
            instruction == OP_DEFINE_GLOBAL_LONG ||
            instruction == OP_GET_GLOBAL_LONG ||
            instruction == OP_SET_GLOBAL_LONG;

        int bytes = longIndex ? 3 : 1;

        if (vm.chunk->count - offset - 1 < bytes) {
          return runtimeError("Missing global operand.", offset);
        }

        uint32_t index = 0;
        for (int i = 0; i < bytes; i++) {
          index |= (uint32_t)(*vm.ip++) << (8 * i);
        }

        if (index >= (uint32_t)vm.chunk->constants.count) {
          return runtimeError("Invalid global name index.", offset);
        }

        Value name = vm.chunk->constants.values[index];

        if (!IS_STRING(name)) {
          return runtimeError("Global name must be a string.", offset);
        }

        bool define = instruction == OP_DEFINE_GLOBAL ||
            instruction == OP_DEFINE_GLOBAL_LONG;

        bool get = instruction == OP_GET_GLOBAL ||
            instruction == OP_GET_GLOBAL_LONG;

        if (define) {
          REQUIRE(1);
          tableSet(&vm.globals, name, vm.stackTop[-1]);
          pop();
        } else {
          Value old;

          if (!tableGet(&vm.globals, name, &old)) {
            ObjString* string = AS_STRING(name);
            char message[256];

            snprintf(message, sizeof(message),
                "Undefined variable '%.*s'.",
                string->length, stringChars(string));

            return runtimeError(message, offset);
          }

          if (get) {
            push(old);
          } else {
            REQUIRE(1);
            tableSet(&vm.globals, name, vm.stackTop[-1]);

            // leave the assigned value on the stack for chained assignments
          }
        }

        break;
      }
      case OP_ADD:
        REQUIRE(2);

        if (IS_STRING(vm.stackTop[-1]) &&
            IS_STRING(vm.stackTop[-2])) {
          ObjString* b = AS_STRING(pop());
          ObjString* a = AS_STRING(pop());
          push(OBJ_VAL(joinStrings(a, b)));
        } else if (IS_NUMBER(vm.stackTop[-1]) &&
                   IS_NUMBER(vm.stackTop[-2])) {
          BINARY_OP(NUMBER_VAL, +);
        } else {
          return runtimeError(
              "Operands must be two numbers or two strings.", offset);
        }
        break;
      case OP_SUBTRACT:
        BINARY_OP(NUMBER_VAL, -);
        break;
      case OP_MULTIPLY:
        BINARY_OP(NUMBER_VAL, *);
        break;
      case OP_DIVIDE:
        BINARY_OP(NUMBER_VAL, /);
        break;

      case OP_NIL: push(NIL_VAL); break;
      case OP_TRUE: push(BOOL_VAL(true)); break;
      case OP_FALSE: push(BOOL_VAL(false)); break;

      case OP_EQUAL: {
        REQUIRE(2);
        Value b = pop();
        Value a = pop();
        push(BOOL_VAL(valuesEqual(a, b)));
        break;
      }

      case OP_GREATER: BINARY_OP(BOOL_VAL, >); break;
      case OP_LESS: BINARY_OP(BOOL_VAL, <); break;

      case OP_NOT:
        REQUIRE(1);
        vm.stackTop[-1] = BOOL_VAL(isFalsey(vm.stackTop[-1]));
        break;

      case OP_NEGATE:
        REQUIRE(1);
        if (!IS_NUMBER(vm.stackTop[-1])) {
          return runtimeError("Operand must be a number.", offset);
        }
        vm.stackTop[-1] = NUMBER_VAL(-AS_NUMBER(vm.stackTop[-1]));
        break;

      case OP_POP:
        REQUIRE(1);
        pop();
        break;

      case OP_JUMP:
      case OP_JUMP_IF_FALSE: {
        if (vm.chunk->count - offset - 1 < 2) {
          return runtimeError("Missing jump operand.", offset);
        }

        int distance = (int)vm.ip[0] << 8 | vm.ip[1];
        vm.ip += 2;
        bool take = true;

        if (instruction == OP_JUMP_IF_FALSE) {
          REQUIRE(1);
          take = isFalsey(vm.stackTop[-1]);
        }

        if (take) {
          int remaining = vm.chunk->count - (int)(vm.ip - vm.chunk->code);

          if (distance >= remaining) {
            return runtimeError("Invalid jump target.", offset);
          }
          vm.ip += distance;
        }
        break;
      }

      case OP_PRINT:
        REQUIRE(1);
        vm.result = pop();

        if (!vm.quiet) {
          printValue(vm.result);
          printf("\n");
        }
        break;

      case OP_RETURN:
        return INTERPRET_OK;
    }
  }

#undef REQUIRE
#undef BINARY_OP
}

InterpretResult interpret(const char* source) {
  Chunk chunk;
  initChunk(&chunk);
  vm.stackTop = vm.stack;

  if (!compile(keepSource(source), &chunk)) {
        freeChunk(&chunk);
    return INTERPRET_COMPILE_ERROR;
  }

  vm.chunk = &chunk;
  vm.ip = chunk.code;
  InterpretResult status = run();

  freeChunk(&chunk);
  vm.chunk = NULL;
  vm.ip = NULL;
  return status;
}