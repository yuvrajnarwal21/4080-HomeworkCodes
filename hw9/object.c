#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "memory.h"
#include "object.h"
#include "table.h"

static Obj* objects = NULL;
static Table strings = {0};

typedef struct Source {
  struct Source* next;
  size_t length;
  char chars[];
} Source;

static Source* sources = NULL;

const char* stringChars(const ObjString* string) {
  return string->borrowed != NULL ? string->borrowed : string->chars;
}

static ObjString* allocateString(int length, const char* borrowed) {
  size_t extra = borrowed == NULL ? (size_t)length + 1 : 0;

  ObjString* string = reallocate(NULL, 0, sizeof(ObjString) + extra);

  string->obj.type = OBJ_STRING;
  string->obj.next = objects;
  objects = (Obj*)string;

  string->length = length;
  string->borrowed = borrowed;
  return string;
}

ObjString* copyString(const char* chars, int length) {
  Obj* found = tableFindString(&strings, chars, length);
  if (found != NULL) return (ObjString*)found;

  ObjString* string = allocateString(length, NULL);

  memcpy(string->chars, chars, (size_t)length);
  string->chars[length] = '\0';

  tableSet(&strings, OBJ_VAL(string), NIL_VAL);
  return string;
}

ObjString* borrowString(const char* chars, int length) {
  Obj* found = tableFindString(&strings, chars, length);
  if (found != NULL) return (ObjString*)found;

  ObjString* string = allocateString(length, chars);

  tableSet(&strings, OBJ_VAL(string), NIL_VAL);
  return string;
}

ObjString* joinStrings(const ObjString* a, const ObjString* b) {
  if (a->length > INT_MAX - b->length) {
    fprintf(stderr, "String is too long.\n");
    exit(1);
  }

  ObjString* string = allocateString(a->length + b->length, NULL);

  memcpy(string->chars, stringChars(a), (size_t)a->length);
  memcpy(string->chars + a->length, stringChars(b), (size_t)b->length);
  string->chars[string->length] = '\0';

  Obj* found = tableFindString(&strings, string->chars, string->length);

  if (found != NULL) {
    // already have this string, so discard the new copy
    objects = string->obj.next;
    reallocate(string, sizeof(ObjString) + (size_t)string->length + 1, 0);
    return (ObjString*)found;
  }

  tableSet(&strings, OBJ_VAL(string), NIL_VAL);
  return string;
}

const char* keepSource(const char* source) {
  size_t length = strlen(source);

  Source* saved = reallocate(NULL, 0, sizeof(Source) + length + 1);

  saved->next = sources;
  saved->length = length;
  memcpy(saved->chars, source, length + 1);

  sources = saved;
  return saved->chars;
}

void printObject(Value value) {
  ObjString* string = AS_STRING(value);
  fwrite(stringChars(string), 1, (size_t)string->length, stdout);
}

void freeObjects(void) {
  freeTable(&strings);

  while (objects != NULL) {
    Obj* next = objects->next;
    ObjString* string = (ObjString*)objects;

    size_t extra = string->borrowed == NULL ?
        (size_t)string->length + 1 : 0;

    reallocate(string, sizeof(ObjString) + extra, 0);
    objects = next;
  }

  // borrowed strings are gone, so their source text can go too
  while (sources != NULL) {
    Source* next = sources->next;

    reallocate(sources, sizeof(Source) + sources->length + 1, 0);
    sources = next;
  }
}