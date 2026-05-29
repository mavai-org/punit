package org.mavai.punit.api.spec;

/**
 * Sentinel for criteria that read no baseline at all. A criterion
 * that declares {@code NoStatistics} as its statistics kind never
 * consults {@link EvaluationContext#baseline()} — the framework
 * therefore treats its resolution as always-empty and never routes
 * any persisted baseline slice to it.
 */
public enum NoStatistics implements BaselineStatistics {
    INSTANCE;

    /**
     * @return 0 — {@code NoStatistics} represents the absence of a
     *         baseline, so there is no sample count to report. The
     *         framework never routes this value into the empirical-
     *         integrity checks; the override exists only to satisfy
     *         the {@link BaselineStatistics} contract.
     */
    @Override
    public int sampleCount() {
        return 0;
    }
}
