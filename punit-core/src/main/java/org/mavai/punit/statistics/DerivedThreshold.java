package org.mavai.punit.statistics;

/**
 * Represents a statistically-derived threshold for probabilistic testing.
 * 
 * <p>The threshold p_threshold is the minimum observed pass rate required for a test
 * to pass. It is derived from the baseline experimental data using one of three
 * {@link OperationalApproach}es.
 * 
 * <p>For the Sample-Size-First approach (most common):
 * <pre>
 *   p_threshold = Wilson one-sided lower bound at confidence (1-α)
 * </pre>
 * 
 * <p>This ensures that if the true rate has not degraded from the baseline,
 * the probability of a false positive (test failing when system is fine) is at most α.
 * 
 * @param value The derived threshold value p_threshold ∈ [0, 1]
 * @param approach The operational approach used for derivation
 * @param context The derivation context (baseline data, sample sizes, confidence)
 * @param isStatisticallySound True if the derivation produces a reliable threshold;
 *                              false if the implied confidence is too low (&lt; 80%)
 * 
 * @see OperationalApproach
 */
public record DerivedThreshold(
    double value,
    OperationalApproach approach,
    DerivationContext context,
    boolean isStatisticallySound
) {
    /**
     * Validates the threshold parameters.
     */
    public DerivedThreshold {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                "Threshold value must be in [0, 1], got: " + value);
        }
        if (approach == null) {
            throw new IllegalArgumentException("Approach must not be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("Context must not be null");
        }
    }
    
    /**
     * Convenience constructor for statistically sound thresholds.
     */
    public DerivedThreshold(double value, OperationalApproach approach, DerivationContext context) {
        this(value, approach, context, true);
    }
    
    /**
     * Returns the gap between the baseline rate and the derived threshold.
     * 
     * <p>This gap accounts for:
     * <ul>
     *   <li>Uncertainty in the baseline estimate</li>
     *   <li>Increased variance with smaller test sample</li>
     *   <li>Desired confidence level</li>
     * </ul>
     * 
     * @return baselineRate - threshold (positive if threshold is below baseline)
     */
    public double gapFromBaseline() {
        return context.baselineRate() - value;
    }
}

