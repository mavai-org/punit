package org.mavai.punit.statistics;

/**
 * Whether a test's sample size is sufficient to contribute to a compliance
 * determination: whether any outcome of this size could demonstrate the
 * requirement under {@code compliance/exact-binomial} (companion §3.6,
 * §5.7.1). A sample below the feasibility minimum cannot pass at any
 * outcome, so its result carries no evidence about compliance.
 */
public final class ComplianceEvidenceEvaluator {

    /** Default one-sided level for the evidence threshold. */
    public static final double DEFAULT_ALPHA = StatisticalDefaults.DEFAULT_ALPHA;

    /** The exact phrase that appears in reports when sample size is insufficient for compliance. */
    public static final String SIZING_NOTE = "sample not sized for compliance verification";

    private ComplianceEvidenceEvaluator() {
        // utility class
    }

    /**
     * Returns true if the sample size is too small to contribute to a
     * compliance determination at the default level.
     *
     * @param samples the number of test samples (N)
     * @param target  the requirement (p&#8320;)
     * @return true if no outcome of this size could demonstrate the requirement
     */
    public static boolean isUndersized(int samples, double target) {
        return isUndersized(samples, target, DEFAULT_ALPHA);
    }

    /**
     * Returns true if the sample size is too small to contribute to a
     * compliance determination at the given level: even the all-success
     * outcome would not demonstrate the requirement.
     *
     * @param samples the number of test samples (N)
     * @param target  the requirement (p&#8320;)
     * @param alpha   the one-sided level
     * @return true if no outcome of this size could demonstrate the requirement
     */
    public static boolean isUndersized(int samples, double target, double alpha) {
        if (samples <= 0 || target <= 0.0 || target >= 1.0) {
            return false;
        }
        return samples < ComplianceRule.minimumFeasibleSamples(target, alpha);
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
