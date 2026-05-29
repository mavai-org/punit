package org.mavai.punit.statistics;

/**
 * Defines the three mutually exclusive operational approaches for configuring
 * probabilistic test thresholds.
 * 
 * <p>At any given time, an organization can control <strong>two of the three variables</strong>
 * (sample size, confidence level, threshold); the third is determined by statistical inference.
 * 
 * <p>This is a fundamental constraint of statistical testing, not a limitation of PUnit.
 * 
 * @see <a href="https://en.wikipedia.org/wiki/Sample_size_determination">Sample Size Determination</a>
 */
public enum OperationalApproach {
    
    /**
     * Cost-Driven approach: Fix sample size and confidence level.
     * 
     * <p><strong>Given:</strong> n (sample size) and (1-α) (confidence level)
     * <p><strong>Derived:</strong> p_threshold (minimum pass rate)
     * 
     * <p>Use this when testing budget is constrained (time, API calls, cost).
     */
    SAMPLE_SIZE_FIRST,
    
    /**
     * Quality-Driven approach: Fix confidence level and minimum detectable effect.
     * 
     * <p><strong>Given:</strong> (1-α) (confidence), δ (effect size), (1-β) (power)
     * <p><strong>Derived:</strong> n (required sample size)
     * 
     * <p>Use this for safety-critical systems where confidence is paramount.
     */
    CONFIDENCE_FIRST,
    
    /**
     * Baseline-Anchored approach: Fix sample size and explicit threshold.
     * 
     * <p><strong>Given:</strong> n (sample size) and p_threshold (explicit threshold)
     * <p><strong>Derived:</strong> (1-α) (implied confidence level)
     * 
     * <p>Use this when matching the exact baseline rate as threshold.
     * Framework will warn if implied confidence is too low.
     */
    THRESHOLD_FIRST
}

