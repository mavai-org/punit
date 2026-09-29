package org.mavai.punit.statistics.conformance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.LatencyResult;
import org.mavai.punit.api.LatencySpec;
import org.mavai.punit.api.PercentileKey;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.ThresholdOrigin;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.covariate.Covariate;
import org.mavai.punit.api.covariate.CovariateProfile;
import org.mavai.punit.api.criterion.Criteria;
import org.mavai.punit.api.criterion.CriterionDecl;
import org.mavai.punit.api.criterion.Decl;
import org.mavai.punit.api.criterion.LatencyCriterion;
import org.mavai.punit.api.spec.BaselineProvider;
import org.mavai.punit.api.spec.BaselineStatistics;
import org.mavai.punit.api.spec.CriterionResult;
import org.mavai.punit.api.spec.EngineResult;
import org.mavai.punit.api.spec.LatencyStatistics;
import org.mavai.punit.api.spec.PassRateStatistics;
import org.mavai.punit.api.spec.PercentileLatency;
import org.mavai.punit.api.spec.PerCriterionPassRateStatistics;
import org.mavai.punit.api.spec.ProbabilisticTest;
import org.mavai.punit.api.spec.ProbabilisticTestResult;
import org.mavai.punit.api.spec.Spec;
import org.mavai.punit.api.spec.TypedSpec;
import org.mavai.punit.internal.engine.Engine;
import org.mavai.punit.internal.engine.baseline.BaselineRecord;
import org.mavai.punit.internal.engine.baseline.BaselineWriter;
import org.mavai.punit.internal.engine.baseline.FactorsFingerprint;
import org.mavai.punit.internal.engine.baseline.YamlBaselineProvider;
import org.mavai.punit.statistics.ConfigurationError;
import org.mavai.punit.statistics.Methodology;

import static org.mavai.punit.api.criterion.Criteria.empirical;
import static org.mavai.punit.api.criterion.Criteria.meeting;

/**
 * Drives fixture cases through punit's production paths — the code a
 * real probabilistic test runs from configuration to verdict.
 *
 * <p><b>Pass-rate criteria</b> run end to end: a baseline carrying the
 * case's counts is written to disk the way a measure run persists one, a
 * contract declaring the case's criteria at their alphas is sampled
 * against a scripted service delivering exactly the case's successes, and
 * the engine resolves the baseline, judges the configuration (refusing it
 * before any sample when any part is invalid), samples, and decides each
 * criterion by its rule. Nothing statistical is recomputed on the test
 * side. Early termination is disabled so the scripted schedule always
 * runs to its full count.
 *
 * <p><b>Latency constraints</b> are judged in two production steps,
 * because the engine measures wall-clock latencies a fixture cannot
 * script: the configuration by the spec's pre-run refusal hook (the
 * engine's), and the decision by the criterion's evaluation step on the
 * case's successful latencies.
 */
final class ProductionPath {

    private static final String USE_CASE_ID = "conformance-use-case";

    record Factors(String scenario) { }

    private static final Factors FACTORS = new Factors("oracle-scenario");

    private ProductionPath() { }

    // ── Pass-rate criteria ──────────────────────────────────────────

    /** One criterion over the scripted postcondition. */
    sealed interface Bar permits Requirement, Baseline { String id(); }

    /** A declared requirement, decided by {@code compliance/exact-binomial}. */
    record Requirement(String id, double threshold, double alpha) implements Bar { }

    /** A baseline-derived bar, decided by {@code regression/fisher}. */
    record Baseline(String id, int successes, int trials, double alpha) implements Bar { }

    /** Runs a test over the given criteria; the result is the engine's. */
    static ProbabilisticTestResult run(
            List<Bar> bars, int testSamples, int observedSuccesses, TestIntent intent) {
        Path baselineDir;
        try {
            baselineDir = Files.createTempDirectory("punit-conformance-baseline");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            writeBaseline(baselineDir, bars);
            List<Decl<String>> decls = new ArrayList<>();
            for (Bar bar : bars) {
                decls.add(decl(bar));
            }
            @SuppressWarnings("unchecked")
            Decl<String>[] array = decls.toArray(Decl[]::new);
            var contract = scriptedContract(observedSuccesses, Criteria.of(array),
                    org.mavai.punit.api.criterion.LatencyCriterion.none());
            ProbabilisticTest spec = ProbabilisticTest.testing(sampling(testSamples, contract), FACTORS)
                    .intent(intent)
                    .disableEarlyTermination()
                    .build();
            return (ProbabilisticTestResult) new Engine(new YamlBaselineProvider(baselineDir)).run(spec);
        } finally {
            deleteRecursively(baselineDir);
        }
    }

    private static Decl<String> decl(Bar bar) {
        CriterionDecl<String> decl = switch (bar) {
            case Requirement r -> meeting().<String>passRate(r.threshold())
                    .atConfidence(Methodology.confidenceFromAlpha(r.alpha()));
            case Baseline b -> empirical().<String>passRate()
                    .atConfidence(Methodology.confidenceFromAlpha(b.alpha()));
        };
        return decl.name(bar.id()).where("scripted response is ok", "ok"::equals);
    }

    /**
     * Persists the baseline entries of the regression bars exactly as a
     * measure run would: each criterion's observed rate and sample count,
     * with an inputs identity matching the paired test's sampling.
     */
    private static void writeBaseline(Path dir, List<Bar> bars) {
        Map<String, PassRateStatistics> entries = new LinkedHashMap<>();
        int trials = 0;
        for (Bar bar : bars) {
            if (bar instanceof Baseline b) {
                entries.put(b.id(), new PassRateStatistics((double) b.successes() / b.trials(), b.trials()));
                trials = Math.max(trials, b.trials());
            }
        }
        if (entries.isEmpty()) {
            return;
        }
        var probe = sampling(1, scriptedContract(1, meeting().<String>zeroFailures(), LatencyCriterion.none()));
        BaselineRecord record = new BaselineRecord(
                USE_CASE_ID, "measureBaseline",
                FactorsFingerprint.of(FactorBundle.of(FACTORS)),
                probe.inputsIdentity(), trials,
                Instant.parse("2026-09-28T00:00:00Z"),
                Map.<String, BaselineStatistics>of(
                        "bernoulli-pass-rate", new PerCriterionPassRateStatistics(entries)));
        try {
            new BaselineWriter().write(record, dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** One criterion's decision artefacts, as the pass-rate evaluation published them. */
    static Map<?, ?> decision(ProbabilisticTestResult result, String criterionId) {
        for (var ec : result.criterionResults()) {
            if (ec.result().detail().get("decisionsByCriterion") instanceof Map<?, ?> byCriterion
                    && byCriterion.get(criterionId) instanceof Map<?, ?> d) {
                return d;
            }
        }
        throw new AssertionError("no decision published for criterion " + criterionId
                + " — result: " + result.criterionResults());
    }

    /** The configuration errors, as the engine's refusal reports them. */
    static List<String> errors(ProbabilisticTestResult result) {
        return result.configurationErrors().stream().map(Enum::name).toList();
    }

    // ── Latency constraints ─────────────────────────────────────────

    /** An explicit ceiling at a percentile, demonstrated at alpha. */
    static PercentileLatency<String> explicit(double percentile, long thresholdMs, double alpha) {
        return PercentileLatency.meeting(spec(key(percentile), thresholdMs), ThresholdOrigin.SLA,
                Methodology.confidenceFromAlpha(alpha));
    }

    /** A baseline-derived threshold at a percentile, derived at alpha. */
    static PercentileLatency<String> baselineDerived(double percentile, double alpha) {
        return PercentileLatency.empirical(Methodology.confidenceFromAlpha(alpha), key(percentile));
    }

    /** A latency baseline: its successful latencies and the size of its run. */
    static LatencyStatistics latencyBaseline(double[] latencies, int runSamples) {
        long[] ms = Arrays.stream(latencies).mapToLong(Math::round).sorted().toArray();
        return new LatencyStatistics(LatencyResult.empty(), ms, ms.length, runSamples);
    }

    /**
     * The configuration errors the engine's pre-run hook reports for a
     * test planned at {@code plannedSamples} whose contract declares the
     * given latency constraint.
     */
    static List<String> latencyRefusal(
            LatencyCriterion latency, int plannedSamples, TestIntent intent,
            Optional<LatencyStatistics> baseline) {
        var contract = scriptedContract(0, meeting().<String>zeroFailures(), latency);
        ProbabilisticTest spec = ProbabilisticTest.testing(sampling(plannedSamples, contract), FACTORS)
                .intent(intent)
                .build();
        BaselineProvider provider = new InMemoryLatencyBaseline(baseline);
        Optional<EngineResult> refused = spec.dispatch(new Spec.Dispatcher<Optional<EngineResult>>() {
            @Override
            public <FT, IT, OT> Optional<EngineResult> apply(TypedSpec<FT, IT, OT> typed) {
                return typed.refusal(provider, plannedSamples);
            }
        });
        return refused
                .map(r -> ((ProbabilisticTestResult) r).configurationErrors().stream()
                        .map(ConfigurationError::name).toList())
                .orElse(List.of());
    }

    /** The contract-side declaration of an explicit ceiling. */
    static LatencyCriterion explicitDeclaration(double percentile, long thresholdMs, double alpha) {
        return meeting().atMost(key(percentile), Duration.ofMillis(thresholdMs))
                .atConfidence(Methodology.confidenceFromAlpha(alpha));
    }

    /** The contract-side declaration of a baseline-derived threshold. */
    static LatencyCriterion baselineDerivedDeclaration(double percentile, double alpha) {
        return empirical().atMost(key(percentile))
                .atConfidence(Methodology.confidenceFromAlpha(alpha));
    }

    /** Decides every constraint of a latency criterion on the given latencies. */
    static CriterionResult decide(
            PercentileLatency<String> criterion, double[] latencies,
            LatencyStatistics baseline, TestIntent intent) {
        return criterion.decide(latencies, baseline, intent);
    }

    static PercentileKey key(double percentile) {
        long percent = Math.round(percentile * 100);
        return switch ((int) percent) {
            case 50 -> PercentileKey.P50;
            case 90 -> PercentileKey.P90;
            case 95 -> PercentileKey.P95;
            case 99 -> PercentileKey.P99;
            default -> throw new IllegalArgumentException("unsupported percentile " + percentile);
        };
    }

    private static LatencySpec spec(PercentileKey key, long ms) {
        LatencySpec.Builder b = LatencySpec.builder();
        switch (key) {
            case P50 -> b.p50Millis(ms);
            case P90 -> b.p90Millis(ms);
            case P95 -> b.p95Millis(ms);
            case P99 -> b.p99Millis(ms);
        }
        return b.build();
    }

    // ── Scaffolding ─────────────────────────────────────────────────

    /**
     * A service delivering exactly {@code successes} passing responses
     * first, then failing ones — a postcondition failure, counted on both
     * the run-level and the per-criterion accounting.
     */
    private static ServiceContract<Factors, Integer, String> scriptedContract(
            int successes, Criteria<String> criteria, LatencyCriterion latency) {
        AtomicInteger invocation = new AtomicInteger();
        return new ServiceContract<>() {
            @Override public String id() { return USE_CASE_ID; }
            @Override public Criteria<String> criteria() { return criteria; }
            @Override public LatencyCriterion latency() { return latency; }
            @Override public Outcome<String> invoke(Integer input, TokenTracker tracker) {
                return invocation.incrementAndGet() <= successes
                        ? Outcome.ok("ok")
                        : Outcome.ok("scripted failing response");
            }
        };
    }

    private static Sampling<Factors, Integer, String> sampling(
            int samples, ServiceContract<Factors, Integer, String> contract) {
        return Sampling.<Factors, Integer, String>builder()
                .serviceContractFactory(f -> contract)
                .inputs(1, 2, 3)
                .samples(samples)
                .build();
    }

    /** A provider holding one latency baseline and nothing else. */
    private record InMemoryLatencyBaseline(Optional<LatencyStatistics> baseline) implements BaselineProvider {
        @Override
        @SuppressWarnings("unchecked")
        public <S extends BaselineStatistics> Optional<S> baselineFor(
                String serviceContractId, FactorBundle factors, String criterionName,
                Class<S> statisticsType, CovariateProfile currentProfile, List<Covariate> declarations) {
            return statisticsType == LatencyStatistics.class
                    ? (Optional<S>) baseline
                    : Optional.empty();
        }

        @Override
        public Optional<String> baselineInputsIdentityFor(
                String serviceContractId, FactorBundle factors,
                CovariateProfile currentProfile, List<Covariate> declarations) {
            return Optional.empty();
        }
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
