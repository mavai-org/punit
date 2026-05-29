package org.mavai.punit.sentinel;

import java.io.PrintStream;
import org.mavai.punit.verdict.ProbabilisticTestVerdict;

/**
 * Owns the console output for the Sentinel CLI summary.
 *
 * <p>Sentinel runs to completion before producing any output;
 * per-method progress is not surfaced (each verdict is dispatched
 * to the configured {@link SentinelConfiguration#verdictSink()} as
 * it lands, which is the user-controllable per-event channel). This
 * class only owns the suite-level summary printed at the end of a
 * run.
 *
 * <h2>Test output</h2>
 * <p>Tests deliver verdicts. The summary shows test-level pass / fail
 * counts and the overall result.
 *
 * <h2>Experiment output</h2>
 * <p>Experiments collect data — they do not deliver verdicts. The
 * summary lists each experiment with its sample counts.
 */
class SentinelConsoleReporter {

    private static final String SEPARATOR = "─".repeat(40);

    private final PrintStream out;

    SentinelConsoleReporter(PrintStream out) {
        this.out = out;
    }

    void printTestSummary(SentinelResult result) {
        out.println();
        out.println("Sentinel Test Summary");
        out.println(SEPARATOR);
        out.println("Total:    " + result.totalTests());
        out.println("Passed:   " + result.passed());
        out.println("Failed:   " + result.failed());
        out.println("Skipped:  " + result.skipped());
        out.println("Duration: " + result.totalDuration().toMillis() + "ms");
        out.println();

        if (result.noneExecuted()) {
            out.println("Result: NO MATCH");
        } else if (result.allPassed()) {
            out.println("Result: PASS");
        } else {
            out.println("Result: FAIL");
            result.verdicts().stream()
                    .filter(v -> !v.junitPassed())
                    .forEach(v -> {
                        String testName = v.identity().className() + "." + v.identity().methodName();
                        out.println("  FAILED: " + testName);
                    });
        }
    }

    void printExperimentSummary(SentinelResult result) {
        out.println();
        out.println("Sentinel Experiment Summary");
        out.println(SEPARATOR);

        for (ProbabilisticTestVerdict verdict : result.verdicts()) {
            String testName = verdict.identity().className() + "." + verdict.identity().methodName();
            int samples = verdict.execution().samplesExecuted();
            int successes = verdict.execution().successes();
            int failures = verdict.execution().failures();
            out.println(testName);
            out.printf("  Samples:   %d  Successes: %d  Failures: %d%n",
                    samples, successes, failures);
        }

        out.println();
        out.println("Duration: " + result.totalDuration().toMillis() + "ms");
        out.println("Result: COMPLETE");
    }
}
