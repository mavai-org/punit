package org.javai.punit.statistics;

/**
 * Evaluates whether a probabilistic test's configuration is sufficient
 * for verification-grade statistical evidence.
 *
 * <p>This is the central feasibility function for the PUnit framework (Req 12b).
 * It answers the question: <em>"Given the configured sample size, target pass rate,
 * and confidence level, can PUnit produce a statistically meaningful verification
 * verdict?"</em>
 *
 * <h2>Method</h2>
 * <p>Uses the Wilson score one-sided lower confidence bound. For a perfect
 * observation (all samples pass, p&#770; = 1.0), the Wilson lower bound simplifies to:
 * <pre>
 *   lower_bound = n / (n + z&sup2;)
 * </pre>
 * where z = &Phi;&sup1;(1 &minus; &alpha;) and &alpha; = 1 &minus; confidence.
 *
 * <p>A sample size is <strong>feasible</strong> if this bound &ge; target. In other
 * words: even under the best possible observation, the statistical evidence must
 * be strong enough to place the lower confidence bound above the target.
 *
 * <h2>Minimum Sample Size</h2>
 * <p>The minimum N is computed by solving {@code n / (n + z&sup2;) &ge; p&#8320;}:
 * <pre>
 *   N_min = &lceil;p&#8320; &middot; z&sup2; / (1 &minus; p&#8320;)&rceil;
 * </pre>
 *
 * <h2>Assumptions</h2>
 * <ul>
 *   <li>Each sample is an independent, identically distributed Bernoulli trial</li>
 *   <li>The system's true pass rate is stationary across samples</li>
 * </ul>
 *
 * @see ComplianceEvidenceEvaluator
 */
public final class VerificationFeasibilityEvaluator {

    private static final BinomialProportionEstimator ESTIMATOR = new BinomialProportionEstimator();

    private VerificationFeasibilityEvaluator() {
        // utility class
    }

    /**
     * Result of a feasibility evaluation.
     *
     * @param feasible        true if the configured sample size is sufficient for verification
     * @param minimumSamples  the minimum N required for verification at the given target and confidence
     * @param configuredAlpha the significance level (1 &minus; confidence)
     * @param target          the target pass rate (p&#8320;)
     * @param configuredSamples the sample size as configured
     * @param criterion       human-readable description of the statistical criterion used
     */
    public record FeasibilityResult(
            boolean feasible,
            int minimumSamples,
            double configuredAlpha,
            double target,
            int configuredSamples,
            String criterion
    ) {
        /** Human-readable criterion name. */
        public static final String CRITERION = "Wilson score one-sided lower bound";

        /** Human-readable assumption statement. */
        public static final String ASSUMPTION = "i.i.d. Bernoulli trials";
    }

    /**
     * Evaluates whether the given configuration is feasible for verification.
     *
     * @param samples    the configured number of samples (N); must be &gt; 0
     * @param target     the target pass rate (p&#8320;); must be in (0, 1) exclusive
     * @param confidence the confidence level (1 &minus; &alpha;); must be in (0, 1) exclusive
     * @return the feasibility result including minimum required samples
     * @throws IllegalArgumentException if any parameter is out of range
     */
    // javai-ref: JVI-M5YQ6RB — do not remove (resolves in javai-orchestrator)
    public static FeasibilityResult evaluate(int samples, double target, double confidence) {
        if (samples <= 0) {
            throw new IllegalArgumentException("samples must be > 0, got: " + samples);
        }
        if (target <= 0.0 || target >= 1.0) {
            throw new IllegalArgumentException(
                    "target must be in (0, 1) exclusive, got: " + target);
        }
        if (confidence <= 0.0 || confidence >= 1.0) {
            throw new IllegalArgumentException(
                    "confidence must be in (0, 1) exclusive, got: " + confidence);
        }

        double alpha = 1.0 - confidence;
        double z = ESTIMATOR.zScoreOneSided(confidence);
        double zSquared = z * z;

        // Minimum N: solve n / (n + z²) >= target → n >= target * z² / (1 - target)
        int minimumSamples = (int) Math.ceil(target * zSquared / (1.0 - target));

        // Check feasibility: Wilson lower bound for perfect observation (k = n)
        double lowerBound = ESTIMATOR.lowerBound(samples, samples, confidence);
        boolean feasible = lowerBound >= target;

        return new FeasibilityResult(
                feasible,
                minimumSamples,
                alpha,
                target,
                samples,
                FeasibilityResult.CRITERION
        );
    }
}
