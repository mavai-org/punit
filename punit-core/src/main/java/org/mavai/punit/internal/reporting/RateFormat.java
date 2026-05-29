package org.mavai.punit.internal.reporting;

/**
 * Central formatting utility for pass rates and thresholds.
 *
 * <p>Pass rates are displayed as raw doubles with 4 decimal places (e.g. {@code 0.9999})
 * rather than percentages. This avoids misleading rounding — a threshold of 0.9999 would
 * display as "100.0%" in percentage format, hiding meaningful precision. The number of
 * decimal digits also serves as a visual cue for whether a value was hand-specified
 * (e.g. {@code 0.9500}) or statistically derived (e.g. {@code 0.9160}).
 *
 * <p>Conventional statistical parameters — confidence levels, alpha/false-positive
 * probability, and quantile labels — remain displayed as percentages because users
 * universally expect "95% confidence" not "0.95 confidence".
 */
public final class RateFormat {

    private RateFormat() {
        // Utility class — no instantiation
    }

    /**
     * Formats a pass rate or threshold as a raw double with 4 decimal places.
     *
     * @param rate a proportion in [0, 1]
     * @return formatted string, e.g. {@code "0.9999"}
     */
    public static String format(double rate) {
        return String.format("%.4f", rate);
    }
}
