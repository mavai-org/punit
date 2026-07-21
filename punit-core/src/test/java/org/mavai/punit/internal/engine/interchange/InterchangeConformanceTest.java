package org.mavai.punit.internal.engine.interchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mavai.punit.api.criterion.Criteria.meeting;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.mavai.outcome.Outcome;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.criterion.Criteria;
import org.mavai.punit.api.spec.Experiment;
import org.mavai.punit.api.spec.NextFactor;
import org.mavai.punit.api.spec.Scorer;
import org.mavai.punit.internal.engine.Engine;
import org.mavai.punit.internal.engine.emit.LatencySection;
import org.mavai.punit.internal.runtime.ExploreEmitter;
import org.mavai.punit.internal.runtime.OptimizeEmitter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Emitter conformance against the mavai family's canonical
 * interchange schemas.
 *
 * <p>Small real EXPLORE and OPTIMIZE experiments are driven through
 * the engine and their emitted YAML artefacts are validated against
 * the vendored, pinned copies of the published JSON Schemas
 * ({@code mavai-explore-1}, {@code mavai-optimize-1}, pinned at the
 * mavai-R v0.9.0 publication). On top of the
 * structural validation, the tests assert the semantic obligations
 * the schemas cannot express: the sorted passing-latency vector is
 * ascending and sized to {@code contributingSamples}; each latency
 * percentile is stated exactly when its minimum-sample floor is
 * cleared; and the optimize convergence block is internally
 * consistent with the iteration it names.
 */
@DisplayName("Canonical interchange emitters — schema and semantic conformance")
class InterchangeConformanceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    record LlmFactors(String model, double temperature) {}

    /** Always-passing contract with a single named criterion. */
    private static final class PassingContract implements ServiceContract<LlmFactors, String, Integer> {
        @Override public String id() { return "interchange-passing"; }
        @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input.length());
        }
        @Override public Criteria<Integer> criteria() {
            return meeting().<Integer>zeroFailures();
        }
    }

    /** Alternates pass / fail deterministically, independent of input choice. */
    private static final class AlternatingContract implements ServiceContract<LlmFactors, String, Integer> {
        private final AtomicInteger invocations = new AtomicInteger();
        @Override public String id() { return "interchange-alternating"; }
        @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input.length());
        }
        @Override public Criteria<Integer> criteria() {
            return meeting().<Integer>zeroFailures()
                    .name("every-other")
                    .satisfies("alternates", v -> invocations.getAndIncrement() % 2 == 0
                            ? Outcome.ok()
                            : Outcome.fail("alternating-check", "odd invocation"));
        }
    }

    /** Never-passing contract — exercises the no-latency-block boundary. */
    private static final class FailingContract implements ServiceContract<LlmFactors, String, Integer> {
        @Override public String id() { return "interchange-failing"; }
        @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input.length());
        }
        @Override public Criteria<Integer> criteria() {
            return meeting().<Integer>zeroFailures()
                    .name("never-passes")
                    .satisfies("always-fails", v -> Outcome.fail("always-fails", "by design"));
        }
    }

    @Nested
    @DisplayName("exploration artefacts")
    class ExplorationArtefacts {

        @Test
        @DisplayName("all-passing run validates and states exactly the floor-cleared percentiles")
        void allPassingRunConforms() {
            Map<String, Object> doc = emitExplore(new PassingContract(), 6);

            assertThat(validate("mavai-explore-1", doc)).isEmpty();

            // 6 passing samples: the latency block is required and its
            // percentiles are gated by the minimum-sample floors.
            @SuppressWarnings("unchecked")
            Map<String, Object> latency = (Map<String, Object>) doc.get("latency");
            assertThat(latency).as("latency block must be present when samples passed").isNotNull();
            assertLatencySemantics(latency);
            assertThat(latency).containsKey("p50Ms");
            assertThat(latency).doesNotContainKeys("p95Ms", "p99Ms");
        }

        @Test
        @DisplayName("mixed run carries the failure distribution the schema requires")
        void mixedRunConforms() {
            Map<String, Object> doc = emitExplore(new AlternatingContract(), 6);

            assertThat(validate("mavai-explore-1", doc)).isEmpty();

            @SuppressWarnings("unchecked")
            Map<String, Object> statistics = (Map<String, Object>) doc.get("statistics");
            int failures = ((Number) statistics.get("failures")).intValue();
            assertThat(failures).isPositive();
            // Sequence-of-entries shape: {condition, count} entries whose
            // counts sum to the enclosing failures total, each condition
            // a bounded identity — never a free-text-keyed mapping.
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> failureDistribution =
                    (List<Map<String, Object>>) statistics.get("failureDistribution");
            assertThat(failureDistribution).isNotEmpty();
            int attributed = 0;
            for (Map<String, Object> entry : failureDistribution) {
                assertThat(entry).containsKeys("condition", "count");
                assertThat((String) entry.get("condition")).hasSizeLessThanOrEqualTo(256);
                attributed += ((Number) entry.get("count")).intValue();
            }
            assertThat(attributed)
                    .as("each failed trial attributed to its first failing condition")
                    .isEqualTo(failures);

            @SuppressWarnings("unchecked")
            Map<String, Object> latency = (Map<String, Object>) doc.get("latency");
            assertThat(latency).as("some samples passed, so latency is present").isNotNull();
            assertLatencySemantics(latency);
        }

        @Test
        @DisplayName("no-passing-samples run omits the latency block as a whole")
        void noPassingSamplesRunConforms() {
            Map<String, Object> doc = emitExplore(new FailingContract(), 4);

            assertThat(validate("mavai-explore-1", doc)).isEmpty();
            assertThat(doc).doesNotContainKey("latency");
        }

        private Map<String, Object> emitExplore(
                ServiceContract<LlmFactors, String, Integer> contract, int samples) {
            Sampling<LlmFactors, String, Integer> sampling = Sampling
                    .<LlmFactors, String, Integer>builder()
                    .serviceContractFactory(f -> contract)
                    .inputs("a", "bb")
                    .samples(samples)
                    .build();
            Experiment experiment = Experiment.exploring(sampling)
                    .grid(List.of(new LlmFactors("gpt-4o", 0.3)))
                    .build();
            new Engine().run(experiment);

            Map<String, String> sink = new LinkedHashMap<>();
            ExploreEmitter.emit(experiment, sink::put);
            assertThat(sink).hasSize(1);
            return new Yaml().load(sink.values().iterator().next());
        }
    }

    @Nested
    @DisplayName("optimize artefacts")
    class OptimizeArtefacts {

        @Test
        @DisplayName("run validates; iterations carry gated latency; convergence names a consistent optimum")
        void optimizeRunConforms() {
            Sampling<LlmFactors, String, Integer> sampling = Sampling
                    .<LlmFactors, String, Integer>builder()
                    .serviceContractFactory(f -> new PassingContract())
                    .inputs("a", "bb")
                    .samples(6)
                    .build();
            Experiment experiment = Experiment.optimizing(sampling)
                    .initialFactors(new LlmFactors("gpt-4o", 0.0))
                    .stepper((cur, hist) -> hist.size() < 2
                            ? NextFactor.next(new LlmFactors("gpt-4o", cur.temperature() + 0.3))
                            : NextFactor.stop())
                    .maximize(Scorer.observedPassRate())
                    .maxIterations(5)
                    .experimentId("interchange-opt-run")
                    .build();
            new Engine().run(experiment);

            Map<String, String> sink = new LinkedHashMap<>();
            OptimizeEmitter.emit(experiment, sink::put);
            assertThat(sink).hasSize(1);
            Map<String, Object> doc = new Yaml().load(sink.values().iterator().next());

            assertThat(validate("mavai-optimize-1", doc)).isEmpty();

            // The built-in scorer is named, so the additive scorer field
            // states its identity — and the document still validates.
            assertThat(doc).containsEntry("scorer", "observed-pass-rate");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> iterations = (List<Map<String, Object>>) doc.get("iterations");
            assertThat(iterations).isNotEmpty();
            for (Map<String, Object> iteration : iterations) {
                @SuppressWarnings("unchecked")
                Map<String, Object> statistics = (Map<String, Object>) iteration.get("statistics");
                int successes = ((Number) statistics.get("successes")).intValue();
                @SuppressWarnings("unchecked")
                Map<String, Object> latency = (Map<String, Object>) iteration.get("latency");
                if (successes > 0) {
                    assertThat(latency)
                            .as("iterations with passing samples must carry a latency block")
                            .isNotNull();
                    assertLatencySemantics(latency);
                } else {
                    assertThat(latency).isNull();
                }
            }

            // Convergence must be internally consistent with the
            // iteration it names as best.
            @SuppressWarnings("unchecked")
            Map<String, Object> convergence = (Map<String, Object>) doc.get("convergence");
            int bestIndex = ((Number) convergence.get("bestIteration")).intValue();
            Map<String, Object> best = iterations.get(bestIndex);
            assertThat(((Number) convergence.get("bestScore")).doubleValue())
                    .isEqualTo(((Number) best.get("score")).doubleValue());
            assertThat(convergence.get("bestFactors")).isEqualTo(best.get("factors"));
            assertThat(((Number) convergence.get("totalIterations")).intValue())
                    .isEqualTo(iterations.size());
        }
    }

    @Nested
    @DisplayName("artefact key discipline")
    class ArtefactKeyDiscipline {

        /** Driving input comfortably past YAML's 1,024-char implicit-key limit. */
        private static final String LONG_INPUT = "lorem ipsum dolor sit amet ".repeat(48);

        /** Condition identities past the 256-char emitted-key bound, sharing a prefix. */
        private static final String LONG_DESCRIPTION_A =
                "the response upholds every clause of the long-winded acceptance narrative "
                        + "x".repeat(300) + " variant-A";
        private static final String LONG_DESCRIPTION_B =
                "the response upholds every clause of the long-winded acceptance narrative "
                        + "x".repeat(300) + " variant-B";

        /**
         * Echoes its over-long input and alternates failures between
         * two conditions whose declared identities exceed the emitted-key
         * bound — exercising both the free-text-key exposure and the
         * prefix-plus-hash truncation's distinctness.
         */
        private final class LongIdentityContract implements ServiceContract<LlmFactors, String, String> {
            private final AtomicInteger invocations = new AtomicInteger();
            @Override public String id() { return "interchange-long-identity"; }
            @Override public Outcome<String> invoke(String input, TokenTracker tracker) {
                return Outcome.ok(input);
            }
            @Override public Criteria<String> criteria() {
                return meeting().<String>zeroFailures()
                        .name("long-identity")
                        .satisfies(LONG_DESCRIPTION_A, v -> invocations.get() % 2 == 0
                                ? Outcome.fail(LONG_DESCRIPTION_A, "even invocation")
                                : Outcome.ok())
                        .satisfies(LONG_DESCRIPTION_B, v -> invocations.getAndIncrement() % 2 == 0
                                ? Outcome.ok()
                                : Outcome.fail(LONG_DESCRIPTION_B, "odd invocation"));
            }
        }

        @Test
        @DisplayName("over-long input and condition identities emit a parseable, schema-valid artefact with bounded keys")
        void longInputAndIdentitiesStayWithinKeyBounds() {
            assertThat(LONG_INPUT.length()).isGreaterThan(1024);

            Sampling<LlmFactors, String, String> sampling = Sampling
                    .<LlmFactors, String, String>builder()
                    .serviceContractFactory(f -> new LongIdentityContract())
                    .inputs(LONG_INPUT)
                    .samples(4)
                    .build();
            Experiment experiment = Experiment.exploring(sampling)
                    .grid(List.of(new LlmFactors("gpt-4o", 0.3)))
                    .build();
            new Engine().run(experiment);

            Map<String, String> sink = new LinkedHashMap<>();
            ExploreEmitter.emit(experiment, sink::put);
            assertThat(sink).hasSize(1);
            String rawYaml = sink.values().iterator().next();

            // The whole document parses — no implicit key anywhere near
            // the 1,024-char limit that broke unbounded-key emission.
            Map<String, Object> doc = new Yaml().load(rawYaml);
            assertThat(validate("mavai-explore-1", doc)).isEmpty();

            // Every mapping key on every surface is bounded and never
            // derived from input content.
            List<String> keys = new ArrayList<>();
            collectKeys(doc, keys);
            for (String key : keys) {
                assertThat(key.length())
                        .as("emitted key must stay within the 256-char bound: %s", key)
                        .isLessThanOrEqualTo(256);
                assertThat(key).doesNotContain(LONG_INPUT.substring(0, 64));
            }

            // Failure attribution: bounded conditions, distinct after
            // truncation despite the shared prefix, counts summing to
            // the failures total.
            @SuppressWarnings("unchecked")
            Map<String, Object> statistics = (Map<String, Object>) doc.get("statistics");
            int failures = ((Number) statistics.get("failures")).intValue();
            assertThat(failures).isEqualTo(4);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> failureDistribution =
                    (List<Map<String, Object>>) statistics.get("failureDistribution");
            assertThat(failureDistribution).hasSize(2);
            List<String> conditions = new ArrayList<>();
            int attributed = 0;
            for (Map<String, Object> entry : failureDistribution) {
                String condition = (String) entry.get("condition");
                assertThat(condition).hasSize(256);
                conditions.add(condition);
                attributed += ((Number) entry.get("count")).intValue();
            }
            assertThat(attributed).isEqualTo(failures);
            assertThat(conditions.get(0)).isNotEqualTo(conditions.get(1));

            // The long input is welcome in values — the per-sample
            // content still carries it in full.
            assertThat(rawYaml).contains("lorem ipsum dolor sit amet");
        }

        /** Recursively collect every mapping key in the document. */
        private static void collectKeys(Object node, List<String> keys) {
            if (node instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    keys.add(String.valueOf(e.getKey()));
                    collectKeys(e.getValue(), keys);
                }
            } else if (node instanceof List<?> list) {
                for (Object item : list) {
                    collectKeys(item, keys);
                }
            }
        }
    }

    // ── Shared semantic assertions ───────────────────────────────────────────

    /**
     * The latency-block obligations the schema cannot express:
     * ascending vector, vector length equal to contributingSamples
     * and bounded by totalSamples, and each percentile stated exactly
     * when its minimum-sample floor is cleared.
     */
    private static void assertLatencySemantics(Map<String, Object> latency) {
        int contributing = ((Number) latency.get("contributingSamples")).intValue();
        int total = ((Number) latency.get("totalSamples")).intValue();
        @SuppressWarnings("unchecked")
        List<Number> sorted = (List<Number>) latency.get("sortedPassingLatenciesMs");

        assertThat(sorted).hasSize(contributing);
        assertThat(contributing).isLessThanOrEqualTo(total);
        List<Long> values = new ArrayList<>();
        for (Number n : sorted) {
            values.add(n.longValue());
        }
        assertThat(values).isSorted();

        for (String label : List.of("p50", "p90", "p95", "p99")) {
            boolean stated = latency.containsKey(label + "Ms");
            boolean floorCleared = contributing >= LatencySection.minimumSamplesFor(label);
            assertThat(stated)
                    .as("%sMs stated iff its floor (%d) is cleared by %d contributing samples",
                            label, LatencySection.minimumSamplesFor(label), contributing)
                    .isEqualTo(floorCleared);
        }
    }

    // ── Schema validation plumbing ───────────────────────────────────────────

    /**
     * Validates a parsed YAML document against a vendored interchange
     * schema; returns the list of violations (empty means conformant).
     */
    private static List<Error> validate(String schemaName, Map<String, Object> document) {
        Schema schema = loadSchema(schemaName);
        JsonNode node = MAPPER.valueToTree(document);
        return schema.validate(node);
    }

    private static Schema loadSchema(String schemaName) {
        String resource = "/conformance/interchange/" + schemaName + ".schema.json";
        InputStream in = InterchangeConformanceTest.class.getResourceAsStream(resource);
        assertThat(in).as("vendored schema %s must be on the test classpath", resource).isNotNull();
        return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(in);
    }
}
