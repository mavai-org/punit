package org.mavai.punit.internal.engine.spec.model;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates whether a set of values meets success criteria.
 *
 * <h2>Supported Syntax</h2>
 * <ul>
 *   <li>{@code "key == value"} - equality</li>
 *   <li>{@code "key != value"} - inequality</li>
 *   <li>{@code "key > value"}, {@code "key >= value"} - greater than</li>
 *   <li>{@code "key < value"}, {@code "key <= value"} - less than</li>
 *   <li>{@code "expr1 && expr2"} - logical AND</li>
 *   <li>{@code "expr1 || expr2"} - logical OR</li>
 * </ul>
 *
 * <h2>Examples</h2>
 * <ul>
 *   <li>{@code "isValid == true"}</li>
 *   <li>{@code "score >= 0.8"}</li>
 *   <li>{@code "isValid == true && errorCount == 0"}</li>
 * </ul>
 */
public interface SuccessCriteria {

	/**
	 * Evaluates whether a set of values meets success criteria.
	 *
	 * @param values the values to evaluate
	 * @return true if the values meet criteria
	 */
	boolean isSuccess(Map<String, Object> values);

	/**
	 * Returns a human-readable description of the criteria.
	 *
	 * @return the description
	 */
	String getDescription();

	/**
	 * Creates a criteria that always returns true.
	 *
	 * @return the always-true criteria
	 */
	static SuccessCriteria alwaysTrue() {
		return new SuccessCriteria() {
			@Override
			public boolean isSuccess(Map<String, Object> values) {
				return true;
			}

			@Override
			public String getDescription() {
				return "(always true)";
			}
		};
	}

	/**
	 * Parses a criteria expression.
	 *
	 * @param expression the expression to parse
	 * @return the success criteria
	 */
	static SuccessCriteria parse(String expression) {
		if (expression == null || expression.trim().isEmpty()) {
			return alwaysTrue();
		}
		return new ExpressionSuccessCriteria(expression.trim());
	}
}

/**
 * Implementation of SuccessCriteria using expression parsing.
 */
class ExpressionSuccessCriteria implements SuccessCriteria {

	private static final Pattern COMPARISON_PATTERN = Pattern.compile(
			"(\\w+)\\s*(==|!=|>=|<=|>|<)\\s*(.+)");

	private final String expression;

	ExpressionSuccessCriteria(String expression) {
		this.expression = Objects.requireNonNull(expression);
	}

	@Override
	public boolean isSuccess(Map<String, Object> values) {
		return evaluate(expression, values);
	}

	@Override
	public String getDescription() {
		return expression;
	}

	private boolean evaluate(String expr, Map<String, Object> values) {
		expr = expr.trim();

		// Handle logical operators (simple left-to-right evaluation)
		int andIdx = expr.indexOf("&&");
		if (andIdx > 0) {
			String left = expr.substring(0, andIdx).trim();
			String right = expr.substring(andIdx + 2).trim();
			return evaluate(left, values) && evaluate(right, values);
		}

		int orIdx = expr.indexOf("||");
		if (orIdx > 0) {
			String left = expr.substring(0, orIdx).trim();
			String right = expr.substring(orIdx + 2).trim();
			return evaluate(left, values) || evaluate(right, values);
		}

		// Handle parentheses
		if (expr.startsWith("(") && expr.endsWith(")")) {
			return evaluate(expr.substring(1, expr.length() - 1), values);
		}

		// Handle comparison
		Matcher m = COMPARISON_PATTERN.matcher(expr);
		if (m.matches()) {
			String key = m.group(1);
			String op = m.group(2);
			String expectedStr = m.group(3).trim();

			Object actual = values.get(key);
			Object expected = parseValue(expectedStr);

			return compare(actual, op, expected);
		}

		// Unknown expression format
		return false;
	}

	private Object parseValue(String value) {
		// Remove quotes if present
		if ((value.startsWith("\"") && value.endsWith("\"")) ||
				(value.startsWith("'") && value.endsWith("'"))) {
			return value.substring(1, value.length() - 1);
		}

		// Boolean
		if ("true".equalsIgnoreCase(value)) return true;
		if ("false".equalsIgnoreCase(value)) return false;

		// Number
		try {
			if (value.contains(".")) {
				return Double.parseDouble(value);
			}
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			return value;
		}
	}

	private boolean compare(Object actual, String op, Object expected) {
		if (actual == null) {
			return "!=".equals(op) && expected != null;
		}

		return switch (op) {
			case "==" -> Objects.equals(actual, expected) || numericEquals(actual, expected);
			case "!=" -> !Objects.equals(actual, expected) && !numericEquals(actual, expected);
			case ">" -> numericCompare(actual, expected) > 0;
			case ">=" -> numericCompare(actual, expected) >= 0;
			case "<" -> numericCompare(actual, expected) < 0;
			case "<=" -> numericCompare(actual, expected) <= 0;
			default -> false;
		};
	}

	private boolean numericEquals(Object actual, Object expected) {
		if (actual instanceof Number && expected instanceof Number) {
			return ((Number) actual).doubleValue() == ((Number) expected).doubleValue();
		}
		return false;
	}

	private int numericCompare(Object actual, Object expected) {
		if (actual instanceof Number && expected instanceof Number) {
			return Double.compare(
					((Number) actual).doubleValue(),
					((Number) expected).doubleValue()
			);
		}
		if (actual instanceof Comparable && expected instanceof Comparable) {
			@SuppressWarnings("unchecked")
			Comparable<Object> c = (Comparable<Object>) actual;
			return c.compareTo(expected);
		}
		return 0;
	}
}

