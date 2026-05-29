package org.mavai.punit.statistics;

/**
 * Represents a point estimate and confidence interval for a binomial proportion.
 * 
 * <p>In statistical notation:
 * <ul>
 *   <li><strong>p̂</strong> (p-hat): Maximum Likelihood Estimate of the true proportion p</li>
 *   <li><strong>n</strong>: Sample size (number of Bernoulli trials)</li>
 *   <li><strong>[lower, upper]</strong>: (1-α) confidence interval for p</li>
 * </ul>
 * 
 * <p>The confidence interval is computed using the Wilson score method, which provides
 * better coverage than the normal approximation for all sample sizes, especially when
 * p is near 0 or 1.
 * 
 * @param pointEstimate The maximum likelihood estimate p̂ = k/n, where k is successes
 * @param sampleSize The number of trials n
 * @param lowerBound The lower bound of the confidence interval
 * @param upperBound The upper bound of the confidence interval
 * @param confidenceLevel The confidence level (1-α), e.g., 0.95 for 95% confidence
 * 
 * @see <a href="https://en.wikipedia.org/wiki/Binomial_proportion_confidence_interval">Binomial Proportion Confidence Interval</a>
 */
public record ProportionEstimate(
    double pointEstimate,
    int sampleSize,
    double lowerBound,
    double upperBound,
    double confidenceLevel
) {
    /**
     * Validates the estimate parameters.
     */
    public ProportionEstimate {
        if (pointEstimate < 0.0 || pointEstimate > 1.0) {
            throw new IllegalArgumentException(
                "Point estimate must be in [0, 1], got: " + pointEstimate);
        }
        if (sampleSize <= 0) {
            throw new IllegalArgumentException(
                "Sample size must be positive, got: " + sampleSize);
        }
        if (lowerBound < 0.0 || lowerBound > 1.0) {
            throw new IllegalArgumentException(
                "Lower bound must be in [0, 1], got: " + lowerBound);
        }
        if (upperBound < 0.0 || upperBound > 1.0) {
            throw new IllegalArgumentException(
                "Upper bound must be in [0, 1], got: " + upperBound);
        }
        if (lowerBound > upperBound) {
            throw new IllegalArgumentException(
                "Lower bound must not exceed upper bound: [" + lowerBound + ", " + upperBound + "]");
        }
        if (confidenceLevel <= 0.0 || confidenceLevel >= 1.0) {
            throw new IllegalArgumentException(
                "Confidence level must be in (0, 1), got: " + confidenceLevel);
        }
    }
    
    /**
     * Returns the width of the confidence interval.
     * 
     * <p>Narrower intervals indicate more precise estimates.
     * 
     * @return upperBound - lowerBound
     */
    public double intervalWidth() {
        return upperBound - lowerBound;
    }
    
    /**
     * Returns the margin of error (half the interval width).
     * 
     * @return (upperBound - lowerBound) / 2
     */
    public double marginOfError() {
        return intervalWidth() / 2.0;
    }
}

