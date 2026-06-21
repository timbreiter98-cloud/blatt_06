package filter.ast;

import filter.FilterParser;
import filter.ast.builder.AstBuilderPattern;
import filter.ast.builder.AstBuilderVisitor;
import filter.ast.builder.AstBuilders;
import filter.ast.nodes.Expr;
import filter.ast.printer.AstPrinter;
import java.util.function.Function;
import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

public class ApprovalTest {

  private static final String[] REPRESENTATIVE_QUERIES = {
    "artist == \"Beatles\"",
    "year == 1965",
    "artist == \"Beatles\" and year == 1965",
    "genre in (\"rock\", \"jazz\")",
    "not artist == \"Beatles\"",
    "(year <= 1990 or artist == \"Beatles\") and year > 1960",
    "genre in (\"rock\", \"jazz\") or year <= 1990 and not artist == \"Beatles\""
  };

  @Test
  void approvePatternBuilderOutput() {
    Approvals.verify(renderWithPatternBuilder());
  }

  @Test
  void approveVisitorBuilderOutput() {
    Approvals.verify(renderWithVisitorBuilder());
  }

  @Test
  void approveBothBuildersSideBySide() {
    var output =
        "PATTERN BUILDER\n"
            + renderWithPatternBuilder()
            + "VISITOR BUILDER\n"
            + renderWithVisitorBuilder();

    Approvals.verify(output);
  }

  private String renderWithPatternBuilder() {
    return render(new AstBuilderPattern()::translate);
  }

  private String renderWithVisitorBuilder() {
    return render(new AstBuilderVisitor()::translate);
  }

  private String render(Function<FilterParser.QueryContext, Expr> translator) {
    var sb = new StringBuilder();

    for (var query : REPRESENTATIVE_QUERIES) {
      var expr = AstBuilders.fromQuery(query, translator);

      sb.append(query)
          .append('\n')
          .append("=> ")
          .append(AstPrinter.toString(expr))
          .append('\n')
          .append('\n');
    }

    return sb.toString();
  }
}
