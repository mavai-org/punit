package org.mavai.punit.api;

import java.time.Instant;

/**
 * Resolved pacing configuration with computed execution plan.
 *
 * <p>This record holds both the raw pacing constraints (as declared by the developer)
 * and the computed execution plan (derived by {@link PacingCalculator}).
 *
 * <p>Formatting methods for human-readable display are in {@link PacingReporter}.
 *
 * <h2>Computed Values</h2>
 * <ul>
 *   <li>{@code effectiveMinDelayMs} — the actual delay between samples after considering all constraints</li>
 *   <li>{@code effectiveConcurrency} — the actual concurrency level (may be throttled)</li>
 *   <li>{@code estimatedDurationMs} — how long execution will take</li>
 *   <li>{@code effectiveRps} — the effective requests per second</li>
 * </ul>
 */
public record PacingConfiguration(
        // Raw constraints
        double maxRequestsPerSecond,
        double maxRequestsPerMinute,
        double maxRequestsPerHour,
        int maxConcurrentRequests,
        long minMsPerSample,

        // Computed execution plan
        long effectiveMinDelayMs,
        int effectiveConcurrency,
        long estimatedDurationMs,
        double effectiveRps
) {

    /**
     * Creates a "no pacing" configuration for when no constraints are specified.
     *
     * @return a configuration with no pacing constraints
     */
    public static PacingConfiguration noPacing() {
        return new PacingConfiguration(0, 0, 0, 0, 0, 0, 1, 0, Double.MAX_VALUE);
    }

    /**
     * Returns true if any pacing constraint is configured.
     */
    public boolean hasPacing() {
        return maxRequestsPerSecond > 0
                || maxRequestsPerMinute > 0
                || maxRequestsPerHour > 0
                || maxConcurrentRequests > 1
                || minMsPerSample > 0;
    }

    /**
     * Returns true if this configuration enables concurrent execution.
     */
    public boolean isConcurrent() {
        return effectiveConcurrency > 1;
    }

    /**
     * Computes the estimated completion time based on start time and duration.
     *
     * @param startTime the execution start time
     * @return the estimated completion time
     */
    public Instant estimatedCompletionTime(Instant startTime) {
        return startTime.plusMillis(estimatedDurationMs);
    }
}

