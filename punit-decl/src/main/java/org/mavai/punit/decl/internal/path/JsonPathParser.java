package org.mavai.punit.decl.internal.path;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.mavai.punit.decl.internal.path.JsonPathAst.And;
import org.mavai.punit.decl.internal.path.JsonPathAst.ComparableExpr;
import org.mavai.punit.decl.internal.path.JsonPathAst.Comparison;
import org.mavai.punit.decl.internal.path.JsonPathAst.ComparisonOp;
import org.mavai.punit.decl.internal.path.JsonPathAst.ExistenceTest;
import org.mavai.punit.decl.internal.path.JsonPathAst.FilterSelector;
import org.mavai.punit.decl.internal.path.JsonPathAst.Function;
import org.mavai.punit.decl.internal.path.JsonPathAst.FunctionCall;
import org.mavai.punit.decl.internal.path.JsonPathAst.FunctionTest;
import org.mavai.punit.decl.internal.path.JsonPathAst.IndexSelector;
import org.mavai.punit.decl.internal.path.JsonPathAst.Literal;
import org.mavai.punit.decl.internal.path.JsonPathAst.LogicalExpr;
import org.mavai.punit.decl.internal.path.JsonPathAst.NameSelector;
import org.mavai.punit.decl.internal.path.JsonPathAst.Not;
import org.mavai.punit.decl.internal.path.JsonPathAst.Or;
import org.mavai.punit.decl.internal.path.JsonPathAst.Query;
import org.mavai.punit.decl.internal.path.JsonPathAst.ReturnType;
import org.mavai.punit.decl.internal.path.JsonPathAst.Segment;
import org.mavai.punit.decl.internal.path.JsonPathAst.Selector;
import org.mavai.punit.decl.internal.path.JsonPathAst.SingularQuery;
import org.mavai.punit.decl.internal.path.JsonPathAst.SliceSelector;
import org.mavai.punit.decl.internal.path.JsonPathAst.WildcardSelector;

/**
 * A strict recursive-descent parser for RFC 9535 JSONPath — the
 * standard's grammar exactly, with no dialect extensions: whitespace
 * only where the ABNF allows it, integer and number shapes as
 * legislated, well-typedness of function expressions enforced at
 * compile time, and singular-query positions enforced grammatically.
 */
final class JsonPathParser {

    /** The RFC's integer bound: values must fit in the I-JSON range. */
    private static final long MAX_SAFE_INTEGER = 9007199254740991L;

    private final String input;
    private int position;

    private JsonPathParser(String input) {
        this.input = input;
    }

    static CompiledJsonPath parse(String expression) {
        JsonPathParser parser = new JsonPathParser(expression);
        List<Segment> segments = parser.query();
        if (parser.position != expression.length()) {
            throw parser.error("unexpected trailing content");
        }
        return new CompiledJsonPath(expression, segments);
    }

    // ── Query and segments ────────────────────────────────────────

    private List<Segment> query() {
        expect('$');
        return segments();
    }

    private List<Segment> segments() {
        List<Segment> segments = new ArrayList<>();
        while (true) {
            int beforeBlank = position;
            skipBlank();
            if (atEnd()) {
                if (position != beforeBlank) {
                    throw error("trailing whitespace");
                }
                return segments;
            }
            char next = peek();
            if (next != '.' && next != '[') {
                position = beforeBlank;
                return segments;
            }
            segments.add(segment());
        }
    }

    private Segment segment() {
        if (lookingAt("..")) {
            advance(2);
            if (atEnd()) {
                throw error("descendant segment needs a selector");
            }
            char next = peek();
            if (next == '[') {
                return new Segment(true, bracketedSelection());
            }
            if (next == '*') {
                advance(1);
                return new Segment(true, List.of(new WildcardSelector()));
            }
            return new Segment(true, List.of(new NameSelector(memberNameShorthand())));
        }
        if (peek() == '.') {
            advance(1);
            if (atEnd()) {
                throw error("child segment needs a selector");
            }
            if (peek() == '*') {
                advance(1);
                return new Segment(false, List.of(new WildcardSelector()));
            }
            return new Segment(false, List.of(new NameSelector(memberNameShorthand())));
        }
        return new Segment(false, bracketedSelection());
    }

    private List<Selector> bracketedSelection() {
        expect('[');
        skipBlank();
        List<Selector> selectors = new ArrayList<>();
        selectors.add(selector());
        while (true) {
            skipBlank();
            if (atEnd()) {
                throw error("unterminated bracketed selection");
            }
            if (peek() == ']') {
                advance(1);
                return selectors;
            }
            expect(',');
            skipBlank();
            selectors.add(selector());
        }
    }

    private Selector selector() {
        if (atEnd()) {
            throw error("expected a selector");
        }
        char next = peek();
        if (next == '\'' || next == '"') {
            return new NameSelector(stringLiteral());
        }
        if (next == '*') {
            advance(1);
            return new WildcardSelector();
        }
        if (next == '?') {
            advance(1);
            skipBlank();
            return new FilterSelector(logicalOrExpr());
        }
        if (next == '-' || isDigit(next) || next == ':') {
            return indexOrSlice();
        }
        throw error("expected a selector");
    }

    private Selector indexOrSlice() {
        Long first = null;
        if (peek() != ':') {
            first = intToken("index");
        }
        int afterFirst = position;
        skipBlank();
        if (!atEnd() && peek() == ':') {
            advance(1);
            skipBlank();
            Long end = null;
            if (!atEnd() && (peek() == '-' || isDigit(peek()))) {
                end = intToken("slice end");
                skipBlank();
            }
            long step = 1;
            if (!atEnd() && peek() == ':') {
                advance(1);
                skipBlank();
                if (!atEnd() && (peek() == '-' || isDigit(peek()))) {
                    step = intToken("slice step");
                    skipBlank();
                }
            }
            return new SliceSelector(first, end, step);
        }
        position = afterFirst;
        if (first == null) {
            throw error("expected an index or slice");
        }
        return new IndexSelector(first);
    }

    /** RFC int: "0" or [-]DIGIT1*DIGIT — no leading zeros, no '+', no '-0'. */
    private long intToken(String what) {
        int start = position;
        boolean negative = false;
        if (!atEnd() && peek() == '-') {
            negative = true;
            advance(1);
        }
        if (atEnd() || !isDigit(peek())) {
            throw error("malformed " + what);
        }
        int digitsStart = position;
        while (!atEnd() && isDigit(peek())) {
            advance(1);
        }
        String digits = input.substring(digitsStart, position);
        if (digits.length() > 1 && digits.charAt(0) == '0') {
            throw error(what + " has a leading zero");
        }
        if (negative && digits.equals("0")) {
            throw error(what + " -0 is not allowed");
        }
        long value;
        try {
            value = Long.parseLong((negative ? "-" : "") + digits);
        } catch (NumberFormatException overflow) {
            throw error(what + " out of range");
        }
        if (value > MAX_SAFE_INTEGER || value < -MAX_SAFE_INTEGER) {
            throw error(what + " out of the interoperable integer range");
        }
        if (start == position) {
            throw error("malformed " + what);
        }
        return value;
    }

    // ── Filter expressions ────────────────────────────────────────

    private LogicalExpr logicalOrExpr() {
        List<LogicalExpr> operands = new ArrayList<>();
        operands.add(logicalAndExpr());
        while (true) {
            int before = position;
            skipBlank();
            if (lookingAt("||")) {
                advance(2);
                skipBlank();
                operands.add(logicalAndExpr());
            } else {
                position = before;
                return operands.size() == 1 ? operands.get(0) : new Or(operands);
            }
        }
    }

    private LogicalExpr logicalAndExpr() {
        List<LogicalExpr> operands = new ArrayList<>();
        operands.add(basicExpr());
        while (true) {
            int before = position;
            skipBlank();
            if (lookingAt("&&")) {
                advance(2);
                skipBlank();
                operands.add(basicExpr());
            } else {
                position = before;
                return operands.size() == 1 ? operands.get(0) : new And(operands);
            }
        }
    }

    private LogicalExpr basicExpr() {
        boolean negated = false;
        if (!atEnd() && peek() == '!') {
            advance(1);
            skipBlank();
            negated = true;
        }
        if (!atEnd() && peek() == '(') {
            advance(1);
            skipBlank();
            LogicalExpr inner = logicalOrExpr();
            skipBlank();
            expect(')');
            return negated ? new Not(inner) : inner;
        }
        // Test expression or comparison. A negated expression can only be
        // a test; comparisons cannot be negated without parentheses.
        if (negated) {
            LogicalExpr test = testExpr();
            return new Not(test);
        }
        // Try a comparison; a test-expr is the fallback when no
        // comparison operator follows.
        int start = position;
        ComparableOrQuery left = comparableOrQuery();
        int afterLeft = position;
        skipBlank();
        ComparisonOp op = comparisonOp();
        if (op == null) {
            position = afterLeft;
            return toTest(left, start);
        }
        ComparableExpr leftComparable = toComparable(left, start);
        skipBlank();
        int rightStart = position;
        ComparableOrQuery right = comparableOrQuery();
        ComparableExpr rightComparable = toComparable(right, rightStart);
        return new Comparison(leftComparable, op, rightComparable);
    }

    private LogicalExpr testExpr() {
        int start = position;
        ComparableOrQuery operand = comparableOrQuery();
        return toTest(operand, start);
    }

    /** An operand that may be a literal, query, or function call. */
    private record ComparableOrQuery(Literal literal, Query query, FunctionCall call) {}

    private ComparableOrQuery comparableOrQuery() {
        if (atEnd()) {
            throw error("expected an expression");
        }
        char next = peek();
        if (next == '@' || next == '$') {
            boolean relative = next == '@';
            advance(1);
            List<Segment> segments = filterQuerySegments();
            return new ComparableOrQuery(null, new Query(relative, segments), null);
        }
        if (next == '\'' || next == '"') {
            return new ComparableOrQuery(new Literal(stringLiteral()), null, null);
        }
        if (next == '-' || isDigit(next)) {
            return new ComparableOrQuery(new Literal(numberLiteral()), null, null);
        }
        if (isFunctionNameStart(next)) {
            String word = word();
            switch (word) {
                case "true" -> {
                    return new ComparableOrQuery(new Literal(Boolean.TRUE), null, null);
                }
                case "false" -> {
                    return new ComparableOrQuery(new Literal(Boolean.FALSE), null, null);
                }
                case "null" -> {
                    return new ComparableOrQuery(new Literal(null), null, null);
                }
                default -> {
                    return new ComparableOrQuery(null, null, functionCall(word));
                }
            }
        }
        throw error("expected an expression");
    }

    /** Segments inside a filter query — the full segment grammar. */
    private List<Segment> filterQuerySegments() {
        List<Segment> segments = new ArrayList<>();
        while (true) {
            int before = position;
            skipBlank();
            if (atEnd()) {
                position = before;
                return segments;
            }
            char next = peek();
            if (next != '.' && next != '[') {
                position = before;
                return segments;
            }
            segments.add(segment());
        }
    }

    private LogicalExpr toTest(ComparableOrQuery operand, int start) {
        if (operand.query() != null) {
            return new ExistenceTest(operand.query());
        }
        if (operand.call() != null) {
            if (operand.call().function().returns != ReturnType.LOGICAL) {
                position = start;
                throw error("a function used as a test must return a logical value");
            }
            return new FunctionTest(operand.call());
        }
        position = start;
        throw error("a literal cannot stand alone in a filter — compare it");
    }

    private ComparableExpr toComparable(ComparableOrQuery operand, int start) {
        if (operand.literal() != null || (operand.literal() == null && operand.query() == null
                && operand.call() == null)) {
            return operand.literal();
        }
        if (operand.query() != null) {
            SingularQuery singular = asSingular(operand.query());
            if (singular == null) {
                position = start;
                throw error("only a singular query can be compared");
            }
            return singular;
        }
        FunctionCall call = operand.call();
        if (call.function().returns != ReturnType.VALUE) {
            position = start;
            throw error("a function used in a comparison must return a value");
        }
        return call;
    }

    private SingularQuery asSingular(Query query) {
        for (Segment segment : query.segments()) {
            if (segment.descendant() || segment.selectors().size() != 1) {
                return null;
            }
            Selector selector = segment.selectors().get(0);
            if (!(selector instanceof NameSelector) && !(selector instanceof IndexSelector)) {
                return null;
            }
        }
        return new SingularQuery(query.relative(), query.segments());
    }

    private ComparisonOp comparisonOp() {
        if (lookingAt("==")) {
            advance(2);
            return ComparisonOp.EQ;
        }
        if (lookingAt("!=")) {
            advance(2);
            return ComparisonOp.NE;
        }
        if (lookingAt("<=")) {
            advance(2);
            return ComparisonOp.LE;
        }
        if (lookingAt(">=")) {
            advance(2);
            return ComparisonOp.GE;
        }
        if (!atEnd() && peek() == '<') {
            advance(1);
            return ComparisonOp.LT;
        }
        if (!atEnd() && peek() == '>') {
            advance(1);
            return ComparisonOp.GT;
        }
        return null;
    }

    // ── Function expressions ──────────────────────────────────────

    private FunctionCall functionCall(String name) {
        Function function;
        try {
            function = Function.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw error("unknown function '" + name + "'");
        }
        if (!name.equals(name.toLowerCase(Locale.ROOT))) {
            throw error("function names are lowercase");
        }
        expect('(');
        skipBlank();
        List<Object> arguments = new ArrayList<>();
        if (!atEnd() && peek() != ')') {
            arguments.add(functionArgument());
            while (true) {
                skipBlank();
                if (atEnd()) {
                    throw error("unterminated function call");
                }
                if (peek() == ')') {
                    break;
                }
                expect(',');
                skipBlank();
                arguments.add(functionArgument());
            }
        }
        expect(')');
        checkWellTyped(function, arguments);
        return new FunctionCall(function, arguments);
    }

    private Object functionArgument() {
        ComparableOrQuery operand = comparableOrQuery();
        if (operand.literal() != null
                || (operand.query() == null && operand.call() == null)) {
            return operand.literal();
        }
        if (operand.query() != null) {
            return operand.query();
        }
        return operand.call();
    }

    private void checkWellTyped(Function function, List<Object> arguments) {
        switch (function) {
            case LENGTH -> {
                requireArity(function, arguments, 1);
                requireValueTyped(function, arguments.get(0));
            }
            case COUNT, VALUE -> {
                requireArity(function, arguments, 1);
                requireNodesTyped(function, arguments.get(0));
            }
            case MATCH, SEARCH -> {
                requireArity(function, arguments, 2);
                requireValueTyped(function, arguments.get(0));
                requireValueTyped(function, arguments.get(1));
            }
        }
    }

    private void requireArity(Function function, List<Object> arguments, int arity) {
        if (arguments.size() != arity) {
            throw error("function '" + function.name().toLowerCase(Locale.ROOT) + "' takes "
                    + arity + " argument" + (arity == 1 ? "" : "s"));
        }
    }

    /** ValueType slot: a literal, a singular query, or a value-returning function. */
    private void requireValueTyped(Function function, Object argument) {
        String name = function.name().toLowerCase(Locale.ROOT);
        if (argument instanceof Query query) {
            if (asSingular(query) == null) {
                throw error("function '" + name + "' requires a singular query argument");
            }
            return;
        }
        if (argument instanceof FunctionCall call && call.function().returns != ReturnType.VALUE) {
            throw error("function '" + name + "' requires a value-returning argument");
        }
    }

    /** NodesType slot: any query; literals and functions cannot supply nodes. */
    private void requireNodesTyped(Function function, Object argument) {
        if (!(argument instanceof Query)) {
            throw error("function '" + function.name().toLowerCase(Locale.ROOT)
                    + "' requires a query argument");
        }
    }

    // ── Literals ──────────────────────────────────────────────────

    private String memberNameShorthand() {
        if (atEnd() || !isNameFirst(input.codePointAt(position))) {
            throw error("expected a member name");
        }
        int start = position;
        while (!atEnd()) {
            int codePoint = input.codePointAt(position);
            if (!isNameChar(codePoint)) {
                break;
            }
            position += Character.charCount(codePoint);
        }
        return input.substring(start, position);
    }

    private String word() {
        int start = position;
        while (!atEnd() && (isFunctionNameChar(peek()))) {
            advance(1);
        }
        return input.substring(start, position);
    }

    private String stringLiteral() {
        char quote = peek();
        advance(1);
        StringBuilder value = new StringBuilder();
        while (true) {
            if (atEnd()) {
                throw error("unterminated string literal");
            }
            char next = peek();
            if (next == quote) {
                advance(1);
                return value.toString();
            }
            if (next == '\\') {
                advance(1);
                value.append(escape(quote));
                continue;
            }
            if (next < 0x20) {
                throw error("unescaped control character in string literal");
            }
            value.append(next);
            advance(1);
        }
    }

    private String escape(char quote) {
        if (atEnd()) {
            throw error("unterminated escape");
        }
        char next = peek();
        advance(1);
        return switch (next) {
            case 'b' -> "\b";
            case 'f' -> "\f";
            case 'n' -> "\n";
            case 'r' -> "\r";
            case 't' -> "\t";
            case '/' -> "/";
            case '\\' -> "\\";
            case 'u' -> unicodeEscape();
            default -> {
                if (next == quote) {
                    yield String.valueOf(quote);
                }
                throw error("invalid escape '\\" + next + "'");
            }
        };
    }

    private String unicodeEscape() {
        int high = hex4();
        if (Character.isLowSurrogate((char) high)) {
            throw error("unpaired low surrogate in string literal");
        }
        if (Character.isHighSurrogate((char) high)) {
            if (!lookingAt("\\u")) {
                throw error("unpaired high surrogate in string literal");
            }
            advance(2);
            int low = hex4();
            if (!Character.isLowSurrogate((char) low)) {
                throw error("invalid surrogate pair in string literal");
            }
            return new String(Character.toChars(
                    Character.toCodePoint((char) high, (char) low)));
        }
        return String.valueOf((char) high);
    }

    private int hex4() {
        if (position + 4 > input.length()) {
            throw error("truncated unicode escape");
        }
        int value = 0;
        for (int i = 0; i < 4; i++) {
            char digit = input.charAt(position + i);
            int nibble = Character.digit(digit, 16);
            if (nibble < 0) {
                throw error("invalid unicode escape");
            }
            if (Character.isUpperCase(digit) && Character.isLetter(digit)) {
                // RFC 9535 allows upper- and lowercase hex digits.
                nibble = Character.digit(Character.toLowerCase(digit), 16);
            }
            value = (value << 4) | nibble;
        }
        position += 4;
        return value;
    }

    private Object numberLiteral() {
        int start = position;
        if (peek() == '-') {
            advance(1);
        }
        if (atEnd() || !isDigit(peek())) {
            throw error("malformed number");
        }
        int intStart = position;
        while (!atEnd() && isDigit(peek())) {
            advance(1);
        }
        String integerDigits = input.substring(intStart, position);
        if (integerDigits.length() > 1 && integerDigits.charAt(0) == '0') {
            throw error("number has a leading zero");
        }
        boolean fractional = false;
        if (!atEnd() && peek() == '.') {
            advance(1);
            fractional = true;
            if (atEnd() || !isDigit(peek())) {
                throw error("malformed number fraction");
            }
            while (!atEnd() && isDigit(peek())) {
                advance(1);
            }
        }
        if (!atEnd() && (peek() == 'e' || peek() == 'E')) {
            advance(1);
            fractional = true;
            if (!atEnd() && (peek() == '+' || peek() == '-')) {
                advance(1);
            }
            if (atEnd() || !isDigit(peek())) {
                throw error("malformed number exponent");
            }
            while (!atEnd() && isDigit(peek())) {
                advance(1);
            }
        }
        String text = input.substring(start, position);
        if (fractional) {
            return Double.parseDouble(text);
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException overflow) {
            return Double.parseDouble(text);
        }
    }

    // ── Character classes and machinery ───────────────────────────

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isNameFirst(int codePoint) {
        return (codePoint >= 'A' && codePoint <= 'Z')
                || (codePoint >= 'a' && codePoint <= 'z')
                || codePoint == '_'
                || (codePoint >= 0x80 && codePoint <= 0xD7FF)
                || (codePoint >= 0xE000 && codePoint <= 0x10FFFF);
    }

    private static boolean isNameChar(int codePoint) {
        return isNameFirst(codePoint) || (codePoint >= '0' && codePoint <= '9');
    }

    private static boolean isFunctionNameStart(char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
    }

    private static boolean isFunctionNameChar(char c) {
        return isFunctionNameStart(c) || isDigit(c) || c == '_';
    }

    private void skipBlank() {
        while (!atEnd()) {
            char next = peek();
            if (next == ' ' || next == '\t' || next == '\n' || next == '\r') {
                advance(1);
            } else {
                return;
            }
        }
    }

    private boolean lookingAt(String token) {
        return input.startsWith(token, position);
    }

    private boolean atEnd() {
        return position >= input.length();
    }

    private char peek() {
        return input.charAt(position);
    }

    private void advance(int count) {
        position += count;
    }

    private void expect(char expected) {
        if (atEnd() || peek() != expected) {
            throw error("expected '" + expected + "'");
        }
        advance(1);
    }

    private PathSyntaxException error(String message) {
        return new PathSyntaxException(
                message + " at position " + position + " in: " + input);
    }
}
