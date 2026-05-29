package org.mavai.punit.api.spec;

/**
 * Marker for a kind of baseline statistics a criterion may read.
 *
 * <p>Unsealed on purpose: a future criterion kind (collision
 * probability, expected-value, distributional fit, …) adds a new
 * {@link BaselineStatistics} implementation without touching existing
 * types or any existing criterion. See
 * {@code docs/DES-CRITERION-EXTENSIBILITY.md} for the rationale.
 *
 * <p>The single contract is {@link #sampleCount()} — every baseline
 * is, by definition, a measurement of N invocations and must report
 * that count. The framework's empirical-integrity checks
 * (see {@link EmpiricalChecks}) depend on it.
 */
public interface BaselineStatistics {

    /**
     * @return the count of invocations the baseline was measured over.
     *         Non-negative.
     */
    int sampleCount();
}
