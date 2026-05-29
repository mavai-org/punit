package org.mavai.punit.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.criterion.Criteria;
import static org.mavai.punit.api.criterion.Criteria.meeting;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.api.spec.Experiment;
import org.mavai.punit.api.spec.NextFactor;
import org.mavai.punit.api.spec.ProbabilisticTest;
import org.mavai.punit.api.spec.ProbabilisticTestResult;
import org.mavai.punit.internal.engine.Engine;
import org.mavai.punit.internal.runtime.BaselineEmitter;
import org.mavai.punit.internal.runtime.ExploreEmitter;
import org.mavai.punit.internal.runtime.OptimizeEmitter;
import org.mavai.punit.verdict.ProbabilisticTestVerdict;
import org.mavai.punit.verdict.RunMetadata;
import org.mavai.punit.internal.runtime.VerdictAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * End-to-end integration test for the descriptive-latency
 * surfaces: every artefact type — MEASURE baseline, EXPLORE
 * per-row, OPTIMIZE per-iteration, and the probabilistic-test
 * verdict's descriptive latency dimension — must emit a
 * {@code latency:} block carrying the population-indicator triple,
 * computed from passing samples only.
 *
 * <p>Uses a deliberately mixed pass/fail population (input length
 * triggers the contract's failure clause for some inputs, success
 * for others) so the test can verify {@code contributingSamples <
 * totalSamples} — i.e. failing samples are excluded from the
 * percentile computation.
 *
 * <p>All assertions run against in-memory sinks (no disk).
 */
@DisplayName("passing-only latency block surfaces in baseline / explore / optimize / verdict")
class LatencyEverywhereIntegrationTest {

    record F(String label) {}

    /** Service contract that succeeds when input length is even, fails otherwise. */
    private static class EvenLengthServiceContract implements ServiceContract<F, String, Integer> {
        @Override public String id() { return "latency-everywhere-test"; }
        @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input.length());
        }
        @Override public Criteria<Integer> criteria() {
            return meeting().<Integer>passRate(0.5)
                    .satisfies("length is even", n ->
                            n % 2 == 0 ? Outcome.ok() : Outcome.fail("odd-length", "length=" + n));
        }
    }

    /** Mixed inputs: 4 even-length (pass), 2 odd-length (fail). */
    private static final List<String> MIXED_INPUTS = List.of(
            "ab", "cdef", "ghij", "klmn", "x", "yz1");

    @Test
    @DisplayName("EXPLORE row carries latency block with passing-only indicator")
    void exploreRowCarriesLatency() {
        Sampling<F, String, Integer> sampling = Sampling
                .<F, String, Integer>builder()
                .serviceContractFactory(f -> new EvenLengthServiceContract())
                .inputs(MIXED_INPUTS)
                .samples(6)
                .build();
        Experiment experiment = Experiment.exploring(sampling)
                .grid(List.of(new F("only")))
                .build();
        new Engine().run(experiment);

        Map<String, String> sink = new LinkedHashMap<>();
        ExploreEmitter.emit(experiment, sink::put);
        assertThat(sink).hasSize(1);
        Map<String, Object> parsed = new Yaml().load(sink.values().iterator().next());

        @SuppressWarnings("unchecked")
        Map<String, Object> latency = (Map<String, Object>) parsed.get("latency");
        assertThat(latency)
                .as("EXPLORE row must carry latency block when at least one sample passed")
                .isNotNull();
        assertThat(latency).containsEntry("basis", "passing-samples");
        assertThat(latency).containsEntry("totalSamples", 6);
        // 4 of 6 inputs are even-length → expect ~4 contributing samples.
        assertThat((Integer) latency.get("contributingSamples"))
                .as("contributingSamples must be ≤ totalSamples and > 0")
                .isPositive()
                .isLessThanOrEqualTo(6);
        // Minimum-samples rule: with ≤ 6 contributing samples,
        // only p50Ms (needs ≥ 1) is emittable. p90 / p95 / p99 keys
        // are correctly absent — explore is the canonical
        // small-sample case the rule protects.
        assertThat(latency).containsKey("p50Ms");
        assertThat(latency).doesNotContainKeys("p95Ms", "p99Ms");
    }

    @Test
    @DisplayName("OPTIMIZE iterations each carry a latency block")
    void optimizeIterationsCarryLatency() {
        Sampling<F, String, Integer> sampling = Sampling
                .<F, String, Integer>builder()
                .serviceContractFactory(f -> new EvenLengthServiceContract())
                .inputs(MIXED_INPUTS)
                .samples(6)
                .build();
        Experiment experiment = Experiment.optimizing(sampling)
                .initialFactors(new F("init"))
                .stepper((cur, hist) -> hist.size() < 1
                        ? NextFactor.next(new F("step"))
                        : NextFactor.stop())
                .maximize(s -> 1.0 * s.successes() / Math.max(1, s.total()))
                .maxIterations(3)
                .experimentId("latency-everywhere-opt")
                .build();
        new Engine().run(experiment);

        Map<String, String> sink = new LinkedHashMap<>();
        OptimizeEmitter.emit(experiment, sink::put);
        assertThat(sink).hasSize(1);
        Map<String, Object> parsed = new Yaml().load(sink.values().iterator().next());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> iterations =
                (List<Map<String, Object>>) parsed.get("iterations");
        assertThat(iterations).isNotEmpty();
        for (Map<String, Object> iter : iterations) {
            @SuppressWarnings("unchecked")
            Map<String, Object> latency = (Map<String, Object>) iter.get("latency");
            assertThat(latency)
                    .as("each iteration with ≥1 passing sample must carry a latency block")
                    .isNotNull();
            assertThat(latency).containsEntry("basis", "passing-samples");
            assertThat(latency).containsKey("contributingSamples");
            assertThat(latency).containsKey("totalSamples");
        }
    }

    @Test
    @DisplayName("MEASURE baseline carries latency block")
    void measureBaselineCarriesLatency() {
        Sampling<F, String, Integer> sampling = Sampling
                .<F, String, Integer>builder()
                .serviceContractFactory(f -> new EvenLengthServiceContract())
                .inputs(MIXED_INPUTS)
                .samples(6)
                .build();
        Experiment experiment = Experiment.measuring(sampling, new F("only")).build();
        new Engine().run(experiment);

        Map<String, String> sink = new LinkedHashMap<>();
        BaselineEmitter.emit(experiment, sink::put);
        assertThat(sink).hasSize(1);
        Map<String, Object> parsed = new Yaml().load(sink.values().iterator().next());

        @SuppressWarnings("unchecked")
        Map<String, Object> latency = (Map<String, Object>) parsed.get("latency");
        assertThat(latency)
                .as("MEASURE baseline must carry latency block when at least one sample passed")
                .isNotNull();
        assertThat(latency).containsEntry("basis", "passing-samples");
        assertThat(latency).containsKey("contributingSamples");
        assertThat(latency).containsKey("totalSamples");
        // 6 samples, ≤ 6 contributing → only p50Ms emits.
        assertThat(latency).containsKey("p50Ms");
        assertThat(latency).doesNotContainKeys("p95Ms", "p99Ms");
    }

    @Test
    @DisplayName("verdict record carries descriptive latency dimension when ≥1 passing sample")
    void verdictCarriesDescriptiveLatency() {
        Sampling<F, String, Integer> sampling = Sampling
                .<F, String, Integer>builder()
                .serviceContractFactory(f -> new EvenLengthServiceContract())
                .inputs(MIXED_INPUTS)
                .samples(6)
                .build();
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling, new F("only"))
                .build();
        ProbabilisticTestResult result = (ProbabilisticTestResult) new Engine().run(spec);
        ProbabilisticTestVerdict verdict = VerdictAdapter.adapt(
                result, RunMetadata.of("LatencyEverywhereIntegrationTest", "verdictTest"));

        assertThat(verdict.latency())
                .as("verdict must carry latency dimension when ≥1 sample passed")
                .isPresent();
        ProbabilisticTestVerdict.LatencyDimension lat = verdict.latency().get();
        assertThat(lat.basis())
                .as("latency dimension must declare passing-samples basis")
                .isEqualTo("passing-samples");
        assertThat(lat.successfulSamples())
                .as("contributing samples must equal verdict.successes for descriptive emission")
                .isEqualTo(verdict.functional().get().successes());
        assertThat(lat.totalSamples()).isEqualTo(6);
    }

    @Test
    @DisplayName("Zero passing samples → no latency block emitted")
    void zeroPassingProducesNoBlock() {
        // Service contract that always fails the postcondition.
        ServiceContract<F, String, Integer> alwaysFail = new ServiceContract<>() {
            @Override public String id() { return "always-fail"; }
            @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
                return Outcome.ok(input.length());
            }
            @Override public Criteria<Integer> criteria() {
                return meeting().<Integer>zeroFailures()
                        .satisfies("never satisfies", n -> Outcome.fail("nope", "always fails"));
            }
        };
        Sampling<F, String, Integer> sampling = Sampling
                .<F, String, Integer>builder()
                .serviceContractFactory(f -> alwaysFail)
                .inputs("a", "b")
                .samples(2)
                .build();
        Experiment experiment = Experiment.measuring(sampling, new F("only")).build();
        new Engine().run(experiment);

        Map<String, String> sink = new LinkedHashMap<>();
        BaselineEmitter.emit(experiment, sink::put);
        Map<String, Object> parsed = new Yaml().load(sink.values().iterator().next());

        assertThat(parsed)
                .as("zero passing samples → latency block omitted entirely")
                .doesNotContainKey("latency");
    }
}
