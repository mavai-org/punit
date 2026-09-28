package org.mavai.punit.internal.engine.criteria;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.ThresholdOrigin;
import org.mavai.punit.api.criterion.CriterionPosture;
import org.mavai.punit.api.spec.ConfigurationCheck;
import org.mavai.punit.api.spec.ConfigurationRefusal;
import org.mavai.punit.api.spec.Criterion;
import org.mavai.punit.api.spec.CriterionResult;
import org.mavai.punit.api.spec.CriterionSampleCounts;
import org.mavai.punit.api.spec.EmpiricalChecks;
import org.mavai.punit.api.spec.EvaluationContext;
import org.mavai.punit.api.spec.Experiment;
import org.mavai.punit.api.spec.PassRateStatistics;
import org.mavai.punit.api.spec.PerCriterionPassRateStatistics;
import org.mavai.punit.api.spec.SampleSummary;
import org.mavai.punit.api.spec.Verdict;
import org.mavai.punit.statistics.ComplianceRule;
import org.mavai.punit.statistics.ConfigurationError;
import org.mavai.punit.statistics.Methodology;
import org.mavai.punit.statistics.RegressionRule;
import org.mavai.punit.statistics.RuleVerdict;
import org.mavai.punit.statistics.StatisticalDefaults;

/**
 * A Bernoulli pass-rate criterion. Reads {@link PassRateStatistics}
 * from the resolved baseline when in an empirical mode.
 *
 * <p>Three factory forms:
 * <ul>
 *   <li>{@link #meeting(ThresholdOrigin, double)} — a declared
 *       requirement with non-empirical origin.</li>
 *   <li>{@link #empirical()} — default closest-match baseline
 *       resolution; judged against the baseline's counts at evaluate
 *       time.</li>
 *   <li>{@link #empiricalFrom(Supplier)} — pinned baseline.</li>
 * </ul>
 *
 * <p>Each methodology criterion the contract declares is decided by the
 * rule its threshold's origin selects (Statistical Companion §3.2):
 *
 * <ul>
 *   <li>a <b>declared</b> requirement by {@code compliance/exact-binomial}
 *       (§3.6): PASS iff the observed success count reaches {@code k_min},
 *       the smallest count the exact one-sided binomial test accepts at
 *       the criterion's alpha;</li>
 *   <li>a <b>baseline-derived</b> bar by {@code regression/fisher}
 *       (§3.4): PASS iff the observed count reaches the integer cutoff of
 *       the one-sided Fisher exact test, derived from the baseline's
 *       counts at the test's own size;</li>
 *   <li>a <b>zero-failures</b> commitment by the count of failures
 *       alone.</li>
 * </ul>
 *
 * <p>A requirement and a baseline on the same postconditions are two
 * criteria, each decided by its own rule at its own alpha, composed by
 * the structural composite (§1.4.6). The criterion holds no statistical
 * code of its own — the rules live in {@code org.mavai.punit.statistics}
 * (see {@code CLAUDE.md} §"Statistics isolation rule").
 *
 * <p>Lives in {@code punit-core} rather than {@code api} because the
 * rules need the statistics package, and {@code api} is contractually
 * free of statistics-library dependencies. The {@link Criterion}
 * interface itself stays in {@code api}.
 */
// mavai-ref: JVI-C5P3EQE — do not remove (resolves in mavai-orchestrator)
public final class PassRate<OT> implements Criterion<OT, PerCriterionPassRateStatistics> {

    private static final String NAME = "bernoulli-pass-rate";

    private enum Mode { CONTRACTUAL, EMPIRICAL_DEFAULT, EMPIRICAL_PINNED, ZERO_FAILURES }

    /** How one methodology criterion is decided. */
    private enum Kind { COMPLIANCE, REGRESSION, ZERO_FAILURES }

    private final Mode mode;
    private final double threshold;
    private final ThresholdOrigin origin;
    private final double confidence;
    private final boolean confidenceDeclared;
    private final Supplier<Experiment> baselineSupplier;

    private PassRate(
            Mode mode,
            double threshold,
            ThresholdOrigin origin,
            double confidence,
            boolean confidenceDeclared,
            Supplier<Experiment> baselineSupplier) {
        this.mode = mode;
        this.threshold = threshold;
        this.origin = origin;
        this.confidence = confidence;
        this.confidenceDeclared = confidenceDeclared;
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
        return new PassRate<>(Mode.CONTRACTUAL, threshold, origin,
                StatisticalDefaults.DEFAULT_CONFIDENCE, false, null);
    }

    static <OT> PassRate<OT> empirical() {
        return new PassRate<>(Mode.EMPIRICAL_DEFAULT, Double.NaN, ThresholdOrigin.EMPIRICAL,
                StatisticalDefaults.DEFAULT_CONFIDENCE, false, null);
    }

    static <OT> PassRate<OT> empiricalFrom(Supplier<Experiment> baseline) {
        Objects.requireNonNull(baseline, "baseline");
        return new PassRate<>(Mode.EMPIRICAL_PINNED, Double.NaN, ThresholdOrigin.EMPIRICAL,
                StatisticalDefaults.DEFAULT_CONFIDENCE, false, baseline);
    }

    /**
     * zero-failures pass-rate criterion: any sample failure fails the
     * criterion. Carries no statistical threshold; its
     * {@link #contractualTarget()} and {@link #earlyTerminationPassRate()}
     * return empty so the framework does not try to underwrite or
     * short-circuit a non-statistical commitment.
     */
    static <OT> PassRate<OT> forZeroFailures(ThresholdOrigin origin) {
        Objects.requireNonNull(origin, "origin");
        if (origin == ThresholdOrigin.EMPIRICAL) {
            throw new IllegalArgumentException(
                    "forZeroFailures(EMPIRICAL) is contradictory — zero-failures is a binary commitment");
        }
        return new PassRate<>(Mode.ZERO_FAILURES, 1.0, origin,
                StatisticalDefaults.DEFAULT_CONFIDENCE, false, null);
    }

    /**
     * Derive a pass-rate spec-criterion from a contract criterion's
     * posture. Used by the test-spec builder when no
     * {@code .criterion(...)} was registered: the contract's acceptance
     * posture drives the spec's evaluator. The posture's confidence
     * becomes this evaluator's default; every methodology criterion is
     * still judged at its own posture's confidence.
     */
    public static <OT> Optional<PassRate<OT>> fromPosture(CriterionPosture posture) {
        Objects.requireNonNull(posture, "posture");
        double postureConfidence = posture.confidenceFloor()
                .orElse(StatisticalDefaults.DEFAULT_CONFIDENCE);
        return switch (posture.kind()) {
            case STATISTICAL_CONTRACTUAL -> {
                double t = posture.threshold().orElseThrow(() -> new IllegalStateException(
                        "STATISTICAL_CONTRACTUAL posture without threshold"));
                ThresholdOrigin o = posture.origin().orElseThrow(() -> new IllegalStateException(
                        "STATISTICAL_CONTRACTUAL posture without origin"));
                PassRate<OT> base = PassRate.meeting(o, t);
                yield Optional.of(base.withDefaultConfidence(postureConfidence));
            }
            case STATISTICAL_EMPIRICAL -> Optional.of(
                    PassRate.<OT>empirical().withDefaultConfidence(postureConfidence));
            case ZERO_FAILURES -> Optional.of(PassRate.<OT>forZeroFailures(
                    posture.origin().orElseThrow(() -> new IllegalStateException(
                            "ZERO_FAILURES posture without origin"))));
            case IMPLICIT_ZERO_FAILURES -> Optional.of(
                    PassRate.<OT>forZeroFailures(ThresholdOrigin.POLICY));
            case LATENCY_EMPIRICAL, LATENCY_CONTRACTUAL -> Optional.empty();
        };
    }

    private PassRate<OT> withDefaultConfidence(double c) {
        return new PassRate<>(mode, threshold, origin, c, false, baselineSupplier);
    }

    /**
     * Returns a new criterion declaring the test's confidence
     * {@code 1 − alpha}. A contract criterion's own
     * {@code .atConfidence(c)} is a floor the test cannot loosen: a
     * declared confidence below it is refused at evaluate time.
     */
    public PassRate<OT> atConfidence(double confidence) {
        if (Double.isNaN(confidence) || confidence <= 0.0 || confidence >= 1.0) {
            throw new IllegalArgumentException(
                    "confidence must be in (0, 1), got " + confidence);
        }
        return new PassRate<>(mode, threshold, origin, confidence, true, baselineSupplier);
    }

    /**
     * The baseline supplier when this criterion was built with
     * {@link #empiricalFrom(Supplier)}; empty otherwise.
     */
    public Optional<Supplier<Experiment>> baselineSupplier() {
        return Optional.ofNullable(baselineSupplier);
    }

    /**
     * The confidence level {@code 1 − alpha} of this evaluator: the
     * test's declared confidence, else the posture's it was derived
     * from, else the framework default.
     */
    public double confidence() {
        return confidence;
    }

    /**
     * The declared requirement when this criterion was built with
     * {@link #meeting(ThresholdOrigin, double)}; empty for empirical
     * criteria, whose bar is resolved from the baseline at evaluate time.
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
    // mavai-ref: JVI-BQTS77W — do not remove (resolves in mavai-orchestrator)
    // mavai-ref: JVI-GZFMZXV — do not remove (resolves in mavai-orchestrator)
    public OptionalDouble earlyTerminationPassRate() {
        return contractualTarget();
    }

    @Override
    public OptionalDouble earlyTerminationConfidence() {
        return mode == Mode.CONTRACTUAL
                ? OptionalDouble.of(confidence)
                : OptionalDouble.empty();
    }

    @Override
    public Map<String, Object> empiricalDetail() {
        if (mode == Mode.CONTRACTUAL || mode == Mode.ZERO_FAILURES) {
            return Map.of();
        }
        return Map.of("confidence", confidence);
    }

    // ── Configuration refusal (§5.7.1) ──────────────────────────────

    /**
     * {@code COMPLIANCE_INFEASIBLE} for a declared requirement no outcome
     * of the planned size can demonstrate, under VERIFICATION; and
     * {@code TEST_LARGER_THAN_BASELINE} for a baseline-derived bar whose
     * baseline run is smaller than the test.
     */
    @Override
    public List<ConfigurationRefusal> configurationRefusals(
            ConfigurationCheck<PerCriterionPassRateStatistics> check) {
        List<ConfigurationRefusal> refusals = new ArrayList<>();
        int planned = check.plannedSamples();
        boolean anyRegression = false;
        for (Map.Entry<String, CriterionPosture> entry : postures(check.criterionPostures())) {
            Kind kind = kindOf(entry.getValue());
            if (kind == Kind.COMPLIANCE && check.intent() == TestIntent.VERIFICATION) {
                double requirement = requirementOf(entry.getValue());
                if (requirement > 0.0) {
                    double alpha = Methodology.alphaFromConfidence(
                            confidenceFor(entry.getKey(), entry.getValue()));
                    int minimum = ComplianceRule.minimumFeasibleSamples(requirement, alpha);
                    if (planned < minimum) {
                        refusals.add(new ConfigurationRefusal(
                                ConfigurationError.COMPLIANCE_INFEASIBLE,
                                String.format("criterion '%s': no count of %d samples can "
                                                + "demonstrate %s at alpha %s (feasibility "
                                                + "minimum %d)",
                                        entry.getKey(), planned, requirement, alpha, minimum)));
                    }
                }
            }
            anyRegression |= kind == Kind.REGRESSION;
        }
        if (anyRegression && check.baseline().isPresent()) {
            int baselineSamples = baselineRunSamples(check.baseline().get());
            if (planned > baselineSamples) {
                refusals.add(new ConfigurationRefusal(
                        ConfigurationError.TEST_LARGER_THAN_BASELINE,
                        String.format("the test (%d samples) is larger than its baseline "
                                + "(%d samples)", planned, baselineSamples)));
            }
        }
        return refusals;
    }

    private static int baselineRunSamples(PerCriterionPassRateStatistics baseline) {
        int max = 0;
        for (PassRateStatistics stats : baseline.byCriterion().values()) {
            max = Math.max(max, stats.sampleCount());
        }
        return max;
    }

    /**
     * The postures to judge: the contract's, or — for an evaluator
     * registered against a contract that declared none — this
     * evaluator's own, under its name.
     */
    private List<Map.Entry<String, CriterionPosture>> postures(Map<String, CriterionPosture> declared) {
        if (!declared.isEmpty()) {
            List<Map.Entry<String, CriterionPosture>> functional = declared.entrySet().stream()
                    .filter(e -> !e.getValue().isLatency())
                    .toList();
            if (!functional.isEmpty()) {
                return functional;
            }
        }
        return List.of(Map.entry(NAME, CriterionPosture.implicit()));
    }

    // ── Evaluation ──────────────────────────────────────────────────

    @Override
    public CriterionResult evaluate(EvaluationContext<OT, PerCriterionPassRateStatistics> ctx) {
        Objects.requireNonNull(ctx, "ctx");
        SampleSummary<OT> summary = ctx.summary();
        if (summary.total() == 0) {
            return inconclusive("zero samples taken", Map.of());
        }
        List<CriterionSampleCounts> methodologyCriteria = methodologyCriteria(summary, ctx);
        PerCriterionPassRateStatistics baselineMap = null;
        if (isEmpirical()) {
            baselineMap = ctx.baseline().orElse(null);
            if (baselineMap == null || baselineMap.byCriterion().isEmpty()) {
                return EmpiricalChecks.noBaseline(NAME, empiricalDetail());
            }
            String baselineIdentity = ctx.baselineInputsIdentity().orElseThrow(() ->
                    new IllegalStateException(
                            "baseline statistics were resolved but baselineInputsIdentity is empty — "
                                    + "the BaselineProvider is producing inconsistent state"));
            Optional<CriterionResult> identityViolation = EmpiricalChecks.inputsIdentityMatch(
                    NAME, ctx.testInputsIdentity(), baselineIdentity, empiricalDetail());
            if (identityViolation.isPresent()) {
                return identityViolation.get();
            }
        }

        boolean isK1 = methodologyCriteria.size() == 1;
        List<Judged> judged = new ArrayList<>(methodologyCriteria.size());
        for (CriterionSampleCounts counts : methodologyCriteria) {
            CriterionPosture posture = ctx.criterionPostures().getOrDefault(
                    counts.criterionId(), CriterionPosture.implicit());
            Optional<Judged> j = judge(counts, posture, baselineMap, isK1);
            if (j.isEmpty()) {
                // A single criterion with no matching baseline entry: the
                // criterion has no baseline to compare against.
                return EmpiricalChecks.noBaseline(NAME, empiricalDetail());
            }
            judged.add(j.get());
        }

        Verdict composite = Verdict.aggregate(judged.stream().map(Judged::verdict).toList());
        Map<String, Object> detail = detailFor(judged, summary, ctx);
        String explanation = isK1
                ? judged.get(0).explanation()
                : "composite verdict over " + judged.size() + " criteria: "
                        + String.join("; ", judged.stream().map(Judged::explanation).toList());
        return new CriterionResult(NAME, composite, explanation, detail);
    }

    /**
     * The per-criterion counts to judge. A single criterion is judged on
     * the run-level counts (every failure of the run is a failure of its
     * only criterion); several are judged each on its own tally. A run
     * that recorded no per-criterion counts (hand-built fixtures) is
     * judged on the run-level counts under the baseline's lone entry, or
     * this evaluator's name.
     */
    private List<CriterionSampleCounts> methodologyCriteria(
            SampleSummary<OT> summary,
            EvaluationContext<OT, PerCriterionPassRateStatistics> ctx) {
        // A latency declaration rides in the criteria bundle under its own
        // id with no postconditions; it is decided by the latency rules,
        // never as a pass rate.
        List<CriterionSampleCounts> recorded = summary.criterionSampleCounts().stream()
                .filter(c -> !ctx.criterionPostures().getOrDefault(
                        c.criterionId(), CriterionPosture.implicit()).isLatency())
                .toList();
        if (recorded.size() == 1) {
            CriterionSampleCounts only = recorded.get(0);
            // Zero apply failures on this synthetic row: run-level
            // failures already include every apply failure, so restating
            // them would count them twice in the denominator.
            return List.of(new CriterionSampleCounts(
                    only.criterionId(), summary.successes(), summary.failures(),
                    only.transformFail(), 0));
        }
        if (!recorded.isEmpty()) {
            return recorded;
        }
        String fallbackId = NAME;
        if (isEmpirical()) {
            PerCriterionPassRateStatistics b = ctx.baseline().orElse(null);
            if (b != null && b.byCriterion().size() == 1) {
                fallbackId = b.byCriterion().keySet().iterator().next();
            }
        }
        return List.of(new CriterionSampleCounts(
                fallbackId, summary.successes(), summary.failures(), 0, 0));
    }

    /**
     * Judges one methodology criterion under its rule. Empty only for a
     * single criterion with no baseline entry.
     */
    private Optional<Judged> judge(
            CriterionSampleCounts counts,
            CriterionPosture posture,
            PerCriterionPassRateStatistics baselineMap,
            boolean isK1) {
        String id = counts.criterionId();
        int successes = counts.pass();
        int total = counts.pass() + counts.fail();
        double observed = total == 0 ? 0.0 : (double) successes / total;
        Kind kind = kindOf(posture);
        Map<String, Object> decision = new LinkedHashMap<>();
        decision.put("observed", observed);
        decision.put("successes", successes);
        decision.put("total", total);
        posture.contractRef().ifPresent(ref -> decision.put("contractRef", ref));

        if (kind == Kind.ZERO_FAILURES) {
            ThresholdOrigin zfOrigin = posture.origin().orElse(
                    mode == Mode.ZERO_FAILURES ? origin : ThresholdOrigin.POLICY);
            Verdict v = total == 0 ? Verdict.INCONCLUSIVE
                    : counts.fail() == 0 ? Verdict.PASS : Verdict.FAIL;
            decision.put("origin", zfOrigin.name());
            return Optional.of(new Judged(id, v, 1.0, decision, String.format(
                    "%s: zero-failures (origin=%s); failures=%d "
                            + "(of which transform/no-value=%d) over %d samples → %s",
                    id, zfOrigin, counts.fail(), counts.transformFail(), total, v)));
        }

        double criterionConfidence = confidenceFor(id, posture);
        double alpha = Methodology.alphaFromConfidence(criterionConfidence);
        decision.put("confidence", criterionConfidence);
        decision.put("alpha", alpha);

        if (kind == Kind.COMPLIANCE) {
            return Optional.of(judgeCompliance(id, successes, total, posture, alpha, decision));
        }
        return judgeRegression(id, successes, total, posture, alpha, baselineMap, isK1, decision);
    }

    private Judged judgeCompliance(
            String id, int successes, int total, CriterionPosture posture, double alpha,
            Map<String, Object> decision) {
        double requirement = requirementOf(posture);
        ThresholdOrigin reqOrigin = posture.origin().orElse(origin);
        decision.put("origin", reqOrigin.name());
        if (total == 0) {
            return new Judged(id, Verdict.INCONCLUSIVE, requirement, decision,
                    id + ": INCONCLUSIVE (no samples)");
        }
        if (requirement <= 0.0) {
            // A zero requirement is met by any evidence; the exact rule is
            // defined for a requirement in (0, 1) only.
            return new Judged(id, Verdict.PASS, requirement, decision,
                    id + ": requirement 0 is met by any outcome → PASS");
        }
        ComplianceRule.Decision d = ComplianceRule.decide(successes, total, requirement, alpha);
        Verdict v = verdictOf(d.verdict());
        decision.put("decisionRule", d.rule().id());
        d.minimumPassing().ifPresent(k -> decision.put("kMin", k));
        decision.put("passPossible", d.passPossible());
        decision.put("falseCompliance", d.falseCompliance());
        decision.put("clopperPearsonLower", d.clopperPearsonLower());
        String artefact = d.passPossible()
                ? "k_min=" + d.minimumPassing().getAsInt()
                : "no count of this size can pass";
        return new Judged(id, v, requirement, decision, String.format(
                "%s: successes=%d of %d vs %s (compliance/exact-binomial, requirement=%.4f, "
                        + "alpha=%s, origin=%s) → %s",
                id, successes, total, artefact, requirement, alpha, reqOrigin, v));
    }

    private Optional<Judged> judgeRegression(
            String id, int successes, int total, CriterionPosture posture, double alpha,
            PerCriterionPassRateStatistics baselineMap, boolean isK1,
            Map<String, Object> decision) {
        PassRateStatistics baseline = baselineMap == null ? null : baselineMap.byCriterion().get(id);
        if (baseline == null && isK1 && baselineMap != null && baselineMap.byCriterion().size() == 1) {
            // K=1 isomorphism: a single-entry baseline matches the single
            // run-side criterion even when their ids differ (older
            // baselines were written under the legacy id "contract").
            baseline = baselineMap.byCriterion().values().iterator().next();
        }
        decision.put("origin", ThresholdOrigin.EMPIRICAL.name());
        if (baseline == null) {
            if (isK1) {
                return Optional.empty();
            }
            return Optional.of(new Judged(id, Verdict.INCONCLUSIVE, Double.NaN, decision,
                    id + ": INCONCLUSIVE (no baseline entry for this criterion)"));
        }
        int baselineTrials = baseline.sampleCount();
        int baselineSuccesses = (int) Math.round(baseline.observedPassRate() * baselineTrials);
        decision.put("baselineSampleCount", baselineTrials);
        decision.put("baselineSuccesses", baselineSuccesses);
        decision.put("baselineRate", baseline.observedPassRate());
        if (total == 0 || baselineTrials == 0) {
            return Optional.of(new Judged(id, Verdict.INCONCLUSIVE, Double.NaN, decision,
                    id + ": INCONCLUSIVE (no samples to compare)"));
        }
        RegressionRule.Decision d = RegressionRule.decide(
                successes, total, baselineSuccesses, baselineTrials, alpha);
        RegressionRule.Derivation derivation = d.derivation();
        Verdict v = verdictOf(d.verdict());
        decision.put("decisionRule", d.rule().id());
        decision.put("cutoff", derivation.cutoff());
        decision.put("displayedRate", derivation.displayedRate());
        derivation.sizeAtAssumedCommonRate()
                .ifPresent(size -> decision.put("sizeAtAssumedCommonRate", size));
        disclosePower(decision, posture, baselineSuccesses, baselineTrials, total, alpha);
        return Optional.of(new Judged(id, v, derivation.thresholdReal(), decision, String.format(
                "%s: successes=%d of %d vs cutoff=%d (regression/fisher against baseline "
                        + "%d of %d, alpha=%s) → %s",
                id, successes, total, derivation.cutoff(), baselineSuccesses, baselineTrials,
                alpha, v)));
    }

    /**
     * What the design can detect (companion §5.6): at a declared design
     * alternative rate, the design power and the resolved-test power,
     * named apart; with none declared, the minimum detectable degradation
     * at 80% power, which inverts the design power.
     */
    private static void disclosePower(
            Map<String, Object> decision, CriterionPosture posture,
            int baselineSuccesses, int baselineTrials, int testSamples, double alpha) {
        double baselineRate = (double) baselineSuccesses / baselineTrials;
        if (baselineRate <= 0.0) {
            return;
        }
        OptionalDouble designRate = designAlternativeRate(posture, baselineRate);
        if (designRate.isPresent() && designRate.getAsDouble() < baselineRate) {
            double rate = designRate.getAsDouble();
            decision.put("designAlternativeRate", rate);
            decision.put("designPower",
                    RegressionRule.designPower(baselineTrials, testSamples, alpha, baselineRate, rate));
            decision.put("resolvedTestPower",
                    RegressionRule.resolvedPower(
                            baselineSuccesses, baselineTrials, testSamples, alpha, rate));
            return;
        }
        RegressionRule.minimumDetectableDegradation(
                        baselineTrials, testSamples, alpha, baselineRate, RegressionRule.MDD_POWER)
                .ifPresent(mdd -> decision.put("minimumDetectableDegradation", mdd));
    }

    /**
     * The design alternative rate a criterion declares: its tolerated
     * rate, or the baseline rate less its declared detectable effect.
     */
    private static OptionalDouble designAlternativeRate(CriterionPosture posture, double baselineRate) {
        if (posture.toleratedRate().isPresent()) {
            return posture.toleratedRate();
        }
        if (posture.mde().isPresent()) {
            return OptionalDouble.of(baselineRate - posture.mde().getAsDouble());
        }
        return OptionalDouble.empty();
    }

    private Kind kindOf(CriterionPosture posture) {
        return switch (posture.kind()) {
            case STATISTICAL_CONTRACTUAL -> Kind.COMPLIANCE;
            case STATISTICAL_EMPIRICAL -> Kind.REGRESSION;
            case ZERO_FAILURES -> Kind.ZERO_FAILURES;
            default -> switch (mode) {
                case CONTRACTUAL -> Kind.COMPLIANCE;
                case EMPIRICAL_DEFAULT, EMPIRICAL_PINNED -> Kind.REGRESSION;
                case ZERO_FAILURES -> Kind.ZERO_FAILURES;
            };
        };
    }

    private double requirementOf(CriterionPosture posture) {
        return posture.kind() == CriterionPosture.Kind.STATISTICAL_CONTRACTUAL
                ? posture.threshold().getAsDouble()
                : threshold;
    }

    /**
     * The confidence a criterion is judged at: the contract criterion's
     * own {@code .atConfidence(c)} where it declared one — a floor the
     * test cannot loosen — else this evaluator's.
     */
    private double confidenceFor(String criterionId, CriterionPosture posture) {
        if (posture.confidenceFloor().isEmpty()) {
            return confidence;
        }
        double floor = posture.confidenceFloor().getAsDouble();
        if (!confidenceDeclared) {
            return floor;
        }
        if (confidence < floor) {
            throw new IllegalStateException(String.format(
                    "criterion '%s' declares a confidence floor of %.4f but the test runs at %.4f — "
                            + "the contract's floor cannot be loosened by the test (raise the test's "
                            + "confidence to %.4f or remove the floor on the criterion)",
                    criterionId, floor, confidence, floor));
        }
        return confidence;
    }

    private static Verdict verdictOf(RuleVerdict verdict) {
        return switch (verdict) {
            case PASS -> Verdict.PASS;
            case FAIL -> Verdict.FAIL;
            case INCONCLUSIVE -> Verdict.INCONCLUSIVE;
        };
    }

    // ── Result detail ───────────────────────────────────────────────

    /**
     * The result detail. A single criterion keeps the flat shape
     * downstream renderers read (observed, threshold, origin, counts and
     * the decision artefacts); several carry per-criterion maps. Every
     * shape carries {@code verdictsByCriterion} — the criterion's own
     * judgements, which the per-criterion aggregation reuses — and
     * {@code decisionsByCriterion}, each criterion's decision artefacts.
     */
    private Map<String, Object> detailFor(
            List<Judged> judged, SampleSummary<OT> summary,
            EvaluationContext<OT, PerCriterionPassRateStatistics> ctx) {
        Map<String, Object> detail = new LinkedHashMap<>();
        Map<String, String> verdicts = new LinkedHashMap<>();
        Map<String, Double> thresholds = new LinkedHashMap<>();
        Map<String, Double> observed = new LinkedHashMap<>();
        Map<String, Map<String, Object>> decisions = new LinkedHashMap<>();
        for (Judged j : judged) {
            verdicts.put(j.id(), j.verdict().name());
            thresholds.put(j.id(), j.threshold());
            observed.put(j.id(), (Double) j.decision().get("observed"));
            Map<String, Object> d = new LinkedHashMap<>(j.decision());
            if (!Double.isNaN(j.threshold())) {
                d.put("threshold", j.threshold());
            }
            decisions.put(j.id(), Map.copyOf(d));
        }
        if (judged.size() == 1) {
            detail.putAll(judged.get(0).decision());
            detail.put("threshold", judged.get(0).threshold());
            detail.put("failures", summary.failures());
        } else {
            detail.put("origin", (isEmpirical() ? ThresholdOrigin.EMPIRICAL : origin).name());
            detail.put("total", summary.total());
            detail.put("successes", summary.successes());
            detail.put("failures", summary.failures());
            detail.put("confidence", confidence);
            detail.put("thresholdsByCriterion", Map.copyOf(thresholds));
            detail.put("observedByCriterion", Map.copyOf(observed));
        }
        publishSizingDeclaration(detail, ctx.criterionPostures());
        detail.put("verdictsByCriterion", Map.copyOf(verdicts));
        detail.put("decisionsByCriterion", Map.copyOf(decisions));
        return detail;
    }

    /**
     * Publishes the run's declared sizing — the tolerated (design
     * alternative) rate, or the relative detectable effect, with the
     * declared power — from the first criterion that declared one, so the
     * verdict path can name the run's operational approach from its
     * configuration.
     */
    private static void publishSizingDeclaration(
            Map<String, Object> detail, Map<String, CriterionPosture> postures) {
        for (CriterionPosture p : postures.values()) {
            if (p.isRiskDriven() || p.isConfidenceFirst()) {
                p.toleratedRate().ifPresent(rate -> detail.put("toleratedRate", rate));
                p.mde().ifPresent(m -> detail.put("declaredMde", m));
                p.power().ifPresent(pw -> detail.put("declaredPower", pw));
                return;
            }
        }
    }

    private CriterionResult inconclusive(String reason, Map<String, Object> detail) {
        return new CriterionResult(NAME, Verdict.INCONCLUSIVE, reason, detail);
    }

    /** One methodology criterion judged under its rule. */
    private record Judged(
            String id, Verdict verdict, double threshold,
            Map<String, Object> decision, String explanation) {
    }
}
