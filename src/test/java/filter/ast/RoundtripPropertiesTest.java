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
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Provide;
import net.jqwik.api.Property;

public class RoundtripPropertiesTest {

  @Property
  boolean patternBuilderRoundtrip(@ForAll("simpleQueries") String query) {
    var first = AstBuilders.fromQuery(query, new AstBuilderPattern()::translate);
    var printed = AstPrinter.toString(first);
    var second = AstBuilders.fromQuery(printed, new AstBuilderPattern()::translate);

    return first.equals(second);
  }

  @Property
  boolean visitorBuilderRoundtrip(@ForAll("simpleQueries") String query) {
    var first = AstBuilders.fromQuery(query, new AstBuilderVisitor()::translate);
    var printed = AstPrinter.toString(first);
    var second = AstBuilders.fromQuery(printed, new AstBuilderVisitor()::translate);

    return first.equals(second);
  }

  @Property
  boolean visitorAndPatternBuilderProduceSameAst(@ForAll("simpleQueries") String query) {
    var patternExpr = AstBuilders.fromQuery(query, new AstBuilderPattern()::translate);
    var visitorExpr = AstBuilders.fromQuery(query, new AstBuilderVisitor()::translate);

    return patternExpr.equals(visitorExpr);
  }

  @Property
  boolean mixedBuilderRoundtripPatternToVisitor(@ForAll("simpleQueries") String query) {
    var patternExpr = AstBuilders.fromQuery(query, new AstBuilderPattern()::translate);
    var printed = AstPrinter.toString(patternExpr);
    var visitorExpr = AstBuilders.fromQuery(printed, new AstBuilderVisitor()::translate);

    return patternExpr.equals(visitorExpr);
  }

  @Property
  boolean simplifyIsIdempotent(@ForAll("expressions") Expr expr) {
    assertEquals(AstBuilders.simplify(expr), AstBuilders.simplify(AstBuilders.simplify(expr)));
    return true;
  }

  @Property
  boolean doubleNegationSimplifiesToOriginal(@ForAll("expressions") Expr expr) {
    assertEquals(
        AstBuilders.simplify(expr), AstBuilders.simplify(new Expr.Not(new Expr.Not(expr))));
    return true;
  }

  @Property
  boolean andWithSameExpressionSimplifiesToExpression(@ForAll("expressions") Expr expr) {
    assertEquals(AstBuilders.simplify(expr), AstBuilders.simplify(new Expr.And(expr, expr)));
    return true;
  }

  @Property
  boolean orWithSameExpressionSimplifiesToExpression(@ForAll("expressions") Expr expr) {
    assertEquals(AstBuilders.simplify(expr), AstBuilders.simplify(new Expr.Or(expr, expr)));
    return true;
  }

  @Provide
  Arbitrary<String> fields() {
    return Arbitraries.of("title", "artist", "genre", "year");
  }

  @Provide
  Arbitrary<String> stringLiterals() {
    return Arbitraries.strings()
        .withChars("abcxyz")
        .ofMinLength(1)
        .ofMaxLength(5)
        .map(text -> "\"" + text + "\"");
  }

  @Provide
  Arbitrary<String> numberLiterals() {
    return Arbitraries.integers().between(1900, 2025).map(Object::toString);
  }

  @Provide
  Arbitrary<String> comparisons() {
    Arbitrary<String> ops = Arbitraries.of("==", "!=", "<", "<=", ">", ">=");

    Arbitrary<String> stringComparison =
        Combinators.combine(fields(), ops, stringLiterals())
            .as((field, op, literal) -> field + " " + op + " " + literal);

    Arbitrary<String> numberComparison =
        Combinators.combine(fields(), ops, numberLiterals())
            .as((field, op, literal) -> field + " " + op + " " + literal);

    Arbitrary<String> inListComparison =
        Combinators.combine(fields(), stringLiterals().list().ofMinSize(1).ofMaxSize(3))
            .as((field, values) -> field + " in (" + String.join(", ", values) + ")");

    return Arbitraries.oneOf(stringComparison, numberComparison, inListComparison);
  }

  @Provide
  Arbitrary<String> simpleQueries() {
    Arbitrary<String> atom = comparisons();
    Arbitrary<String> prefixedNot = atom.map(query -> "not " + query);
    Arbitrary<String> parenthesized = atom.map(query -> "(" + query + ")");
    Arbitrary<String> simple = Arbitraries.oneOf(atom, prefixedNot, parenthesized);
    Arbitrary<String> connector = Arbitraries.of(" and ", " or ");

    Arbitrary<String> binary =
        Combinators.combine(simple, connector, simple)
            .as((left, op, right) -> left + op + right);

    Arbitrary<String> ternary =
        Combinators.combine(simple, connector, simple, connector, simple)
            .as((left, op1, middle, op2, right) -> left + op1 + middle + op2 + right);

    return Arbitraries.oneOf(simple, binary, ternary);
  }

  @Provide
  Arbitrary<Value> values() {
    Arbitrary<Value> strings =
        Arbitraries.strings()
            .withChars("abcxyz")
            .ofMinLength(1)
            .ofMaxLength(5)
            .map(text -> (Value) new Value.Str(text));
    Arbitrary<Value> numbers =
        Arbitraries.integers().between(1900, 2025).map(number -> (Value) new Value.Num(number));

    return Arbitraries.oneOf(strings, numbers);
  }

  @Provide
  Arbitrary<Expr> expressions() {
    return expressions(3);
  }

  private Arbitrary<Expr> expressions(int depth) {
    var leaf = leafExpressions();

    if (depth == 0) {
      return leaf;
    }

    var smaller = expressions(depth - 1);
    Arbitrary<Expr> andExpr =
        Combinators.combine(smaller, smaller)
            .as((left, right) -> (Expr) new Expr.And(left, right));
    Arbitrary<Expr> orExpr =
        Combinators.combine(smaller, smaller)
            .as((left, right) -> (Expr) new Expr.Or(left, right));
    Arbitrary<Expr> notExpr = smaller.map(inner -> (Expr) new Expr.Not(inner));

    return Arbitraries.oneOf(leaf, andExpr, orExpr, notExpr);
  }

  private Arbitrary<Expr> leafExpressions() {
    Arbitrary<Expr> comparison =
        Combinators.combine(fields(), Arbitraries.of(CompOp.values()), values())
            .as((field, op, value) -> (Expr) new Expr.Comparison(field, op, value));

    Arbitrary<Expr> inList =
        Combinators.combine(fields(), values().list().ofMinSize(1).ofMaxSize(3))
            .as((field, values) -> (Expr) new Expr.InList(field, List.copyOf(values)));

    return Arbitraries.oneOf(comparison, inList);
  }
}
