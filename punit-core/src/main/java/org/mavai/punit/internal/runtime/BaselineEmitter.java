package org.mavai.punit.internal.runtime;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.InputSupplier;
import org.mavai.punit.api.LatencyResult;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.api.covariate.Covariate;
import org.mavai.punit.api.covariate.CovariateProfile;
import org.mavai.punit.api.spec.BaselineStatistics;
import org.mavai.punit.api.spec.Configuration;
import org.mavai.punit.api.spec.CriterionSampleCounts;
import org.mavai.punit.api.spec.Experiment;
import org.mavai.punit.api.spec.LatencyStatistics;
import org.mavai.punit.api.spec.PassRateStatistics;
import org.mavai.punit.api.spec.PerCriterionPassRateStatistics;
import org.mavai.punit.api.spec.SampleSummary;
import org.mavai.punit.api.spec.Spec;
import org.mavai.punit.api.spec.TypedSpec;
import org.mavai.punit.api.criterion.CriterionPosture;
import org.mavai.punit.api.spec.MeasuredBaseline;
import org.mavai.punit.api.spec.NormativeJudgement;
import org.mavai.punit.internal.engine.baseline.BaselineRecord;
import org.mavai.punit.internal.engine.baseline.BaselineWriter;
import org.mavai.punit.internal.engine.baseline.FactorsFingerprint;
import org.mavai.punit.internal.engine.baseline.LatencyIndicator;
import org.mavai.punit.internal.engine.covariate.CovariateResolver;
import org.mavai.punit.internal.reporting.NormativeJudgementRenderer;
import org.mavai.punit.statistics.NormativeJudgementEvaluator;
import org.mavai.punit.statistics.StatisticalDefaults;

/**
 * Translates a completed MEASURE {@link Experiment} into a
 * {@link BaselineRecord} on disk, by way of the engine's
 * {@link BaselineWriter}.
 *
 * <p>Called from {@link PUnit.MeasureBuilder#run} after the engine
 * finishes its sampling loop. The record carries both
 * {@link PassRateStatistics} and {@link LatencyStatistics} so any
 * empirical criterion — today's kinds and future ones, additively —
 * can read what it needs from the same baseline file.
 *
 * <p>Identity fields:
 *
 * <ul>
 *   <li>{@code serviceContractId} — from
 *       {@code spec.serviceContractFactory().apply(factors).id()}</li>
 *   <li>{@code factorsFingerprint} —
 *       {@link FactorsFingerprint#of(FactorBundle)}</li>
 *   <li>{@code inputsIdentity} — recomputed by content hash via
 *       {@link InputSupplier}, matching what a paired test's
 *       {@link InputSupplier} produces from the same input list</li>
 *   <li>{@code methodName} — the experiment's
 *       {@code experimentId} (no JUnit method context is on hand
 *       in the static-helper path; the field is retained on disk
 *       for forward-compatible filename uniqueness but is not part
 *       of the resolver's lookup key)</li>
 *   <li>{@code generatedAt} — {@code Instant.now()}</li>
 * </ul>
 */
// javai-ref: JVI-EC8CPT3 — do not remove (resolves in javai-orchestrator)
public final class BaselineEmitter {

    private BaselineEmitter() { }

    /**
     * Emit the baseline artefact to {@code baselineDir} and return the
     * emission summary the measure terminals consume: artefact
     * identity, the run's normative judgements, and their console
     * rendering. The artefact is on disk before this method returns —
     * the terminals render and, under the gating terminal, assert
     * strictly after persistence.
     */
    public static MeasuredBaseline emit(Experiment experiment, Path baselineDir) {
        Objects.requireNonNull(baselineDir, "baselineDir");
        BaselineRecord record = emit(experiment, (relativePath, content) -> {
            try {
                Files.createDirectories(baselineDir);
                Path target = baselineDir.resolve(relativePath);
                Files.writeString(target, content, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(
                        "Failed to write baseline " + relativePath + " under " + baselineDir, e);
            }
        });
        return new MeasuredBaseline(
                record.serviceContractId(),
                record.sampleCount(),
                record.filename(),
                record.normativeJudgements(),
                NormativeJudgementRenderer.render(record));
    }

    /**
     * Test seam — emit the baseline artefact via a sink so tests can
     * scrutinise the YAML without touching disk. The sink receives a
     * single {@code (relativePath, yamlContent)} pair where
     * {@code relativePath} is the canonical baseline filename.
     *
     * @return the composed {@link BaselineRecord}, including the run's
     *         normative judgements — the caller (the measure terminal)
     *         renders and, under the gating terminal, asserts on them
     *         strictly after the artefact has been handed to the sink.
     */
    public static BaselineRecord emit(Experiment experiment, BiConsumer<String, String> sink) {
        Objects.requireNonNull(experiment, "experiment");
        Objects.requireNonNull(sink, "sink");
        if (experiment.kind() != Experiment.Kind.MEASURE) {
            // Only MEASURE produces a baseline. The only caller is
            // PUnit.MeasureBuilder.run(), which by construction passes a
            // MEASURE experiment — reaching this branch is a programming
            // error, not a runtime condition.
            throw new IllegalArgumentException(
                    "BaselineEmitter.emit only accepts MEASURE-flavour Experiments; got "
                            + experiment.kind()
                            + ". EXPLORE / OPTIMIZE produce their own artefacts and must not "
                            + "be routed through the baseline emitter.");
        }
        SampleSummary<?> summary = experiment.lastSummary().orElseThrow(() ->
                new IllegalStateException(
                        "MEASURE experiment has no recorded summary — the engine did not "
                                + "consume the spec before emission. This is a framework "
                                + "invariant violation."));
        if (summary.total() == 0) {
            throw new IllegalStateException(
                    "MEASURE experiment recorded zero samples — nothing to baseline. "
                            + "Check the spec's sample count and budget configuration.");
        }
        int expiresInDays = experiment.expiresInDays().orElse(0);
        BaselineRecord record = experiment.dispatch(new Spec.Dispatcher<>() {
            @Override
            public <FT, IT, OT> BaselineRecord apply(TypedSpec<FT, IT, OT> typed) {
                return composeRecord(typed, summary, experiment.experimentId(), expiresInDays);
            }
        });
        sink.accept(record.filename(), new BaselineWriter().toYaml(record));
        return record;
    }

    @SuppressWarnings("unchecked")
    private static <FT, IT, OT> BaselineRecord composeRecord(
            TypedSpec<FT, IT, OT> typed,
            SampleSummary<?> rawSummary,
            String experimentId,
            int expiresInDays) {
        Iterator<Configuration<FT, IT, OT>> configs = typed.configurations();
        if (!configs.hasNext()) {
            throw new IllegalStateException(
                    "MEASURE produced no configuration — typed spec is malformed");
        }
        Configuration<FT, IT, OT> cfg = configs.next();
        FT factors = cfg.factors();
        ServiceContract<FT, IT, OT> serviceContract = typed.serviceContractFactory().apply(factors);
        String serviceContractId = serviceContract.id();
        String factorsFingerprint = FactorsFingerprint.of(FactorBundle.of(factors));
        String inputsIdentity = InputSupplier.from(cfg::inputs).identity();

        SampleSummary<OT> summary = (SampleSummary<OT>) rawSummary;
        int total = summary.total();
        Map<String, BaselineStatistics> stats = new LinkedHashMap<>();
        stats.put("bernoulli-pass-rate", buildPerCriterionPassRate(summary));
        // LatencyStatistics retained on the in-memory record under
        // the criterion-name "percentile-latency" so the
        // PercentileLatency criterion's lookup path keeps working.
        // Sourced from passingLatencyResult: only samples whose
        // contract evaluated to Outcome.ok contribute. The
        // legacy YAML emission of this entry under
        // statistics.percentile-latency is retired; the data lives
        // canonically in the top-level latency: block, and the
        // reader synthesises this map entry from that block at load
        // time.
        LatencyResult passingForCriterion = summary.passingLatencyResult();
        long[] sortedPassingMs = sortedPassingLatenciesMs(summary);
        if (passingForCriterion.sampleCount() > 0) {
            stats.put("percentile-latency",
                    new LatencyStatistics(passingForCriterion, sortedPassingMs, summary.successes()));
        }

        // The resolved covariate profile is part of the baseline's
        // identity. The service contract's declarations + custom resolvers
        // feed the resolver; the resulting profile is stamped into
        // the BaselineRecord and surfaces as a covariate-hash tail
        // in the filename, plus a covariates: block in the YAML body.
        List<Covariate> declarations = serviceContract.covariates();
        CovariateProfile profile = declarations.isEmpty()
                ? CovariateProfile.empty()
                : CovariateResolver.defaults()
                        .resolve(declarations, serviceContract.customCovariateResolvers());

        // Descriptive latency: passing-only percentiles plus the
        // population indicator. Empty when no samples passed;
        // BaselineWriter omits the block entirely in that case.
        LatencyResult passing = summary.passingLatencyResult();
        LatencyIndicator latencyIndicator = passing.sampleCount() == 0
                ? LatencyIndicator.empty()
                : new LatencyIndicator(passing, sortedPassingMs, summary.successes(), total);

        return new BaselineRecord(
                serviceContractId,
                experimentId,
                factorsFingerprint,
                inputsIdentity,
                total,
                Instant.now(),
                stats,
                profile,
                latencyIndicator,
                expiresInDays,
                judgeNormativeCriteria(serviceContract, summary));
    }

    /**
     * Normative judgement at experiment time: for each normative
     * criterion the contract declares (the {@code meeting(...)}
     * pass-rate posture — a stipulated minimum acceptable rate whose
     * validity does not depend on any baseline), judge the run's own
     * evidence against the stipulated threshold at the criterion's
     * confidence. The judgement machinery — Wilson lower bound at the
     * run's sample count plus the supportability gate — lives in
     * {@link NormativeJudgementEvaluator}; this method only routes
     * counts and posture metadata into it.
     *
     * <p>Empirical criteria are never judged at experiment time: their
     * bar does not exist until a baseline supplies it. Zero-failures
     * and latency postures are likewise out of scope — the judgement
     * is defined over the stipulated pass-rate form only.
     */
    private static <FT, IT, OT> List<NormativeJudgement> judgeNormativeCriteria(
            ServiceContract<FT, IT, OT> serviceContract, SampleSummary<?> summary) {
        Map<String, CriterionSampleCounts> countsByCriterionId = new LinkedHashMap<>();
        for (CriterionSampleCounts counts : summary.criterionSampleCounts()) {
            countsByCriterionId.put(counts.criterionId(), counts);
        }
        List<NormativeJudgement> judgements = new ArrayList<>();
        for (org.mavai.punit.api.criterion.Criterion<OT> criterion
                : serviceContract.effectiveCriteria()) {
            CriterionPosture posture = criterion.posture();
            if (posture.kind() != CriterionPosture.Kind.STATISTICAL_CONTRACTUAL
                    || posture.threshold().isEmpty()) {
                continue;
            }
            CriterionSampleCounts counts = countsByCriterionId.get(criterion.id());
            if (counts == null || counts.total() == 0) {
                // No per-criterion evidence recorded for this criterion —
                // nothing to judge. Unreachable in practice: the zero-sample
                // guard above rejects empty runs before composition.
                continue;
            }
            double confidence = posture.confidenceFloor()
                    .orElse(StatisticalDefaults.DEFAULT_CONFIDENCE);
            NormativeJudgementEvaluator.Judgement judgement =
                    NormativeJudgementEvaluator.judge(
                            counts.pass(),
                            counts.total(),
                            posture.threshold().getAsDouble(),
                            confidence);
            judgements.add(new NormativeJudgement(
                    criterion.id(), judgement, contractRefFor(posture)));
        }
        return judgements;
    }

    private static Optional<String> contractRefFor(CriterionPosture posture) {
        Optional<String> ref = posture.contractRef();
        Optional<String> originName = posture.origin().map(Enum::name);
        if (ref.isPresent() && originName.isPresent()
                && !"UNSPECIFIED".equals(originName.get())) {
            return Optional.of(originName.get() + ", " + ref.get());
        }
        return ref.isPresent() ? ref : Optional.empty();
    }

    /**
     * Build the per-methodology-criterion pass-rate basis from the
     * engine's recorded {@code criterionSampleCounts}. One entry per
     * criterion the contract declared, in contract declaration order.
     * For K=1 (the common case) this yields a single-entry map keyed
     * by the lone criterion's id.
     */
    private static PerCriterionPassRateStatistics buildPerCriterionPassRate(
            SampleSummary<?> summary) {
        Map<String, PassRateStatistics> byCriterion = new LinkedHashMap<>();
        for (CriterionSampleCounts c : summary.criterionSampleCounts()) {
            int criterionTotal = c.pass() + c.fail();
            double observed = criterionTotal == 0
                    ? 0.0
                    : (double) c.pass() / (double) criterionTotal;
            byCriterion.put(c.criterionId(),
                    new PassRateStatistics(observed, criterionTotal));
        }
        return new PerCriterionPassRateStatistics(byCriterion);
    }

    /**
     * Pull passing-sample latencies from the trial stream, in
     * milliseconds, sorted ascending. Underpins the
     * {@link LatencyStatistics#sortedLatenciesMs()} field consumed by
     * {@code LatencyThresholdDeriver} per Statistical Companion §12.4.2.
     */
    private static long[] sortedPassingLatenciesMs(SampleSummary<?> summary) {
        long[] arr = summary.trials().stream()
                .filter(t -> t.outcome().value() instanceof org.mavai.outcome.Outcome.Ok)
                .mapToLong(t -> t.duration().toMillis())
                .toArray();
        java.util.Arrays.sort(arr);
        return arr;
    }
}
