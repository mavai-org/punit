package org.mavai.punit.statistics.conformance;

import static org.mavai.punit.api.criterion.Criteria.meeting;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.Contract;
import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.LatencyResult;
import org.mavai.punit.api.PercentileKey;
import org.mavai.punit.api.ServiceContractOutcome;
import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.criterion.Criteria;
import org.mavai.punit.api.spec.EvaluationContext;
import org.mavai.punit.api.spec.LatencyStatistics;
import org.mavai.punit.api.spec.SampleSummary;
import org.mavai.punit.api.spec.TerminationReason;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Scaffolding for driving the latency bootstrap fixture through the
 * production evaluation path ({@code PercentileLatency.evaluate}
 * reading a baseline {@code LatencyStatistics}).
 */
final class LatencyProductionPath {

    private LatencyProductionPath() { }

    static PercentileKey percentileKeyFor(double p) {
        if (p == 0.95) return PercentileKey.P95;
        if (p == 0.99) return PercentileKey.P99;
        throw new IllegalArgumentException(
                "fixture case asserts percentile " + p
                        + ", which has no PercentileKey on the production surface");
    }

    static LatencyStatistics buildBaseline(long[] sortedAscMs) {
        // sortedLatenciesMs is the only field the deriver reads;
        // percentile point estimates are reporting metadata. Fill them
        // honestly from the sorted vector so the baseline object is
        // internally consistent.
        LatencyResult percentiles = new LatencyResult(
                Duration.ofMillis(nearestRankMs(sortedAscMs, 0.50)),
                Duration.ofMillis(nearestRankMs(sortedAscMs, 0.90)),
                Duration.ofMillis(nearestRankMs(sortedAscMs, 0.95)),
                Duration.ofMillis(nearestRankMs(sortedAscMs, 0.99)),
                sortedAscMs.length);
        return new LatencyStatistics(percentiles, sortedAscMs, sortedAscMs.length);
    }

    private static long nearestRankMs(long[] sortedAsc, double p) {
        int index = (int) Math.ceil(p * sortedAsc.length) - 1;
        index = Math.max(0, Math.min(index, sortedAsc.length - 1));
        return sortedAsc[index];
    }

    static EvaluationContext<String, LatencyStatistics> evaluationContext(
            LatencyStatistics baseline, TestIntent intent) {
        int testSampleCount = Math.max(1, baseline.sampleCount() / 2);
        SampleSummary<String> summary = buildSummary(testSampleCount);
        String identity = "sha256:test-fixed-identity";
        return new EvaluationContext<>() {
            @Override public SampleSummary<String> summary() { return summary; }
            @Override public Optional<LatencyStatistics> baseline() {
                return Optional.of(baseline);
            }
            @Override public FactorBundle factors() {
                return FactorBundle.of(new Object());
            }
            @Override public String testInputsIdentity() { return identity; }
            @Override public Optional<String> baselineInputsIdentity() {
                return Optional.of(identity);
            }
            @Override public TestIntent intent() { return intent; }
        };
    }

    private static SampleSummary<String> buildSummary(int sampleCount) {
        // Synthetic observed latencies safely below every published
        // bootstrap-case threshold (smallest threshold across the four
        // cases is 419 ms); the check asserts on the deriver's threshold
        // value flowing through evaluate, not on the
        // observation-vs-threshold comparison.
        LatencyResult observed = new LatencyResult(
                Duration.ofMillis(1),
                Duration.ofMillis(1),
                Duration.ofMillis(1),
                Duration.ofMillis(1),
                sampleCount);
        var outcomes = new ArrayList<ServiceContractOutcome<?, String>>(sampleCount);
        for (int i = 0; i < sampleCount; i++) {
            outcomes.add(new ServiceContractOutcome<>(
                    Outcome.ok("ok"), STUB_CONTRACT, List.of(),
                    0L, Duration.ofMillis(1)));
        }
        return new SampleSummary<>(
                outcomes,
                Duration.ofMillis(1),
                sampleCount, 0, 0L, 0,
                observed,
                TerminationReason.COMPLETED,
                List.of(),
                Map.of(), LatencyResult.empty(), List.of());
    }

    private static final Contract<Object, String> STUB_CONTRACT = new Contract<>() {
        @Override public Outcome<String> invoke(Object input, TokenTracker tracker) {
            return Outcome.ok("ok");
        }
        @Override public Criteria<String> criteria() {
            return meeting().<String>zeroFailures();
        }
    };
}
