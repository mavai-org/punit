package org.mavai.punit.decl.internal.path;

import java.util.List;

/**
 * The RFC 9535 abstract syntax: segments of selectors, and the filter
 * expression grammar. Construction happens only in
 * {@link JsonPathParser}; evaluation only in {@link JsonPathEvaluator}.
 */
final class JsonPathAst {

    private JsonPathAst() {}

    /** One query segment: child or descendant, holding its selectors. */
    record Segment(boolean descendant, List<Selector> selectors) {}

    sealed interface Selector
            permits NameSelector, WildcardSelector, IndexSelector, SliceSelector, FilterSelector {}

    record NameSelector(String name) implements Selector {}

    record WildcardSelector() implements Selector {}

    record IndexSelector(long index) implements Selector {}

    record SliceSelector(Long start, Long end, long step) implements Selector {}

    record FilterSelector(LogicalExpr expression) implements Selector {}

    // ── Filter expressions ────────────────────────────────────────

    sealed interface LogicalExpr permits Or, And, Not, Comparison, ExistenceTest, FunctionTest {}

    record Or(List<LogicalExpr> operands) implements LogicalExpr {}

    record And(List<LogicalExpr> operands) implements LogicalExpr {}

    record Not(LogicalExpr operand) implements LogicalExpr {}

    record Comparison(ComparableExpr left, ComparisonOp op, ComparableExpr right)
            implements LogicalExpr {}

    /** A filter query used as an existence test. */
    record ExistenceTest(Query query) implements LogicalExpr {}

    /** A LogicalType function used as a test. */
    record FunctionTest(FunctionCall call) implements LogicalExpr {}

    enum ComparisonOp { EQ, NE, LT, LE, GT, GE }

    sealed interface ComparableExpr permits Literal, SingularQuery, FunctionCall {}

    /** A literal value: String, Number, Boolean, or null (wrapped). */
    record Literal(Object value) implements ComparableExpr {}

    /** A singular query — name and index segments only. */
    record SingularQuery(boolean relative, List<Segment> segments) implements ComparableExpr {}

    /** A general filter query (possibly non-singular). */
    record Query(boolean relative, List<Segment> segments) {}

    /** A function call, typed per the RFC's function extension system. */
    record FunctionCall(Function function, List<Object> arguments) implements ComparableExpr {}

    /** Argument slots hold: Literal, SingularQuery, Query, or FunctionCall. */
    enum Function {
        LENGTH(ReturnType.VALUE),
        COUNT(ReturnType.VALUE),
        MATCH(ReturnType.LOGICAL),
        SEARCH(ReturnType.LOGICAL),
        VALUE(ReturnType.VALUE);

        final ReturnType returns;

        Function(ReturnType returns) {
            this.returns = returns;
        }
    }

    enum ReturnType { VALUE, LOGICAL }
}
