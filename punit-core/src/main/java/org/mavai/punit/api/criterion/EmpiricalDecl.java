package org.mavai.punit.api.criterion;

import org.mavai.punit.api.PercentileKey;

/**
 * The kind selector returned by {@link Criteria#empirical()}.
 *
 * <p>Opens an empirical-mode chain — the threshold is derived from
 * the resolved baseline at evaluate time, not supplied by the author.
 *
 * <p>Two kind-selector methods:
 * <ul>
 *   <li>{@link #passRate()} → {@link CriterionDecl} — empirical
 *       pass-rate criterion (Wilson-vs-baseline procedure). No rate
 *       argument — the threshold is derived from the baseline.</li>
 *   <li>{@link #atMost(PercentileKey)} → {@link LatencyCriterion} —
 *       empirical latency criterion at the asserted percentile. No
 *       duration argument — the upper bound is derived from the
 *       baseline at the configured confidence.</li>
 * </ul>
 *
 * <p>No {@code zeroFailures()} method — zero-failures is
 * intrinsically contractual (the threshold is exactly 1.0, with no
 * baseline to derive from).
 *
 * <p>The interface itself is non-generic; the type parameter
 * {@code <O>} enters the chain at {@code passRate()}. The latency
 * kind selector has no type parameter — latency criteria care about
 * durations, not per-sample outcome values.
 *
 * <p>This is the canonical empirical-criterion authoring surface,
 * opened by {@link Criteria#empirical()}.
 */
public interface EmpiricalDecl {

    /**
     * Statistical, empirical pass-rate criterion. Threshold derived
     * from the resolved baseline via the Wilson-lower-bound procedure.
     */
    <O> CriterionDecl<O> passRate();

    /**
     * Empirical latency criterion at the asserted percentile.
     * Returns a {@link LatencyCriterion} suitable for return from
     * {@link org.mavai.punit.api.Contract#latency()}. Chain further
     * {@code .atMost(...)} calls on the returned criterion to assert
     * additional percentiles.
     */
    LatencyCriterion atMost(PercentileKey key);
}
