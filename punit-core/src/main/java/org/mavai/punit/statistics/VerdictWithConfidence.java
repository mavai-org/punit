package org.mavai.punit.statistics;

/**
 * Represents the outcome of a probabilistic test with full statistical context.
 * 
 * <p>Unlike a simple boolean pass/fail, this verdict includes:
 * <ul>
 *   <li>The statistical basis for the decision</li>
 *   <li>The probability that the result is a false positive</li>
 *   <li>Human-readable interpretation</li>
 * </ul>
 * 
 * <h2>Interpreting Failures</h2>
 * <p>A test failure does NOT mean "definitely broken." It means:
 * <blockquote>
 *   "The observed behavior is statistically inconsistent with the baseline
 *   at the configured confidence level."
 * </blockquote>
 * 
 * <p>The {@link #falsePositiveProbability()} quantifies the chance that this
 * failure is due to sampling variance rather than actual degradation.
 * 
 * @param passed True if observed rate ≥ threshold
 * @param observedRate The observed success rate from the test (k_test / n_test)
 * @param threshold The threshold used for the decision
 * @param falsePositiveProbability P(Type I error) = α; probability of failure when system is fine
 * @param interpretation Human-readable explanation of the result
 */
public record VerdictWithConfidence(
    boolean passed,
    double observedRate,
    DerivedThreshold threshold,
    double falsePositiveProbability,
    String interpretation
) {
    /**
     * Validates the verdict parameters.
     */
    public VerdictWithConfidence {
        if (observedRate < 0.0 || observedRate > 1.0) {
            throw new IllegalArgumentException(
                "Observed rate must be in [0, 1], got: " + observedRate);
        }
        if (threshold == null) {
            throw new IllegalArgumentException("Threshold must not be null");
        }
        if (falsePositiveProbability < 0.0 || falsePositiveProbability > 1.0) {
            throw new IllegalArgumentException(
                "False positive probability must be in [0, 1], got: " + falsePositiveProbability);
        }
    }
    
    /**
     * Returns the shortfall below the threshold, or 0 if passed.
     * 
     * @return threshold - observedRate if failed, 0 otherwise
     */
    public double shortfall() {
        return passed ? 0.0 : threshold.value() - observedRate;
    }
    
    /**
     * Returns the confidence level of this verdict.
     * 
     * @return 1 - falsePositiveProbability
     */
    public double confidence() {
        return 1.0 - falsePositiveProbability;
    }
}

