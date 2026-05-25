package org.javai.punit.statistics;

import org.apache.commons.statistics.distribution.NormalDistribution;

/**
 * Computes point estimates and confidence intervals for binomial proportions.
 * 
 * <h2>Statistical Background</h2>
 * <p>Given k successes in n Bernoulli trials, we estimate the true success probability p.
 * The Maximum Likelihood Estimator (MLE) is p̂ = k/n.
 * 
 * <p>This class provides:
 * <ul>
 *   <li>{@link #estimate}: Wilson score confidence interval (two-sided)</li>
 *   <li>{@link #lowerBound}: Wilson score lower bound (one-sided, for threshold derivation)</li>
 *   <li>{@link #standardError}: Standard error of the proportion estimate</li>
 * </ul>
 * 
 * <h2>Why Wilson Score?</h2>
 * <p>The Wilson score interval is preferred over the normal (Wald) approximation because:
 * <ul>
 *   <li>Better coverage probability for all sample sizes</li>
 *   <li>Remains valid when p̂ is near 0 or 1 (avoids SE collapse)</li>
 *   <li>Never produces bounds outside [0, 1]</li>
 * </ul>
 * 
 * @see <a href="https://en.wikipedia.org/wiki/Binomial_proportion_confidence_interval#Wilson_score_interval">Wilson Score Interval</a>
 */
public class BinomialProportionEstimator {
    
    /**
     * The standard normal distribution N(0, 1) for computing z-scores.
     */
    private static final NormalDistribution STANDARD_NORMAL = NormalDistribution.of(0, 1);
    
    /**
     * Computes the standard error of the proportion estimate.
     * 
     * <p>Formula:
     * <pre>
     *   SE(p̂) = √(p̂(1-p̂)/n)
     * </pre>
     * 
     * <p><strong>Note:</strong> This collapses to 0 when p̂ = 0 or p̂ = 1.
     * For confidence intervals in these cases, use the Wilson method instead.
     * 
     * @param successes Number of successes k
     * @param trials Number of trials n
     * @return Standard error of the proportion estimate
     */
    public double standardError(int successes, int trials) {
        validateInputs(successes, trials);
        
        double pHat = (double) successes / trials;
        
        // SE = √(p̂(1-p̂)/n)
        return Math.sqrt(pHat * (1.0 - pHat) / trials);
    }
    
    /**
     * Computes a point estimate and confidence interval for the proportion.
     * 
     * <p>Uses the Wilson score interval, which has better coverage properties
     * than the normal approximation for all sample sizes.
     * 
     * <h3>Wilson Score Formula</h3>
     * <pre>
     *   center = (p̂ + z²/2n) / (1 + z²/n)
     *   margin = z × √(p̂(1-p̂)/n + z²/4n²) / (1 + z²/n)
     *   
     *   lower = center - margin
     *   upper = center + margin
     * </pre>
     * 
     * @param successes Number of successes k
     * @param trials Number of trials n
     * @param confidenceLevel Confidence level (1-α), e.g., 0.95 for 95% CI
     * @return Proportion estimate with Wilson score confidence interval
     */
    // javai-ref: JVI-58DBYP~ — do not remove (resolves in javai-orchestrator)
    public ProportionEstimate estimate(int successes, int trials, double confidenceLevel) {
        validateInputs(successes, trials);
        validateConfidenceLevel(confidenceLevel);
        
        double pHat = (double) successes / trials;
        
        // z-score for two-sided interval: z_{α/2}
        // For 95% CI: α = 0.05, so we need z_{0.975} ≈ 1.96
        double alpha = 1.0 - confidenceLevel;
        double z = STANDARD_NORMAL.inverseCumulativeProbability(1.0 - alpha / 2.0);
		CenterMargin centerMargin = getCenterMargin(trials, z, pHat);

		return new ProportionEstimate(pHat, trials, centerMargin.lower(), centerMargin.upper(), confidenceLevel);
    }

	/**
     * Computes the one-sided Wilson lower bound for the proportion.
     * 
     * <p>This is the critical method for threshold derivation. It answers:
     * <blockquote>
     *   "What is the lowest value for the true proportion p that is consistent
     *   with our observations at the given confidence level?"
     * </blockquote>
     * 
     * <h3>One-Sided vs Two-Sided</h3>
     * <p>For a one-sided lower bound at confidence (1-α), we use z_{α} (not z_{α/2}).
     * For example, 95% one-sided uses z = 1.645, not 1.96.
     * 
     * <h3>Perfect Baseline Handling (p̂ = 1)</h3>
     * <p>When all trials succeed (k = n), the Wilson formula remains valid and
     * produces a sensible lower bound below 1.0. This avoids the "perfect baseline
     * problem" where naive methods produce threshold = 1.0.
     * 
     * @param successes Number of successes k
     * @param trials Number of trials n
     * @param confidenceLevel Confidence level (1-α), e.g., 0.95 for 95% lower bound
     * @return One-sided lower confidence bound for p
     */
    // javai-ref: JVI-MNVWS4U — do not remove (resolves in javai-orchestrator)
    // javai-ref: JVI-TX478RT — do not remove (resolves in javai-orchestrator)
    public double lowerBound(int successes, int trials, double confidenceLevel) {
        validateInputs(successes, trials);
        double pHat = (double) successes / trials;
        return lowerBoundFromRate(pHat, trials, confidenceLevel);
    }

    /**
     * Computes the one-sided Wilson lower bound from a continuous rate.
     *
     * <p>Same Wilson formula as {@link #lowerBound}, but takes a continuous
     * proportion {@code pHat} rather than discrete successes. Used by the
     * two-step threshold construction (statistical companion §4.3.2),
     * where the second step needs to apply Wilson at {@code n_test} with
     * a rate already derived from the baseline.
     *
     * @param pHat            the rate to wrap, in [0, 1]
     * @param trials          the sample size n at which to evaluate Wilson
     * @param confidenceLevel one-sided confidence level (1 − α)
     * @return Wilson one-sided lower bound at the given rate and sample size
     */
    public double lowerBoundFromRate(double pHat, int trials, double confidenceLevel) {
        if (Double.isNaN(pHat) || pHat < 0.0 || pHat > 1.0) {
            throw new IllegalArgumentException(
                    "pHat must be in [0, 1], got: " + pHat);
        }
        if (trials <= 0) {
            throw new IllegalArgumentException("Trials must be positive, got: " + trials);
        }
        validateConfidenceLevel(confidenceLevel);

        double alpha = 1.0 - confidenceLevel;
        double z = STANDARD_NORMAL.inverseCumulativeProbability(1.0 - alpha);
        CenterMargin centerMargin = getCenterMargin(trials, z, pHat);
        return Math.max(0.0, centerMargin.lower());
    }
    
    /**
     * Computes the z-score for a given confidence level (one-sided).
     * 
     * <p>This is the quantile of the standard normal distribution:
     * z = Φ⁻¹(1-α) where α = 1 - confidenceLevel.
     * 
     * @param confidenceLevel Confidence level (1-α)
     * @return z-score (quantile of standard normal)
     */
    public double zScoreOneSided(double confidenceLevel) {
        validateConfidenceLevel(confidenceLevel);
        double alpha = 1.0 - confidenceLevel;
        return STANDARD_NORMAL.inverseCumulativeProbability(1.0 - alpha);
    }
    
    /**
     * Computes the z-score for a given confidence level (two-sided).
     *
     * <p>This is the quantile of the standard normal distribution:
     * z = Φ⁻¹(1-α/2) where α = 1 - confidenceLevel.
     *
     * @param confidenceLevel Confidence level (1-α)
     * @return z-score (quantile of standard normal)
     */
    public double zScoreTwoSided(double confidenceLevel) {
        validateConfidenceLevel(confidenceLevel);
        double alpha = 1.0 - confidenceLevel;
        return STANDARD_NORMAL.inverseCumulativeProbability(1.0 - alpha / 2.0);
    }

    /**
     * Computes the z-test statistic for a one-sided binomial proportion test.
     *
     * <p>Tests H₀: p ≥ π₀ vs H₁: p < π₀ using the test statistic:
     * <pre>
     *   z = (p̂ - π₀) / √(π₀(1-π₀)/n)
     * </pre>
     *
     * @param observedRate the observed proportion p̂
     * @param hypothesizedRate the hypothesized proportion π₀
     * @param sampleSize the number of trials n
     * @return the z-test statistic, or 0 if the standard error is zero
     */
    public double zTestStatistic(double observedRate, double hypothesizedRate, int sampleSize) {
        if (sampleSize <= 0) {
            return 0.0;
        }
        double se = Math.sqrt(hypothesizedRate * (1 - hypothesizedRate) / sampleSize);
        return se > 0 ? (observedRate - hypothesizedRate) / se : 0.0;
    }

    /**
     * Computes the one-sided p-value P(Z ≤ z) for a left-tailed test.
     *
     * <p>Under H₀: p ≥ π₀ vs H₁: p &lt; π₀, the p-value is the probability of
     * observing a test statistic this low or lower if the null hypothesis is true.
     * A small p-value indicates strong evidence of degradation.
     *
     * @param z the z-score
     * @return the lower-tail probability
     */
    public double oneSidedPValue(double z) {
        return STANDARD_NORMAL.cumulativeProbability(z);
    }
    
    /**
     * Minimum sample count for the normal approximation to the binomial
     * to be statistically meaningful at the given target rate.
     *
     * <p>The classical sufficiency rule is {@code n · p ≥ 5} and
     * {@code n · (1 − p) ≥ 5}, giving
     * <pre>
     *   n_min = ⌈5 / min(p, 1 − p)⌉
     * </pre>
     *
     * <p>Below this floor the sampling distribution is too skewed for
     * the normal approximation to underwrite a verdict; the run must
     * continue past a guaranteed-success short-circuit until the floor
     * is met.
     *
     * @param targetRate the proportion at which the floor is evaluated, in (0, 1)
     * @return the minimum number of trials for the normal approximation
     *         to be valid at {@code targetRate}
     */
    public int minSamplesForNormalApproximation(double targetRate) {
        if (Double.isNaN(targetRate) || targetRate <= 0.0 || targetRate >= 1.0) {
            throw new IllegalArgumentException(
                    "targetRate must be in (0, 1), got: " + targetRate);
        }
        double tighter = Math.min(targetRate, 1.0 - targetRate);
        return (int) Math.ceil(5.0 / tighter);
    }

    private void validateInputs(int successes, int trials) {
        if (trials <= 0) {
            throw new IllegalArgumentException("Trials must be positive, got: " + trials);
        }
        if (successes < 0) {
            throw new IllegalArgumentException("Successes must be non-negative, got: " + successes);
        }
        if (successes > trials) {
            throw new IllegalArgumentException(
                "Successes (" + successes + ") cannot exceed trials (" + trials + ")");
        }
    }
    
    private void validateConfidenceLevel(double confidenceLevel) {
        if (confidenceLevel <= 0.0 || confidenceLevel >= 1.0) {
            throw new IllegalArgumentException(
                "Confidence level must be in (0, 1), got: " + confidenceLevel);
        }
    }

	private static CenterMargin getCenterMargin(int n, double z, double pHat) {
		double zSquared = z * z;
		// Wilson score interval components
		double denominator = 1.0 + zSquared / n;
		double center = (pHat + zSquared / (2.0 * n)) / denominator;
		double margin = z * Math.sqrt(pHat * (1.0 - pHat) / n + zSquared / (4.0 * n * n)) / denominator;
		return new CenterMargin(center, margin);
	}

	private record CenterMargin(double center, double margin) {
		public double lower() {
			return Math.max(0.0, center - margin);
		}
		public double upper() {
			return Math.min(1.0, center + margin);
		}
	}
}

