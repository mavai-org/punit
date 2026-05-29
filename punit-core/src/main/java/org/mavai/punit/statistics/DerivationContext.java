package org.mavai.punit.statistics;

/**
 * Captures the context in which a threshold was derived.
 * 
 * <p>This record stores all inputs used in threshold derivation, enabling:
 * <ul>
 *   <li>Reproducibility of the derivation</li>
 *   <li>Reporting of the derivation method and parameters</li>
 *   <li>Audit trail for statistical decisions</li>
 * </ul>
 * 
 * @param baselineRate The observed success rate from the baseline experiment (p̂_baseline)
 * @param baselineSamples The sample size from the baseline experiment (n_baseline)
 * @param testSamples The intended sample size for the test (n_test)
 * @param confidence The confidence level used in derivation (1-α)
 */
public record DerivationContext(
    double baselineRate,
    int baselineSamples,
    int testSamples,
    double confidence
) {
    /**
     * Validates the context parameters.
     */
    public DerivationContext {
        if (baselineRate < 0.0 || baselineRate > 1.0) {
            throw new IllegalArgumentException(
                "Baseline rate must be in [0, 1], got: " + baselineRate);
        }
        if (baselineSamples <= 0) {
            throw new IllegalArgumentException(
                "Baseline samples must be positive, got: " + baselineSamples);
        }
        if (testSamples <= 0) {
            throw new IllegalArgumentException(
                "Test samples must be positive, got: " + testSamples);
        }
        if (confidence <= 0.0 || confidence >= 1.0) {
            throw new IllegalArgumentException(
                "Confidence must be in (0, 1), got: " + confidence);
        }
    }
}

