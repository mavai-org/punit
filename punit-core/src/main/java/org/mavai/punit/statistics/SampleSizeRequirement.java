package org.mavai.punit.statistics;

/**
 * Represents the result of a sample size calculation for the Confidence-First approach.
 * 
 * <p>This is the output of power analysis: given desired confidence (1-α), power (1-β),
 * and minimum detectable effect (δ), compute the required sample size n.
 * 
 * <h2>Statistical Formulation</h2>
 * <p>For a one-sided binomial test:
 * <ul>
 *   <li>H₀: p = p₀ (null hypothesis: no degradation)</li>
 *   <li>H₁: p = p₁ = p₀ - δ (alternative: degradation by δ)</li>
 * </ul>
 * 
 * <p>The required sample size n satisfies:
 * <pre>
 *   n = ((z_α × σ₀ + z_β × σ₁) / δ)²
 * </pre>
 * where σ₀ = √(p₀(1-p₀)) and σ₁ = √(p₁(1-p₁)).
 * 
 * @param requiredSamples The computed sample size n
 * @param confidence The specified confidence level (1-α)
 * @param power The specified statistical power (1-β)
 * @param minDetectableEffect The minimum degradation to detect (δ)
 * @param nullRate The null hypothesis rate p₀ (baseline rate)
 * @param alternativeRate The alternative hypothesis rate p₁ = p₀ - δ
 * 
 * @see <a href="https://en.wikipedia.org/wiki/Power_of_a_test">Statistical Power</a>
 */
public record SampleSizeRequirement(
    int requiredSamples,
    double confidence,
    double power,
    double minDetectableEffect,
    double nullRate,
    double alternativeRate
) {
    /**
     * Validates the requirement parameters.
     */
    public SampleSizeRequirement {
        if (requiredSamples <= 0) {
            throw new IllegalArgumentException(
                "Required samples must be positive, got: " + requiredSamples);
        }
        if (confidence <= 0.0 || confidence >= 1.0) {
            throw new IllegalArgumentException(
                "Confidence must be in (0, 1), got: " + confidence);
        }
        if (power <= 0.0 || power >= 1.0) {
            throw new IllegalArgumentException(
                "Power must be in (0, 1), got: " + power);
        }
        if (minDetectableEffect <= 0.0 || minDetectableEffect >= 1.0) {
            throw new IllegalArgumentException(
                "Minimum detectable effect must be in (0, 1), got: " + minDetectableEffect);
        }
        if (nullRate < 0.0 || nullRate > 1.0) {
            throw new IllegalArgumentException(
                "Null rate must be in [0, 1], got: " + nullRate);
        }
        if (alternativeRate < 0.0 || alternativeRate > 1.0) {
            throw new IllegalArgumentException(
                "Alternative rate must be in [0, 1], got: " + alternativeRate);
        }
    }
}

