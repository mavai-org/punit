package org.mavai.punit.api.spec;

/**
 * Pass-rate baseline, read by empirical variants of
 * {@code PassRate}.
 *
 * @param observedPassRate the baseline's successes / total
 * @param sampleCount      the baseline's total invocation count
 */
public record PassRateStatistics(
        double observedPassRate,
        @Override int sampleCount) implements BaselineStatistics {

    public PassRateStatistics {
        if (Double.isNaN(observedPassRate)
                || observedPassRate < 0.0
                || observedPassRate > 1.0) {
            throw new IllegalArgumentException(
                    "observedPassRate must be in [0, 1], got " + observedPassRate);
        }
        if (sampleCount < 0) {
            throw new IllegalArgumentException(
                    "sampleCount must be non-negative, got " + sampleCount);
        }
    }
}
