package org.javai.punit.internal.engine.optimize;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.javai.outcome.Outcome;
import org.javai.punit.api.Sampling;
import org.javai.punit.api.TokenTracker;
import org.javai.punit.api.ServiceContract;
import org.javai.punit.api.criterion.Criteria;
import static org.javai.punit.api.criterion.Criteria.meeting;
import org.javai.punit.api.spec.Experiment;
import org.javai.punit.api.spec.NextFactor;
import org.javai.punit.internal.engine.Engine;
import org.javai.punit.internal.runtime.OptimizeEmitter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Pure / in-memory tests for the optimize output writer.
 *
 * <p>The writer is exercised through {@link OptimizeEmitter}'s
 * in-memory sink overload — neither test touches disk.
 */
@DisplayName("OptimizeOutputWriter — schema, end-to-end via in-memory sink")
class OptimizeOutputWriterTest {

    record LlmFactors(String model, double temperature) {}

    private static class IdServiceContract implements ServiceContract<LlmFactors, String, Integer> {
        private final String id;
        IdServiceContract(String id) { this.id = id; }
        @Override public String id() { return id; }
        @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input.length());
        }
        @Override public Criteria<Integer> criteria() {
            return meeting().<Integer>zeroFailures();
        }

    }

    @Test
    @DisplayName("OptimizeEmitter writes one YAML carrying iterations + convergence to the sink")
    void emitterCapturesOneFilePerRun() {
        Sampling<LlmFactors, String, Integer> sampling = Sampling
                .<LlmFactors, String, Integer>builder()
                .serviceContractFactory(f -> new IdServiceContract("optimize-test"))
                .inputs("a", "bb")
                .samples(2)
                .build();
        Experiment experiment = Experiment.optimizing(sampling)
                .initialFactors(new LlmFactors("gpt-4o", 0.0))
                .stepper((cur, hist) -> hist.size() < 2
                        ? NextFactor.next(new LlmFactors("gpt-4o", cur.temperature() + 0.3))
                        : NextFactor.stop())
                .maximize(s -> 1.0 * s.successes() / Math.max(1, s.total()))
                .maxIterations(5)
                .experimentId("opt-run-1")
                .build();
        new Engine().run(experiment);

        Map<String, String> sink = new LinkedHashMap<>();
        BiConsumer<String, String> capture = sink::put;
        OptimizeEmitter.emit(experiment, capture);

        assertThat(sink).hasSize(1);
        assertThat(sink).containsKey("optimize-test/opt-run-1.yaml");

        String yaml = sink.values().iterator().next();
        Map<String, Object> parsed = new Yaml().load(yaml);
        assertThat(parsed).containsEntry("schemaVersion", "punit-spec-1");
        assertThat(parsed).containsEntry("useCaseId", "optimize-test");
        assertThat(parsed).containsEntry("experimentId", "opt-run-1");
        assertThat(parsed).containsEntry("objective", "MAXIMIZE");
        assertThat(parsed).containsKeys("iterations", "convergence", "generatedAt");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> iterations = (List<Map<String, Object>>) parsed.get("iterations");
        assertThat(iterations).isNotEmpty();
        Map<String, Object> first = iterations.get(0);
        assertThat(first).containsKeys("iteration", "factors", "score",
                "execution", "statistics", "cost", "resultProjection");

        // Per-iteration block layout mirrors EXPLORE's per-cell shape:
        // execution.samplesExecuted, statistics.{observed, successes,
        // failures, failureDistribution}, cost.{totalTimeMs, avgTimePerSampleMs}.
        @SuppressWarnings("unchecked")
        Map<String, Object> execution = (Map<String, Object>) first.get("execution");
        assertThat(execution).containsKeys("samplesExecuted", "terminationReason");
        @SuppressWarnings("unchecked")
        Map<String, Object> statistics = (Map<String, Object>) first.get("statistics");
        assertThat(statistics).containsKeys("observed", "successes", "failures",
                "failureDistribution");
        @SuppressWarnings("unchecked")
        Map<String, Object> cost = (Map<String, Object>) first.get("cost");
        assertThat(cost).containsKeys("totalTimeMs", "avgTimePerSampleMs");

        // Each iteration's resultProjection must carry one sample[N] entry per trial.
        @SuppressWarnings("unchecked")
        Map<String, Object> projection = (Map<String, Object>) first.get("resultProjection");
        assertThat(projection).containsKeys("sample[0]", "sample[1]");
        @SuppressWarnings("unchecked")
        Map<String, Object> sample0 = (Map<String, Object>) projection.get("sample[0]");
        assertThat(sample0).containsKeys("inputIndex", "postconditions", "executionTimeMs", "content");
        assertThat(sample0).doesNotContainKey("input");

        @SuppressWarnings("unchecked")
        Map<String, Object> convergence = (Map<String, Object>) parsed.get("convergence");
        assertThat(convergence).containsKeys("totalIterations", "bestIteration",
                "bestScore", "bestFactors", "terminationReason");

        // Diff anchor comments must appear before every sample[N]: line.
        // History size × samples-per-iteration = total anchor count.
        int totalSamples = iterations.stream()
                .mapToInt(it -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> ex = (Map<String, Object>) it.get("execution");
                    return ((Number) ex.get("samplesExecuted")).intValue();
                })
                .sum();
        long anchorCount = yaml.lines()
                .filter(line -> line.contains("anchor:"))
                .count();
        assertThat(anchorCount).isEqualTo((long) totalSamples);
    }

    @Test
    @DisplayName("K>1 contract: each iteration carries a per-criterion criteria: block")
    void perIterationCriteriaBlockForMultiCriterionContract() {
        // Contract declaring two methodology criteria. The "always-passes"
        // criterion holds across every sample; "starts-with-a" fails for
        // inputs that don't start with 'a'. Three inputs ("a", "bb", "ccc")
        // give the second criterion a deterministic 1/3 pass rate.
        ServiceContract<LlmFactors, String, String> contract = new ServiceContract<>() {
            @Override public String id() { return "two-criterion-contract"; }
            @Override public Criteria<String> criteria() {
                return Criteria.of(
                        meeting().<String>zeroFailures()
                                .name("always-passes")
                                .satisfies("passes", v -> Outcome.ok()),
                        meeting().<String>zeroFailures()
                                .name("starts-with-a")
                                .satisfies("first-char-a", v ->
                                        v.startsWith("a")
                                                ? Outcome.ok()
                                                : Outcome.fail("no-a", "input " + v)));
            }
            @Override public Outcome<String> invoke(String input, TokenTracker tracker) {
                return Outcome.ok(input);
            }
        };
        Sampling<LlmFactors, String, String> sampling = Sampling
                .<LlmFactors, String, String>builder()
                .serviceContractFactory(f -> contract)
                .inputs("a", "bb", "ccc")
                .samples(3)
                .build();
        Experiment experiment = Experiment.optimizing(sampling)
                .initialFactors(new LlmFactors("gpt-4o", 0.0))
                .stepper((cur, hist) -> hist.size() < 1
                        ? NextFactor.next(new LlmFactors("gpt-4o", cur.temperature() + 0.1))
                        : NextFactor.stop())
                .maximize(s -> 1.0 * s.successes() / Math.max(1, s.total()))
                .maxIterations(2)
                .experimentId("two-criterion-opt")
                .build();
        new Engine().run(experiment);

        Map<String, String> sink = new LinkedHashMap<>();
        OptimizeEmitter.emit(experiment, sink::put);
        Map<String, Object> parsed = new Yaml().load(sink.values().iterator().next());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> iterations = (List<Map<String, Object>>) parsed.get("iterations");
        assertThat(iterations).isNotEmpty();
        for (Map<String, Object> iter : iterations) {
            @SuppressWarnings("unchecked")
            Map<String, Object> stats = (Map<String, Object>) iter.get("statistics");
            assertThat(stats).as("iteration statistics block should carry criteria: for K>1 contract")
                    .containsKey("criteria");
            @SuppressWarnings("unchecked")
            Map<String, Object> criteria = (Map<String, Object>) stats.get("criteria");
            assertThat(criteria).containsOnlyKeys("always-passes", "starts-with-a");
            @SuppressWarnings("unchecked")
            Map<String, Object> alwaysPasses = (Map<String, Object>) criteria.get("always-passes");
            assertThat(alwaysPasses).containsEntry("pass", 3).containsEntry("fail", 0);
            assertThat((double) alwaysPasses.get("observedPassRate")).isEqualTo(1.0);
            @SuppressWarnings("unchecked")
            Map<String, Object> startsWithA = (Map<String, Object>) criteria.get("starts-with-a");
            assertThat(startsWithA).containsEntry("pass", 1).containsEntry("fail", 2);
        }
    }

    @Test
    @DisplayName("OptimizeEmitter rejects non-OPTIMIZE experiments")
    void emitterRejectsWrongKind() {
        Sampling<LlmFactors, String, Integer> sampling = Sampling
                .<LlmFactors, String, Integer>builder()
                .serviceContractFactory(f -> new IdServiceContract("x"))
                .inputs("a")
                .samples(1)
                .build();
        Experiment measure = Experiment.measuring(sampling, new LlmFactors("gpt-4o", 0.3)).build();

        try {
            OptimizeEmitter.emit(measure, new HashMap<String, String>()::put);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("OPTIMIZE");
            return;
        }
        throw new AssertionError("expected IllegalArgumentException for non-OPTIMIZE experiment");
    }
}
