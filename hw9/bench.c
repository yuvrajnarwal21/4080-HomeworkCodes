#include <assert.h>
#include <math.h>
#include <stdio.h>
#include <time.h>

#include "bench.h"
#include "memory.h"
#include "object.h"
#include "table.h"

static volatile double checksum = 0;

static void testTable(void) {
  Table table;
  initTable(&table);
  Value value;

  assert(tableSet(&table, NIL_VAL, NUMBER_VAL(10)));
  assert(tableSet(&table, BOOL_VAL(false), NUMBER_VAL(20)));
  assert(tableSet(&table, BOOL_VAL(true), NUMBER_VAL(30)));
  assert(tableSet(&table, NUMBER_VAL(0), NUMBER_VAL(40)));
  assert(table.count == 4);

  assert(tableGet(&table, NIL_VAL, &value) && AS_NUMBER(value) == 10);
  assert(tableGet(&table, BOOL_VAL(false), &value) && AS_NUMBER(value) == 20);
  assert(tableGet(&table, BOOL_VAL(true), &value) && AS_NUMBER(value) == 30);

  assert(!tableSet(&table, NUMBER_VAL(-0.0), NUMBER_VAL(50)));
  assert(tableGet(&table, NUMBER_VAL(0), &value) && AS_NUMBER(value) == 50);

  assert(tableSet(&table, NUMBER_VAL(NAN), NUMBER_VAL(60)));
  assert(tableGet(&table, NUMBER_VAL(NAN), &value) && AS_NUMBER(value) == 60);

  ObjString* a = copyString("hello", 5);
  ObjString* b = borrowString("hello", 5);

  assert(a == b);
  assert(joinStrings(copyString("he", 2), copyString("llo", 3)) == a);
  assert(tableSet(&table, OBJ_VAL(a), NUMBER_VAL(70)));
  assert(tableGet(&table, OBJ_VAL(b), &value) && AS_NUMBER(value) == 70);

  for (int i = 1; i <= 2000; i++) {
    tableSet(&table, NUMBER_VAL(i), NUMBER_VAL(i * 2));
  }

  for (int i = 1; i <= 2000; i++) {
    assert(tableGet(&table, NUMBER_VAL(i), &value));
    assert(AS_NUMBER(value) == i * 2);
  }

  for (int i = 1; i <= 2000; i += 2) {
    assert(tableDelete(&table, NUMBER_VAL(i)));
    assert(!tableGet(&table, NUMBER_VAL(i), &value));
  }

  for (int i = 1; i <= 2000; i += 2) {
    assert(tableSet(&table, NUMBER_VAL(i), NUMBER_VAL(i * 3)));
  }

  for (int i = 1; i <= 2000; i++) {
    assert(tableGet(&table, NUMBER_VAL(i), &value));
    assert(AS_NUMBER(value) == i * (i % 2 ? 3 : 2));
  }

  Table copy;
  initTable(&copy);
  tableAddAll(&table, &copy);
  assert(copy.count == table.count);

  for (int i = 0; i < table.capacity; i++) {
    Entry* entry = &table.entries[i];
    if (entry->state != ENTRY_FULL) continue;

    assert(tableGet(&copy, entry->key, &value));
    assert(valuesEqual(entry->value, value));
  }

  freeTable(&copy);
  freeTable(&table);
  puts("table tests passed");
}

static void report(const char* name, int count,
    long operations, clock_t start) {
  double seconds = (double)(clock() - start) / CLOCKS_PER_SEC;

  printf("%-24s n=%-6d ops=%-8ld %.6f seconds\n",
      name, count, operations, seconds);
}

static void benchKeys(int count, bool strings) {
  Value* keys = GROW_ARRAY(Value, NULL, 0, count * 2);

  // build keys before timing
  for (int i = 0; i < count * 2; i++) {
    if (strings) {
      char text[80];

      int length = snprintf(text, sizeof(text),
          "shared_prefix_for_string_key_%08d", i);

      keys[i] = OBJ_VAL(copyString(text, length));
    } else {
      keys[i] = NUMBER_VAL(i);
    }
  }

  Table table;
  initTable(&table);

  clock_t start = clock();

  for (int i = 0; i < count; i++) {
    tableSet(&table, keys[i], NUMBER_VAL(i));
  }

  report(strings ? "string insert + grow" : "number insert + grow",
      count, count, start);

  const int rounds = 20;
  double sum = 0;
  Value value;

  start = clock();

  for (int r = 0; r < rounds; r++) {
    for (int i = 0; i < count; i++) {
      bool found = tableGet(&table, keys[i], &value);
      assert(found);
      sum += AS_NUMBER(value);
    }
  }

  checksum += sum;

  report(strings ? "string hits" : "number hits",
      count, (long)count * rounds, start);

  long misses = 0;
  start = clock();

  for (int r = 0; r < rounds; r++) {
    for (int i = count; i < count * 2; i++) {
      if (!tableGet(&table, keys[i], &value)) misses++;
    }
  }

  assert(misses == (long)count * rounds);
  checksum += misses;

  report(strings ? "string misses" : "number misses",
      count, (long)count * rounds, start);

  start = clock();

  for (int r = 0; r < rounds; r++) {
    for (int i = 0; i < count; i++) {
      int old = (r % 2 == 0) ? i : i + count;
      int next = (r % 2 == 0) ? i + count : i;

      bool removed = tableDelete(&table, keys[old]);
      bool added = tableSet(&table, keys[next], NUMBER_VAL(i));

      assert(removed && added);
    }
  }

  assert(table.count == count);

  report(strings ? "string delete + insert" : "number delete + insert",
      count, (long)count * rounds * 2, start);

  freeTable(&table);
  FREE_ARRAY(Value, keys, count * 2);
}

static void benchCollisions(void) {
  Table table;
  initTable(&table);

  Value keys[128];
  int found = 0;

  // force these keys to start in the same bucket
  for (int i = 0; found < 128; i++) {
    Value key = NUMBER_VAL(i);
    if ((hashValue(key) & 255u) == 0) keys[found++] = key;
  }

  for (int i = 0; i < 128; i++) {
    tableSet(&table, keys[i], NUMBER_VAL(i));
  }

  assert(table.capacity == 256);

  double sum = 0;
  clock_t start = clock();

  for (int r = 0; r < 1000; r++) {
    for (int i = 0; i < 128; i++) {
      Value value;
      bool exists = tableGet(&table, keys[i], &value);

      assert(exists);
      sum += AS_NUMBER(value);
    }
  }

  checksum += sum;
  report("colliding number hits", 128, 128000, start);

  assert(tableDelete(&table, keys[0]));

  for (int i = 1; i < 128; i++) {
    Value value;
    assert(tableGet(&table, keys[i], &value));
    assert(AS_NUMBER(value) == i);
  }

  freeTable(&table);
}

void runTableBenchmarks(void) {
  testTable();

  benchKeys(128, false);
  benchKeys(32768, false);
  benchKeys(128, true);
  benchKeys(32768, true);
  benchCollisions();

  printf("checksum: %.0f\n", checksum);
}