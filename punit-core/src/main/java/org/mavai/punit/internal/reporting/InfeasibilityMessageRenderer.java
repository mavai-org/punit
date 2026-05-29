package org.mavai.punit.internal.reporting;

import org.mavai.punit.statistics.VerificationFeasibilityEvaluator.FeasibilityResult;

/**
 * Renders human-readable infeasibility messages when a VERIFICATION
 * test's sample size is too small for meaningful statistical evidence.
 *
 * <p>Separates presentation from statistical evaluation: the math
 * lives in
 * {@link org.mavai.punit.statistics.VerificationFeasibilityEvaluator},
 * the prose lives here.
 */
public final class InfeasibilityMessageRenderer {

    private InfeasibilityMessageRenderer() {}

    /**
     * Builds a human-readable infeasibility message.
     *
     * <p>When {@code verbose} is false, produces a concise message suited to
     * non-statisticians. When true, includes the full statistical context
     * (criterion, confidence, alpha, assumptions).
     *
     * @param testName the test identity (service contract id) — appears verbatim
     *                 in the output
     * @param result   the infeasible evaluation result
     * @param verbose  true for full statistical detail, false for summary
     * @return a formatted message explaining why verification is impossible
     */
    public static String render(String testName, FeasibilityResult result, boolean verbose) {
        return verbose
                ? renderVerbose(testName, result)
                : renderSummary(testName, result);
    }

    private static String renderSummary(String testName, FeasibilityResult result) {
        String targetPercent = formatTargetAsPercentage(result.target());
        StringBuilder sb = new StringBuilder();
        sb.append("\nINFEASIBLE VERIFICATION\n\n");
        sb.append(testName).append("\n\n");
        sb.append(String.format(
                "The configured sample size (%d) is too small to verify a %s\n",
                result.configuredSamples(), targetPercent));
        sb.append(String.format("pass rate. At least %d samples are required.\n\n", result.minimumSamples()));
        sb.append("REMEDIATION\n");
        sb.append("  • Increase samples to at least ").append(result.minimumSamples()).append("\n");
        sb.append("  • Set intent = SMOKE to run as a sentinel test");
        return sb.toString();
    }

    private static String renderVerbose(String testName, FeasibilityResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("\nINFEASIBLE VERIFICATION\n\n");
        sb.append(testName).append("\n\n");
        sb.append(String.format(
                "The configured sample size (N=%d) is insufficient for verification\n",
                result.configuredSamples()));
        sb.append("at the declared confidence level.\n\n");
        sb.append("CONFIGURATION\n");
        sb.append("  ").append(PUnitReporter.labelValueLn("Target (p₀):", String.format("%.4f", result.target())));
        sb.append("  ").append(PUnitReporter.labelValueLn("Confidence:",
                String.format("%.2f (α = %.2f)", 1.0 - result.configuredAlpha(), result.configuredAlpha())));
        sb.append("  ").append(PUnitReporter.labelValueLn("Samples:", String.valueOf(result.configuredSamples())));
        sb.append("\nFEASIBILITY\n");
        sb.append("  ").append(PUnitReporter.labelValueLn("Criterion:", result.criterion()));
        sb.append("  ").append(PUnitReporter.labelValueLn("Minimum N:", String.valueOf(result.minimumSamples())));
        sb.append("  ").append(PUnitReporter.labelValueLn("Assumption:", FeasibilityResult.ASSUMPTION));
        sb.append("\nREMEDIATION\n");
        sb.append("  • Increase samples to at least ").append(result.minimumSamples()).append("\n");
        sb.append("  • Set intent = SMOKE to run as a sentinel test");
        return sb.toString();
    }

    /**
     * Builds a soundness-floor breach message — the configured
     * confidence level is below the framework's hard floor and the
     * test cannot underwrite a verdict at that confidence regardless
     * of sampling. Distinct from the
     * {@link #render(String, FeasibilityResult, boolean) "INFEASIBLE
     * VERIFICATION"} message: that one is intent-gated (silent under
     * SMOKE); the soundness-floor breach fires under SMOKE too.
     *
     * @param testName the test identity (service contract id) — appears
     *                 verbatim in the output
     * @param confidence the configured confidence level that breached
     *                   the floor
     * @param floor      the framework's soundness-floor constant
     * @return a formatted message explaining the breach and the fix
     */
    public static String renderSoundnessFloorBreach(
            String testName, double confidence, double floor) {
        String configured = formatTargetAsPercentage(confidence);
        String floorPercent = formatTargetAsPercentage(floor);
        StringBuilder sb = new StringBuilder();
        sb.append("\nINFEASIBLE: confidence below soundness floor\n\n");
        sb.append(testName).append("\n\n");
        sb.append(String.format(
                "The configured confidence level (%s) is below the framework's\n",
                configured));
        sb.append(String.format(
                "soundness floor (%s). A test that cannot make a claim at the\n",
                floorPercent));
        sb.append("floor's confidence level cannot underwrite a verdict — even a\n");
        sb.append("Smoke-intent test does not silently produce results below this\n");
        sb.append("floor.\n\n");
        sb.append("REMEDIATION\n");
        sb.append("  • Raise confidence to at least ").append(floorPercent)
                .append(" (e.g. .atConfidence(0.95))\n");
        sb.append("  • Or remove the .atConfidence(...) override to use the framework default");
        return sb.toString();
    }

    static String formatTargetAsPercentage(double target) {
        double percent = target * 100.0;
        if (percent == Math.floor(percent)) {
            return String.format("%.0f%%", percent);
        }
        String formatted = String.format("%.4f", percent).replaceAll("0+$", "").replaceAll("\\.$", "");
        return formatted + "%";
    }
}
