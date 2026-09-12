class RpnPrinter implements Expr.Visitor<String> {
	//...
 
	String print(Expr expr) {
		return expr.accept(this);
	}
 
	String visitBinaryExpr(Expr.Binary expr) {
		return expr.left.accept(this) + " " +
			expr.right.accept(this) + " " + expr.operator.lexeme;
	}
 
	String visitGroupingExpr(Expr.Grouping expr) {
		return expr.expression.accept(this);
	}
 
	String visitLiteralExpr(Expr.Literal expr) {
		return expr.value.toString();
	}
 
	String visitUnaryExpr(Expr.Unary expr) {
		String operator = expr.operator.lexeme;
		if (expr.operator.type == TokenType.MINUS) {
			//can't reuse "-" for unary and binary in rpn
			operator = "~";
		}
		return expr.right.accept(this) + " " + operator;
	}
 
	//...
}