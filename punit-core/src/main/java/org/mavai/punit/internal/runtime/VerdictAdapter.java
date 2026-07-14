package org.mavai.punit.internal.runtime;

import java.util.List;

import org.mavai.punit.api.ThresholdOrigin;
import org.mavai.punit.api.LatencyResult;
import org.mavai.punit.api.covariate.CovariateAlignment;
import org.mavai.punit.api.spec.EngineRunSummary;
import org.mavai.punit.api.spec.EvaluatedCriterion;
import org.mavai.punit.api.spec.ProbabilisticTestResult;
import org.mavai.punit.api.spec.Verdict;
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
        LatencyInput latencyInput = toLatencyInput(engine);
        if (latencyInput != null) {
            b.latencyDimension(latencyInput);
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
        b.perCriterion(translatePerCriterion(result.perCriterionEvaluation()));

        return b.build();
    }

    private static org.mavai.punit.verdict.PerCriterionStructure translatePerCriterion(
            org.mavai.punit.api.spec.PerCriterionEvaluation evaluation) {
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
                    v.threshold()));
        }
        return new org.mavai.punit.verdict.PerCriterionStructure(
                rows, evaluation.compositeVerdict());
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

    private static LatencyInput toLatencyInput(EngineRunSummary engine) {
        // Only samples whose contract evaluated to Outcome.ok
        // contribute to the percentiles. The latency dimension at
        // the verdict layer is purely descriptive — declared
        // latency thresholds are gated at the criterion layer via
        // PercentileLatency, not via the dimension's data. Each
        // percentile is set to LatencySection.PERCENTILE_UNAVAILABLE_MS
        // (the "unavailable" sentinel) when contributingSamples is
        // below the minimum-samples threshold for that percentile.
        LatencyResult lat = engine.passingLatencyResult();
        if (lat.sampleCount() == 0) {
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
        };
    }
}
