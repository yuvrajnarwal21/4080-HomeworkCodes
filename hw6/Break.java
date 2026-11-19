// jumps out of the nearest loop without a java stack trace
class Break extends RuntimeException {
  Break() {
    super(null, null, false, false);
  }
}