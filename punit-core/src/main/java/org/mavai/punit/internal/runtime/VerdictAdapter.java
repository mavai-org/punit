package org.mavai.punit.internal.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.stream.Collectors;

import org.mavai.punit.api.ThresholdOrigin;
import org.mavai.punit.api.LatencyResult;
import org.mavai.punit.api.covariate.CovariateAlignment;
import org.mavai.punit.api.spec.EngineRunSummary;
import org.mavai.punit.api.spec.EvaluatedCriterion;
import org.mavai.punit.api.spec.ProbabilisticTestResult;
import org.mavai.punit.api.PercentileKey;
import org.mavai.punit.api.spec.CriterionRole;
import org.mavai.punit.api.spec.PercentileLatency;
import org.mavai.punit.api.spec.Verdict;
import org.mavai.punit.api.spec.VerdictComposition;
import org.mavai.punit.statistics.DecisionRule;
import org.mavai.punit.verdict.LatencyEvaluation;
import org.mavai.punit.verdict.RegressionDisclosure;
import org.mavai.punit.verdict.TestDecision;
import org.mavai.punit.verdict.PUnitVerdict;
import org.mavai.punit.verdict.ProbabilisticTestVerdict;
import org.mavai.punit.verdict.ProbabilisticTestVerdictBuilder;
import org.mavai.punit.verdict.RunMetadata;
import org.mavai.punit.verdict.TokenMode;
import org.mavai.punit.internal.engine.emit.LatencySection;
import org.mavai.punit.verdict.TerminationReason;
import org.mavai.punit.verdict.ProbabilisticTestVerdictBuilder.LatencyInput;
import org.mavai.punit.verdict.ProbabilisticTestVerdictBuilder.MisalignmentInput;

/**
 * Adapts a {@link ProbabilisticTestResult} to the XML-bound
 * {@link ProbabilisticTestVerdict} shape so the
 * {@link org.mavai.punit.report.VerdictXmlWriter VerdictXmlWriter} (and
 * the HTML report) can serialise runs to verdict XML.
 *
 * <p>The adapter is a thin field-mapping function — it does not perform
 * statistical computation, judgement, or rendering. It reads the
 * result's components and the supplied {@link RunMetadata}, and
 * delegates to {@link ProbabilisticTestVerdictBuilder} for the heavy
 * lifting (Wilson-score CI, baseline-derivation narrative, verdict-reason
 * synthesis).
 *
 * <h2>Field provenance</h2>
 *
 * <ul>
 *   <li><b>Identity</b> — from {@code RunMetadata}.</li>
 *   <li><b>Execution</b> — from {@code result.engineSummary()}; the
 *       observed pass rate is computed from successes/total. The
 *       threshold is lifted from the first {@code PassRate}
 *       criterion's detail map (key {@code "threshold"}); falls back
 *       to 0.0 when no such criterion exists.</li>
 *   <li><b>Functional dimension</b> — from
 *       {@code engineSummary.successes()} / {@code .failures()}.</li>
 *   <li><b>Latency</b> — from {@code engineSummary.latencyResult()},
 *       observed-only (no per-percentile assertions). Skipped when
 *       {@code sampleCount() == 0}.</li>
 *   <li><b>Statistics</b> — derived by the builder from the execution
 *       summary at the configured confidence.</li>
 *   <li><b>Covariates</b> — from {@code result.covariates()}.</li>
 *   <li><b>Cost</b> — only {@code methodTokensConsumed} populated;
 *       budgets and {@link TokenMode} default to "unlimited / NONE"
 *       because per-method budgets are not surfaced on the result
 *       today.</li>
 *   <li><b>Provenance</b> — threshold origin from the first
 *       {@code PassRate} criterion's {@code "origin"} detail
 *       key; contract reference from
 *       {@link ProbabilisticTestResult#contractRef()}; spec filename
 *       from {@code engineSummary.baselineFilename()}.</li>
 *   <li><b>Termination</b> — the API
 *       {@link org.mavai.punit.api.spec.TerminationReason} mapped
 *       to the richer core enum; details kept null.</li>
 *   <li><b>Postcondition failures</b> — pass-through from
 *       {@link ProbabilisticTestResult#failuresByPostcondition()}.</li>
 *   <li><b>Verdict</b> — the criterion's three-state
 *       {@link Verdict} flows straight through to the builder via
 *       {@link ProbabilisticTestVerdictBuilder#criterionVerdict}. The
 *       builder then derives the {@link PUnitVerdict} consulting both
 *       the criterion verdict and the run-level overrides
 *       (covariate misalignment and budget exhaustion both force
 *       INCONCLUSIVE regardless of what the criterion concluded).
 *       This preserves a non-covariate INCONCLUSIVE — no baseline,
 *       sample-size violation, identity mismatch — through to the
 *       rendered verdict, preserving the invariant that the verdict
 *       is consistent with the statistical analysis.</li>
 * </ul>
 *
 * <h2>What the adapter cannot fill</h2>
 *
 * <p>Some {@link ProbabilisticTestVerdict} components have no analogue
 * on the result today: budget snapshots, pacing configuration, spec
 * expiration, JUnit-pass status. The adapter produces a verdict with
 * those left at their builder defaults. Field-level fidelity is
 * captured in the test suite; renderers are tolerant of absent
 * optional fields.
 */
public final class VerdictAdapter {

    private VerdictAdapter() { }

    /**
     * Build a {@link ProbabilisticTestVerdict} from a result and the
     * per-run metadata.
     *
     * @param result the run's result
     * @param meta   the run metadata captured at the JUnit boundary
     * @return a fully populated verdict, ready for XML/HTML
     *         serialisation
     */
    public static ProbabilisticTestVerdict adapt(
            ProbabilisticTestResult result, RunMetadata meta) {

        EngineRunSummary engine = result.engineSummary();
        ProbabilisticTestVerdictBuilder b = new ProbabilisticTestVerdictBuilder();

        // Envelope
        meta.correlationId().ifPresent(b::correlationId);
        b.environmentMetadata(meta.environmentMetadata());

        // Identity
        b.identity(
                meta.className(),
                meta.methodName(),
                meta.serviceContractId().orElse(null));

        if (result.refused()) {
            return adaptRefused(result, engine, b);
        }

        // Execution
        double threshold = scanDoubleDetail(result.criterionResults(), "threshold").orElse(0.0);
        double observed = engine.samplesExecuted() == 0
                ? 0.0
                : (double) engine.successes() / (double) engine.samplesExecuted();
        b.execution(
                engine.plannedSamples(),
                engine.samplesExecuted(),
                engine.successes(),
                engine.failures(),
                threshold,
                observed,
                engine.elapsedMs());
        b.intent(result.intent(), engine.confidence());

        // Functional + latency dimensions
        b.functionalDimension(engine.successes(), engine.failures());
        List<LatencyEvaluation> evaluations = latencyEvaluations(result.criterionResults());
        Optional<Verdict> latencyVerdict = result.composition()
                .flatMap(VerdictComposition::latencyVerdict);
        LatencyInput latencyInput = toLatencyInput(engine, !evaluations.isEmpty() || latencyVerdict.isPresent());
        if (latencyInput != null) {
            b.latencyDimension(latencyInput);
            b.latencyEvaluations(latencyVerdict, evaluations);
        }

        // Covariates
        CovariateAlignment alignment = result.covariates();
        b.covariateProfiles(
                alignment.baseline().values(),
                alignment.observed().values());
        b.misalignments(toMisalignmentInputs(alignment));

        // No per-method budget surface yet; pass tokensConsumed and
        // zero budgets / NONE token mode.
        b.cost(engine.tokensConsumed(), 0L, 0L, TokenMode.NONE);

        // Provenance
        ThresholdOrigin origin = scanThresholdOrigin(result.criterionResults());
        if (origin != null || result.contractRef().isPresent() || engine.baselineFilename().isPresent()) {
            b.provenance(
                    origin,
                    result.contractRef().orElse(null),
                    engine.baselineFilename().orElse(null));
        }

        // The resolved baseline's size and rate, published on the empirical
        // criterion's derivation detail — the sizing-transparency
        // disclosures compare the run's size against them.
        java.util.Optional<Double> baselineSamples =
                scanDoubleDetail(result.criterionResults(), "baselineSampleCount");
        java.util.Optional<Double> baselineRate =
                scanDoubleDetail(result.criterionResults(), "baselineRate");
        if (baselineSamples.isPresent() && baselineRate.isPresent()) {
            b.sizingBaseline((int) Math.round(baselineSamples.get()), baselineRate.get());
        }

        // The run's declared sizing, published on the criterion detail —
        // the disclosure names the operational approach from the
        // configuration, never inferred from the threshold origin alone.
        b.sizingDeclaration(
                scanDoubleDetail(result.criterionResults(), "toleratedRate").orElse(null),
                scanDoubleDetail(result.criterionResults(), "declaredMde").orElse(null),
                scanDoubleDetail(result.criterionResults(), "declaredPower").orElse(null));

        // Termination
        b.termination(
                mapTerminationReason(engine.terminationReason()),
                null);

        // Postcondition failure histogram
        b.postconditionFailures(result.failuresByPostcondition());

        // Verdict
        b.criterionVerdict(result.verdict());
        // Criterion results carry the inconclusive-reason discriminant
        // per InconclusiveReasons.DETAIL_KEY; the builder reads them
        // when synthesising the verdict reason.
        b.criterionResults(result.criterionResults());
        // No JUnit-pass concept on the result; default true (the
        // field is only meaningful inside the JUnit context).
        b.junitPassed(true);

        // Per-criterion structural decomposition: the per-criterion verdict
        // rows and the composite over them. The
        // in-memory PerCriterionEvaluation translates row-for-row into
        // the persistence-layer PerCriterionStructure. Empty
        // evaluations leave the field absent.
        Map<String, Map<?, ?>> decisions = decisionsByCriterion(result.criterionResults());
        b.perCriterion(translatePerCriterion(result.perCriterionEvaluation(), decisions));

        // The decision behind the verdict: the rule, what triggered a
        // FAIL or an INCONCLUSIVE, the envelopes; and what the deciding
        // regression rule discloses about the design.
        b.decision(testDecision(result, decisions, evaluations));
        regressionDisclosure(result.perCriterionEvaluation(), decisions)
                .ifPresent(b::regressionDisclosure);

        // The postcondition standings — descriptive per-(input, check)
        // tallies, stated first-class in the record (and the verdict
        // XML's standings element from schema revision 1.3).
        result.postconditionStandings().ifPresent(b::postconditionStandings);

        return b.build();
    }

    /**
     * A configuration refused before any sample ran: identity, the
     * planned execution, the termination and the configuration errors —
     * no verdict, no dimensions, no statistics.
     */
    private static ProbabilisticTestVerdict adaptRefused(
            ProbabilisticTestResult result, EngineRunSummary engine,
            ProbabilisticTestVerdictBuilder b) {
        String detail = result.refusals().stream()
                .map(r -> r.code().name() + ": " + r.reason())
                .collect(Collectors.joining("; "));
        b.execution(engine.plannedSamples(), 0, 0, 0, 0.0, 0.0, 0L);
        b.intent(result.intent(), engine.confidence());
        b.cost(0L, 0L, 0L, TokenMode.NONE);
        result.contractRef().ifPresent(ref -> b.provenance(null, ref, null));
        b.termination(TerminationReason.CONFIGURATION_REFUSED, detail);
        b.criterionVerdict(Verdict.INCONCLUSIVE);
        b.junitPassed(true);
        b.decision(new TestDecision(
                result.configurationErrors(), Optional.of(detail), Optional.empty(), List.of(),
                OptionalDouble.empty(), OptionalDouble.empty()));
        return b.build();
    }

    private static org.mavai.punit.verdict.PerCriterionStructure translatePerCriterion(
            org.mavai.punit.api.spec.PerCriterionEvaluation evaluation,
            Map<String, Map<?, ?>> decisions) {
        if (evaluation.perCriterionVerdicts().isEmpty()) {
            return null;
        }
        List<org.mavai.punit.verdict.CriterionRow> rows = new java.util.ArrayList<>(
                evaluation.perCriterionVerdicts().size());
        for (var v : evaluation.perCriterionVerdicts()) {
            var counts = v.counts();
            rows.add(new org.mavai.punit.verdict.CriterionRow(
                    v.criterionId(),
                    v.verdict(),
                    counts.pass(),
                    counts.fail(),
                    // No per-trial inconclusive any more — a failed
                    // transform / no-value trial is a FAIL. The XML
                    // schema's per-trial inconclusive count is retained
                    // for cross-framework compatibility and is always
                    // zero here.
                    0,
                    v.observed(),
                    v.threshold(),
                    ruleOf(decisions.get(v.criterionId()), "decisionRule")));
        }
        return new org.mavai.punit.verdict.PerCriterionStructure(
                rows, evaluation.compositeVerdict());
    }

    /** Each methodology criterion's decision artefacts, from the pass-rate evaluation. */
    private static Map<String, Map<?, ?>> decisionsByCriterion(List<EvaluatedCriterion> evaluated) {
        Map<String, Map<?, ?>> out = new LinkedHashMap<>();
        for (EvaluatedCriterion ec : evaluated) {
            if (ec.result().detail().get("decisionsByCriterion") instanceof Map<?, ?> raw) {
                raw.forEach((k, v) -> {
                    if (k instanceof String id && v instanceof Map<?, ?> m) {
                        out.putIfAbsent(id, m);
                    }
                });
            }
        }
        return out;
    }

    private static Optional<DecisionRule> ruleOf(Map<?, ?> detail, String key) {
        if (detail != null && detail.get(key) instanceof String id) {
            return DecisionRule.fromId(id);
        }
        return Optional.empty();
    }

    /**
     * The enforced latency constraints as the verdict records them. A
     * constraint with no threshold that is not saturated — no successful
     * latency to rank — has no evaluation row; its INCONCLUSIVE outcome
     * is carried by the latency dimension's verdict.
     */
    private static List<LatencyEvaluation> latencyEvaluations(List<EvaluatedCriterion> evaluated) {
        List<LatencyEvaluation> out = new java.util.ArrayList<>();
        for (EvaluatedCriterion ec : evaluated) {
            if (ec.role() != CriterionRole.REQUIRED
                    || !PercentileLatency.NAME.equals(ec.result().criterionName())) {
                continue;
            }
            Map<String, Object> d = ec.result().detail();
            boolean explicit = "explicit".equals(d.get("source"));
            for (PercentileKey key : PercentileKey.values()) {
                String k = key.detailKey();
                if (!(d.get("verdict." + k) instanceof String verdictName)) {
                    continue;
                }
                boolean saturated = Boolean.TRUE.equals(d.get("saturated." + k));
                OptionalLong threshold = longOf(d.get("threshold." + k));
                if (threshold.isEmpty() && !saturated) {
                    continue;
                }
                Optional<DecisionRule> rule = ruleOf(d, "decisionRule." + k);
                if (rule.isEmpty()) {
                    continue;
                }
                LatencyEvaluation.Status status = saturated
                        ? LatencyEvaluation.Status.SATURATED
                        : switch (Verdict.valueOf(verdictName)) {
                            case PASS -> LatencyEvaluation.Status.PASS;
                            case FAIL -> LatencyEvaluation.Status.STRICT_FAIL;
                            case INCONCLUSIVE -> LatencyEvaluation.Status.INFEASIBLE;
                        };
                out.add(new LatencyEvaluation(
                        k,
                        longOf(d.get("observed." + k)),
                        threshold,
                        explicit ? LatencyEvaluation.Provenance.EXPLICIT
                                : LatencyEvaluation.Provenance.BASELINE_DERIVED,
                        status,
                        !explicit && d.get("confidence") instanceof Number c
                                ? OptionalDouble.of(c.doubleValue()) : OptionalDouble.empty(),
                        intOf(d.get("threshold." + k + ".rank")),
                        explicit ? OptionalInt.empty() : intOf(d.get("baselineSampleCount")),
                        rule.get(),
                        intOf(d.get("withinThreshold." + k)),
                        intOf(d.get("requiredWithin." + k)),
                        Boolean.TRUE.equals(d.get("indicative." + k))));
            }
        }
        return out;
    }

    /**
     * The decision behind the verdict: the rule, when one rule decided
     * every criterion and constraint; the triggers; the envelopes.
     */
    private static TestDecision testDecision(
            ProbabilisticTestResult result, Map<String, Map<?, ?>> decisions,
            List<LatencyEvaluation> evaluations) {
        java.util.Set<DecisionRule> rules = new java.util.LinkedHashSet<>();
        for (var v : result.perCriterionEvaluation().perCriterionVerdicts()) {
            ruleOf(decisions.get(v.criterionId()), "decisionRule").ifPresent(rules::add);
        }
        evaluations.forEach(e -> rules.add(e.decisionRule()));
        Optional<DecisionRule> single = rules.size() == 1
                ? Optional.of(rules.iterator().next()) : Optional.empty();
        return result.composition()
                .map(c -> new TestDecision(List.of(), Optional.empty(), single, c.triggering(),
                        c.falseComplianceEnvelope(), c.falseDegradationSignalEnvelope()))
                .orElse(new TestDecision(List.of(), Optional.empty(), single, List.of(),
                        OptionalDouble.empty(), OptionalDouble.empty()));
    }

    /**
     * What a single {@code regression/fisher} criterion discloses about
     * its design; empty unless exactly one criterion was so decided.
     */
    private static Optional<RegressionDisclosure> regressionDisclosure(
            org.mavai.punit.api.spec.PerCriterionEvaluation evaluation,
            Map<String, Map<?, ?>> decisions) {
        if (evaluation.perCriterionVerdicts().size() != 1) {
            return Optional.empty();
        }
        Map<?, ?> d = decisions.get(evaluation.perCriterionVerdicts().get(0).criterionId());
        if (ruleOf(d, "decisionRule").filter(r -> r == DecisionRule.REGRESSION_FISHER).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RegressionDisclosure(
                doubleOf(d.get("sizeAtAssumedCommonRate")),
                doubleOf(d.get("designAlternativeRate")),
                doubleOf(d.get("designPower")),
                doubleOf(d.get("resolvedTestPower")),
                doubleOf(d.get("minimumDetectableDegradation"))));
    }

    private static OptionalDouble doubleOf(Object value) {
        return value instanceof Number n ? OptionalDouble.of(n.doubleValue()) : OptionalDouble.empty();
    }

    private static OptionalLong longOf(Object value) {
        return value instanceof Number n ? OptionalLong.of(n.longValue()) : OptionalLong.empty();
    }

    private static OptionalInt intOf(Object value) {
        return value instanceof Number n ? OptionalInt.of(n.intValue()) : OptionalInt.empty();
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private static java.util.Optional<Double> scanDoubleDetail(
            List<EvaluatedCriterion> evaluated, String key) {
        for (EvaluatedCriterion ec : evaluated) {
            Object v = ec.result().detail().get(key);
            if (v instanceof Number n) {
                return java.util.Optional.of(n.doubleValue());
            }
        }
        return java.util.Optional.empty();
    }

    private static ThresholdOrigin scanThresholdOrigin(List<EvaluatedCriterion> evaluated) {
        for (EvaluatedCriterion ec : evaluated) {
            Object v = ec.result().detail().get("origin");
            if (v instanceof String s && !s.isBlank()) {
                try {
                    return ThresholdOrigin.valueOf(s);
                } catch (IllegalArgumentException ignored) {
                    // Detail map carried a non-enum value; treat as
                    // unspecified rather than throwing.
                    return null;
                }
            }
        }
        return null;
    }

    private static LatencyInput toLatencyInput(EngineRunSummary engine, boolean enforced) {
        // Only samples whose contract evaluated to Outcome.ok
        // contribute to the percentiles. The latency dimension at
        // the verdict layer is purely descriptive — declared
        // latency thresholds are gated at the criterion layer via
        // PercentileLatency, not via the dimension's data. Each
        // percentile is set to LatencySection.PERCENTILE_UNAVAILABLE_MS
        // (the "unavailable" sentinel) when contributingSamples is
        // below the minimum-samples threshold for that percentile.
        LatencyResult lat = engine.passingLatencyResult();
        if (lat.sampleCount() == 0 && !enforced) {
            return null;
        }
        int contributing = engine.successes();
        return new LatencyInput(
                contributing,                          // contributing (passing) samples
                engine.samplesExecuted(),              // total samples
                false,                                 // skipped
                null,                                  // skipReason
                msIfEmittable("p50", lat.p50(), contributing),
                msIfEmittable("p90", lat.p90(), contributing),
                msIfEmittable("p95", lat.p95(), contributing),
                msIfEmittable("p99", lat.p99(), contributing),
                LatencySection.PERCENTILE_UNAVAILABLE_MS, // maxMs unavailable
                List.of());                            // caveats
    }

    /**
     * Returns {@code duration.toMillis()} when the contributing-sample
     * count meets the minimum-samples threshold for the named
     * percentile; otherwise returns
     * {@link LatencySection#PERCENTILE_UNAVAILABLE_MS} to signal "not
     * computed reliably." Renderers and serialisers recognise this
     * sentinel and omit the percentile from their output.
     */
    private static long msIfEmittable(String label, java.time.Duration duration, int contributingSamples) {
        return LatencySection.isPercentileEmittable(label, contributingSamples)
                ? duration.toMillis()
                : LatencySection.PERCENTILE_UNAVAILABLE_MS;
    }

    private static List<MisalignmentInput> toMisalignmentInputs(CovariateAlignment alignment) {
        if (alignment.aligned() || alignment.mismatches().isEmpty()) {
            return List.of();
        }
        return alignment.mismatches().stream()
                .map(m -> new MisalignmentInput(
                        m.covariateKey(),
                        m.baseline() == null ? "" : m.baseline(),
                        m.observed() == null ? "" : m.observed()))
                .toList();
    }

    private static TerminationReason mapTerminationReason(
            org.mavai.punit.api.spec.TerminationReason source) {
        return switch (source) {
            case COMPLETED -> TerminationReason.COMPLETED;
            case TIME_BUDGET -> TerminationReason.METHOD_TIME_BUDGET_EXHAUSTED;
            case TOKEN_BUDGET -> TerminationReason.METHOD_TOKEN_BUDGET_EXHAUSTED;
            case IMPOSSIBILITY -> TerminationReason.IMPOSSIBILITY;
            case SUCCESS_GUARANTEED -> TerminationReason.SUCCESS_GUARANTEED;
            case CONFIGURATION_REFUSED -> TerminationReason.CONFIGURATION_REFUSED;
        };
    }
}
