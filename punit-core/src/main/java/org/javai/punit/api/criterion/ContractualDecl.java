package org.javai.punit.api.criterion;

import java.time.Duration;

import org.javai.punit.api.PercentileKey;

/**
 * The kind selector returned by {@link Criteria#meeting()}.
 *
 * <p>Opens a contractual-mode chain — the threshold is supplied by
 * the author (a rate, a per-percentile duration ceiling), not derived
 * from a baseline. The origin defaults to
 * {@link org.javai.punit.api.ThresholdOrigin#UNSPECIFIED} and is
 * overridden when the author later calls
 * {@code .contractRef(origin, ref)} on the kind-specific decl.
 *
 * <p>Three kind-selector methods, each returning a kind-specific
 * decl:
 * <ul>
 *   <li>{@link #passRate(double)} → {@link CriterionDecl} —
 *       statistical pass-rate criterion at the supplied rate.</li>
 *   <li>{@link #zeroFailures()} → {@link CriterionDecl} — explicit
 *       any-failure-fails commitment.</li>
 *   <li>{@link #atMost(PercentileKey, Duration)} →
 *       {@link LatencyCriterion} — contractual latency criterion
 *       with the supplied per-percentile ceiling. Chain further
 *       {@code .atMost(...)} calls on the returned criterion to
 *       declare additional ceilings.</li>
 * </ul>
 *
 * <p>The interface itself is non-generic; the type parameter
 * {@code <O>} enters the chain at the kind-selector method that
 * needs it ({@code passRate}, {@code zeroFailures}). The latency
 * kind selector has no type parameter — latency criteria care about
 * durations, not per-sample outcome values.
 *
 * <p>This is the canonical contractual-criterion authoring surface,
 * opened by {@link Criteria#meeting()}.
 */
public interface ContractualDecl {

    /**
     * Statistical, contractual pass-rate criterion. PASS iff
     * observed pass-rate ≥ {@code rate}.
     *
     * @param rate the required pass-rate, in {@code [0, 1)}
     */
    <O> CriterionDecl<O> passRate(double rate);

    /**
     * Explicit zero-failures criterion. Any failed sample fails
     * the criterion.
     */
    <O> CriterionDecl<O> zeroFailures();

    /**
     * Contractual latency ceiling at the asserted percentile.
     * Returns a {@link LatencyCriterion} suitable for return from
     * {@link org.javai.punit.api.Contract#latency()}. Chain further
     * {@code .atMost(...)} calls on the returned criterion to
     * declare additional ceilings.
     *
     * <p>The monotonicity check fires when two or more
     * {@code .atMost(...)} calls disagree with the order-statistic
     * ordering (higher percentile assigned a lower duration than a
     * lower percentile).
     */
    LatencyCriterion atMost(PercentileKey key, Duration value);
}
