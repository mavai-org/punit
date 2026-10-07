package org.mavai.punit.api.spec;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.mavai.punit.api.LatencySpec;
import org.mavai.punit.api.PercentileKey;
import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.ThresholdOrigin;
import org.mavai.punit.statistics.ComplianceRule;
import org.mavai.punit.statistics.ConfigurationError;
import org.mavai.punit.statistics.DecisionRule;
import org.mavai.punit.statistics.LatencyRules;
import org.mavai.punit.statistics.Methodology;
import org.mavai.punit.statistics.RuleVerdict;
import org.mavai.punit.statistics.StatisticalDefaults;

/**
 * A percentile-latency criterion: one latency constraint per asserted
 * percentile, each decided after the run on the
 * <em>successful</em> latencies — those of the samples that passed every
 * functional criterion (Statistical Companion §12.2.1).
 *
 * <p>A constraint is decided by the rule for its threshold source
 * (§12.3.3):
 * <ul>
 *   <li>an <b>explicit</b> ceiling ({@link #meeting}) by
 *       {@code latency/compliance-exact-binomial} (§12.3.4): PASS iff the
 *       count of latencies at or below the ceiling reaches {@code y_min};
 *       INCONCLUSIVE when too few latencies arrived for any count to
 *       pass. The raw comparison of the observed percentile with the
 *       ceiling is reported beside it, as a raw figure that decides
 *       nothing;</li>
 *   <li>a <b>baseline-derived</b> threshold ({@link #empirical}) by
 *       {@code latency/precedence} (§12.4.2): the threshold is the
 *       baseline latency at the smallest rank an undegraded service would
 *       exceed with probability at most alpha, for the test's actual
 *       number of successful latencies; PASS iff the test's percentile is
 *       at most it. INCONCLUSIVE when no rank achieves alpha (saturated)
 *       and, under VERIFICATION, below the percentile's non-degeneracy
 *       minimum (§12.5.2).</li>
 * </ul>
 *
 * <p>The criterion's verdict is the latency dimension's verdict
 * {@code V_latency}: the structural composite of its constraints
 * (§12.3.2).
 *
 * <p>The conventional authoring path is the contract-side surface:
 * {@code empirical().atMost(P95).atMost(P99)} (empirical) or
 * {@code meeting().atMost(P95, ofMillis(500)).contractRef(SLA, "...")}
 * (explicit), declared on the contract's
 * {@link org.mavai.punit.api.Contract#latency()} sibling. The framework's
 * auto-injection routes the contract's posture through
 * {@code SpecCriterionDeriver} to a {@code PercentileLatency} instance.
 */
public final class PercentileLatency<OT> implements Criterion<OT, LatencyStatistics> {

    /** The criterion's name, as its results carry it. */
    public static final String NAME = "percentile-latency";

    private enum Mode { CONTRACTUAL, EMPIRICAL_DEFAULT, EMPIRICAL_PINNED }

    private final Mode mode;
    private final LatencySpec declaredSpec;
    private final ThresholdOrigin origin;
    private final EnumSet<PercentileKey> assertedPercentiles;
    private final Supplier<Experiment> baselineSupplier;
    private final double confidence;

    private PercentileLatency(
            Mode mode,
            LatencySpec declaredSpec,
            ThresholdOrigin origin,
            EnumSet<PercentileKey> assertedPercentiles,
            Supplier<Experiment> baselineSupplier,
            double confidence) {
        this.mode = mode;
        this.declaredSpec = declaredSpec;
        this.origin = origin;
        this.assertedPercentiles = assertedPercentiles;
        this.baselineSupplier = baselineSupplier;
        this.confidence = confidence;
    }

    /** Explicit ceilings at the framework's default confidence. */
    public static <OT> PercentileLatency<OT> meeting(LatencySpec spec, ThresholdOrigin origin) {
        return meeting(spec, origin, StatisticalDefaults.DEFAULT_CONFIDENCE);
    }

    /**
     * Explicit ceilings, each demonstrated by
     * {@code latency/compliance-exact-binomial} at {@code 1 − confidence}.
     */
    public static <OT> PercentileLatency<OT> meeting(
            LatencySpec spec, ThresholdOrigin origin, double confidence) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(origin, "origin");
        validateConfidence(confidence);
        if (!spec.hasAnyThreshold()) {
            throw new IllegalArgumentException(
                    "LatencySpec must assert at least one percentile — call .p50Millis(...), "
                            + ".p90Millis(...), .p95Millis(...), or .p99Millis(...) on the builder");
        }
        if (origin == ThresholdOrigin.EMPIRICAL) {
            throw new IllegalArgumentException(
                    "ThresholdOrigin.EMPIRICAL is reserved for the empirical factories; "
                            + "call PercentileLatency.empirical(...) or .empiricalFrom(...) instead");
        }
        return new PercentileLatency<>(
                Mode.CONTRACTUAL, spec, origin, assertedFromSpec(spec), null, confidence);
    }

    public static <OT> PercentileLatency<OT> empirical(PercentileKey first, PercentileKey... rest) {
        return empirical(StatisticalDefaults.DEFAULT_CONFIDENCE, first, rest);
    }

    public static <OT> PercentileLatency<OT> empirical(
            double confidence, PercentileKey first, PercentileKey... rest) {
        validateConfidence(confidence);
        EnumSet<PercentileKey> asserted = toEnumSet(first, rest);
        return new PercentileLatency<>(
                Mode.EMPIRICAL_DEFAULT, null, ThresholdOrigin.EMPIRICAL, asserted, null, confidence);
    }

    public static <OT> PercentileLatency<OT> empiricalFrom(
            Supplier<Experiment> baseline,
            PercentileKey first,
            PercentileKey... rest) {
        Objects.requireNonNull(baseline, "baseline");
        EnumSet<PercentileKey> asserted = toEnumSet(first, rest);
        return new PercentileLatency<>(
                Mode.EMPIRICAL_PINNED, null, ThresholdOrigin.EMPIRICAL, asserted, baseline,
                StatisticalDefaults.DEFAULT_CONFIDENCE);
    }

    private static void validateConfidence(double c) {
        if (Double.isNaN(c) || c <= 0.0 || c >= 1.0) {
            throw new IllegalArgumentException(
                    "confidence must be in (0, 1), got " + c);
        }
    }

    /** The confidence level {@code 1 − alpha} every constraint is decided at. */
    public double confidence() {
        return confidence;
    }

    /** The baseline supplier when pinned; empty otherwise. */
    public Optional<Supplier<Experiment>> baselineSupplier() {
        return Optional.ofNullable(baselineSupplier);
    }

    /** The percentiles this criterion asserts against. */
    public EnumSet<PercentileKey> assertedPercentiles() {
        return EnumSet.copyOf(assertedPercentiles);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Class<LatencyStatistics> statisticsType() {
        return LatencyStatistics.class;
    }

    @Override
    public boolean isEmpirical() {
        return mode != Mode.CONTRACTUAL;
    }

    @Override
    public Map<String, Object> empiricalDetail() {
        if (mode == Mode.CONTRACTUAL) {
            return Map.of();
        }
        return Map.of("assertedPercentiles", assertedCsv());
    }

    private double alpha() {
        return Methodology.alphaFromConfidence(confidence);
    }

    // ── Before the run ──────────────────────────────────────────────

    /**
     * {@code COMPLIANCE_INFEASIBLE} for an explicit ceiling whose planned
     * samples could not demonstrate it even if every sample succeeded,
     * under VERIFICATION; {@code TEST_LARGER_THAN_BASELINE} for a
     * baseline-derived threshold whose baseline run is smaller than the
     * test. The numbers of successful latencies are never compared: they
     * are not known before the run.
     */
    @Override
    public List<ConfigurationRefusal> configurationRefusals(ConfigurationCheck<LatencyStatistics> check) {
        List<ConfigurationRefusal> refusals = new ArrayList<>();
        int planned = check.plannedSamples();
        if (mode == Mode.CONTRACTUAL) {
            if (check.intent() == TestIntent.VERIFICATION) {
                for (PercentileKey key : assertedPercentiles) {
                    int minimum = ComplianceRule.minimumFeasibleSamples(key.value(), alpha());
                    if (planned < minimum) {
                        refusals.add(new ConfigurationRefusal(
                                ConfigurationError.COMPLIANCE_INFEASIBLE,
                                String.format("latency %s: no count of %d latencies can "
                                                + "demonstrate the requirement at alpha %s "
                                                + "(feasibility minimum %d)",
                                        key.detailKey(), planned, alpha(), minimum)));
                    }
                }
            }
            return refusals;
        }
        check.baseline().ifPresent(baseline -> {
            if (planned > baseline.runSamples()) {
                refusals.add(new ConfigurationRefusal(
                        ConfigurationError.TEST_LARGER_THAN_BASELINE,
                        String.format("the test (%d samples) is larger than its latency baseline "
                                + "(%d samples)", planned, baseline.runSamples())));
            }
        });
        return refusals;
    }

    /**
     * The pre-run planning checks of a baseline-derived constraint
     * (§12.5.3): non-degeneracy and the precedence rank's existence at
     * the expected number of successful latencies. They are warnings with
     * a planning figure, never a refusal: the run goes ahead and both are
     * decided on the actual count.
     */
    public List<String> planningWarnings(int plannedSamples, LatencyStatistics baseline) {
        Objects.requireNonNull(baseline, "baseline");
        if (mode == Mode.CONTRACTUAL || baseline.sampleCount() == 0) {
            return List.of();
        }
        double rate = baseline.passingRate();
        List<String> warnings = new ArrayList<>();
        for (PercentileKey key : assertedPercentiles) {
            LatencyRules.NondegeneracyPlanning nd =
                    LatencyRules.planNondegeneracy(key.value(), plannedSamples, rate);
            if (nd.warning()) {
                warnings.add(String.format(
                        "Latency %s: %d samples are expected to give %d successful latencies, "
                                + "below the %s minimum of %d; a run that returns fewer is "
                                + "INCONCLUSIVE. %d planned samples are expected to reach it.",
                        key.detailKey(), plannedSamples, nd.expectedTestSamples(),
                        key.detailKey(), nd.minimumContributingSamples(),
                        nd.plannedSamplesNeeded()));
            }
            LatencyRules.PrecedencePlanning pp = LatencyRules.planPrecedence(
                    baseline.sampleCount(), plannedSamples, rate, key.value(), alpha());
            if (pp.warning()) {
                warnings.add(String.format(
                        "Latency %s: against a baseline of %d latencies no threshold exists at "
                                + "alpha %s for the %d successful latencies expected; the run "
                                + "is likely to be INCONCLUSIVE (saturated).%s",
                        key.detailKey(), baseline.sampleCount(), alpha(),
                        pp.expectedTestSamples(),
                        pp.minimumBaselineTrials().isPresent()
                                ? " A baseline of at least " + pp.minimumBaselineTrials().getAsInt()
                                        + " latencies supports one."
                                : ""));
            }
        }
        return warnings;
    }

    // ── After the run ───────────────────────────────────────────────

    @Override
    public CriterionResult evaluate(EvaluationContext<OT, LatencyStatistics> ctx) {
        Objects.requireNonNull(ctx, "ctx");
        SampleSummary<OT> summary = ctx.summary();
        if (summary.total() == 0) {
            return new CriterionResult(NAME, Verdict.INCONCLUSIVE, "zero samples taken", Map.of());
        }
        LatencyStatistics baseline = null;
        if (mode != Mode.CONTRACTUAL) {
            baseline = ctx.baseline().orElse(null);
            if (baseline == null) {
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
        return decide(successfulLatencies(summary), baseline, ctx.intent());
    }

    /**
     * Decides every asserted constraint on the given successful latencies
     * — the evaluation step after the run, exposed so the decision can be
     * checked against reference latencies without a run.
     *
     * @param latencies successful latencies in milliseconds, any order
     * @param baseline  the latency baseline, for a baseline-derived
     *                  criterion; ignored for an explicit one
     * @param intent    the test's intent
     */
    public CriterionResult decide(double[] latencies, LatencyStatistics baseline, TestIntent intent) {
        Objects.requireNonNull(latencies, "latencies");
        Objects.requireNonNull(intent, "intent");
        if (mode != Mode.CONTRACTUAL) {
            Objects.requireNonNull(baseline, "a baseline-derived latency criterion needs its baseline");
        }
        boolean underVerification = intent == TestIntent.VERIFICATION;
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("assertedPercentiles", assertedCsv());
        detail.put("origin", (mode == Mode.CONTRACTUAL ? origin : ThresholdOrigin.EMPIRICAL).name());
        detail.put("source", mode == Mode.CONTRACTUAL ? "explicit" : "baseline-derived");
        detail.put("confidence", confidence);
        detail.put("alpha", alpha());
        detail.put("successfulSamples", latencies.length);
        if (baseline != null && mode != Mode.CONTRACTUAL) {
            detail.put("baselineSampleCount", baseline.sampleCount());
        }

        List<Verdict> verdicts = new ArrayList<>();
        List<String> undecided = new ArrayList<>();
        List<String> breached = new ArrayList<>();
        for (PercentileKey key : assertedPercentiles) {
            Verdict v = mode == Mode.CONTRACTUAL
                    ? decideExplicit(key, latencies, detail)
                    : decideBaselineDerived(key, latencies, baseline, underVerification, detail);
            detail.put("verdict." + key.detailKey(), v.name());
            verdicts.add(v);
            if (v == Verdict.FAIL) {
                breached.add(key.detailKey());
            } else if (v == Verdict.INCONCLUSIVE) {
                undecided.add(key.detailKey());
            }
        }
        Verdict verdict = Verdict.aggregate(verdicts);
        String explanation = switch (verdict) {
            case PASS -> String.format("all %d latency constraints pass over %d successful latencies",
                    assertedPercentiles.size(), latencies.length);
            case FAIL -> String.format("latency constraint(s) %s fail over %d successful latencies",
                    String.join(", ", breached), latencies.length);
            case INCONCLUSIVE -> String.format(
                    "latency constraint(s) %s undecided over %d successful latencies",
                    String.join(", ", undecided), latencies.length);
        };
        return new CriterionResult(NAME, verdict, explanation, detail);
    }

    /** {@code latency/compliance-exact-binomial} on one explicit ceiling. */
    private Verdict decideExplicit(PercentileKey key, double[] latencies, Map<String, Object> detail) {
        String k = key.detailKey();
        long ceiling = key.ceilingMillis(declaredSpec).getAsLong();
        LatencyRules.LatencyCompliance c =
                LatencyRules.evaluateCompliance(latencies, ceiling, key.value(), alpha());
        detail.put("threshold." + k, ceiling);
        detail.put("decisionRule." + k, DecisionRule.LATENCY_COMPLIANCE_EXACT_BINOMIAL.id());
        detail.put("withinThreshold." + k, c.withinThreshold());
        c.minimumWithin().ifPresent(y -> detail.put("requiredWithin." + k, y));
        c.falseCompliance().ifPresent(f -> detail.put("falseCompliance." + k, f));
        c.clopperPearsonLower().ifPresent(lb -> detail.put("clopperPearsonLower." + k, lb));
        c.observedPercentileMs().ifPresent(o -> detail.put("observed." + k, (long) Math.floor(o)));
        c.rawPercentilePass().ifPresent(pass -> detail.put("rawPercentilePass." + k, pass));
        Verdict v = verdictOf(c.verdict());
        if (v == Verdict.FAIL) {
            c.observedPercentileMs().ifPresent(o -> detail.put("breach." + k, (long) Math.floor(o)));
        }
        return v;
    }

    /** {@code latency/precedence} on one baseline-derived threshold. */
    private Verdict decideBaselineDerived(
            PercentileKey key, double[] latencies, LatencyStatistics baseline,
            boolean underVerification, Map<String, Object> detail) {
        String k = key.detailKey();
        detail.put("decisionRule." + k, DecisionRule.LATENCY_PRECEDENCE.id());
        int n = latencies.length;
        LatencyRules.NondegeneracyDecision nd = LatencyRules.decideNondegeneracy(
                key.value(), n, underVerification, LatencyRules.ThresholdSource.BASELINE_DERIVED);
        if (nd.outcome() == LatencyRules.NondegeneracyOutcome.INDICATIVE) {
            detail.put("indicative." + k, true);
        }
        if (n == 0 || baseline.sampleCount() == 0) {
            return Verdict.INCONCLUSIVE;
        }
        double observed = org.mavai.punit.statistics.LatencyStatistics.nearestRankPercentile(
                latencies, key.value());
        detail.put("observed." + k, (long) Math.floor(observed));
        LatencyRules.PrecedenceThreshold t = LatencyRules.derivePrecedenceThreshold(
                toDoubles(baseline.sortedLatenciesMs()), n, key.value(), alpha());
        detail.put("threshold." + k + ".baselinePercentile", (long) t.baselinePercentile());
        if (t.saturated()) {
            detail.put("saturated." + k, true);
            return Verdict.INCONCLUSIVE;
        }
        long threshold = (long) t.threshold().getAsDouble();
        detail.put("threshold." + k, threshold);
        detail.put("threshold." + k + ".rank", t.rank().getAsInt());
        if (nd.outcome() == LatencyRules.NondegeneracyOutcome.INCONCLUSIVE) {
            return Verdict.INCONCLUSIVE;
        }
        if (observed > threshold) {
            detail.put("breach." + k, (long) Math.floor(observed));
            return Verdict.FAIL;
        }
        return Verdict.PASS;
    }

    /**
     * The durations, in milliseconds, of the samples that passed every
     * functional criterion. Every passing outcome is retained on the
     * summary (only failures are capped), so the outcomes list carries
     * them all. Whole milliseconds, truncated exactly as the baseline
     * records them, so the test's latencies and the baseline's are on
     * one scale.
     */
    private static <OT> double[] successfulLatencies(SampleSummary<OT> summary) {
        return summary.outcomes().stream()
                .filter(outcome -> outcome.value().isOk())
                .mapToDouble(outcome -> (double) outcome.duration().toMillis())
                .toArray();
    }

    private static double[] toDoubles(long[] values) {
        double[] out = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = values[i];
        }
        return out;
    }

    private static Verdict verdictOf(RuleVerdict verdict) {
        return switch (verdict) {
            case PASS -> Verdict.PASS;
            case FAIL -> Verdict.FAIL;
            case INCONCLUSIVE -> Verdict.INCONCLUSIVE;
        };
    }

    private String assertedCsv() {
        return assertedPercentiles.stream().map(PercentileKey::detailKey)
                .collect(Collectors.joining(","));
    }

    private static EnumSet<PercentileKey> toEnumSet(PercentileKey first, PercentileKey... rest) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(rest, "rest");
        EnumSet<PercentileKey> set = EnumSet.of(first);
        for (PercentileKey k : rest) {
            if (k == null) {
                throw new NullPointerException("asserted percentiles must not contain null");
            }
            set.add(k);
        }
        return set;
    }

    private static EnumSet<PercentileKey> assertedFromSpec(LatencySpec spec) {
        EnumSet<PercentileKey> set = EnumSet.noneOf(PercentileKey.class);
        for (PercentileKey key : PercentileKey.values()) {
            OptionalLong ceiling = key.ceilingMillis(spec);
            if (ceiling.isPresent()) {
                set.add(key);
            }
        }
        return set;
    }
}
