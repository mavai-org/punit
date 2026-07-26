package org.mavai.punit.decl.internal.path;

import java.util.List;
import org.mavai.punit.decl.internal.path.JsonPathAst.Segment;

/**
 * A compiled RFC 9535 JSONPath query — punit-decl's own engine, private
 * to the module. Compilation is eager (a selection expression that does
 * not parse is a load-time refusal); selection walks the parsed JSON
 * data model (maps, lists, strings, numbers, booleans, nulls) and
 * returns the selected values in visit order.
 */
public final class CompiledJsonPath {

    private final String expression;
    private final List<Segment> segments;

    CompiledJsonPath(String expression, List<Segment> segments) {
        this.expression = expression;
        this.segments = List.copyOf(segments);
    }

    /** Compiles an expression; a malformed one raises {@link PathSyntaxException}. */
    public static CompiledJsonPath compile(String expression) {
        return JsonPathParser.parse(expression);
    }

    /** The values the query selects from the document, in visit order. */
    public List<Object> select(Object document) {
        return new JsonPathEvaluator(document).evaluate(segments, document);
    }

    /** The source expression. */
    public String expression() {
        return expression;
    }

    @Override
    public String toString() {
        return expression;
    }
}
