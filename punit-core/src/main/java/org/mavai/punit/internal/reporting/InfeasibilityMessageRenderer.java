package org.mavai.punit.internal.reporting;

import java.util.List;

import org.mavai.punit.api.spec.ConfigurationRefusal;
import org.mavai.punit.statistics.ConfigurationError;


/**
 * Renders human-readable messages for configurations the framework
 * refuses before any sample runs: the configuration errors of
 * Statistical Companion §5.7.1, and the soundness-floor breach.
 */
public final class InfeasibilityMessageRenderer {

    private InfeasibilityMessageRenderer() {}

    /**
     * Builds the message for a refused configuration: every
     * configuration error, in the fixed reporting order, with the reason
     * each part of the configuration is invalid, and the remedies.
     *
     * @param testName the test identity (service contract id) — appears verbatim
     * @param refusals the refusals, in the fixed reporting order; at least one
     * @return a formatted message naming every code
     */
    public static String renderRefusal(String testName, List<ConfigurationRefusal> refusals) {
        StringBuilder sb = new StringBuilder();
        sb.append("\nCONFIGURATION REFUSED\n\n");
        sb.append(testName).append("\n\n");
        sb.append("The test was refused before any sample ran:\n");
        for (ConfigurationRefusal refusal : refusals) {
            sb.append("  • ").append(refusal.code().name()).append(" — ")
                    .append(refusal.reason()).append("\n");
        }
        sb.append("\nREMEDIATION\n");
        boolean larger = refusals.stream()
                .anyMatch(r -> r.code() == ConfigurationError.TEST_LARGER_THAN_BASELINE);
        boolean infeasible = refusals.stream()
                .anyMatch(r -> r.code() == ConfigurationError.COMPLIANCE_INFEASIBLE);
        if (larger) {
            sb.append("  • Measure a baseline at least as large as the test, or run fewer samples\n");
        }
        if (infeasible) {
            sb.append("  • Increase samples to at least the feasibility minimum\n");
            sb.append("  • Set intent = SMOKE to run as a sentinel test (a pass is then not possible)\n");
        }
        return sb.toString().stripTrailing();
    }

    /**
     * Builds a soundness-floor breach message — the configured
     * confidence level is below the framework's hard floor and the
     * test cannot underwrite a verdict at that confidence regardless
     * of sampling. It fires under SMOKE too.
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
