package filter.ast.builder;

import filter.FilterBaseVisitor;
import filter.FilterParser;
import filter.ast.nodes.CompOp;
import filter.ast.nodes.Expr;
import filter.ast.nodes.Value;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;

public class AstBuilderVisitor extends FilterBaseVisitor<Void> {

  private final Deque<Expr> exprStack = new ArrayDeque<>();
  private final Deque<Value> valueStack = new ArrayDeque<>();

  public Expr translate(FilterParser.QueryContext ctx) {
    exprStack.clear();
    valueStack.clear();

    visit(ctx);

    if (exprStack.size() != 1) {
      throw new IllegalStateException("Expected exactly one expression on the stack");
    }

    return exprStack.pop();
  }

  @Override
  public Void visitQuery(FilterParser.QueryContext ctx) {
    visit(ctx.expr());
    return null;
  }

  @Override
  public Void visitExpr(FilterParser.ExprContext ctx) {
    visit(ctx.orExpr());
    return null;
  }

  @Override
  public Void visitOrExpr(FilterParser.OrExprContext ctx) {
    var parts = ctx.andExpr();
    visit(parts.getFirst());
    Expr result = exprStack.pop();

    for (int i = 1; i < parts.size(); i++) {
      visit(parts.get(i));
      var right = exprStack.pop();
      result = new Expr.Or(result, right);
    }

    exprStack.push(result);
    return null;
  }

  @Override
  public Void visitAndExpr(FilterParser.AndExprContext ctx) {
    var parts = ctx.notExpr();
    visit(parts.getFirst());
    Expr result = exprStack.pop();

    for (int i = 1; i < parts.size(); i++) {
      visit(parts.get(i));
      var right = exprStack.pop();
      result = new Expr.And(result, right);
    }

    exprStack.push(result);
    return null;
  }

  @Override
  public Void visitNotExpr(FilterParser.NotExprContext ctx) {
    if (ctx.NOT() != null) {
      visit(ctx.notExpr());
      exprStack.push(new Expr.Not(exprStack.pop()));
    } else {
      visit(ctx.primary());
    }

    return null;
  }

  @Override
  public Void visitPrimary(FilterParser.PrimaryContext ctx) {
    if (ctx.comparison() != null) {
      visit(ctx.comparison());
    } else {
      visit(ctx.expr());
    }

    return null;
  }

  @Override
  public Void visitComparison(FilterParser.ComparisonContext ctx) {
    var field = ctx.IDENTIFIER().getText();

    if (ctx.op != null) {
      visit(ctx.value);
      exprStack.push(
          new Expr.Comparison(field, CompOp.fromSymbol(ctx.op.getText()), valueStack.pop()));
    } else {
      var values = new ArrayList<Value>();

      for (var literal : ctx.literalList().literal()) {
        visit(literal);
        values.add(valueStack.pop());
      }

      exprStack.push(new Expr.InList(field, values));
    }

    return null;
  }

  @Override
  public Void visitLiteralList(FilterParser.LiteralListContext ctx) {
    for (var literal : ctx.literal()) {
      visit(literal);
    }

    return null;
  }

  @Override
  public Void visitLiteral(FilterParser.LiteralContext ctx) {
    if (ctx.STRING() != null) {
      valueStack.push(new Value.Str(unquote(ctx.STRING().getText())));
    } else {
      valueStack.push(new Value.Num(Integer.parseInt(ctx.NUMBER().getText())));
    }

    return null;
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
