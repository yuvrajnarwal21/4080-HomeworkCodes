#include <stdio.h>

#include "memory.h"
#include "value.h"

void initValueArray(ValueArray* array) {
  array->count = 0;
  array->capacity = 0;
  array->values = NULL;
}

void writeValueArray(ValueArray* array, Value value) {
  if (array->count == array->capacity) {
    int old = array->capacity;
    array->capacity = GROW_CAPACITY(old);

    array->values = GROW_ARRAY(Value, array->values,
        old, array->capacity);
  }

  array->values[array->count++] = value;
}

void freeValueArray(ValueArray* array) {
  FREE_ARRAY(Value, array->values, array->capacity);
  initValueArray(array);
}

void printValue(Value value) {
  printf("%g", value);
}