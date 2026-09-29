package org.mavai.punit.statistics;

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
 *   <li>{@link #lowerBound}: Wilson score lower bound (one-sided)</li>
 *   <li>{@link #standardError}: Standard error of the proportion estimate</li>
 * </ul>
 * 
 * <p>The Wilson interval is the methodology's <em>descriptive</em>
 * interval (companion §2.3.1): it is reported beside a verdict and decides
 * none. Verdicts are decided by the exact rules in {@link RegressionRule}
 * and {@link ComplianceRule}.
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
    // mavai-ref: JVI-58DBYP~ — do not remove (resolves in mavai-orchestrator)
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
     * <p>A descriptive bound, reported beside verdicts; it decides none.
     * It answers:
     * <blockquote>
     *   "What is the lowest value for the true proportion p that is consistent
     *   with our observations at the given confidence level?"
     * </blockquote>
     * 
     * <h3>One-Sided vs Two-Sided</h3>
     * <p>For a one-sided lower bound at confidence (1-α), we use z_{α} (not z_{α/2}).
     * For example, 95% one-sided uses z = 1.645, not 1.96.
     * 
     * @param successes Number of successes k
     * @param trials Number of trials n
     * @param confidenceLevel Confidence level (1-α), e.g., 0.95 for 95% lower bound
     * @return One-sided lower confidence bound for p
     */
    // mavai-ref: JVI-MNVWS4U — do not remove (resolves in mavai-orchestrator)
    // mavai-ref: JVI-TX478RT — do not remove (resolves in mavai-orchestrator)
    public double lowerBound(int successes, int trials, double confidenceLevel) {
        validateInputs(successes, trials);
        double pHat = (double) successes / trials;
        return lowerBoundFromRate(pHat, trials, confidenceLevel);
    }

    /**
     * The one-sided Wilson lower bound at a rate {@code pHat} and size
     * {@code trials}.
     */
    private double lowerBoundFromRate(double pHat, int trials, double confidenceLevel) {
        if (Double.isNaN(pHat) || pHat < 0.0 || pHat > 1.0) {
            throw new IllegalArgumentException(
                    "pHat must be in [0, 1], got: " + pHat);
        }
        if (trials <= 0) {
            throw new IllegalArgumentException("Trials must be positive, got: " + trials);
        }
        validateConfidenceLevel(confidenceLevel);

        // At pHat = 0 the centre and the margin are the same quantity,
        // z^2 / (2n), so the bound is exactly 0 at every n and confidence.
        // Floating point leaves a residue near 1e-18 at some sizes; return
        // the algebraic value. The exact comparison is on an input whose
        // domain is discrete (k/n is exactly +0.0 when k = 0).
        if (pHat == 0.0) {
            return 0.0;
        }

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

