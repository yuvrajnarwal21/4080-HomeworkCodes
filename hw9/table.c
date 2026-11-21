#include <math.h>
#include <string.h>

#include "memory.h"
#include "object.h"
#include "table.h"

static uint32_t hashBytes(const unsigned char* bytes, size_t length) {
  uint32_t hash = 2166136261u;

  for (size_t i = 0; i < length; i++) {
    hash ^= bytes[i];
    hash *= 16777619u;
  }

  return hash;
}

uint32_t hashValue(Value value) {
  switch (value.type) {
    case VAL_NIL:
      return 0x9e3779b9u;

    case VAL_BOOL:
      return AS_BOOL(value) ? 0x85ebca6bu : 0xc2b2ae35u;

    case VAL_NUMBER: {
      double number = AS_NUMBER(value);

      if (isnan(number)) return 0x7fc00000u;
      if (number == 0) number = 0;   // +0 and -0 need the same hash

      return hashBytes((const unsigned char*)&number, sizeof(number));
    }

    case VAL_OBJ: {
      ObjString* string = AS_STRING(value);

      return hashBytes((const unsigned char*)stringChars(string),
          (size_t)string->length);
    }
  }

  return 0;
}

static bool sameKey(Value a, Value b) {
  // NaN keys share one slot, even though normal Lox NaN equality is false
  if (IS_NUMBER(a) && IS_NUMBER(b) &&
      isnan(AS_NUMBER(a)) && isnan(AS_NUMBER(b))) return true;

  return valuesEqual(a, b);
}

void initTable(Table* table) {
  table->count = 0;
  table->used = 0;
  table->capacity = 0;
  table->entries = NULL;
}

void freeTable(Table* table) {
  FREE_ARRAY(Entry, table->entries, table->capacity);
  initTable(table);
}

static Entry* findEntry(Entry* entries, int capacity, Value key) {
  uint32_t index = hashValue(key) & (uint32_t)(capacity - 1);
  Entry* deleted = NULL;

  for (;;) {
    Entry* entry = &entries[index];

    if (entry->state == ENTRY_EMPTY) {
      return deleted != NULL ? deleted : entry;
    }

    if (entry->state == ENTRY_DELETED) {
      if (deleted == NULL) deleted = entry;
    } else if (sameKey(entry->key, key)) {
      return entry;
    }

    index = (index + 1) & (uint32_t)(capacity - 1);
  }
}

static void resizeTable(Table* table, int capacity) {
  Entry* entries = GROW_ARRAY(Entry, NULL, 0, capacity);

  for (int i = 0; i < capacity; i++) {
    entries[i] = (Entry){NIL_VAL, NIL_VAL, ENTRY_EMPTY};
  }

  for (int i = 0; i < table->capacity; i++) {
    Entry* old = &table->entries[i];
    if (old->state != ENTRY_FULL) continue;

    *findEntry(entries, capacity, old->key) = *old;
  }

  FREE_ARRAY(Entry, table->entries, table->capacity);

  table->entries = entries;
  table->capacity = capacity;
  table->used = table->count;
}

bool tableGet(Table* table, Value key, Value* value) {
  if (table->count == 0) return false;

  Entry* entry = findEntry(table->entries, table->capacity, key);
  if (entry->state != ENTRY_FULL) return false;

  *value = entry->value;
  return true;
}

bool tableSet(Table* table, Value key, Value value) {
  if (table->capacity == 0) resizeTable(table, 8);

  Entry* entry = findEntry(table->entries, table->capacity, key);

  if (entry->state == ENTRY_FULL) {
    entry->value = value;
    return false;
  }

  // entries and tombstones together stay below the load limit
  if (table->used + 1 > table->capacity - table->capacity / 4) {
    int capacity = table->capacity;

    if (table->count + 1 > capacity - capacity / 4) {
      capacity = GROW_CAPACITY(capacity);
    }

    resizeTable(table, capacity);
    entry = findEntry(table->entries, table->capacity, key);
  }

  if (entry->state == ENTRY_EMPTY) table->used++;

  table->count++;
  *entry = (Entry){key, value, ENTRY_FULL};
  return true;
}

bool tableDelete(Table* table, Value key) {
  if (table->count == 0) return false;

  Entry* entry = findEntry(table->entries, table->capacity, key);
  if (entry->state != ENTRY_FULL) return false;

  *entry = (Entry){NIL_VAL, NIL_VAL, ENTRY_DELETED};
  table->count--;
  return true;
}

void tableAddAll(Table* from, Table* to) {
  if (from == to) return;

  for (int i = 0; i < from->capacity; i++) {
    Entry* entry = &from->entries[i];

    if (entry->state == ENTRY_FULL) {
      tableSet(to, entry->key, entry->value);
    }
  }
}

Obj* tableFindString(Table* table, const char* chars, int length) {
  if (table->count == 0) return NULL;

  uint32_t hash = hashBytes((const unsigned char*)chars, (size_t)length);
  uint32_t index = hash & (uint32_t)(table->capacity - 1);

  for (;;) {
    Entry* entry = &table->entries[index];

    if (entry->state == ENTRY_EMPTY) return NULL;

    if (entry->state == ENTRY_FULL && IS_STRING(entry->key)) {
      ObjString* string = AS_STRING(entry->key);

      if (string->length == length &&
          memcmp(stringChars(string), chars, (size_t)length) == 0) {
        return AS_OBJ(entry->key);
      }
    }

    index = (index + 1) & (uint32_t)(table->capacity - 1);
  }
}