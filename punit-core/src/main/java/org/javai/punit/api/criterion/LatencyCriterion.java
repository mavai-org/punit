package org.javai.punit.api.criterion;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

import org.javai.punit.api.LatencySpec;
import org.javai.punit.api.PercentileKey;
import org.javai.punit.api.ThresholdOrigin;

/**
 * The contract's per-percentile latency commitment. 0..1 per
 * contract; surfaced via the {@link org.javai.punit.api.Contract#latency()}
 * sibling method.
 *
 * <p>Authored via the {@link Criteria#meeting()} / {@link Criteria#empirical()}
 * factories chained with {@link #atMost(PercentileKey, Duration)} or
 * {@link #atMost(PercentileKey)}, and bound with
 * {@link #contractRef(ThresholdOrigin, String)}:
 *
 * <pre>{@code
 * import static org.javai.punit.api.PercentileKey.P95;
 * import static org.javai.punit.api.PercentileKey.P99;
 * import static org.javai.punit.api.ThresholdOrigin.SLA;
 * import static org.javai.punit.api.criterion.Criteria.meeting;
 * import static org.javai.punit.api.criterion.Criteria.empirical;
 * import static java.time.Duration.ofMillis;
 *
 * // empirical
 * empirical().atMost(P95).atMost(P99);
 *
 * // contractual
 * meeting().atMost(P95, ofMillis(500))
 *          .atMost(P99, ofMillis(1500))
 *          .contractRef(SLA, "Acme Payment SLA v3.2 §4.2");
 * }</pre>
 *
 * <p>The id of the resulting runtime criterion is fixed at
 * {@value #ID} — 0..1 cardinality is structural, so no name is
 * authored at the call site. Authors who want a project-specific
 * label put it in {@link #contractRef(ThresholdOrigin, String)}.
 */
// javai-ref: JVI-MPAYH0Q — do not remove (resolves in javai-orchestrator)
// javai-ref: JVI-QVNG2SX — do not remove (resolves in javai-orchestrator)
public final class LatencyCriterion {

    /** The fixed id under which a present latency criterion appears in {@code effectiveCriteria()}. */
    public static final String ID = "latency";

    private enum Mode { NONE, EMPIRICAL, CONTRACTUAL }

    private static final LatencyCriterion NONE = new LatencyCriterion(
            Mode.NONE, EnumSet.noneOf(PercentileKey.class),
            LatencySpec.disabled(), ThresholdOrigin.EMPIRICAL, Optional.empty(),
            OptionalDouble.empty());

    private final Mode mode;
    private final EnumSet<PercentileKey> assertedPercentiles;
    private final LatencySpec spec;
    private final ThresholdOrigin origin;
    private final Optional<String> contractRef;
    private final OptionalDouble confidenceFloor;

    private LatencyCriterion(
            Mode mode,
            EnumSet<PercentileKey> assertedPercentiles,
            LatencySpec spec,
            ThresholdOrigin origin,
            Optional<String> contractRef,
            OptionalDouble confidenceFloor) {
        this.mode = mode;
        this.assertedPercentiles = EnumSet.copyOf(assertedPercentiles);
        this.spec = spec;
        this.origin = origin;
        this.contractRef = contractRef;
        this.confidenceFloor = confidenceFloor;
    }

    /** The empty sentinel — returned by the default {@code Contract.latency()}. */
    public static LatencyCriterion none() {
        return NONE;
    }

    /**
     * Empirical latency at the asserted percentiles. Thresholds are
     * derived from the resolved baseline at evaluate time. Internal
     * factory used by {@link EmpiricalDeclImpl}; authors open the
     * chain via {@link Criteria#empirical()}.
     */
    static LatencyCriterion empirical(PercentileKey first, PercentileKey... rest) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(rest, "rest");
        EnumSet<PercentileKey> set = EnumSet.of(first);
        for (PercentileKey k : rest) {
            if (k == null) {
                throw new NullPointerException("asserted percentiles must not contain null");
            }
            set.add(k);
        }
        return new LatencyCriterion(Mode.EMPIRICAL, set, LatencySpec.disabled(),
                ThresholdOrigin.EMPIRICAL, Optional.empty(),
                OptionalDouble.empty());
    }

    /**
     * Contractual latency: a fixed per-percentile ceiling for each
     * {@link Ceiling} pair. {@code origin} must be a non-empirical
     * origin (SLA / SLO / POLICY / UNSPECIFIED). Internal factory used
     * by {@link ContractualDeclImpl}; authors open the chain via
     * {@link Criteria#meeting()}.
     */
    static LatencyCriterion meeting(
            ThresholdOrigin origin, Ceiling first, Ceiling... rest) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(rest, "rest");
        if (origin == ThresholdOrigin.EMPIRICAL) {
            throw new IllegalArgumentException(
                    "meeting(EMPIRICAL, ...) is contradictory — "
                            + "use Criteria.empirical() for baseline-derived latency");
        }
        EnumSet<PercentileKey> asserted = EnumSet.of(first.percentile());
        LatencySpec.Builder b = LatencySpec.builder();
        applyCeiling(b, first);
        for (Ceiling c : rest) {
            Objects.requireNonNull(c, "ceiling");
            if (!asserted.add(c.percentile())) {
                throw new IllegalArgumentException(
                        "duplicate percentile in ceilings: " + c.percentile());
            }
            applyCeiling(b, c);
        }
        return new LatencyCriterion(Mode.CONTRACTUAL, asserted, b.build(),
                origin, Optional.empty(), OptionalDouble.empty());
    }

    /**
     * Internal factory for {@link Ceiling}, used by
     * {@link ContractualDeclImpl} when constructing the first ceiling
     * of a new contractual chain.
     */
    static Ceiling ceiling(PercentileKey percentile, Duration duration) {
        return new Ceiling(percentile, duration);
    }

    /** Attach a human-readable contract reference. */
    public LatencyCriterion contractRef(String ref) {
        Objects.requireNonNull(ref, "ref");
        if (ref.isBlank()) {
            throw new IllegalArgumentException(".contractRef requires a non-blank string");
        }
        if (mode == Mode.NONE) {
            throw new IllegalStateException(
                    ".contractRef cannot be set on LatencyCriterion.none()");
        }
        return new LatencyCriterion(mode, assertedPercentiles, spec, origin,
                Optional.of(ref), confidenceFloor);
    }

    /**
     * Attach a contract reference and update the origin in one call.
     * Designed for the new {@link Criteria#meeting()} authoring shape
     * where {@code meeting()} opens the chain with origin
     * {@link ThresholdOrigin#UNSPECIFIED} and the author later names
     * the source on the same line as the reference text.
     *
     * <p>{@code origin} must be a non-empirical origin (SLA / SLO /
     * POLICY / UNSPECIFIED).
     */
    public LatencyCriterion contractRef(ThresholdOrigin origin, String ref) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(ref, "ref");
        if (ref.isBlank()) {
            throw new IllegalArgumentException(".contractRef requires a non-blank string");
        }
        if (origin == ThresholdOrigin.EMPIRICAL) {
            throw new IllegalArgumentException(
                    ".contractRef(EMPIRICAL, ...) is contradictory — "
                            + "use empirical() for baseline-derived criteria");
        }
        if (mode == Mode.NONE) {
            throw new IllegalStateException(
                    ".contractRef cannot be set on LatencyCriterion.none()");
        }
        if (mode == Mode.EMPIRICAL) {
            throw new IllegalStateException(
                    ".contractRef(origin, ref) cannot be set on an empirical "
                            + "latency criterion — the baseline IS the source");
        }
        return new LatencyCriterion(mode, assertedPercentiles, spec, origin,
                Optional.of(ref), confidenceFloor);
    }

    /**
     * Chain an additional contractual ceiling at the given
     * percentile. Used by the new {@link Criteria#meeting()}
     * authoring shape:
     *
     * <pre>{@code
     * @Override public LatencyCriterion latency() {
     *     return meeting()
     *             .atMost(P95, ofSeconds(1))
     *             .atMost(P99, ofSeconds(5))
     *             .contractRef(SLA, "Acme Payment SLA v3.2 §4.2");
     * }
     * }</pre>
     *
     * <p>Rejected on empirical-mode criteria (use
     * {@link #atMost(PercentileKey)} with no duration). Rejected on
     * the {@link #none()} sentinel.
     *
     * <p><b>Monotonicity check.</b> Per Statistical Companion §12.4.2
     * the published per-percentile ceilings must be monotone non-
     * decreasing in percentile level (observed P50 ≤ P90 ≤ P95 ≤ P99
     * by definition of order statistics). If this call would assign
     * a higher percentile a lower duration than a previously-declared
     * lower percentile (or vice versa), the call is rejected with
     * {@link IllegalArgumentException} naming both percentiles and
     * both durations.
     */
    public LatencyCriterion atMost(PercentileKey key, Duration value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        if (mode == Mode.EMPIRICAL) {
            throw new IllegalStateException(
                    ".atMost(percentile, duration) cannot be chained on an "
                            + "empirical latency criterion — empirical bounds are "
                            + "derived from the baseline. Use .atMost(percentile) "
                            + "without a duration.");
        }
        if (mode == Mode.NONE) {
            throw new IllegalStateException(
                    ".atMost(...) cannot be chained on LatencyCriterion.none(); "
                            + "open a chain via Criteria.meeting() instead");
        }
        if (assertedPercentiles.contains(key)) {
            throw new IllegalArgumentException(
                    "duplicate percentile in ceilings: " + key);
        }
        checkMonotonicity(key, value);
        EnumSet<PercentileKey> nextAsserted = EnumSet.copyOf(assertedPercentiles);
        nextAsserted.add(key);
        LatencySpec nextSpec = specWithCeiling(spec, key, value.toMillis());
        return new LatencyCriterion(Mode.CONTRACTUAL, nextAsserted, nextSpec,
                origin, contractRef, confidenceFloor);
    }

    /**
     * Chain an additional empirical-mode percentile assertion. Used
     * by the new {@link Criteria#empirical()} authoring shape:
     *
     * <pre>{@code
     * @Override public LatencyCriterion latency() {
     *     return empirical().atMost(P95).atMost(P99).atConfidence(0.99);
     * }
     * }</pre>
     *
     * <p>Rejected on contractual-mode criteria (use
     * {@link #atMost(PercentileKey, Duration)} with an explicit
     * duration). Rejected on the {@link #none()} sentinel.
     */
    public LatencyCriterion atMost(PercentileKey key) {
        Objects.requireNonNull(key, "key");
        if (mode == Mode.CONTRACTUAL) {
            throw new IllegalStateException(
                    ".atMost(percentile) without a duration cannot be chained on a "
                            + "contractual latency criterion. Use "
                            + ".atMost(percentile, duration) for a contractual ceiling.");
        }
        if (mode == Mode.NONE) {
            throw new IllegalStateException(
                    ".atMost(...) cannot be chained on LatencyCriterion.none(); "
                            + "open a chain via Criteria.empirical() instead");
        }
        if (assertedPercentiles.contains(key)) {
            throw new IllegalArgumentException(
                    "duplicate percentile in empirical assertions: " + key);
        }
        EnumSet<PercentileKey> nextAsserted = EnumSet.copyOf(assertedPercentiles);
        nextAsserted.add(key);
        return new LatencyCriterion(Mode.EMPIRICAL, nextAsserted, spec, origin,
                contractRef, confidenceFloor);
    }

    private void checkMonotonicity(PercentileKey newKey, Duration newValue) {
        long newMs = newValue.toMillis();
        for (PercentileKey existing : assertedPercentiles) {
            long existingMs = existingCeilingMillis(existing);
            if (existing.ordinal() < newKey.ordinal() && existingMs > newMs) {
                throw monotonicityFailure(existing, existingMs, newKey, newMs);
            }
            if (existing.ordinal() > newKey.ordinal() && existingMs < newMs) {
                throw monotonicityFailure(newKey, newMs, existing, existingMs);
            }
        }
    }

    private IllegalArgumentException monotonicityFailure(
            PercentileKey lower, long lowerMs,
            PercentileKey higher, long higherMs) {
        return new IllegalArgumentException(String.format(
                "%s atMost (%dms) cannot be lower than %s atMost (%dms) — "
                        + "observed %s ≥ %s, so the %s constraint dominates and "
                        + "the %s declaration is unreachable. Either raise %s's "
                        + "allowance, lower %s's, or remove the redundant declaration.",
                higher, higherMs, lower, lowerMs,
                higher, lower, higher, lower, higher, lower));
    }

    private long existingCeilingMillis(PercentileKey key) {
        return switch (key) {
            case P50 -> spec.p50Millis().orElseThrow();
            case P90 -> spec.p90Millis().orElseThrow();
            case P95 -> spec.p95Millis().orElseThrow();
            case P99 -> spec.p99Millis().orElseThrow();
        };
    }

    private static LatencySpec specWithCeiling(
            LatencySpec base, PercentileKey key, long millis) {
        LatencySpec.Builder b = LatencySpec.builder();
        base.p50Millis().ifPresent(b::p50Millis);
        base.p90Millis().ifPresent(b::p90Millis);
        base.p95Millis().ifPresent(b::p95Millis);
        base.p99Millis().ifPresent(b::p99Millis);
        switch (key) {
            case P50 -> b.p50Millis(millis);
            case P90 -> b.p90Millis(millis);
            case P95 -> b.p95Millis(millis);
            case P99 -> b.p99Millis(millis);
        }
        return b.build();
    }

    /**
     * Set the per-criterion confidence floor for the binomial
     * order-statistic upper bound (Statistical Companion §12.4.2).
     * Composes only with {@link Mode#EMPIRICAL}; rejected on
     * contractual latency (the ceiling is the threshold; nothing to
     * estimate).
     *
     * <p>Defaults to {@code StatisticalDefaults.DEFAULT_CONFIDENCE}
     * (0.95) when not set explicitly.
     *
     * @throws IllegalStateException on contractual or empty decls
     * @throws IllegalArgumentException when {@code confidence} is
     *         outside {@code (0, 1)}
     */
    public LatencyCriterion atConfidence(double confidence) {
        if (mode != Mode.EMPIRICAL) {
            throw new IllegalStateException(
                    ".atConfidence(...) composes only with empirical latency; "
                            + "the contractual ceiling is the threshold");
        }
        if (Double.isNaN(confidence) || confidence <= 0.0 || confidence >= 1.0) {
            throw new IllegalArgumentException(
                    ".atConfidence(c) requires c in (0, 1), got " + confidence);
        }
        return new LatencyCriterion(mode, assertedPercentiles, spec, origin,
                contractRef, OptionalDouble.of(confidence));
    }

    /** True for a meaningful latency criterion; false for {@link #none()}. */
    public boolean isPresent() {
        return mode != Mode.NONE;
    }

    /**
     * Lower this latency declaration to a runtime
     * {@link Criterion}. Framework-internal; called by
     * {@code Contract.effectiveCriteria()} when this criterion is
     * present.
     *
     * @throws IllegalStateException when called on {@link #none()}
     */
    public <O> Criterion<O> toRuntime() {
        if (mode == Mode.NONE) {
            throw new IllegalStateException(
                    "LatencyCriterion.none() cannot be lowered to a runtime criterion");
        }
        CriterionPosture posture = mode == Mode.EMPIRICAL
                ? CriterionPosture.latencyEmpirical(assertedPercentiles)
                : CriterionPosture.latencyContractual(spec, origin);
        if (confidenceFloor.isPresent()) {
            posture = posture.withConfidenceFloor(confidenceFloor.getAsDouble());
        }
        if (contractRef.isPresent()) {
            posture = posture.withContractRef(contractRef.get());
        }
        return new DirectCriterion<>(ID, List.of(), posture);
    }

    private static void applyCeiling(LatencySpec.Builder b, Ceiling c) {
        long millis = c.duration().toMillis();
        switch (c.percentile()) {
            case P50 -> b.p50Millis(millis);
            case P90 -> b.p90Millis(millis);
            case P95 -> b.p95Millis(millis);
            case P99 -> b.p99Millis(millis);
        }
    }
}
