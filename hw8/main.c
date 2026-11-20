#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "memory.h"
#include "vm.h"

static int exitCode(InterpretResult status) {
  if (status == INTERPRET_COMPILE_ERROR) return 65;
  if (status == INTERPRET_RUNTIME_ERROR) return 70;
  return 0;
}

// read a whole expression file, including its newlines
static char* readFile(const char* path) {
  FILE* file = fopen(path, "rb");
  if (file == NULL) {
    fprintf(stderr, "Could not open '%s'.\n", path);
    exit(74);
  }

  if (fseek(file, 0, SEEK_END) != 0) {
    fclose(file);
    exit(74);
  }

  long length = ftell(file);
  if (length < 0) {
    fclose(file);
    exit(74);
  }
  rewind(file);

  char* source = GROW_ARRAY(char, NULL, 0, (size_t)length + 1);
  size_t got = fread(source, 1, (size_t)length, file);
  fclose(file);

  if (got != (size_t)length) {
    fprintf(stderr, "Could not read '%s'.\n", path);
    FREE_ARRAY(char, source, (size_t)length + 1);
    exit(74);
  }

  source[got] = '\0';
  return source;
}

static void repl(void) {
  size_t capacity = 128;
  char* line = GROW_ARRAY(char, NULL, 0, capacity);

  for (;;) {
    printf("> ");
    fflush(stdout);

    size_t count = 0;
    int c;

    while ((c = getchar()) != '\n' && c != EOF) {
      if (count + 1 == capacity) {
        size_t old = capacity;
        capacity *= 2;
        line = GROW_ARRAY(char, line, old, capacity);
      }
      line[count++] = (char)c;
    }

    line[count] = '\0';
    if (count > 0) interpret(line);
    if (c == EOF) { printf("\n"); break; }
  }

  FREE_ARRAY(char, line, capacity);
}

int main(int argc, char** argv) {
  initVM();
  int status = 0;

  if (argc == 1) {
    repl();
  } else if (argc == 3 && strcmp(argv[1], "-e") == 0) {
    status = exitCode(interpret(argv[2]));
  } else if (argc == 2) {
    char* source = readFile(argv[1]);
    status = exitCode(interpret(source));
    FREE_ARRAY(char, source, strlen(source) + 1);
  } else {
    fprintf(stderr, "Usage: %s [file | -e expression]\n", argv[0]);
    status = 64;
  }

  freeVM();
  return status;
}