package org.mavai.punit.internal.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.LatencyResult;
import org.mavai.punit.api.PercentileKey;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.api.ThresholdOrigin;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.criterion.Criteria;
import org.mavai.punit.api.criterion.LatencyCriterion;
import org.mavai.punit.api.spec.BaselineStatistics;
import org.mavai.punit.api.spec.LatencyStatistics;
import org.mavai.punit.api.spec.ProbabilisticTest;
import org.mavai.punit.api.spec.ProbabilisticTestResult;
import org.mavai.punit.api.spec.Verdict;
import org.mavai.punit.internal.engine.baseline.BaselineRecord;
import org.mavai.punit.internal.engine.baseline.BaselineWriter;
import org.mavai.punit.internal.engine.baseline.FactorsFingerprint;
import org.mavai.punit.internal.engine.baseline.LatencyIndicator;
import org.mavai.punit.internal.engine.baseline.YamlBaselineProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end pin for the contract-side latency authoring surface.
 * The contract declares its latency commitment via the sibling
 * {@link org.mavai.punit.api.Contract#latency()} method; the paired
 * probabilistic test invokes the contract with no explicit
 * {@code .criterion(...)}, and the framework auto-injects the
 * latency criterion alongside any functional criteria.
 */
@DisplayName("Contract-side latency end-to-end — Contract.latency() sibling method")
class ContractLatencyEndToEndIntegrationTest {

    record Factors(String model) { }

    private static final Factors FACTORS = new Factors("model-a");
    private static final String USE_CASE_ID = "contract-latency-pin";

    /** Contract declares functional + latency on separate sibling methods. */
    static class LatencyOnContract implements ServiceContract<Factors, Integer, Integer> {
        private final Criteria<Integer> criteria;
        private final LatencyCriterion latency;
        LatencyOnContract(Criteria<Integer> criteria, LatencyCriterion latency) {
            this.criteria = criteria;
            this.latency = latency;
        }
        @Override public String id() { return USE_CASE_ID; }
        @Override public Outcome<Integer> invoke(Integer input, TokenTracker tracker) {
            return Outcome.ok(input);
        }
        @Override public Criteria<Integer> criteria() { return criteria; }
        @Override public LatencyCriterion latency() { return latency; }
    }

    private static Sampling<Factors, Integer, Integer> sampling(
            Criteria<Integer> criteria, LatencyCriterion latency, int samples) {
        return Sampling.<Factors, Integer, Integer>builder()
                .serviceContractFactory(f -> new LatencyOnContract(criteria, latency))
                .inputs(1, 2, 3, 4, 5)
                .samples(samples)
                .build();
    }

    @Test
    @DisplayName("Contract.latency() empirical → auto-injected latency criterion resolves baseline and PASSes")
    void empiricalLatencyOnContractAutoInjects(@TempDir Path baselineDir) throws IOException {
        // Baseline asserts p95 = 1 minute — far above any plausible
        // microbench latency, so observed p95 will be below it.
        writeLatencyBaseline(baselineDir, Duration.ofMinutes(1), 1000);

        Criteria<Integer> criteria = Criteria.meeting().<Integer>zeroFailures();
        LatencyCriterion latency = Criteria.empirical().atMost(PercentileKey.P95);
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling(criteria, latency, 20), FACTORS)
                .build();   // no .criterion(...) — auto-injection from contract

        ProbabilisticTestResult result = (ProbabilisticTestResult)
                new Engine(new YamlBaselineProvider(baselineDir)).run(spec);

        var latencyEntry = result.criterionResults().stream()
                .filter(ec -> "percentile-latency".equals(ec.result().criterionName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "auto-injection failed: no percentile-latency entry on result"));
        assertThat(latencyEntry.result().verdict()).isEqualTo(Verdict.PASS);
        assertThat(latencyEntry.result().detail())
                .containsEntry("origin", "EMPIRICAL")
                .containsEntry("baselineSampleCount", 1000)
                .containsKey("observed.p95")
                .containsEntry("threshold.p95", 60_000L);
    }

    @Test
    @DisplayName("Contract.latency() contractual ceiling → contractual latency PASSes when observed below ceiling")
    void contractualLatencyOnContract(@TempDir Path baselineDir) throws IOException {
        Criteria<Integer> criteria = Criteria.meeting().<Integer>zeroFailures();
        LatencyCriterion latency = Criteria.meeting()
                .atMost(PercentileKey.P95, Duration.ofMillis(500))
                .contractRef(ThresholdOrigin.SLA, "test-contract-ref");
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling(criteria, latency, 20), FACTORS)
                .build();

        ProbabilisticTestResult result = (ProbabilisticTestResult)
                new Engine(new YamlBaselineProvider(baselineDir)).run(spec);

        var latencyEntry = result.criterionResults().stream()
                .filter(ec -> "percentile-latency".equals(ec.result().criterionName()))
                .findFirst()
                .orElseThrow();
        assertThat(latencyEntry.result().verdict()).isEqualTo(Verdict.PASS);
        assertThat(latencyEntry.result().detail())
                .containsEntry("origin", "SLA")
                .containsEntry("threshold.p95", 500L)
                .containsKey("observed.p95");
    }

    @Test
    @DisplayName("functional criteria() + latency() on one contract → both criteria auto-injected")
    void functionalAndLatencyCombine(@TempDir Path baselineDir) throws IOException {
        writeLatencyBaseline(baselineDir, Duration.ofMinutes(1), 1000);

        Criteria<Integer> criteria = Criteria.meeting().<Integer>passRate(0.99)
                .satisfies("value-not-negative",
                        v -> v >= 0 ? Outcome.ok() : Outcome.fail("neg", "v=" + v));
        LatencyCriterion latency = Criteria.empirical().atMost(PercentileKey.P95);

        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling(criteria, latency, 20), FACTORS)
                .build();

        ProbabilisticTestResult result = (ProbabilisticTestResult)
                new Engine(new YamlBaselineProvider(baselineDir)).run(spec);

        assertThat(result.criterionResults().stream()
                .anyMatch(ec -> "percentile-latency".equals(ec.result().criterionName())))
                .as("auto-injection must inject the latency criterion")
                .isTrue();
        assertThat(result.criterionResults().stream()
                .anyMatch(ec -> "bernoulli-pass-rate".equals(ec.result().criterionName())))
                .as("auto-injection must inject the pass-rate criterion alongside latency")
                .isTrue();
    }

    @Test
    @DisplayName("Contract.latency() default (none) → no latency criterion in effectiveCriteria")
    void defaultLatencyIsNone() {
        var contract = new ServiceContract<Factors, Integer, Integer>() {
            @Override public String id() { return USE_CASE_ID; }
            @Override public Outcome<Integer> invoke(Integer i, TokenTracker t) { return Outcome.ok(i); }
            @Override public Criteria<Integer> criteria() {
                return Criteria.empirical().<Integer>passRate()
                        .satisfies("v >= 0", v -> v >= 0 ? Outcome.ok() : Outcome.fail("neg", "v=" + v));
            }
        };

        assertThat(contract.latency().isPresent()).isFalse();
        assertThat(contract.effectiveCriteria()).hasSize(1);
        assertThat(contract.effectiveCriteria().get(0).posture().isLatency()).isFalse();
    }

    @Test
    @DisplayName("present LatencyCriterion is merged with fixed id 'latency'")
    void presentLatencyMergesWithFixedId() {
        var contract = new ServiceContract<Factors, Integer, Integer>() {
            @Override public String id() { return USE_CASE_ID; }
            @Override public Outcome<Integer> invoke(Integer i, TokenTracker t) { return Outcome.ok(i); }
            @Override public Criteria<Integer> criteria() {
                return Criteria.meeting().<Integer>zeroFailures();
            }
            @Override public LatencyCriterion latency() {
                return Criteria.empirical().atMost(PercentileKey.P95);
            }
        };

        var effective = contract.effectiveCriteria();
        assertThat(effective).hasSize(2);
        assertThat(effective.get(1).id()).isEqualTo(LatencyCriterion.ID);
        assertThat(effective.get(1).posture().isLatency()).isTrue();
    }

    private static void writeLatencyBaseline(
            Path baselineDir, Duration pX, int sampleCount) throws IOException {
        String fingerprint = FactorsFingerprint.of(FactorBundle.of(FACTORS));
        LatencyResult percentiles = new LatencyResult(pX, pX, pX, pX, sampleCount);
        long[] sorted = new long[sampleCount];
        java.util.Arrays.fill(sorted, pX.toMillis());
        LatencyIndicator indicator = new LatencyIndicator(percentiles, sorted, sampleCount, sampleCount);
        BaselineRecord record = new BaselineRecord(
                USE_CASE_ID, "latencyBaseline", fingerprint,
                sampling(Criteria.empty(), Criteria.empirical().atMost(PercentileKey.P95), 1)
                        .inputsIdentity(),
                sampleCount,
                Instant.parse("2026-05-18T12:00:00Z"),
                Map.<String, BaselineStatistics>of(
                        "percentile-latency",
                        LatencyStatistics.fromPercentiles(percentiles, sampleCount)),
                org.mavai.punit.api.covariate.CovariateProfile.empty(),
                indicator);
        new BaselineWriter().write(record, baselineDir);
    }
}
