package org.mavai.punit.statistics;

/**
 * Evaluates whether a probabilistic test's sample size is sufficient to contribute
 * to a compliance determination.
 *
 * <p>All PUnit probabilistic tests are <strong>conformance tests</strong>: they check
 * whether observed behavior conforms to the service contract. A conformance test
 * can additionally contribute to a <strong>compliance determination</strong> only
 * when the sample size provides verification-grade statistical evidence at the
 * declared target.
 *
 * <p>This class answers the question: "If this test observes zero failures, would
 * the statistical evidence be strong enough to support a compliance claim?"
 *
 * <h2>Method</h2>
 * <p>Uses the Wilson score one-sided lower confidence bound. For a perfect
 * observation (p̂ = 1.0), the Wilson lower bound simplifies to:
 * <pre>
 *   lower_bound = n / (n + z²)
 * </pre>
 * where z = Φ⁻¹(1 − α). If this bound is still below the target pass rate,
 * even perfect observed performance cannot justify a compliance claim — the
 * result is a conformance smoke test, not compliance evidence.
 *
 * <h2>Example with α = 0.001 (z ≈ 3.09) and target p₀ = 0.9999</h2>
 * <ul>
 *   <li>n = 200: lower bound ≈ 0.954 &lt; 0.9999 → undersized</li>
 *   <li>n = 500: lower bound ≈ 0.981 &lt; 0.9999 → undersized</li>
 *   <li>n = 10,000: lower bound ≈ 0.999 &lt; 0.9999 → undersized</li>
 *   <li>n ≈ 95,500: lower bound ≈ 0.9999 → sufficient for compliance evidence</li>
 * </ul>
 */
public final class ComplianceEvidenceEvaluator {

    /** Default significance level for the evidence threshold (α = 1 − confidence). */
    public static final double DEFAULT_ALPHA = StatisticalDefaults.DEFAULT_ALPHA;

    /** The exact phrase that appears in reports when sample size is insufficient for compliance. */
    public static final String SIZING_NOTE = "sample not sized for compliance verification";

    private static final BinomialProportionEstimator ESTIMATOR = new BinomialProportionEstimator();

    private ComplianceEvidenceEvaluator() {
        // utility class
    }

    /**
     * Returns true if the sample size is too small to contribute to a compliance
     * determination at the default significance level (α = 0.001).
     *
     * @param samples the number of test samples (N)
     * @param target  the required pass rate (p₀), e.g. 0.9999
     * @return true if the sample size is insufficient for compliance evidence
     */
    public static boolean isUndersized(int samples, double target) {
        return isUndersized(samples, target, DEFAULT_ALPHA);
    }

    /**
     * Returns true if the sample size is too small to contribute to a compliance
     * determination at the given significance level.
     *Probabili
     * <p>A sample is undersized when even a perfect observation (all successes)
     * would produce a one-sided lower confidence bound below the target.
     *
     * @param samples the number of test samples (N)
     * @param target  the required pass rate (p₀)
     * @param alpha   the significance level (e.g. 0.001)
     * @return true if the sample size is insufficient for compliance evidence
     */
    public static boolean isUndersized(int samples, double target, double alpha) {
        if (samples <= 0 || target <= 0.0 || target >= 1.0) {
            return false;
        }
        double confidenceLevel = 1.0 - alpha;
        // Lower bound assuming perfect observation (k = n, i.e. zero failures)
        double lowerBound = ESTIMATOR.lowerBound(samples, samples, confidenceLevel);
        return lowerBound < target;
    }

    /**
     * Determines whether a test has a compliance context — i.e., whether it is
     * anchored to an external standard that could require compliance verification.
     *
     * <p>A test has a compliance context if its threshold origin is a prescribed
     * standard ({@code SLA}, {@code SLO}, or {@code POLICY}) or if a contract
     * reference is provided.
     *
     * @param thresholdOriginName the threshold origin name (e.g. "SLA", "SLO", "POLICY")
     * @param contractRef         the contract reference string (may be null)
     * @return true if the test has a compliance context
     */
    public static boolean hasComplianceContext(String thresholdOriginName, String contractRef) {
        boolean hasPrescribedOrigin = "SLA".equalsIgnoreCase(thresholdOriginName)
                || "SLO".equalsIgnoreCase(thresholdOriginName)
                || "POLICY".equalsIgnoreCase(thresholdOriginName);
        boolean hasContractRef = contractRef != null && !contractRef.isEmpty();
        return hasPrescribedOrigin || hasContractRef;
    }
}
