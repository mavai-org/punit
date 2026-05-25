package org.javai.punit.api.criterion;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

import org.javai.punit.api.LatencySpec;
import org.javai.punit.api.PercentileKey;
import org.javai.punit.api.ThresholdOrigin;

/**
 * The methodology criterion's run-time *commitment*: what counts as
 * acceptable, optionally how confidently to evaluate it. Authored on
 * the criterion via the {@link Criteria#meeting()} /
 * {@link Criteria#empirical()} factories with the kind selectors
 * ({@code .passRate}, {@code .zeroFailures}, {@code .atMost});
 * consumed by the framework's evaluation path when it computes a
 * per-criterion verdict from the run's sample counts.
 *
 * <p>Three kinds map to the three posture methods on the criterion-
 * declaration handle, plus an implicit kind for criteria that
 * declared no posture at all:
 *
 * <ul>
 *   <li>{@link Kind#STATISTICAL_CONTRACTUAL} — explicit contractual
 *       threshold via {@code .meeting(rate, origin)}. Carries the
 *       rate and origin; carries the confidence iff
 *       {@code .atConfidence(c)} was called.</li>
 *   <li>{@link Kind#STATISTICAL_EMPIRICAL} — empirical threshold via
 *       {@code .empirical()}. The threshold is derived from the
 *       baseline at evaluate time; the {@code origin} is
 *       {@link ThresholdOrigin#EMPIRICAL}.</li>
 *   <li>{@link Kind#ZERO_FAILURES} — explicit zero-failures via
 *       {@code .zeroFailures(origin)}. Threshold is implicitly 1.0;
 *       the run classifies SMOKE per the methodology rule
 *       (zero-failures is not statistically verifiable).
 *       Composition with {@code .atConfidence(...)} is rejected at
 *       build time.</li>
 *   <li>{@link Kind#IMPLICIT_ZERO_FAILURES} — no posture method
 *       called on the criterion declaration. Equivalent in every
 *       respect to {@code .zeroFailures(POLICY)} once the implicit
 *       default is active (directive step 3).</li>
 * </ul>
 *
 * <p>An instance is immutable; the static factory methods produce
 * the four kinds.
 */
// javai-ref: JVI-0NGDRGR — do not remove (resolves in javai-orchestrator)
// javai-ref: JVI-0FVFYBM — do not remove (resolves in javai-orchestrator)
// javai-ref: JVI-5YJVXGF — do not remove (resolves in javai-orchestrator)
// javai-ref: JVI-6789AKT — do not remove (resolves in javai-orchestrator)
// javai-ref: JVI-THPVKXD — do not remove (resolves in javai-orchestrator)
public final class CriterionPosture {

    /** Posture kinds. */
    public enum Kind {
        /** {@code .meeting(rate, origin)} — explicit numeric target. */
        STATISTICAL_CONTRACTUAL,
        /** {@code .empirical()} — threshold derived from baseline. */
        STATISTICAL_EMPIRICAL,
        /** {@code .zeroFailures(origin)} — explicit binary commitment. */
        ZERO_FAILURES,
        /** No posture method called — implicit zero-failures (step 3 default). */
        IMPLICIT_ZERO_FAILURES,
        /**
         * Latency, empirical: per-percentile thresholds derived from the
         * resolved baseline at evaluate time. Authored via
         * {@code empirical().atMost(P95).atMost(P99)} on the contract's
         * {@link org.javai.punit.api.Contract#latency()} sibling.
         */
        LATENCY_EMPIRICAL,
        /**
         * Latency, contractual: per-percentile ceilings declared via
         * {@code meeting().atMost(P95, ofMillis(500)).contractRef(SLA, "...")}
         * on the contract's {@link org.javai.punit.api.Contract#latency()}
         * sibling. The threshold is the declared duration; the origin
         * is the supplied non-empirical origin.
         */
        LATENCY_CONTRACTUAL
    }

    private static final CriterionPosture IMPLICIT =
            new CriterionPosture(Kind.IMPLICIT_ZERO_FAILURES,
                    OptionalDouble.empty(),
                    Optional.empty(),
                    OptionalDouble.empty(),
                    OptionalDouble.empty(),
                    OptionalDouble.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty());

    private final Kind kind;
    private final OptionalDouble threshold;
    private final Optional<ThresholdOrigin> origin;
    private final OptionalDouble confidenceFloor;
    private final OptionalDouble mde;
    private final OptionalDouble power;
    private final Optional<String> contractRef;
    private final Optional<EnumSet<PercentileKey>> assertedPercentiles;
    private final Optional<LatencySpec> latencySpec;

    private CriterionPosture(
            Kind kind,
            OptionalDouble threshold,
            Optional<ThresholdOrigin> origin,
            OptionalDouble confidenceFloor,
            OptionalDouble mde,
            OptionalDouble power,
            Optional<String> contractRef,
            Optional<EnumSet<PercentileKey>> assertedPercentiles,
            Optional<LatencySpec> latencySpec) {
        this.kind = kind;
        this.threshold = threshold;
        this.origin = origin;
        this.confidenceFloor = confidenceFloor;
        this.mde = mde;
        this.power = power;
        this.contractRef = contractRef;
        this.assertedPercentiles = assertedPercentiles;
        this.latencySpec = latencySpec;
    }

    /** The implicit-zero-failures posture — the default for any criterion that declared no posture method. */
    public static CriterionPosture implicit() {
        return IMPLICIT;
    }

    /** Statistical, contractual: lowered from {@code meeting().passRate(rate).contractRef(origin, ...)}. */
    public static CriterionPosture meeting(ThresholdOrigin origin, double rate) {
        Objects.requireNonNull(origin, "origin");
        if (Double.isNaN(rate) || rate < 0.0 || rate >= 1.0) {
            throw new IllegalArgumentException(
                    "meeting(origin, rate): rate must be in [0, 1), got " + rate);
        }
        if (origin == ThresholdOrigin.EMPIRICAL) {
            throw new IllegalArgumentException(
                    "meeting(EMPIRICAL, rate) is contradictory — call .empirical() instead");
        }
        return new CriterionPosture(Kind.STATISTICAL_CONTRACTUAL,
                OptionalDouble.of(rate),
                Optional.of(origin),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    /** Statistical, empirical: {@code .empirical()}. */
    public static CriterionPosture empirical() {
        return new CriterionPosture(Kind.STATISTICAL_EMPIRICAL,
                OptionalDouble.empty(),
                Optional.of(ThresholdOrigin.EMPIRICAL),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    /** Explicit zero-failures: {@code .zeroFailures(origin)}. */
    public static CriterionPosture zeroFailures(ThresholdOrigin origin) {
        Objects.requireNonNull(origin, "origin");
        if (origin == ThresholdOrigin.EMPIRICAL) {
            throw new IllegalArgumentException(
                    "zeroFailures(EMPIRICAL) is contradictory — zero-failures is a binary commitment, not an empirical one");
        }
        return new CriterionPosture(Kind.ZERO_FAILURES,
                OptionalDouble.of(1.0),
                Optional.of(origin),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    /**
     * Latency, empirical: lowered from {@code empirical().atMost(P95).atMost(P99)...}.
     * Per-percentile thresholds are derived from the resolved baseline
     * at evaluate time via Statistical Companion §12.4.2's exact
     * binomial order-statistic upper confidence bound. The
     * {@link #confidenceFloor()} carries the operative confidence
     * level (defaults to {@code StatisticalDefaults.DEFAULT_CONFIDENCE}
     * when not set explicitly).
     */
    public static CriterionPosture latencyEmpirical(EnumSet<PercentileKey> asserted) {
        Objects.requireNonNull(asserted, "asserted");
        if (asserted.isEmpty()) {
            throw new IllegalArgumentException(
                    "latencyEmpirical requires at least one PercentileKey");
        }
        return new CriterionPosture(Kind.LATENCY_EMPIRICAL,
                OptionalDouble.empty(),
                Optional.of(ThresholdOrigin.EMPIRICAL),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                Optional.empty(),
                Optional.of(EnumSet.copyOf(asserted)),
                Optional.empty());
    }

    /**
     * Latency, contractual: lowered from
     * {@code meeting().atMost(percentile, duration)...contractRef(origin, ref)}.
     * Per-percentile ceilings are declared on the contract; the origin
     * is the supplied non-empirical origin.
     */
    public static CriterionPosture latencyContractual(LatencySpec spec, ThresholdOrigin origin) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(origin, "origin");
        if (!spec.hasAnyThreshold()) {
            throw new IllegalArgumentException(
                    "latencyContractual requires at least one ceiling — "
                            + "chain .atMost(percentile, duration) on the decl");
        }
        if (origin == ThresholdOrigin.EMPIRICAL) {
            throw new IllegalArgumentException(
                    "latencyContractual(spec, EMPIRICAL) is contradictory — "
                            + "use Criteria.empirical() for baseline-derived latency");
        }
        EnumSet<PercentileKey> asserted = EnumSet.noneOf(PercentileKey.class);
        for (PercentileKey k : PercentileKey.values()) {
            if (k.ceilingMillis(spec).isPresent()) {
                asserted.add(k);
            }
        }
        return new CriterionPosture(Kind.LATENCY_CONTRACTUAL,
                OptionalDouble.empty(),
                Optional.of(origin),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                Optional.empty(),
                Optional.of(asserted),
                Optional.of(spec));
    }

    /**
     * Returns a copy of this posture with the given confidence floor.
     * Rejects composition with zero-failures (explicit or implicit)
     * — the statistical math is undefined at the threshold boundary.
     * Rejects composition with {@code .meeting(...)} — threshold-first
     * is deterministic and accepts no rigour adjuncts.
     */
    public CriterionPosture withConfidenceFloor(double confidence) {
        rejectRigourAdjunct("atConfidence");
        if (Double.isNaN(confidence) || confidence <= 0.0 || confidence >= 1.0) {
            throw new IllegalArgumentException(
                    ".atConfidence(c) requires c in (0, 1), got " + confidence);
        }
        return new CriterionPosture(kind, threshold, origin,
                OptionalDouble.of(confidence), mde, power, contractRef,
                assertedPercentiles, latencySpec);
    }

    /**
     * Returns a copy of this posture with the given minimum
     * detectable effect — the smallest regression (in percentage
     * points off the baseline rate) the criterion commits to
     * detecting. Composes only with {@code .empirical()}; must be
     * paired with {@link #withPower(double)}.
     */
    public CriterionPosture withMde(double mdeValue) {
        rejectRigourAdjunct("detectingMde");
        if (Double.isNaN(mdeValue) || mdeValue <= 0.0 || mdeValue >= 1.0) {
            throw new IllegalArgumentException(
                    ".detectingMde(m) requires m in (0, 1), got " + mdeValue);
        }
        return new CriterionPosture(kind, threshold, origin,
                confidenceFloor, OptionalDouble.of(mdeValue), power, contractRef,
                assertedPercentiles, latencySpec);
    }

    /**
     * Returns a copy of this posture with the given statistical
     * power — probability of detecting a true regression of size MDE.
     * Composes only with {@code .empirical()}; must be paired with
     * {@link #withMde(double)}.
     */
    public CriterionPosture withPower(double powerValue) {
        rejectRigourAdjunct("atPower");
        if (Double.isNaN(powerValue) || powerValue <= 0.0 || powerValue >= 1.0) {
            throw new IllegalArgumentException(
                    ".atPower(p) requires p in (0, 1), got " + powerValue);
        }
        return new CriterionPosture(kind, threshold, origin,
                confidenceFloor, mde, OptionalDouble.of(powerValue), contractRef,
                assertedPercentiles, latencySpec);
    }

    /**
     * Returns a copy of this posture with the given contract reference —
     * a human-readable pointer to the document and clause that justify
     * the commitment (e.g. {@code "Payment Provider SLA v2.3, §4.1"}).
     * Surfaced in the verdict path so compliance reports cite the
     * authority alongside the verdict.
     *
     * <p>Composes with every posture kind, including implicit
     * zero-failures; the ref is informational, not part of the
     * commitment's math.
     */
    public CriterionPosture withContractRef(String ref) {
        Objects.requireNonNull(ref, "contractRef");
        if (ref.isBlank()) {
            throw new IllegalArgumentException(".contractRef requires a non-blank string");
        }
        return new CriterionPosture(kind, threshold, origin,
                confidenceFloor, mde, power, Optional.of(ref),
                assertedPercentiles, latencySpec);
    }

    /**
     * Returns a copy of this posture with both the origin and the
     * contract reference updated in one step. Used by the new
     * authoring surface where {@code Criteria.meeting()} opens a
     * chain with origin {@link ThresholdOrigin#UNSPECIFIED} and the
     * author later names both the source category and the document
     * reference together via
     * {@code .contractRef(ThresholdOrigin, String)}.
     *
     * <p>{@code newOrigin} must not be {@link ThresholdOrigin#EMPIRICAL}
     * — empirical-mode criteria reach this posture machinery via
     * {@code Criteria.empirical()}, not via {@code contractRef}.
     * Empirical-mode postures reject the call entirely.
     */
    public CriterionPosture withContractRef(ThresholdOrigin newOrigin, String ref) {
        Objects.requireNonNull(newOrigin, "origin");
        Objects.requireNonNull(ref, "contractRef");
        if (ref.isBlank()) {
            throw new IllegalArgumentException(".contractRef requires a non-blank string");
        }
        if (newOrigin == ThresholdOrigin.EMPIRICAL) {
            throw new IllegalArgumentException(
                    ".contractRef(EMPIRICAL, ...) is contradictory — empirical "
                            + "criteria are sourced from the baseline, not from a "
                            + "named document");
        }
        if (kind == Kind.STATISTICAL_EMPIRICAL || kind == Kind.LATENCY_EMPIRICAL) {
            throw new IllegalStateException(
                    ".contractRef(origin, ref) cannot retag an empirical-mode "
                            + "criterion with a contractual origin; the baseline "
                            + "IS the source");
        }
        return new CriterionPosture(kind, threshold, Optional.of(newOrigin),
                confidenceFloor, mde, power, Optional.of(ref),
                assertedPercentiles, latencySpec);
    }

    private void rejectRigourAdjunct(String methodName) {
        switch (kind) {
            case ZERO_FAILURES, IMPLICIT_ZERO_FAILURES -> throw new IllegalStateException(
                    "." + methodName + "(...) cannot compose with zero-failures — "
                            + "statistical math is undefined at the threshold boundary");
            case STATISTICAL_CONTRACTUAL -> throw new IllegalStateException(
                    "." + methodName + "(...) cannot compose with .meeting(...) — "
                            + "threshold-first is deterministic and accepts no rigour adjuncts; "
                            + "switch to .empirical() if you want a statistical comparison");
            case LATENCY_CONTRACTUAL -> throw new IllegalStateException(
                    "." + methodName + "(...) does not apply to contractual latency — "
                            + "the ceiling is the threshold; nothing to estimate");
            case LATENCY_EMPIRICAL -> {
                if (!methodName.equals("atConfidence")) {
                    throw new IllegalStateException(
                            "." + methodName + "(...) does not apply to empirical latency; "
                                    + "only .atConfidence(...) is supported");
                }
            }
            case STATISTICAL_EMPIRICAL -> { /* ok */ }
        }
    }

    /**
     * Validate that this posture is internally consistent — used by
     * the framework at first criteria() access to catch partial
     * sensitivity declarations (MDE without power, or vice versa).
     *
     * @throws IllegalStateException when the posture pairs only one
     *         of {MDE, power}.
     */
    public void validate() {
        if (mde.isPresent() && power.isEmpty()) {
            throw new IllegalStateException(
                    ".detectingMde(m) requires a paired .atPower(p) — "
                            + "half a sensitivity declaration is undefined");
        }
        if (power.isPresent() && mde.isEmpty()) {
            throw new IllegalStateException(
                    ".atPower(p) requires a paired .detectingMde(m) — "
                            + "half a sensitivity declaration is undefined");
        }
    }

    public Kind kind() {
        return kind;
    }

    /** The contractual target rate when present (statistical-contractual + zero-failures), empty otherwise. */
    public OptionalDouble threshold() {
        return threshold;
    }

    public Optional<ThresholdOrigin> origin() {
        return origin;
    }

    /** Per-criterion confidence floor when declared via {@code .atConfidence(...)}, empty otherwise. */
    public OptionalDouble confidenceFloor() {
        return confidenceFloor;
    }

    /** Minimum detectable effect when declared via {@code .detectingMde(...)}, empty otherwise. */
    public OptionalDouble mde() {
        return mde;
    }

    /** Statistical power when declared via {@code .atPower(...)}, empty otherwise. */
    public OptionalDouble power() {
        return power;
    }

    /**
     * Author-supplied audit pointer to the contract clause that
     * justifies this criterion's commitment — for example
     * {@code "Payment Provider SLA v2.3, §4.1"}. Empty when not
     * declared.
     */
    public Optional<String> contractRef() {
        return contractRef;
    }

    /**
     * Percentiles this latency posture asserts against. Present only
     * for {@link Kind#LATENCY_EMPIRICAL} / {@link Kind#LATENCY_CONTRACTUAL};
     * empty otherwise.
     */
    public Optional<EnumSet<PercentileKey>> assertedPercentiles() {
        return assertedPercentiles.map(EnumSet::copyOf);
    }

    /**
     * The contractual {@link LatencySpec} with per-percentile ceilings.
     * Present only for {@link Kind#LATENCY_CONTRACTUAL}; empty otherwise.
     */
    public Optional<LatencySpec> latencySpec() {
        return latencySpec;
    }

    /** Whether this posture asks for a pass-rate statistical evaluation. */
    public boolean isStatistical() {
        return kind == Kind.STATISTICAL_CONTRACTUAL || kind == Kind.STATISTICAL_EMPIRICAL;
    }

    /** Whether this posture asks for a latency evaluation. */
    public boolean isLatency() {
        return kind == Kind.LATENCY_EMPIRICAL || kind == Kind.LATENCY_CONTRACTUAL;
    }

    /** Whether this posture asks for a binary evaluation (zero-failures, explicit or implicit). */
    public boolean isZeroFailures() {
        return kind == Kind.ZERO_FAILURES || kind == Kind.IMPLICIT_ZERO_FAILURES;
    }

    /**
     * Whether this posture is in the confidence-first approach —
     * empirical with both MDE and power declared. Used by the
     * framework to identify criteria that contribute a
     * PowerAnalysis-derived sample-count floor to the run.
     */
    public boolean isConfidenceFirst() {
        return kind == Kind.STATISTICAL_EMPIRICAL && mde.isPresent() && power.isPresent();
    }
}
