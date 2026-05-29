package org.mavai.punit.internal.engine.criteria;

import static org.mavai.punit.api.criterion.Criteria.meeting;
import org.mavai.punit.api.criterion.Criteria;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.ThresholdOrigin;
import org.mavai.punit.api.Contract;
import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.LatencyResult;
import org.mavai.punit.api.LatencySpec;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.ServiceContractOutcome;
import org.mavai.punit.api.spec.BaselineStatistics;
import org.mavai.punit.api.spec.CriterionResult;
import org.mavai.punit.api.spec.EvaluationContext;
import org.mavai.punit.api.spec.LatencyStatistics;
import org.mavai.punit.api.spec.PerCriterionPassRateStatistics;
import org.mavai.punit.api.PercentileKey;
import org.mavai.punit.api.spec.PercentileLatency;
import org.mavai.punit.api.spec.SampleSummary;
import org.mavai.punit.api.spec.TerminationReason;
import org.mavai.punit.api.spec.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the cross-criterion detail-key conventions. The keys feed
 * the verdict-XML {@code <detail>} block directly; if a key drifts,
 * the serialiser sees a different shape, so the conventions are
 * load-bearing.
 */
@DisplayName("CriterionResult.detail conventions")
class CriterionResultDetailTest {

    record Factors(String label) {}

    private static final Contract<Object, Integer> STUB_CONTRACT = new Contract<>() {
        @Override public Outcome<Integer> invoke(Object input, TokenTracker tracker) {
            return Outcome.ok(0);
        }
        @Override public Criteria<Integer> criteria() {
            return meeting().<Integer>zeroFailures();
        }

    };

    private static ServiceContractOutcome<Object, Integer> stubOutcome(int value) {
        return new ServiceContractOutcome<>(
                Outcome.ok(value), STUB_CONTRACT,
                List.of(),
                0L, Duration.ZERO);
    }

    private static SampleSummary<Integer> summaryWithLatency(LatencyResult latency) {
        return new SampleSummary<>(
                List.of(stubOutcome(1), stubOutcome(2)),
                Duration.ofMillis(10),
                2, 0, 0L, 0,
                latency,
                TerminationReason.COMPLETED,
                List.of(),
                java.util.Map.of(), LatencyResult.empty(), List.of());
    }

    private static final String DEFAULT_IDENTITY = "sha256:test-default-identity";

    private static <S extends BaselineStatistics> EvaluationContext<Integer, S> ctx(
            SampleSummary<Integer> s, Optional<S> baseline) {
        return new EvaluationContext<Integer, S>() {
            @Override public SampleSummary<Integer> summary() { return s; }
            @Override public Optional<S> baseline() { return baseline; }
            @Override public FactorBundle factors() { return FactorBundle.of(new Factors("m")); }
            @Override public String testInputsIdentity() { return DEFAULT_IDENTITY; }
            @Override public Optional<String> baselineInputsIdentity() {
                return baseline.isPresent() ? Optional.of(DEFAULT_IDENTITY) : Optional.empty();
            }
        };
    }

    private static LatencyResult observed(long p50, long p90, long p95, long p99, int n) {
        return new LatencyResult(
                Duration.ofMillis(p50), Duration.ofMillis(p90),
                Duration.ofMillis(p95), Duration.ofMillis(p99), n);
    }

    @Test
    @DisplayName("both criteria record the threshold origin under the key 'origin' as the enum's name()")
    void originKeyIsStableAcrossCriteria() {
        var bernoulli = PassRate.<Integer>meeting(ThresholdOrigin.SLA, 0.5)
                .evaluate(ctx(summaryWithLatency(LatencyResult.empty()), Optional.empty()));
        var percentile = PercentileLatency.<Integer>meeting(
                LatencySpec.builder().p95Millis(500).build(), ThresholdOrigin.SLO)
                .evaluate(ctx(summaryWithLatency(observed(10, 20, 30, 40, 2)), Optional.empty()));

        assertThat(bernoulli.detail()).containsKey("origin");
        assertThat(percentile.detail()).containsKey("origin");
        assertThat(bernoulli.detail().get("origin")).isEqualTo("SLA");
        assertThat(percentile.detail().get("origin")).isEqualTo("SLO");
    }

    @Test
    @DisplayName("both criteria record baselineSampleCount under that key when in an empirical mode")
    void baselineSampleCountKeyIsStable() {
        var passRate = PassRate.<Integer>empirical().evaluate(
                ctx(summaryWithLatency(LatencyResult.empty()),
                        Optional.of(PerCriterionPassRateStatistics.of("contract", 0.9, 1234))));
        var latency = PercentileLatency.<Integer>empirical(PercentileKey.P95).evaluate(
                ctx(summaryWithLatency(observed(10, 20, 30, 40, 2)),
                        Optional.of(LatencyStatistics.fromPercentiles(observed(10, 20, 50, 60, 1234), 1234))));

        assertThat(passRate.detail()).containsEntry("baselineSampleCount", 1234);
        assertThat(latency.detail()).containsEntry("baselineSampleCount", 1234);
    }

    @Test
    @DisplayName("PassRate's contractual detail carries observed/threshold/origin/successes/failures/total")
    void bernoulliContractualDetailKeys() {
        var result = PassRate.<Integer>meeting(ThresholdOrigin.SLA, 0.5)
                .evaluate(ctx(summaryWithLatency(LatencyResult.empty()), Optional.empty()));

        assertThat(result.detail()).containsKeys(
                "observed", "threshold", "origin", "successes", "failures", "total");
        // Contractual variants do not record the confidence — that knob is only meaningful
        // for the empirical Wilson-score-aware comparison Stage 4 will wire in.
        assertThat(result.detail()).doesNotContainKey("confidence");
    }

    @Test
    @DisplayName("PassRate's empirical detail also carries confidence and baselineSampleCount")
    void bernoulliEmpiricalDetailKeys() {
        var result = PassRate.<Integer>empirical().evaluate(
                ctx(summaryWithLatency(LatencyResult.empty()),
                        Optional.of(PerCriterionPassRateStatistics.of("contract", 0.9, 1234))));

        assertThat(result.detail()).containsKeys("confidence", "baselineSampleCount");
        assertThat(result.detail()).containsEntry("origin", "EMPIRICAL");
    }

    @Test
    @DisplayName("PercentileLatency uses dotted-suffix keys per asserted percentile (observed.pNN, threshold.pNN)")
    void percentileLatencyDottedKeys() {
        var result = PercentileLatency.<Integer>meeting(
                LatencySpec.builder().p95Millis(500).p99Millis(1000).build(),
                ThresholdOrigin.SLA)
                .evaluate(ctx(summaryWithLatency(observed(50, 100, 200, 300, 2)), Optional.empty()));

        assertThat(result.detail()).containsKeys(
                "observed.p95", "observed.p99", "threshold.p95", "threshold.p99");
        assertThat(result.detail()).containsEntry("threshold.p95", 500L);
        assertThat(result.detail()).containsEntry("threshold.p99", 1000L);
        assertThat(result.detail()).doesNotContainKey("breach.p95");
    }

    @Test
    @DisplayName("PercentileLatency adds breach.pNN entries on FAIL, omits them on PASS")
    void percentileLatencyBreachKeysAppearOnlyOnFail() {
        var pass = PercentileLatency.<Integer>meeting(
                LatencySpec.builder().p95Millis(500).build(), ThresholdOrigin.SLA)
                .evaluate(ctx(summaryWithLatency(observed(50, 100, 200, 300, 2)), Optional.empty()));
        var fail = PercentileLatency.<Integer>meeting(
                LatencySpec.builder().p95Millis(50).build(), ThresholdOrigin.SLA)
                .evaluate(ctx(summaryWithLatency(observed(50, 100, 200, 300, 2)), Optional.empty()));

        assertThat(pass.verdict()).isEqualTo(Verdict.PASS);
        assertThat(fail.verdict()).isEqualTo(Verdict.FAIL);
        assertThat(pass.detail()).doesNotContainKey("breach.p95");
        assertThat(fail.detail()).containsEntry("breach.p95", 200L);
    }

    @Test
    @DisplayName("PercentileLatency's assertedPercentiles is a comma-separated short-form list")
    void assertedPercentilesIsCsv() {
        var result = PercentileLatency.<Integer>meeting(
                LatencySpec.builder().p50Millis(100).p95Millis(500).p99Millis(1000).build(),
                ThresholdOrigin.SLA)
                .evaluate(ctx(summaryWithLatency(observed(50, 100, 200, 300, 2)), Optional.empty()));

        assertThat(result.detail()).containsEntry("assertedPercentiles", "p50,p95,p99");
    }

    @Test
    @DisplayName("CriterionResult.detail is immutable — modifying the original input does not change the stored map")
    void detailIsImmutable() {
        java.util.Map<String, Object> mutable = new java.util.HashMap<>();
        mutable.put("foo", "bar");
        CriterionResult r = new CriterionResult("x", Verdict.PASS, "ok", mutable);

        mutable.put("foo", "tampered");
        mutable.put("baz", "added");

        assertThat(r.detail()).containsEntry("foo", "bar");
        assertThat(r.detail()).doesNotContainKey("baz");
    }
}
