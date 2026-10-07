package org.mavai.punit.statistics;

import java.math.BigDecimal;

/**
 * The Statistical Companion methodology whose decision rules this
 * package implements, and the one conversion every rule shares.
 */
public final class Methodology {

    /**
     * The methodology version. Every verdict record names it, and the
     * conformance suite checks it against the version the mavai-R
     * fixtures declare.
     */
    public static final String VERSION = "1.6.0";

    private Methodology() {
    }

    /**
     * The one-sided level {@code 1 − confidence}, as the decimal it is
     * written as.
     *
     * <p>{@code 1 − 0.95} in binary floating point is
     * {@code 0.050000000000000044}; the level a developer declared is
     * {@code 0.05}, and that is the value the exact-boundary convention
     * reads (companion §10.6).
     *
     * @param confidence the confidence level, in (0, 1)
     * @return {@code 1 − confidence}, computed in decimal
     */
    public static double alphaFromConfidence(double confidence) {
        if (Double.isNaN(confidence) || confidence <= 0.0 || confidence >= 1.0) {
            throw new IllegalArgumentException(
                    "confidence must be in (0, 1), got " + confidence);
        }
        return BigDecimal.ONE.subtract(new BigDecimal(Double.toString(confidence))).doubleValue();
    }

    /**
     * The confidence level {@code 1 − alpha}, as the decimal it is
     * written as — the inverse of {@link #alphaFromConfidence(double)}.
     */
    public static double confidenceFromAlpha(double alpha) {
        if (Double.isNaN(alpha) || alpha <= 0.0 || alpha >= 1.0) {
            throw new IllegalArgumentException("alpha must be in (0, 1), got " + alpha);
        }
        return BigDecimal.ONE.subtract(new BigDecimal(Double.toString(alpha))).doubleValue();
    }
}
