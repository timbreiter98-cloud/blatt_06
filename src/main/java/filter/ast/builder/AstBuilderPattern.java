package filter.ast.builder;

import filter.FilterParser;
import filter.ast.nodes.CompOp;
import filter.ast.nodes.Expr;
import filter.ast.nodes.Value;
import java.util.ArrayList;
import java.util.List;

public class AstBuilderPattern {

  public Expr translate(FilterParser.QueryContext ctx) {
    return buildExpr(ctx.expr());
  }

  private Expr buildExpr(FilterParser.ExprContext ctx) {
    return buildOrExpr(ctx.orExpr());
  }

  private Expr buildOrExpr(FilterParser.OrExprContext ctx) {
    var parts = ctx.andExpr();
    Expr result = buildAndExpr(parts.getFirst());

    for (int i = 1; i < parts.size(); i++) {
      result = new Expr.Or(result, buildAndExpr(parts.get(i)));
    }

    return result;
  }

  private Expr buildAndExpr(FilterParser.AndExprContext ctx) {
    var parts = ctx.notExpr();
    Expr result = buildNotExpr(parts.getFirst());

    for (int i = 1; i < parts.size(); i++) {
      result = new Expr.And(result, buildNotExpr(parts.get(i)));
    }

    return result;
  }

  private Expr buildNotExpr(FilterParser.NotExprContext ctx) {
    if (ctx.NOT() != null) {
      return new Expr.Not(buildNotExpr(ctx.notExpr()));
    }

    return buildPrimary(ctx.primary());
  }

  private Expr buildPrimary(FilterParser.PrimaryContext ctx) {
    if (ctx.comparison() != null) {
      return buildComparison(ctx.comparison());
    }

    return buildExpr(ctx.expr());
  }

  private Expr buildComparison(FilterParser.ComparisonContext ctx) {
    var field = ctx.IDENTIFIER().getText();

    if (ctx.op != null) {
      return new Expr.Comparison(
          field, CompOp.fromSymbol(ctx.op.getText()), buildLiteral(ctx.value));
    }

    return new Expr.InList(field, buildLiteralList(ctx.literalList()));
  }

  private List<Value> buildLiteralList(FilterParser.LiteralListContext ctx) {
    var values = new ArrayList<Value>();

    for (var literal : ctx.literal()) {
      values.add(buildLiteral(literal));
    }

    return values;
  }

  private Value buildLiteral(FilterParser.LiteralContext ctx) {
    if (ctx.STRING() != null) {
      return new Value.Str(unquote(ctx.STRING().getText()));
    }

    return new Value.Num(Integer.parseInt(ctx.NUMBER().getText()));
  }

  private String unquote(String text) {
    var inner = text.substring(1, text.length() - 1);
    var result = new StringBuilder();
    boolean escaped = false;

    for (int i = 0; i < inner.length(); i++) {
      char c = inner.charAt(i);

      if (escaped) {
        result.append(c);
        escaped = false;
      } else if (c == '\\') {
        escaped = true;
      } else {
        result.append(c);
      }
    }

    if (escaped) {
      result.append('\\');
    }

    return result.toString();
  }
}
