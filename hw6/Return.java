// carries the answer back out of a function
class Return extends RuntimeException {
  final Token keyword;
  final Object val;

  Return(Token keyword, Object val) {
    super(null, null, false, false);
    this.keyword = keyword;
    this.val = val;
  }
}