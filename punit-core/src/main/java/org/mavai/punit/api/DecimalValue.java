package org.mavai.punit.api;

/**
 * Decimal scalar factor value.
 *
 * <p>Canonical form uses a minimal decimal representation — no trailing
 * zeros (e.g. {@code 0.3}, {@code 1.5}, {@code 2.0}). The
 * factor-bundle hash depends on this form being stable across
 * language implementations.
 */
public record DecimalValue(double value) implements FactorValue {

    @Override
    public String canonical() {
        if (Double.isNaN(value)) {
            throw new IllegalStateException("NaN is not an admissible factor value");
        }
        if (Double.isInfinite(value)) {
            throw new IllegalStateException("Infinite is not an admissible factor value");
        }
        // Minimal decimal form: strip trailing zeros from the fractional part,
        // keep at least one digit after the point so that the form remains a
        // decimal (i.e. 2.0 stays 2.0, not 2).
        if (value == (long) value) {
            return Long.toString((long) value) + ".0";
        }
        String s = Double.toString(value);
        // Double.toString already produces no trailing zeros beyond significance
        // (e.g. 0.3 → "0.3", 1.5 → "1.5"). It may produce scientific notation
        // for very small / very large values; the canonical-form spec does
        // not special-case that, so we keep Java's rendering.
        return s;
    }

    @Override
    public Object yamlValue() {
        return value;
    }
}
