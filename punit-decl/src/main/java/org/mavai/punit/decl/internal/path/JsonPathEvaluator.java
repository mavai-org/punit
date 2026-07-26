package org.mavai.punit.decl.internal.path;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.mavai.punit.decl.internal.path.JsonPathAst.And;
import org.mavai.punit.decl.internal.path.JsonPathAst.ComparableExpr;
import org.mavai.punit.decl.internal.path.JsonPathAst.Comparison;
import org.mavai.punit.decl.internal.path.JsonPathAst.ExistenceTest;
import org.mavai.punit.decl.internal.path.JsonPathAst.FilterSelector;
import org.mavai.punit.decl.internal.path.JsonPathAst.FunctionCall;
import org.mavai.punit.decl.internal.path.JsonPathAst.FunctionTest;
import org.mavai.punit.decl.internal.path.JsonPathAst.IndexSelector;
import org.mavai.punit.decl.internal.path.JsonPathAst.Literal;
import org.mavai.punit.decl.internal.path.JsonPathAst.LogicalExpr;
import org.mavai.punit.decl.internal.path.JsonPathAst.NameSelector;
import org.mavai.punit.decl.internal.path.JsonPathAst.Not;
import org.mavai.punit.decl.internal.path.JsonPathAst.Or;
import org.mavai.punit.decl.internal.path.JsonPathAst.Query;
import org.mavai.punit.decl.internal.path.JsonPathAst.Segment;
import org.mavai.punit.decl.internal.path.JsonPathAst.Selector;
import org.mavai.punit.decl.internal.path.JsonPathAst.SingularQuery;
import org.mavai.punit.decl.internal.path.JsonPathAst.SliceSelector;
import org.mavai.punit.decl.internal.path.JsonPathAst.WildcardSelector;

/**
 * RFC 9535 evaluation over the parsed JSON data model: segments and
 * selectors in visit order, the standard's filter semantics (forgiving
 * comparisons, existence tests over nodelists), and the five standard
 * functions with I-Regexp matching mapped onto Java's engine.
 */
final class JsonPathEvaluator {

    /** The absent value, distinct from JSON null. */
    private static final Object NOTHING = new Object() {
        @Override
        public String toString() {
            return "<nothing>";
        }
    };

    private final Object root;

    JsonPathEvaluator(Object root) {
        this.root = root;
    }

    List<Object> evaluate(List<Segment> segments, Object start) {
        List<Object> nodes = new ArrayList<>();
        nodes.add(start);
        for (Segment segment : segments) {
            List<Object> next = new ArrayList<>();
            for (Object node : nodes) {
                if (segment.descendant()) {
                    for (Object visited : descendants(node)) {
                        applySelectors(segment.selectors(), visited, next);
                    }
                } else {
                    applySelectors(segment.selectors(), node, next);
                }
            }
            nodes = next;
        }
        return nodes;
    }

    /** The node and every descendant, depth-first, node before children. */
    private List<Object> descendants(Object node) {
        List<Object> visited = new ArrayList<>();
        collectDescendants(node, visited);
        return visited;
    }

    private void collectDescendants(Object node, List<Object> visited) {
        visited.add(node);
        if (node instanceof List<?> array) {
            for (Object element : array) {
                collectDescendants(element, visited);
            }
        } else if (node instanceof Map<?, ?> object) {
            for (Object member : object.values()) {
                collectDescendants(member, visited);
            }
        }
    }

    private void applySelectors(List<Selector> selectors, Object node, List<Object> out) {
        for (Selector selector : selectors) {
            applySelector(selector, node, out);
        }
    }

    private void applySelector(Selector selector, Object node, List<Object> out) {
        if (selector instanceof NameSelector name) {
            if (node instanceof Map<?, ?> object && object.containsKey(name.name())) {
                out.add(object.get(name.name()));
            }
        } else if (selector instanceof WildcardSelector) {
            if (node instanceof List<?> array) {
                out.addAll(array);
            } else if (node instanceof Map<?, ?> object) {
                out.addAll(object.values());
            }
        } else if (selector instanceof IndexSelector index) {
            if (node instanceof List<?> array) {
                long i = index.index() < 0 ? array.size() + index.index() : index.index();
                if (i >= 0 && i < array.size()) {
                    out.add(array.get((int) i));
                }
            }
        } else if (selector instanceof SliceSelector slice) {
            if (node instanceof List<?> array) {
                selectSlice(slice, array, out);
            }
        } else if (selector instanceof FilterSelector filter) {
            if (node instanceof List<?> array) {
                for (Object element : array) {
                    if (truthy(filter.expression(), element)) {
                        out.add(element);
                    }
                }
            } else if (node instanceof Map<?, ?> object) {
                for (Object member : object.values()) {
                    if (truthy(filter.expression(), member)) {
                        out.add(member);
                    }
                }
            }
        }
    }

    private void selectSlice(SliceSelector slice, List<?> array, List<Object> out) {
        long step = slice.step();
        if (step == 0) {
            return;
        }
        long length = array.size();
        long start = slice.start() != null ? slice.start() : (step > 0 ? 0 : length - 1);
        long end = slice.end() != null ? slice.end() : (step > 0 ? length : -length - 1);
        if (start < 0) {
            start = length + start;
        }
        if (end < 0) {
            end = length + end;
        }
        if (step > 0) {
            start = Math.max(0, Math.min(start, length));
            end = Math.max(0, Math.min(end, length));
            for (long i = start; i < end; i += step) {
                out.add(array.get((int) i));
            }
        } else {
            start = Math.max(-1, Math.min(start, length - 1));
            end = Math.max(-1, Math.min(end, length - 1));
            for (long i = start; i > end; i += step) {
                out.add(array.get((int) i));
            }
        }
    }

    // ── Filter semantics ──────────────────────────────────────────

    private boolean truthy(LogicalExpr expression, Object current) {
        if (expression instanceof Or or) {
            for (LogicalExpr operand : or.operands()) {
                if (truthy(operand, current)) {
                    return true;
                }
            }
            return false;
        }
        if (expression instanceof And and) {
            for (LogicalExpr operand : and.operands()) {
                if (!truthy(operand, current)) {
                    return false;
                }
            }
            return true;
        }
        if (expression instanceof Not not) {
            return !truthy(not.operand(), current);
        }
        if (expression instanceof ExistenceTest test) {
            return !queryNodes(test.query(), current).isEmpty();
        }
        if (expression instanceof FunctionTest test) {
            Object result = callFunction(test.call(), current);
            return Boolean.TRUE.equals(result);
        }
        Comparison comparison = (Comparison) expression;
        Object left = comparableValue(comparison.left(), current);
        Object right = comparableValue(comparison.right(), current);
        return switch (comparison.op()) {
            case EQ -> looseEquals(left, right);
            case NE -> !looseEquals(left, right);
            case LT -> lessThan(left, right);
            case GT -> lessThan(right, left);
            case LE -> lessThan(left, right) || looseEquals(left, right);
            case GE -> lessThan(right, left) || looseEquals(left, right);
        };
    }

    private List<Object> queryNodes(Query query, Object current) {
        return evaluate(query.segments(), query.relative() ? current : root);
    }

    /** A comparable's value: a JSON value, or {@link #NOTHING} when absent. */
    private Object comparableValue(ComparableExpr comparable, Object current) {
        if (comparable instanceof Literal literal) {
            return literal.value();
        }
        if (comparable instanceof SingularQuery singular) {
            List<Object> nodes =
                    evaluate(singular.segments(), singular.relative() ? current : root);
            return nodes.isEmpty() ? NOTHING : nodes.get(0);
        }
        return callFunction((FunctionCall) comparable, current);
    }

    // ── Functions ─────────────────────────────────────────────────

    private Object callFunction(FunctionCall call, Object current) {
        return switch (call.function()) {
            case LENGTH -> {
                Object value = argumentValue(call.arguments().get(0), current);
                if (value instanceof String text) {
                    yield (long) text.codePointCount(0, text.length());
                }
                if (value instanceof List<?> array) {
                    yield (long) array.size();
                }
                if (value instanceof Map<?, ?> object) {
                    yield (long) object.size();
                }
                yield NOTHING;
            }
            case COUNT -> (long) argumentNodes(call.arguments().get(0), current).size();
            case VALUE -> {
                List<Object> nodes = argumentNodes(call.arguments().get(0), current);
                yield nodes.size() == 1 ? nodes.get(0) : NOTHING;
            }
            case MATCH -> regex(call, current, true);
            case SEARCH -> regex(call, current, false);
        };
    }

    private boolean regex(FunctionCall call, Object current, boolean fullMatch) {
        Object subject = argumentValue(call.arguments().get(0), current);
        Object pattern = argumentValue(call.arguments().get(1), current);
        if (!(subject instanceof String text) || !(pattern instanceof String iRegexp)) {
            return false;
        }
        try {
            Pattern compiled = Pattern.compile(IRegexp.toJavaRegex(iRegexp));
            return fullMatch
                    ? compiled.matcher(text).matches()
                    : compiled.matcher(text).find();
        } catch (PatternSyntaxException | PathSyntaxException invalid) {
            return false;
        }
    }

    private Object argumentValue(Object argument, Object current) {
        if (argument instanceof Query query) {
            List<Object> nodes = queryNodes(query, current);
            return nodes.isEmpty() ? NOTHING : nodes.get(0);
        }
        if (argument instanceof FunctionCall call) {
            return callFunction(call, current);
        }
        if (argument instanceof Literal literal) {
            return literal.value();
        }
        return argument;
    }

    private List<Object> argumentNodes(Object argument, Object current) {
        return queryNodes((Query) argument, current);
    }

    // ── Comparison semantics (RFC 9535 §2.3.5.2.2) ────────────────

    private boolean looseEquals(Object left, Object right) {
        if (left == NOTHING && right == NOTHING) {
            return true;
        }
        if (left == NOTHING || right == NOTHING) {
            return false;
        }
        return deepEquals(left, right);
    }

    private boolean deepEquals(Object left, Object right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        if (left instanceof Number a && right instanceof Number b) {
            return numeric(a).compareTo(numeric(b)) == 0;
        }
        if (left instanceof List<?> a && right instanceof List<?> b) {
            if (a.size() != b.size()) {
                return false;
            }
            for (int i = 0; i < a.size(); i++) {
                if (!deepEquals(a.get(i), b.get(i))) {
                    return false;
                }
            }
            return true;
        }
        if (left instanceof Map<?, ?> a && right instanceof Map<?, ?> b) {
            if (a.size() != b.size()) {
                return false;
            }
            for (Map.Entry<?, ?> entry : a.entrySet()) {
                if (!b.containsKey(entry.getKey())
                        || !deepEquals(entry.getValue(), b.get(entry.getKey()))) {
                    return false;
                }
            }
            return true;
        }
        return left.equals(right);
    }

    private boolean lessThan(Object left, Object right) {
        if (left instanceof Number a && right instanceof Number b) {
            return numeric(a).compareTo(numeric(b)) < 0;
        }
        if (left instanceof String a && right instanceof String b) {
            return a.compareTo(b) < 0;
        }
        return false;
    }

    private static BigDecimal numeric(Number number) {
        if (number instanceof BigDecimal decimal) {
            return decimal;
        }
        if (number instanceof Double || number instanceof Float) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(number.toString());
    }
}
