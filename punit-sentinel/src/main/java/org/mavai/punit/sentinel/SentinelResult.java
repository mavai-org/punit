package org.mavai.punit.sentinel;

import java.time.Duration;
import java.util.List;
import org.mavai.punit.verdict.ProbabilisticTestVerdict;

/**
 * The aggregate outcome of a Sentinel execution — either a test run or an experiment run.
 *
 * <p>The caller (scheduler, HTTP handler, operator script) uses this result to
 * determine next steps (alerting, circuit-breaking, etc.). The Sentinel itself
 * takes no action beyond reporting.
 *
 * @param totalTests total number of tests or experiments executed
 * @param passed number that passed
 * @param failed number that failed
 * @param skipped number skipped (e.g., missing spec)
 * @param verdicts individual verdict details
 * @param totalDuration wall-clock time for the entire run
 */
public record SentinelResult(
        int totalTests,
        int passed,
        int failed,
        int skipped,
        List<ProbabilisticTestVerdict> verdicts,
        Duration totalDuration
) {

    /**
     * Returns {@code true} if at least one test executed and all passed.
     */
    public boolean allPassed() {
        return totalTests > 0 && failed == 0 && skipped == 0;
    }

    /**
     * Returns {@code true} if no tests or experiments were executed.
     */
    public boolean noneExecuted() {
        return totalTests == 0;
    }
}
