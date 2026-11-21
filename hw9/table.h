#ifndef clox_table_h
#define clox_table_h

#include "value.h"

typedef enum {
  ENTRY_EMPTY,
  ENTRY_FULL,
  ENTRY_DELETED
} EntryState;

typedef struct {
  Value key;
  Value value;
  EntryState state;
} Entry;

typedef struct {
  int count;      // actual entries
  int used;       // entries plus tombstones
  int capacity;
  Entry* entries;
} Table;

void initTable(Table* table);
void freeTable(Table* table);
bool tableGet(Table* table, Value key, Value* value);
bool tableSet(Table* table, Value key, Value value);
bool tableDelete(Table* table, Value key);
void tableAddAll(Table* from, Table* to);
Obj* tableFindString(Table* table, const char* chars, int length);
uint32_t hashValue(Value value);

#endif