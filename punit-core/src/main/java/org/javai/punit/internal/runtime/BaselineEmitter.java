package org.javai.punit.internal.runtime;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.javai.punit.api.FactorBundle;
import org.javai.punit.api.InputSupplier;
import org.javai.punit.api.LatencyResult;
import org.javai.punit.api.ServiceContract;
import org.javai.punit.api.covariate.Covariate;
import org.javai.punit.api.covariate.CovariateProfile;
import org.javai.punit.api.spec.BaselineStatistics;
import org.javai.punit.api.spec.Configuration;
import org.javai.punit.api.spec.CriterionSampleCounts;
import org.javai.punit.api.spec.Experiment;
import org.javai.punit.api.spec.LatencyStatistics;
import org.javai.punit.api.spec.PassRateStatistics;
import org.javai.punit.api.spec.PerCriterionPassRateStatistics;
import org.javai.punit.api.spec.SampleSummary;
import org.javai.punit.api.spec.Spec;
import org.javai.punit.api.spec.TypedSpec;
import org.javai.punit.internal.engine.baseline.BaselineRecord;
import org.javai.punit.internal.engine.baseline.BaselineWriter;
import org.javai.punit.internal.engine.baseline.FactorsFingerprint;
import org.javai.punit.internal.engine.baseline.LatencyIndicator;
import org.javai.punit.internal.engine.covariate.CovariateResolver;

/**
 * Translates a completed MEASURE {@link Experiment} into a
 * {@link BaselineRecord} on disk, by way of the engine's
 * {@link BaselineWriter}.
 *
 * <p>Called from {@link PUnit.MeasureBuilder#run} after the engine
 * finishes its sampling loop. The record carries both
 * {@link PassRateStatistics} and {@link LatencyStatistics} so any
 * future empirical criterion (CR02 or CR03 today, future kinds
 * additively) can read what it needs from the same baseline file.
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
public final class BaselineEmitter {

    private BaselineEmitter() { }

    public static void emit(Experiment experiment, Path baselineDir) {
        Objects.requireNonNull(baselineDir, "baselineDir");
        emit(experiment, (relativePath, content) -> {
            try {
                Files.createDirectories(baselineDir);
                Path target = baselineDir.resolve(relativePath);
                Files.writeString(target, content, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(
                        "Failed to write baseline " + relativePath + " under " + baselineDir, e);
            }
        });
    }

    /**
     * Test seam — emit the baseline artefact via a sink so tests can
     * scrutinise the YAML without touching disk. The sink receives a
     * single {@code (relativePath, yamlContent)} pair where
     * {@code relativePath} is the canonical baseline filename.
     */
    public static void emit(Experiment experiment, BiConsumer<String, String> sink) {
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
                expiresInDays);
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
            int criterionTotal = c.pass() + c.fail() + c.inconclusive();
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
                .filter(t -> t.outcome().value() instanceof org.javai.outcome.Outcome.Ok)
                .mapToLong(t -> t.duration().toMillis())
                .toArray();
        java.util.Arrays.sort(arr);
        return arr;
    }
}
