package org.javai.punit.internal.engine.criteria;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

import java.util.ArrayList;
import java.util.List;

import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.criterion.CriterionPosture;
import org.javai.punit.api.spec.Criterion;
import org.javai.punit.api.spec.CriterionResult;
import org.javai.punit.api.spec.CriterionSampleCounts;
import org.javai.punit.api.spec.EmpiricalChecks;
import org.javai.punit.api.spec.EvaluationContext;
import org.javai.punit.api.spec.Experiment;
import org.javai.punit.api.spec.PassRateStatistics;
import org.javai.punit.api.spec.PerCriterionPassRateStatistics;
import org.javai.punit.api.spec.SampleSummary;
import org.javai.punit.api.spec.Verdict;
import org.javai.punit.statistics.BinomialProportionEstimator;
import org.javai.punit.statistics.DerivedThreshold;
import org.javai.punit.statistics.ThresholdDeriver;

/**
 * A Bernoulli pass-rate criterion. Reads {@link PassRateStatistics}
 * from the resolved baseline when in an empirical mode.
 *
 * <p>Three factory forms:
 * <ul>
 *   <li>{@link #meeting(double, ThresholdOrigin)} — contractual; the
 *       threshold is declared explicitly with non-empirical
 *       origin.</li>
 *   <li>{@link #empirical()} — default closest-match baseline
 *       resolution; threshold derived at evaluate time from the
 *       baseline's observed pass rate.</li>
 *   <li>{@link #empiricalFrom(Supplier)} — pinned baseline; threshold
 *       derived from the explicitly supplied baseline.</li>
 * </ul>
 *
 * <p>The empirical paths derive a threshold by applying the one-sided
 * Wilson lower bound at the test sample size to the baseline rate
 * (with a perfect-baseline two-step refinement when {@code k = n};
 * statistical companion §3.4 / §4.3.2), and PASS iff the observed
 * pass rate respects that derived threshold (§5.1). The underlying
 * statistical machinery is {@link BinomialProportionEstimator}; this
 * criterion holds no statistical code of its own — that's the punit
 * design rule (statistics live only in
 * {@code org.javai.punit.statistics}). See {@code CLAUDE.md}
 * §"Statistics isolation rule".
 *
 * <p>The contractual path keeps a deterministic
 * {@code observed >= threshold} check: an SLA-style threshold is an
 * external commitment, not a statistical claim against a baseline.
 *
 * <p>Lives in {@code punit-core} rather than {@code api package} because
 * the empirical path needs {@code BinomialProportionEstimator} and
 * {@code api package} is contractually free of statistics-library
 * dependencies. The {@link Criterion} interface itself stays in
 * {@code api package}; only this implementation moved.
 */
public final class PassRate<OT> implements Criterion<OT, PerCriterionPassRateStatistics> {

    private static final String NAME = "bernoulli-pass-rate";
    private static final double DEFAULT_CONFIDENCE = 0.95;
    private static final ThresholdDeriver DERIVER = new ThresholdDeriver();

    private enum Mode { CONTRACTUAL, EMPIRICAL_DEFAULT, EMPIRICAL_PINNED, ZERO_FAILURES }

    private final Mode mode;
    private final double threshold;
    private final ThresholdOrigin origin;
    private final double confidence;
    private final Supplier<Experiment> baselineSupplier;

    private PassRate(
            Mode mode,
            double threshold,
            ThresholdOrigin origin,
            double confidence,
            Supplier<Experiment> baselineSupplier) {
        this.mode = mode;
        this.threshold = threshold;
        this.origin = origin;
        this.confidence = confidence;
        this.baselineSupplier = baselineSupplier;
    }

    static <OT> PassRate<OT> meeting(ThresholdOrigin origin, double threshold) {
        Objects.requireNonNull(origin, "origin");
        if (threshold < 0.0 || threshold >= 1.0 || Double.isNaN(threshold)) {
            throw new IllegalArgumentException(
                    "threshold must be in [0, 1), got " + threshold);
        }
        if (origin == ThresholdOrigin.EMPIRICAL) {
            throw new IllegalArgumentException(
                    "ThresholdOrigin.EMPIRICAL is reserved for the empirical factories; "
                            + "call PassRate.empirical() or .empiricalFrom(...) instead");
        }
        return new PassRate<>(
                Mode.CONTRACTUAL, threshold, origin, DEFAULT_CONFIDENCE, null);
    }

    static <OT> PassRate<OT> empirical() {
        return new PassRate<>(
                Mode.EMPIRICAL_DEFAULT, Double.NaN, ThresholdOrigin.EMPIRICAL, DEFAULT_CONFIDENCE, null);
    }

    static <OT> PassRate<OT> empiricalFrom(Supplier<Experiment> baseline) {
        Objects.requireNonNull(baseline, "baseline");
        return new PassRate<>(
                Mode.EMPIRICAL_PINNED, Double.NaN, ThresholdOrigin.EMPIRICAL, DEFAULT_CONFIDENCE, baseline);
    }

    /**
     * zero-failures pass-rate criterion: any sample failure fails the
     * criterion. Carries no statistical threshold; its
     * {@link #contractualTarget()} and {@link #earlyTerminationPassRate()}
     * return empty so the framework does not try to underwrite or
     * short-circuit a non-statistical commitment.
     *
     * <p>Used by {@link #fromPosture(org.javai.punit.api.criterion.CriterionPosture)}
     * to auto-inject an evaluator for contracts whose criteria declared
     * a {@code .zeroFailures(...)} posture or no posture at all (the
     * implicit zero-failures default).
     */
    static <OT> PassRate<OT> forZeroFailures(ThresholdOrigin origin) {
        Objects.requireNonNull(origin, "origin");
        if (origin == ThresholdOrigin.EMPIRICAL) {
            throw new IllegalArgumentException(
                    "forZeroFailures(EMPIRICAL) is contradictory — zero-failures is a binary commitment");
        }
        return new PassRate<>(
                Mode.ZERO_FAILURES, 1.0, origin, DEFAULT_CONFIDENCE, null);
    }

    /**
     * Derive a pass-rate spec-criterion from a contract criterion's
     * posture. Used by the test-spec builder when no
     * {@code .criterion(...)} was registered: the contract's
     * acceptance posture drives the spec's evaluator.
     *
     * <p>Maps:
     * <ul>
     *   <li>{@code STATISTICAL_CONTRACTUAL} → {@link #meeting(double, ThresholdOrigin)}</li>
     *   <li>{@code STATISTICAL_EMPIRICAL} → {@link #empirical()}</li>
     * </ul>
     *
     * <p>{@code ZERO_FAILURES} and {@code IMPLICIT_ZERO_FAILURES}
     * postures map to a {@link #forZeroFailures(ThresholdOrigin)}
     * pass-rate that fails on any sample failure. The implicit
     * default uses {@link ThresholdOrigin#POLICY} as its origin per
     * methodology.
     */
    public static <OT> Optional<PassRate<OT>> fromPosture(
            org.javai.punit.api.criterion.CriterionPosture posture) {
        Objects.requireNonNull(posture, "posture");
        return switch (posture.kind()) {
            case STATISTICAL_CONTRACTUAL -> {
                double threshold = posture.threshold().orElseThrow(() -> new IllegalStateException(
                        "STATISTICAL_CONTRACTUAL posture without threshold"));
                ThresholdOrigin origin = posture.origin().orElseThrow(() -> new IllegalStateException(
                        "STATISTICAL_CONTRACTUAL posture without origin"));
                PassRate<OT> base = PassRate.meeting(origin, threshold);
                yield Optional.of(posture.confidenceFloor().isPresent()
                        ? base.atConfidence(posture.confidenceFloor().getAsDouble())
                        : base);
            }
            case STATISTICAL_EMPIRICAL -> {
                PassRate<OT> base = PassRate.empirical();
                yield Optional.of(posture.confidenceFloor().isPresent()
                        ? base.atConfidence(posture.confidenceFloor().getAsDouble())
                        : base);
            }
            case ZERO_FAILURES -> Optional.of(PassRate.<OT>forZeroFailures(
                    posture.origin().orElseThrow(() -> new IllegalStateException(
                            "ZERO_FAILURES posture without origin"))));
            case IMPLICIT_ZERO_FAILURES -> Optional.of(
                    PassRate.<OT>forZeroFailures(ThresholdOrigin.POLICY));
            case LATENCY_EMPIRICAL, LATENCY_CONTRACTUAL -> Optional.empty();
        };
    }

    /**
     * Returns a new criterion with the declared confidence — used for
     * the empirical path's Wilson-score lower-bound comparison.
     */
    public PassRate<OT> atConfidence(double confidence) {
        if (Double.isNaN(confidence) || confidence <= 0.0 || confidence >= 1.0) {
            throw new IllegalArgumentException(
                    "confidence must be in (0, 1), got " + confidence);
        }
        return new PassRate<>(mode, threshold, origin, confidence, baselineSupplier);
    }

    /**
     * The baseline supplier when this criterion was built with
     * {@link #empiricalFrom(Supplier)}; empty otherwise. The
     * framework consults this at spec-conclude time to route a
     * pinned baseline into the evaluation context.
     */
    public Optional<Supplier<Experiment>> baselineSupplier() {
        return Optional.ofNullable(baselineSupplier);
    }

    /**
     * The confidence level (1 − α) used by the empirical Wilson-score
     * comparison. Defaults to {@code 0.95}; override via
     * {@link #atConfidence(double)}. Surfaced for the framework's
     * pre-flight feasibility check (see {@code PUnit}'s wire-up of
     * {@link org.javai.punit.statistics.VerificationFeasibilityEvaluator})
     * — given a configured {@code samples} and a resolved baseline
     * rate, the check needs the criterion's confidence to decide
     * whether the configuration is verification-grade.
     */
    public double confidence() {
        return confidence;
    }

    /**
     * The contractual target rate when this criterion was built with
     * {@link #meeting(double, ThresholdOrigin)}; empty for empirical
     * criteria, whose target is resolved from the baseline at
     * evaluate time.
     *
     * <p>Surfaced for the framework's pre-flight feasibility check:
     * a contractual SLA / SLO / POLICY threshold is no less in need
     * of statistical underwriting than an empirical one — n=50 with
     * a 99.99% target at 95% confidence is infeasible regardless of
     * where the 99.99% came from.
     */
    public OptionalDouble contractualTarget() {
        return mode == Mode.CONTRACTUAL
                ? OptionalDouble.of(threshold)
                : OptionalDouble.empty();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Class<PerCriterionPassRateStatistics> statisticsType() {
        return PerCriterionPassRateStatistics.class;
    }

    @Override
    public boolean isEmpirical() {
        return mode == Mode.EMPIRICAL_DEFAULT || mode == Mode.EMPIRICAL_PINNED;
    }

    @Override
    public OptionalDouble earlyTerminationPassRate() {
        return contractualTarget();
    }

    @Override
    public Map<String, Object> empiricalDetail() {
        if (mode == Mode.CONTRACTUAL || mode == Mode.ZERO_FAILURES) {
            return Map.of();
        }
        return Map.of("confidence", confidence);
    }

    @Override
    public CriterionResult evaluate(EvaluationContext<OT, PerCriterionPassRateStatistics> ctx) {
        Objects.requireNonNull(ctx, "ctx");
        SampleSummary<OT> summary = ctx.summary();
        int total = summary.total();
        if (total == 0) {
            return inconclusive("zero samples taken", Map.of());
        }
        // Resolve the per-criterion baseline map up-front (empirical
        // paths only). Identity / sample-size gates also fire once at
        // the run level — identity is a property of the matched
        // baseline file, not of any one criterion.
        PerCriterionPassRateStatistics baselineMap = null;
        List<CriterionSampleCounts> methodologyCriteria = summary.criterionSampleCounts();
        // K=1 path uses run-level counts (summary.successes/failures),
        // not the auto-derived methodology criterion's counts. The
        // legacy K=1 behaviour read these run-level numbers, and
        // K=1 tests are pinned against them. K>1 must use per-criterion
        // counts so each methodology criterion is evaluated against
        // its own pass/fail tally.
        if (methodologyCriteria.size() == 1) {
            CriterionSampleCounts auto = methodologyCriteria.get(0);
            methodologyCriteria = List.of(
                    new CriterionSampleCounts(
                            auto.criterionId(),
                            summary.successes(),
                            summary.failures(),
                            auto.inconclusive()));
        }
        if (methodologyCriteria.isEmpty()) {
            // Run produced no methodology-criterion sample counts
            // (apply-level-failure run, or a hand-built test fixture).
            // Fall back to run-level pass / fail counts. The criterion
            // id is borrowed from the baseline map's lone entry when
            // available (so the lookup below finds it); otherwise the
            // criterion's own name is used as a stable placeholder
            // — there's no baseline entry to match in that case
            // (contractual mode) so the placeholder is never queried.
            String fallbackId = NAME;
            if (isEmpirical()) {
                PerCriterionPassRateStatistics b = ctx.baseline().orElse(null);
                if (b != null && b.byCriterion().size() == 1) {
                    fallbackId = b.byCriterion().keySet().iterator().next();
                }
            }
            methodologyCriteria = List.of(
                    new CriterionSampleCounts(
                            fallbackId, summary.successes(), summary.failures(), 0));
        }
        // Resolve the baseline map up-front (empirical mode only).
        if (isEmpirical()) {
            baselineMap = ctx.baseline().orElse(null);
            if (baselineMap == null || baselineMap.byCriterion().isEmpty()) {
                return EmpiricalChecks.noBaseline(NAME, empiricalDetail());
            }
            Map<String, Object> empiricalDetail = Map.of("confidence", confidence);
            String baselineIdentity = ctx.baselineInputsIdentity().orElseThrow(() ->
                    new IllegalStateException(
                            "baseline statistics were resolved but baselineInputsIdentity is empty — "
                                    + "the BaselineProvider is producing inconsistent state"));
            Optional<CriterionResult> identityViolation = EmpiricalChecks.inputsIdentityMatch(
                    NAME, ctx.testInputsIdentity(), baselineIdentity, empiricalDetail);
            if (identityViolation.isPresent()) {
                return identityViolation.get();
            }
        }

        // Evaluate each methodology criterion against its own basis
        // (empirical: per-criterion Wilson threshold; contractual:
        // shared external threshold). Compose per the FAIL-dominant
        // rule (companion §1.4.6).
        List<Verdict> perCriterionVerdicts = new ArrayList<>(methodologyCriteria.size());
        Map<String, Double> thresholdsByCriterion = new LinkedHashMap<>();
        Map<String, Double> observedByCriterion = new LinkedHashMap<>();
        Map<String, PassRateStatistics> resolvedBaselineByCriterion = new LinkedHashMap<>();
        List<String> perCriterionExplanations = new ArrayList<>();
        ThresholdOrigin resolvedOrigin = isEmpirical()
                ? ThresholdOrigin.EMPIRICAL
                : origin;

        // K=1 short-circuit: when there's a single criterion and an
        // empirical-check violation fires (sample-size constraint), the
        // result is the violation's full CriterionResult — preserving
        // the legacy detail keys (testSampleCount, baselineSampleCount)
        // and explanation phrasing that downstream consumers depend on.
        // Identity violations already returned above.
        boolean isK1 = methodologyCriteria.size() == 1;

        for (CriterionSampleCounts counts : methodologyCriteria) {
            int criterionTotal = counts.pass() + counts.fail() + counts.inconclusive();
            double observed = criterionTotal == 0
                    ? 0.0
                    : (double) counts.pass() / (double) criterionTotal;
            observedByCriterion.put(counts.criterionId(), observed);

            // Resolve the criterion's posture (commitment) — read from
            // the contract's per-criterion declaration. STATISTICAL_CONTRACTUAL
            // and ZERO_FAILURES shortcut the legacy threshold-on-PassRate
            // path; STATISTICAL_EMPIRICAL and IMPLICIT_ZERO_FAILURES
            // delegate to the existing logic so step 1 preserves
            // legacy behaviour for un-postured criteria.
            CriterionPosture posture = ctx.criterionPostures().getOrDefault(
                    counts.criterionId(), CriterionPosture.implicit());

            // Confidence-floor ratchet: contract criterion's
            // .atConfidence(c_criterion) cannot be loosened by the test.
            if (posture.confidenceFloor().isPresent()
                    && confidence < posture.confidenceFloor().getAsDouble()) {
                throw new IllegalStateException(String.format(
                        "criterion '%s' declares a confidence floor of %.4f but the test runs at %.4f — "
                                + "the contract's floor cannot be loosened by the test (raise the test's "
                                + "confidence to %.4f or remove the floor on the criterion)",
                        counts.criterionId(),
                        posture.confidenceFloor().getAsDouble(),
                        confidence,
                        posture.confidenceFloor().getAsDouble()));
            }

            // Posture-driven shortcut: STATISTICAL_CONTRACTUAL and
            // ZERO_FAILURES skip the legacy mode handling and use the
            // criterion's own commitment directly.
            if (posture.kind() == CriterionPosture.Kind.STATISTICAL_CONTRACTUAL) {
                double pThreshold = posture.threshold().getAsDouble();
                ThresholdOrigin pOrigin = posture.origin().orElseThrow();
                thresholdsByCriterion.put(counts.criterionId(), pThreshold);
                Verdict pv = criterionTotal == 0
                        ? Verdict.INCONCLUSIVE
                        : (observed >= pThreshold ? Verdict.PASS : Verdict.FAIL);
                perCriterionVerdicts.add(pv);
                perCriterionExplanations.add(String.format(
                        "%s: observed=%.4f vs threshold=%.4f (origin=%s) over %d samples → %s",
                        counts.criterionId(), observed, pThreshold, pOrigin, criterionTotal, pv));
                continue;
            }
            // zero-failures fires when the contract committed explicitly
            // (posture == ZERO_FAILURES) or when the criterion was
            // auto-injected for an implicit-zero-failures posture (mode
            // == ZERO_FAILURES). A bare default IMPLICIT_ZERO_FAILURES
            // posture without a matching auto-injected PassRate is not
            // enough — that combination arises in hand-built test fixtures
            // that use PassRate.meeting/empirical and expect their own
            // mode to drive evaluation.
            if (posture.kind() == CriterionPosture.Kind.ZERO_FAILURES
                    || mode == Mode.ZERO_FAILURES) {
                // Binary semantics: any failed sample fails the criterion.
                // Implicit zero-failures defaults origin to POLICY.
                ThresholdOrigin ztOrigin = posture.origin().orElse(ThresholdOrigin.POLICY);
                thresholdsByCriterion.put(counts.criterionId(), 1.0);
                Verdict pv;
                if (criterionTotal == 0) {
                    pv = Verdict.INCONCLUSIVE;
                } else if (counts.fail() == 0 && counts.inconclusive() == 0) {
                    pv = Verdict.PASS;
                } else {
                    pv = Verdict.FAIL;
                }
                perCriterionVerdicts.add(pv);
                perCriterionExplanations.add(String.format(
                        "%s: zero-failures (origin=%s); failures=%d, inconclusive=%d over %d samples → %s",
                        counts.criterionId(),
                        ztOrigin,
                        counts.fail(), counts.inconclusive(), criterionTotal, pv));
                continue;
            }

            double criterionThreshold;
            Double baselineRate = null;
            Integer baselineSampleCount = null;
            if (mode == Mode.CONTRACTUAL) {
                criterionThreshold = threshold;
            } else {
                PassRateStatistics criterionBaseline =
                        baselineMap.byCriterion().get(counts.criterionId());
                if (criterionBaseline == null
                        && isK1 && baselineMap.byCriterion().size() == 1) {
                    // K=1 isomorphism: when there's exactly one
                    // methodology criterion on both the run side and
                    // the baseline side, treat them as the same
                    // criterion even when their ids differ. Older
                    // baselines were written with the legacy default
                    // id "contract"; newer runs auto-derive an id
                    // from the service contract class name.
                    criterionBaseline = baselineMap.byCriterion().values().iterator().next();
                }
                if (criterionBaseline == null) {
                    if (isK1) {
                        return EmpiricalChecks.noBaseline(NAME, empiricalDetail());
                    }
                    perCriterionVerdicts.add(Verdict.INCONCLUSIVE);
                    perCriterionExplanations.add(String.format(
                            "%s: INCONCLUSIVE (no baseline entry for this criterion)",
                            counts.criterionId()));
                    thresholdsByCriterion.put(counts.criterionId(), Double.NaN);
                    continue;
                }
                Optional<CriterionResult> sizeViolation =
                        EmpiricalChecks.sampleSizeConstraint(
                                NAME, criterionTotal, criterionBaseline.sampleCount(),
                                Map.of("confidence", confidence));
                if (sizeViolation.isPresent()) {
                    if (isK1) {
                        return sizeViolation.get();
                    }
                    perCriterionVerdicts.add(sizeViolation.get().verdict());
                    perCriterionExplanations.add(
                            counts.criterionId() + ": " + sizeViolation.get().explanation());
                    thresholdsByCriterion.put(counts.criterionId(), Double.NaN);
                    continue;
                }
                resolvedBaselineByCriterion.put(counts.criterionId(), criterionBaseline);
                int baselineSuccesses = (int) Math.round(
                        criterionBaseline.observedPassRate() * criterionBaseline.sampleCount());
                DerivedThreshold derived = DERIVER.deriveSampleSizeFirst(
                        criterionBaseline.sampleCount(), baselineSuccesses, criterionTotal, confidence);
                criterionThreshold = derived.value();
                baselineRate = criterionBaseline.observedPassRate();
                baselineSampleCount = criterionBaseline.sampleCount();
            }
            thresholdsByCriterion.put(counts.criterionId(), criterionThreshold);

            Verdict v;
            if (criterionTotal == 0) {
                v = Verdict.INCONCLUSIVE;
            } else {
                v = observed >= criterionThreshold ? Verdict.PASS : Verdict.FAIL;
            }
            perCriterionVerdicts.add(v);
            if (mode == Mode.CONTRACTUAL) {
                perCriterionExplanations.add(String.format(
                        "%s: observed=%.4f vs threshold=%.4f over %d samples → %s",
                        counts.criterionId(), observed, criterionThreshold, criterionTotal, v));
            } else {
                perCriterionExplanations.add(String.format(
                        "%s: observed=%.4f vs threshold=%.4f "
                                + "(Wilson-%.0f%% lower of baseline rate %.4f at n=%d) → %s",
                        counts.criterionId(), observed, criterionThreshold,
                        confidence * 100.0, baselineRate, criterionTotal, v));
            }
        }

        Verdict composite = Verdict.aggregate(perCriterionVerdicts);

        // Build the result detail map. For K=1 the legacy flat shape
        // (observed/threshold/successes/failures/total) is preserved
        // so downstream renderers and tests that read it continue to
        // work. For K>1 the same keys carry the lone criterion's
        // values when applicable, and a perCriterion-keyed sub-map
        // carries the K-row breakdown.
        Map<String, Object> detail = new LinkedHashMap<>();
        if (methodologyCriteria.size() == 1) {
            CriterionSampleCounts only = methodologyCriteria.get(0);
            int onlyTotal = only.pass() + only.fail() + only.inconclusive();
            double observed = observedByCriterion.get(only.criterionId());
            double t = thresholdsByCriterion.get(only.criterionId());
            detail.put("observed", observed);
            detail.put("threshold", t);
            detail.put("origin", resolvedOrigin.name());
            detail.put("successes", only.pass());
            detail.put("failures", only.fail());
            detail.put("total", onlyTotal);
            if (isEmpirical()) {
                PassRateStatistics b = resolvedBaselineByCriterion.get(only.criterionId());
                detail.put("confidence", confidence);
                detail.put("baselineSampleCount", b == null ? null : b.sampleCount());
                detail.put("baselineRate", b == null ? null : b.observedPassRate());
            }
            CriterionPosture onlyPosture = ctx.criterionPostures().getOrDefault(
                    only.criterionId(), CriterionPosture.implicit());
            onlyPosture.contractRef().ifPresent(ref -> detail.put("contractRef", ref));
        } else {
            detail.put("origin", resolvedOrigin.name());
            detail.put("total", total);
            detail.put("successes", summary.successes());
            detail.put("failures", summary.failures());
            if (isEmpirical()) {
                detail.put("confidence", confidence);
            }
            detail.put("thresholdsByCriterion", Map.copyOf(thresholdsByCriterion));
            detail.put("observedByCriterion", Map.copyOf(observedByCriterion));
        }

        String explanation;
        if (methodologyCriteria.size() == 1) {
            CriterionSampleCounts only = methodologyCriteria.get(0);
            int onlyTotal = only.pass() + only.fail() + only.inconclusive();
            double observed = observedByCriterion.get(only.criterionId());
            double t = thresholdsByCriterion.get(only.criterionId());
            if (isEmpirical()) {
                PassRateStatistics b = resolvedBaselineByCriterion.get(only.criterionId());
                explanation = String.format(
                        "observed=%.4f vs threshold=%.4f (Wilson-%.0f%% lower of "
                                + "baseline rate %.4f at n_test=%d; origin=%s) over %d samples",
                        observed, t, confidence * 100.0,
                        b == null ? Double.NaN : b.observedPassRate(),
                        onlyTotal, resolvedOrigin, onlyTotal);
            } else {
                explanation = perCriterionExplanations.isEmpty()
                        ? String.format(
                                "observed=%.4f, threshold=%.4f (origin=%s) over %d samples",
                                observed, t, resolvedOrigin, onlyTotal)
                        : perCriterionExplanations.get(0);
            }
        } else {
            explanation = "composite verdict over " + methodologyCriteria.size()
                    + " criteria: " + String.join("; ", perCriterionExplanations);
        }

        return new CriterionResult(NAME, composite, explanation, detail);
    }

    private CriterionResult inconclusive(String reason, Map<String, Object> detail) {
        return new CriterionResult(NAME, Verdict.INCONCLUSIVE, reason, detail);
    }
}
