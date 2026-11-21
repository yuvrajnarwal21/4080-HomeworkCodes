#ifndef clox_object_h
#define clox_object_h

#include "value.h"

typedef enum { OBJ_STRING } ObjType;

struct Obj {
  ObjType type;
  struct Obj* next;
};

typedef struct {
  Obj obj;
  int length;
  const char* borrowed;   // NULL means the characters live right below
  char chars[];
} ObjString;

#define IS_STRING(v) (IS_OBJ(v) && AS_OBJ(v)->type == OBJ_STRING)
#define AS_STRING(v) ((ObjString*)AS_OBJ(v))

const char* stringChars(const ObjString* string);
ObjString* copyString(const char* chars, int length);
ObjString* borrowString(const char* chars, int length);
ObjString* joinStrings(const ObjString* a, const ObjString* b);
const char* keepSource(const char* source);
void printObject(Value value);
void freeObjects(void);

#endif