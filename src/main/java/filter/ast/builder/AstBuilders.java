package filter.ast.builder;

import filter.FilterLexer;
import filter.FilterParser;
import filter.ast.nodes.Expr;
import java.util.function.Function;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

public class AstBuilders {

  public static Expr fromQuery(String query, Function<FilterParser.QueryContext, Expr> translator) {
    return simplify(translator.apply(parse(query)));
  }

  public static Expr simplify(Expr expr) {
    return switch (expr) {
      case Expr.And(var left, var right) -> {
        var simplifiedLeft = simplify(left);
        var simplifiedRight = simplify(right);

        if (simplifiedLeft.equals(simplifiedRight)) {
          yield simplifiedLeft;
        }

        yield new Expr.And(simplifiedLeft, simplifiedRight);
      }
      case Expr.Or(var left, var right) -> {
        var simplifiedLeft = simplify(left);
        var simplifiedRight = simplify(right);

        if (simplifiedLeft.equals(simplifiedRight)) {
          yield simplifiedLeft;
        }

        yield new Expr.Or(simplifiedLeft, simplifiedRight);
      }
      case Expr.Not(Expr.Not(var inner)) -> simplify(inner);
      case Expr.Not(var inner) -> new Expr.Not(simplify(inner));
      case Expr.Comparison comparison -> comparison;
      case Expr.InList inList -> inList;
    };
  }

  public static FilterParser.QueryContext parse(String query) {
    var charStream = CharStreams.fromString(query);
    var lexer = new FilterLexer(charStream);
    var tokens = new CommonTokenStream(lexer);
    var parser = new FilterParser(tokens);
    var context = parser.query();

    if (parser.getNumberOfSyntaxErrors() > 0) {
      throw new IllegalStateException("Syntax errors in query: " + query);
    }

    return context;
  }
}
