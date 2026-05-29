package org.mavai.punit.statistics;

/**
 * Evaluates test results against a threshold and produces qualified verdicts.
 *
 * <h2>Purpose</h2>
 * <p>Unlike simple pass/fail testing, this evaluator provides:
 * <ul>
 *   <li>Statistical context for the decision</li>
 *   <li>False positive probability quantification</li>
 *   <li>Human-readable interpretation</li>
 * </ul>
 *
 * <h2>Interpretation of Failures</h2>
 * <p>A test failure means:
 * <blockquote>
 *   "The observed behavior is statistically inconsistent with the baseline
 *   at the configured confidence level."
 * </blockquote>
 *
 * <p>It does NOT mean "definitely broken." The {@link VerdictWithConfidence#falsePositiveProbability()}
 * quantifies the chance that this failure is due to sampling variance.
 *
 * @see VerdictWithConfidence
 */
public class TestVerdictEvaluator {

    private final VerdictInterpreter interpreter = new VerdictInterpreter();

    /**
     * Evaluates test results against a threshold.
     *
     * <h3>Decision Rule</h3>
     * <pre>
     *   observed_rate = k_test / n_test
     *
     *   if observed_rate >= threshold:
     *       PASS (no evidence of degradation)
     *   else:
     *       FAIL (evidence of degradation at confidence level)
     * </pre>
     *
     * @param testSuccesses Number of successful samples in the test (k_test)
     * @param testSamples Total number of samples in the test (n_test)
     * @param threshold The derived threshold against which to compare
     * @return Verdict with full statistical context
     */
    // javai-ref: JVI-T08PKZ6 — do not remove (resolves in javai-orchestrator)
    public VerdictWithConfidence evaluate(int testSuccesses, int testSamples, DerivedThreshold threshold) {
        validateInputs(testSuccesses, testSamples, threshold);

        double observedRate = (double) testSuccesses / testSamples;
        boolean passed = observedRate >= threshold.value();

        // False positive probability = alpha = 1 - confidence
        // This is the probability of failing when the system is actually fine
        double falsePositiveProbability = passed ? 0.0 : (1.0 - threshold.context().confidence());

        String interpretation = interpreter.generateInterpretation(passed, observedRate, threshold);

        return new VerdictWithConfidence(
            passed,
            observedRate,
            threshold,
            falsePositiveProbability,
            interpretation
        );
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
     * @param verdicts Array of individual test verdicts
     * @return Summary interpretation
     */
    public String summarizeMultipleRuns(VerdictWithConfidence... verdicts) {
        return interpreter.summarizeMultipleRuns(verdicts);
    }

    private void validateInputs(int testSuccesses, int testSamples, DerivedThreshold threshold) {
        if (testSamples <= 0) {
            throw new IllegalArgumentException("Test samples must be positive, got: " + testSamples);
        }
        if (testSuccesses < 0) {
            throw new IllegalArgumentException("Test successes must be non-negative, got: " + testSuccesses);
        }
        if (testSuccesses > testSamples) {
            throw new IllegalArgumentException(
                "Test successes (" + testSuccesses + ") cannot exceed samples (" + testSamples + ")");
        }
        if (threshold == null) {
            throw new IllegalArgumentException("Threshold must not be null");
        }
    }
}
