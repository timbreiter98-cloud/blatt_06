package filter.ast.printer;

import filter.ast.nodes.Expr;
import filter.ast.nodes.Value;
import java.util.stream.Collectors;

public class AstPrinter {

  public static String toString(Expr expr) {
    return printExpr(expr);
  }

  private static String printExpr(Expr expr) {
    return switch (expr) {
      case Expr.And(var left, var right) ->
          "(" + printExpr(left) + " and " + printExpr(right) + ")";
      case Expr.Or(var left, var right) ->
          "(" + printExpr(left) + " or " + printExpr(right) + ")";
      case Expr.Not(var inner) -> "(not " + printExpr(inner) + ")";
      case Expr.Comparison(var field, var op, var value) ->
          "(" + field + " " + op + " " + printValue(value) + ")";
      case Expr.InList(var field, var values) ->
          "("
              + field
              + " in ("
              + values.stream().map(AstPrinter::printValue).collect(Collectors.joining(", "))
              + "))";
    };
  }

  private static String printValue(Value value) {
    return switch (value) {
      case Value.Str(var text) -> "\"" + escape(text) + "\"";
      case Value.Num(var number) -> Integer.toString(number);
    };
  }

  private static String escape(String text) {
    return text.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
