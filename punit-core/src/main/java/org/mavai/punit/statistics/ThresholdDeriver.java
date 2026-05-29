package org.mavai.punit.statistics;

/**
 * Derives pass/fail thresholds for probabilistic tests from baseline experimental data.
 * 
 * <h2>The Core Problem</h2>
 * <p>An experiment observes p̂_exp = 951/1000 = 95.1%. If we use 95.1% as the threshold
 * for a test running only 100 samples, normal sampling variance will cause false
 * failures approximately 50% of the time.
 * 
 * <h2>The Solution</h2>
 * <p>Derive a threshold that accounts for:
 * <ul>
 *   <li>Uncertainty in the baseline estimate (it's an estimate, not the true value)</li>
 *   <li>Increased variance with smaller test samples</li>
 *   <li>Desired false positive rate (α = 1 - confidence)</li>
 * </ul>
 * 
 * <h2>Supported Approaches</h2>
 * <ul>
 *   <li>{@link #deriveSampleSizeFirst}: Given n_test and confidence, compute threshold</li>
 *   <li>{@link #deriveThresholdFirst}: Given n_test and threshold, compute implied confidence</li>
 * </ul>
 * 
 * @see OperationalApproach
 */
public class ThresholdDeriver {
    
    private final BinomialProportionEstimator estimator;
    
    /**
     * Creates a new threshold deriver with the default proportion estimator.
     */
    public ThresholdDeriver() {
        this(new BinomialProportionEstimator());
    }
    
    /**
     * Creates a new threshold deriver with a custom proportion estimator.
     * 
     * @param estimator The proportion estimator to use
     */
    public ThresholdDeriver(BinomialProportionEstimator estimator) {
        this.estimator = estimator;
    }
    
    /**
     * Derives a threshold using the Sample-Size-First (Cost-Driven) approach.
     * 
     * <p><strong>Given:</strong> Test sample size n_test and desired confidence (1-α)
     * <p><strong>Computes:</strong> Threshold p_threshold
     * 
     * <h3>Method</h3>
     * <p>The threshold is the one-sided Wilson lower bound of the baseline estimate.
     * This ensures that if the true rate has not degraded from the baseline,
     * the probability of a false positive is at most α.
     * 
     * <h3>Example</h3>
     * <pre>
     *   Baseline: 951/1000 (95.1%)
     *   Test samples: 100
     *   Confidence: 95%
     *   
     *   → Threshold ≈ 91.6%
     * </pre>
     * 
     * @param baselineSamples Number of trials in baseline experiment (n_baseline)
     * @param baselineSuccesses Number of successes in baseline experiment (k_baseline)
     * @param testSamples Intended test sample size (n_test)
     * @param thresholdConfidence Desired confidence level (1-α)
     * @return Derived threshold with full context
     */
    // javai-ref: JVI-9HJ92BC — do not remove (resolves in javai-orchestrator)
    public DerivedThreshold deriveSampleSizeFirst(
            int baselineSamples,
            int baselineSuccesses,
            int testSamples,
            double thresholdConfidence) {

        validateInputs(baselineSamples, baselineSuccesses, testSamples, thresholdConfidence);

        double baselineRate = (double) baselineSuccesses / baselineSamples;
        double effectiveBaselineRate = effectiveBaselineRate(
                baselineSuccesses, baselineSamples, thresholdConfidence);
        double threshold = estimator.lowerBoundFromRate(
                effectiveBaselineRate, testSamples, thresholdConfidence);

        DerivationContext context = new DerivationContext(
            baselineRate, baselineSamples, testSamples, thresholdConfidence);

        return new DerivedThreshold(threshold, OperationalApproach.SAMPLE_SIZE_FIRST, context, true);
    }

    /**
     * Effective baseline rate per the statistical companion's two-step
     * construction (§4.3.2): when the baseline observed perfect success
     * (k = n) the raw rate of 1.0 has zero variance and would force the
     * threshold to 1.0; instead the discrete one-sided Wilson lower
     * bound at {@code n_baseline} carries forward as the rate. Otherwise
     * the effective rate is simply the observed proportion.
     */
    private double effectiveBaselineRate(
            int baselineSuccesses, int baselineSamples, double confidence) {
        if (baselineSuccesses == baselineSamples) {
            return estimator.lowerBound(baselineSuccesses, baselineSamples, confidence);
        }
        return (double) baselineSuccesses / baselineSamples;
    }
    
    /**
     * Derives implied confidence using the Threshold-First (Baseline-Anchored) approach.
     * 
     * <p><strong>Given:</strong> Test sample size n_test and explicit threshold p_threshold
     * <p><strong>Computes:</strong> Implied confidence level (1-α)
     * 
     * <h3>Warning</h3>
     * <p>If the explicit threshold equals or exceeds the baseline rate, the implied
     * confidence will be very low (≤ 50%), resulting in a high false positive rate.
     * The returned threshold will have {@code isStatisticallySound = false}.
     * 
     * <h3>Example</h3>
     * <pre>
     *   Baseline: 951/1000 (95.1%)
     *   Test samples: 100
     *   Explicit threshold: 95.1%
     *   
     *   → Implied confidence ≈ 50% (UNSOUND!)
     * </pre>
     * 
     * @param baselineSamples Number of trials in baseline experiment (n_baseline)
     * @param baselineSuccesses Number of successes in baseline experiment (k_baseline)
     * @param testSamples Intended test sample size (n_test)
     * @param explicitThreshold The explicitly specified threshold
     * @return Derived threshold with implied confidence and soundness flag
     */
    // javai-ref: JVI-HHV7KT0 — do not remove (resolves in javai-orchestrator)
    public DerivedThreshold deriveThresholdFirst(
            int baselineSamples,
            int baselineSuccesses,
            int testSamples,
            double explicitThreshold) {
        
        if (explicitThreshold < 0.0 || explicitThreshold > 1.0) {
            throw new IllegalArgumentException(
                "Explicit threshold must be in [0, 1], got: " + explicitThreshold);
        }
        
        double baselineRate = (double) baselineSuccesses / baselineSamples;

        double impliedConfidence = computeImpliedConfidence(
            baselineSuccesses, baselineSamples, testSamples, explicitThreshold);

        // Consider statistically sound if implied confidence ≥ 80%
        boolean isStatisticallySound = impliedConfidence >= 0.80;

        DerivationContext context = new DerivationContext(
            baselineRate, baselineSamples, testSamples, impliedConfidence);

        return new DerivedThreshold(
            explicitThreshold, OperationalApproach.THRESHOLD_FIRST, context, isStatisticallySound);
    }

    /**
     * Implied confidence level for a given target threshold (statistical
     * companion §6.3): the confidence at which
     * {@link #deriveSampleSizeFirst} would produce the same threshold.
     *
     * <p>Uses binary search; the inverse has no closed form.
     */
    private double computeImpliedConfidence(
            int baselineSuccesses, int baselineSamples, int testSamples, double targetThreshold) {
        double tolerance = 1e-10;
        int maxIterations = 100;
        double lowConf = 1e-6;
        double highConf = 1.0 - 1e-6;

        for (int i = 0; i < maxIterations; i++) {
            double midConf = (lowConf + highConf) / 2.0;
            double effectiveRate = effectiveBaselineRate(
                    baselineSuccesses, baselineSamples, midConf);
            double derivedThreshold = estimator.lowerBoundFromRate(
                    effectiveRate, testSamples, midConf);

            if (Math.abs(derivedThreshold - targetThreshold) < tolerance) {
                return midConf;
            }
            // Derived threshold decreases with confidence: higher z → larger margin.
            if (derivedThreshold > targetThreshold) {
                lowConf = midConf;
            } else {
                highConf = midConf;
            }
        }

        return (lowConf + highConf) / 2.0;
    }
    
    private void validateInputs(int baselineSamples, int baselineSuccesses, 
                                int testSamples, double confidence) {
        if (baselineSamples <= 0) {
            throw new IllegalArgumentException(
                "Baseline samples must be positive, got: " + baselineSamples);
        }
        if (baselineSuccesses < 0 || baselineSuccesses > baselineSamples) {
            throw new IllegalArgumentException(
                "Baseline successes must be in [0, " + baselineSamples + "], got: " + baselineSuccesses);
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

