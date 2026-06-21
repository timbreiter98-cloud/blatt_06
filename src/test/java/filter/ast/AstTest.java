package filter.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;

import filter.ast.builder.AstBuilderPattern;
import filter.ast.builder.AstBuilderVisitor;
import filter.ast.builder.AstBuilders;
import filter.ast.nodes.CompOp;
import filter.ast.nodes.Expr;
import filter.ast.nodes.Value;
import filter.ast.printer.AstPrinter;
import java.util.List;
import org.junit.jupiter.api.Test;

public class AstTest {

  @Test
  void buildsSimpleComparisonWithPatternBuilder() {
    var expr = AstBuilders.fromQuery("artist == \"Beatles\"", new AstBuilderPattern()::translate);

    assertEquals(new Expr.Comparison("artist", CompOp.EQ, new Value.Str("Beatles")), expr);
  }

  @Test
  void buildsSimpleComparisonWithVisitorBuilder() {
    var expr = AstBuilders.fromQuery("year <= 1965", new AstBuilderVisitor()::translate);

    assertEquals(new Expr.Comparison("year", CompOp.LE, new Value.Num(1965)), expr);
  }

  @Test
  void buildsAndExpressionLeftAssociative() {
    var expr =
        AstBuilders.fromQuery(
            "year <= 1990 and artist == \"Beatles\" and year > 1960",
            new AstBuilderPattern()::translate);

    var expected =
        new Expr.And(
            new Expr.And(
                new Expr.Comparison("year", CompOp.LE, new Value.Num(1990)),
                new Expr.Comparison("artist", CompOp.EQ, new Value.Str("Beatles"))),
            new Expr.Comparison("year", CompOp.GT, new Value.Num(1960)));

    assertEquals(expected, expr);
  }

  @Test
  void respectsPrecedenceOfAndBeforeOr() {
    var expr =
        AstBuilders.fromQuery(
            "genre in (\"rock\", \"jazz\") or year <= 1990 and not artist == \"Beatles\"",
            new AstBuilderPattern()::translate);

    assertEquals(
        "((genre in (\"rock\", \"jazz\")) or ((year <= 1990) and (not (artist == \"Beatles\"))))",
        AstPrinter.toString(expr));
  }

  @Test
  void respectsParentheses() {
    var expr =
        AstBuilders.fromQuery(
            "(year <= 1990 or artist == \"Beatles\") and year > 1960",
            new AstBuilderPattern()::translate);

    assertEquals(
        "(((year <= 1990) or (artist == \"Beatles\")) and (year > 1960))",
        AstPrinter.toString(expr));
  }

  @Test
  void buildsInListExpression() {
    var expr =
        AstBuilders.fromQuery(
            "genre in (\"rock\", \"jazz\")", new AstBuilderVisitor()::translate);

    assertEquals(
        new Expr.InList("genre", List.of(new Value.Str("rock"), new Value.Str("jazz"))), expr);
  }

  @Test
  void visitorAndPatternBuilderProduceSameAst() {
    var query = "genre in (\"rock\", \"jazz\") or year <= 1990 and not artist == \"Beatles\"";
    var visitorExpr = AstBuilders.fromQuery(query, new AstBuilderVisitor()::translate);
    var patternExpr = AstBuilders.fromQuery(query, new AstBuilderPattern()::translate);

    assertEquals(patternExpr, visitorExpr);
  }

  @Test
  void simplifyRemovesDoubleNegation() {
    var expr =
        new Expr.Not(
            new Expr.Not(new Expr.Comparison("artist", CompOp.EQ, new Value.Str("Beatles"))));

    assertEquals(
        new Expr.Comparison("artist", CompOp.EQ, new Value.Str("Beatles")),
        AstBuilders.simplify(expr));
  }

  @Test
  void simplifyRemovesDuplicateAndExpression() {
    var comparison = new Expr.Comparison("year", CompOp.GE, new Value.Num(2000));

    assertEquals(comparison, AstBuilders.simplify(new Expr.And(comparison, comparison)));
  }

  @Test
  void printerEscapesStringValuesForRoundtrip() {
    var expr = new Expr.Comparison("title", CompOp.EQ, new Value.Str("A \"quoted\" title"));
    var printed = AstPrinter.toString(expr);
    var reparsed = AstBuilders.fromQuery(printed, new AstBuilderPattern()::translate);

    assertEquals(expr, reparsed);
  }
}
