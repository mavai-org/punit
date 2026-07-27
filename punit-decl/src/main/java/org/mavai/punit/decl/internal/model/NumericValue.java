package org.mavai.punit.decl.internal.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.regex.Pattern;

/**
 * The numeric interpretation rule the value-comparison forms share, for
 * operands and subjects alike: a number — never a boolean — or a
 * numeric string (sign, decimal point, exponent) qualifies, and
 * comparison is decimal, not binary floating point, so formatting
 * differences ({@code 2637.80} vs {@code 2.6378e3}) and float
 * artefacts never decide a verdict. Quoting an operand preserves the
 * exact decimal.
 */
public final class NumericValue {

    private static final Pattern NUMERIC_STRING =
            Pattern.compile("^-?[0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?$");

    private NumericValue() {}

    /** The value as an exact decimal, or {@code null} when uninterpretable. */
    public static BigDecimal of(Object value) {
        if (value instanceof Boolean) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof BigInteger integer) {
            return new BigDecimal(integer);
        }
        if (value instanceof Double || value instanceof Float) {
            return new BigDecimal(String.valueOf(value));
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.longValue());
        }
        if (value instanceof String text && NUMERIC_STRING.matcher(text.strip()).matches()) {
            return new BigDecimal(text.strip());
        }
        return null;
    }

    /** Whether the value qualifies as a numeric operand or subject. */
    public static boolean interpretable(Object value) {
        return of(value) != null;
    }
}
