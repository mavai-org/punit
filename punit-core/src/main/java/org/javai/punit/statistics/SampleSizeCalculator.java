package org.javai.punit.statistics;

import org.apache.commons.statistics.distribution.NormalDistribution;

/**
 * Calculates required sample sizes for the Confidence-First operational approach.
 * 
 * <h2>Power Analysis</h2>
 * <p>This class performs power analysis for one-sided binomial proportion tests.
 * Given:
 * <ul>
 *   <li>Baseline rate p₀ (null hypothesis: no degradation)</li>
 *   <li>Minimum detectable effect δ (alternative: p₁ = p₀ - δ)</li>
 *   <li>Confidence level (1-α)</li>
 *   <li>Statistical power (1-β)</li>
 * </ul>
 * 
 * <p>Compute: Required sample size n
 * 
 * <h2>Hypothesis Test Setup</h2>
 * <pre>
 *   H₀: p = p₀     (system has not degraded)
 *   H₁: p = p₁     (system has degraded by at least δ)
 *   
 *   where p₁ = p₀ - δ
 * </pre>
 * 
 * <h2>Interpretation</h2>
 * <ul>
 *   <li>α (Type I error): Probability of rejecting H₀ when it's true (false positive)</li>
 *   <li>β (Type II error): Probability of failing to reject H₀ when H₁ is true (false negative)</li>
 *   <li>Power = 1-β: Probability of correctly detecting a real degradation</li>
 * </ul>
 * 
 * @see <a href="https://en.wikipedia.org/wiki/Sample_size_determination#Proportions">Sample Size for Proportions</a>
 */
public class SampleSizeCalculator {
    
    /**
     * The standard normal distribution N(0, 1) for computing z-scores.
     */
    private static final NormalDistribution STANDARD_NORMAL = NormalDistribution.of(0, 1);
    
    /**
     * Calculates the required sample size for a given power configuration.
     * 
     * <h3>Formula</h3>
     * <pre>
     *   n = ((z_α × σ₀ + z_β × σ₁) / δ)²
     *   
     *   where:
     *     σ₀ = √(p₀(1-p₀))  — standard deviation under null hypothesis
     *     σ₁ = √(p₁(1-p₁))  — standard deviation under alternative
     *     δ = p₀ - p₁       — minimum detectable effect
     *     z_α = Φ⁻¹(1-α)    — z-score for confidence level
     *     z_β = Φ⁻¹(1-β)    — z-score for power
     * </pre>
     * 
     * <h3>Example</h3>
     * <pre>
     *   Baseline rate: 95%
     *   Detect 5% degradation (to 90%)
     *   Confidence: 95% (α = 0.05)
     *   Power: 80% (β = 0.20)
     *   
     *   → Required samples ≈ 150
     * </pre>
     * 
     * @param baselineRate The baseline success rate p₀ (null hypothesis)
     * @param minDetectableEffect The minimum degradation to detect δ (e.g., 0.05 for 5%)
     * @param confidence The desired confidence level (1-α)
     * @param power The desired statistical power (1-β)
     * @return Sample size requirement with full context
     */
    // javai-ref: JVI-2FYNHXX — do not remove (resolves in javai-orchestrator)
    // javai-ref: JVI-EGMJ0MU — do not remove (resolves in javai-orchestrator)
    public SampleSizeRequirement calculateForPower(
            double baselineRate,
            double minDetectableEffect,
            double confidence,
            double power) {
        
        validateInputs(baselineRate, minDetectableEffect, confidence, power);
        
        // Null hypothesis rate (no degradation)
        double p0 = baselineRate;
        
        // Alternative hypothesis rate (degraded by effect size)
        double p1 = baselineRate - minDetectableEffect;
        
        if (p1 < 0.0) {
            throw new IllegalArgumentException(
                "Effect size " + minDetectableEffect + " exceeds baseline rate " + baselineRate);
        }
        
        // Standard deviations under null and alternative hypotheses
        double sigma0 = Math.sqrt(p0 * (1.0 - p0));
        double sigma1 = Math.sqrt(p1 * (1.0 - p1));
        
        // z-scores for one-sided test
        double zAlpha = STANDARD_NORMAL.inverseCumulativeProbability(confidence);    // z_α
        double zBeta = STANDARD_NORMAL.inverseCumulativeProbability(power);          // z_β
        
        // Sample size formula for one-sided binomial test
        // n = ((z_α × σ₀ + z_β × σ₁) / δ)²
        double numerator = zAlpha * sigma0 + zBeta * sigma1;
        double n = Math.pow(numerator / minDetectableEffect, 2);
        
        // Round up to ensure we meet the power requirement
        int requiredSamples = (int) Math.ceil(n);
        
        return new SampleSizeRequirement(
            requiredSamples,
            confidence,
            power,
            minDetectableEffect,
            p0,
            p1
        );
    }
    
    /**
     * Calculates the achieved power for a given sample size.
     * 
     * <p>This is the inverse of {@link #calculateForPower}: given n, compute power.
     * 
     * <h3>Formula</h3>
     * <pre>
     *   z_β = (δ × √n - z_α × σ₀) / σ₁
     *   power = Φ(z_β)
     * </pre>
     * 
     * @param sampleSize The sample size n
     * @param baselineRate The baseline success rate p₀
     * @param minDetectableEffect The minimum degradation to detect δ
     * @param confidence The confidence level (1-α)
     * @return Achieved power (1-β)
     */
    public double calculateAchievedPower(
            int sampleSize,
            double baselineRate,
            double minDetectableEffect,
            double confidence) {
        
        if (sampleSize <= 0) {
            throw new IllegalArgumentException("Sample size must be positive, got: " + sampleSize);
        }
        
        double p0 = baselineRate;
        double p1 = baselineRate - minDetectableEffect;
        
        if (p1 < 0.0) {
            throw new IllegalArgumentException(
                "Effect size " + minDetectableEffect + " exceeds baseline rate " + baselineRate);
        }
        
        double sigma0 = Math.sqrt(p0 * (1.0 - p0));
        double sigma1 = Math.sqrt(p1 * (1.0 - p1));
        
        double zAlpha = STANDARD_NORMAL.inverseCumulativeProbability(confidence);
        
        // Solve for z_β: z_β = (δ × √n - z_α × σ₀) / σ₁
        double zBeta = (minDetectableEffect * Math.sqrt(sampleSize) - zAlpha * sigma0) / sigma1;
        
        // Power = Φ(z_β)
        return STANDARD_NORMAL.cumulativeProbability(zBeta);
    }
    
    /**
     * Validates inputs for {@link #calculateForPower}.
     *
     * <p>{@code baselineRate ∈ (0, 1]} — perfect baselines (rate = 1.0)
     * are admissible. The formula handles {@code σ0 = √(p0(1−p0)) = 0}
     * cleanly: the first term of {@code n = ((z_α · σ0 + z_β · σ1) / δ)²}
     * vanishes and the calculation reduces to {@code n = (z_β · σ1)² / δ²},
     * which is well-defined for any {@code p1 = rate − mde > 0}. The
     * companion {@code p1 > 0} invariant is enforced separately at the
     * call site (line 89 below) so the two checks document independently
     * meaningful preconditions.
     */
    private void validateInputs(double baselineRate, double minDetectableEffect,
                                double confidence, double power) {
        if (baselineRate <= 0.0 || baselineRate > 1.0) {
            throw new IllegalArgumentException(
                "Baseline rate must be in (0, 1], got: " + baselineRate);
        }
        if (minDetectableEffect <= 0.0 || minDetectableEffect >= 1.0) {
            throw new IllegalArgumentException(
                "Minimum detectable effect must be in (0, 1), got: " + minDetectableEffect);
        }
        if (confidence <= 0.0 || confidence >= 1.0) {
            throw new IllegalArgumentException(
                "Confidence must be in (0, 1), got: " + confidence);
        }
        if (power <= 0.0 || power >= 1.0) {
            throw new IllegalArgumentException(
                "Power must be in (0, 1), got: " + power);
        }
    }
}

