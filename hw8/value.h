#ifndef clox_value_h
#define clox_value_h

#include "common.h"

// the tag tells us which part of the union to read
typedef enum { VAL_BOOL, VAL_NIL, VAL_NUMBER } ValueType;

typedef struct {
  ValueType type;
  union {
    bool boolean;
    double number;
  } as;
} Value;

#define BOOL_VAL(v) ((Value){VAL_BOOL, {.boolean = (v)}})
#define NIL_VAL ((Value){VAL_NIL, {.number = 0}})
#define NUMBER_VAL(v) ((Value){VAL_NUMBER, {.number = (v)}})

#define IS_BOOL(v) ((v).type == VAL_BOOL)
#define IS_NIL(v) ((v).type == VAL_NIL)
#define IS_NUMBER(v) ((v).type == VAL_NUMBER)
#define AS_BOOL(v) ((v).as.boolean)
#define AS_NUMBER(v) ((v).as.number)

typedef struct {
  int count;
  int capacity;
  Value* values;
} ValueArray;

void initValueArray(ValueArray* array);
void writeValueArray(ValueArray* array, Value value);
void freeValueArray(ValueArray* array);
void printValue(Value value);
bool valuesEqual(Value a, Value b);

#endif