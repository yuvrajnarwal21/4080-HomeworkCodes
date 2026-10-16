// Question 4: Scanner Lox:
// YSN: Chapter 4

public class Lox {
	//prev code
 
	//bumps line on newlines same as string(), nesting was less work than i thought since a comment has nothing to track but characters, one counter handles it
	private void blockComment() {
		int nesting = 1;
		while (nesting > 0) {
			if (isAtEnd()) {
				System.out.println("Unterminated block comment.");
				return;
			}
			if (peek() == '/' && peekNext() == '*') {
				advance();
				advance();
				nesting++;
			} else if (peek() == '*' && peekNext() == '/') {
				advance();
				advance();
				nesting--;
			} else {
				if (peek() == '\n') line++;
				advance();
			}
		}
	}
 
	//next code

    private boolean isAtEnd() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    private char peek() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    private void advance() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    private char peekNext() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
 