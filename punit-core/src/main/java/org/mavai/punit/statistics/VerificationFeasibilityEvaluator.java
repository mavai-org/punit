package org.mavai.punit.statistics;

/**
 * Whether a normative (compliance) design of a given size can pass at all
 * (companion §5.7.1).
 *
 * <p>Under {@code compliance/exact-binomial} a pass is possible only if the
 * all-success outcome clears the exact test, {@code p_req^n ≤ alpha}, so
 * the minimum is {@code N_min = ⌈log alpha / log p_req⌉}. Feasible means a
 * pass is possible, not that the design is adequately powered (§5.5).
 *
 * <p>Under VERIFICATION intent an infeasible design is refused before it
 * runs ({@link ConfigurationError#COMPLIANCE_INFEASIBLE}); under SMOKE it
 * runs and reports that a pass is not possible at this size.
 *
 * @see ComplianceRule#minimumFeasibleSamples(double, double)
 */
public final class VerificationFeasibilityEvaluator {

    private VerificationFeasibilityEvaluator() {
        // utility class
    }

    /**
     * Result of a feasibility evaluation.
     *
     * @param feasible          true if a pass is possible at the configured size
     * @param minimumSamples    the smallest size at which a pass is possible
     * @param configuredAlpha   the one-sided level (1 &minus; confidence)
     * @param target            the requirement (p&#8320;)
     * @param configuredSamples the sample size as configured
     * @param criterion         the name of the feasibility criterion, as reported
     */
    public record FeasibilityResult(
            boolean feasible,
            int minimumSamples,
            double configuredAlpha,
            double target,
            int configuredSamples,
            String criterion
    ) {
        /** The feasibility criterion's name. */
        public static final String CRITERION = "exact_binomial_pass_possible";
        /** Human-readable assumption statement. */
        public static final String ASSUMPTION = "i.i.d. Bernoulli trials";
    }

    /**
     * Evaluates whether a normative design of {@code samples} can pass.
     *
     * @param samples    the configured number of samples (N); must be &gt; 0
     * @param target     the requirement (p&#8320;); must be in (0, 1) exclusive
     * @param confidence the confidence level (1 &minus; &alpha;); must be in (0, 1) exclusive
     * @return the feasibility result including the minimum feasible size
     * @throws IllegalArgumentException if any parameter is out of range
     */
    // mavai-ref: JVI-M5YQ6RB — do not remove (resolves in mavai-orchestrator)
    public static FeasibilityResult evaluate(int samples, double target, double confidence) {
        if (samples <= 0) {
            throw new IllegalArgumentException("samples must be > 0, got: " + samples);
        }
        if (target <= 0.0 || target >= 1.0) {
            throw new IllegalArgumentException(
                    "target must be in (0, 1) exclusive, got: " + target);
        }
        double alpha = Methodology.alphaFromConfidence(confidence);
        int minimumSamples = ComplianceRule.minimumFeasibleSamples(target, alpha);
        return new FeasibilityResult(
                samples >= minimumSamples,
                minimumSamples,
                alpha,
                target,
                samples,
                FeasibilityResult.CRITERION);
    }
}
