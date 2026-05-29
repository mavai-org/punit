package org.mavai.punit.statistics;

import org.mavai.punit.internal.reporting.RateFormat;

/**
 * Generates human-readable interpretations of test verdicts.
 *
 * <p>Extracted from {@link TestVerdictEvaluator} to separate prose
 * generation from statistical evaluation logic.
 */
class VerdictInterpreter {

    /**
     * Generates a human-readable interpretation of a single test result.
     *
     * @param passed whether the test passed
     * @param observedRate the observed success rate
     * @param threshold the threshold used
     * @return interpretation string
     */
    String generateInterpretation(boolean passed, double observedRate, DerivedThreshold threshold) {
        double thresholdValue = threshold.value();
        double confidence = threshold.context().confidence();

        if (passed) {
            return String.format(
                "Observed %s >= %s threshold. No evidence of degradation from baseline.",
                RateFormat.format(observedRate),
                RateFormat.format(thresholdValue)
            );
        } else {
            double shortfall = thresholdValue - observedRate;
            double falsePositivePercent = (1.0 - confidence) * 100;

            return String.format(
                "Observed %s < %s min pass rate (shortfall: %s). " +
                "This indicates DEGRADATION from the baseline with %.0f%% confidence. " +
                "There is a %.1f%% probability this failure is due to sampling variance " +
                "rather than actual system degradation.",
                RateFormat.format(observedRate),
                RateFormat.format(thresholdValue),
                RateFormat.format(shortfall),
                confidence * 100,
                falsePositivePercent
            );
        }
    }

    /**
     * Evaluates multiple test runs and determines if there's a consistent pattern.
     *
     * <p>This is useful for distinguishing between:
     * <ul>
     *   <li>Single failure (might be false positive)</li>
     *   <li>Repeated failures (strong evidence of degradation)</li>
     * </ul>
     *
     * @param verdicts array of individual test verdicts
     * @return summary interpretation
     */
    String summarizeMultipleRuns(VerdictWithConfidence... verdicts) {
        if (verdicts.length == 0) {
            return "No test runs to summarize.";
        }

        int passCount = 0;
        int failCount = 0;
        double totalFalsePositiveProb = 1.0;

        for (VerdictWithConfidence verdict : verdicts) {
            if (verdict.passed()) {
                passCount++;
            } else {
                failCount++;
                totalFalsePositiveProb *= verdict.falsePositiveProbability();
            }
        }

        if (failCount == 0) {
            return String.format(
                "All %d runs passed. No evidence of degradation.",
                passCount
            );
        } else if (failCount == 1) {
            return String.format(
                "%d of %d runs failed. Single failure may be a false positive (%.1f%% probability).",
                failCount, verdicts.length,
                verdicts[0].falsePositiveProbability() * 100
            );
        } else {
            return String.format(
                "%d of %d runs failed. Probability of ALL being false positives: %.4f%%. " +
                "Strong evidence of actual degradation.",
                failCount, verdicts.length,
                totalFalsePositiveProb * 100
            );
        }
    }
}
