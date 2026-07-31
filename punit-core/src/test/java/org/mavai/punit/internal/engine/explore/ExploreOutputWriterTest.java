package org.mavai.punit.internal.engine.explore;

import static org.mavai.punit.api.criterion.Criteria.meeting;
import org.mavai.punit.api.criterion.Criteria;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.api.spec.Experiment;
import org.mavai.punit.api.spec.PerConfigSummary;
import org.mavai.punit.internal.engine.Engine;
import org.mavai.punit.internal.runtime.ExploreEmitter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Pure / in-memory tests for the explore output writer.
 *
 * <p>The writer is exercised both directly ({@link #writeYamlAndFilenameForOneConfig})
 * and through {@link ExploreEmitter}'s in-memory sink overload
 * ({@link #emitterCapturesOnePerConfig}) — neither test touches
 * disk.
 */
@DisplayName("ExploreOutputWriter — schema, filename, end-to-end via in-memory sink")
class ExploreOutputWriterTest {

    record LlmFactors(String model, double temperature) {}

    /** Always-passing service contract. Output type is the input length, for trivial sampling. */
    private static class LengthServiceContract implements ServiceContract<LlmFactors, String, Integer> {
        @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input.length());
        }
        @Override public Criteria<Integer> criteria() {
            return meeting().<Integer>zeroFailures();
        }

    }

    @Test
    @DisplayName("only the declared base configuration states the base marker")
    void baseConfigurationIsStatedOnlyWhereDeclared() {
        // The sweep's base is a fact the author holds and the artefact
        // must carry: a balanced grid gives a consumer nothing to infer
        // it from, every point holding each value equally often.
        LlmFactors base = new LlmFactors("gpt-4o", 0.3);
        LlmFactors other = new LlmFactors("gpt-4o-mini", 0.7);
        Sampling<LlmFactors, String, Integer> sampling = Sampling
                .<LlmFactors, String, Integer>builder()
                .serviceContractFactory(f -> new LengthServiceContract())
                .inputs("a")
                .samples(1)
                .build();
        Experiment experiment = Experiment.exploring(sampling)
                .grid(List.of(base, other))
                .baseConfiguration(base)
                .build();
        new Engine().run(experiment);

        ExploreOutputWriter writer = new ExploreOutputWriter();
        PerConfigSummary<?, ?> baseEntry = experiment.perConfigSummaries().stream()
                .filter(e -> e.factors().equals(base)).findFirst().orElseThrow();
        PerConfigSummary<?, ?> otherEntry = experiment.perConfigSummaries().stream()
                .filter(e -> e.factors().equals(other)).findFirst().orElseThrow();

        Map<String, Object> marked = new Yaml().load(writer.writeYaml(
                "LengthServiceContract", FactorBundle.of(baseEntry.factors()), baseEntry,
                List.of(), true));
        assertThat(marked).containsEntry("baseConfiguration", Boolean.TRUE);

        // Absence, never a stated false: a consumer must not read silence
        // as "not the base".
        String unmarked = writer.writeYaml(
                "LengthServiceContract", FactorBundle.of(otherEntry.factors()), otherEntry,
                List.of(), false);
        assertThat(unmarked).doesNotContain("baseConfiguration");
        assertThat(new Yaml().load(unmarked).toString()).doesNotContain("baseConfiguration");
    }

    @Test
    @DisplayName("a base configuration outside the grid is a misuse defect")
    void baseConfigurationMustBeAGridElement() {
        Sampling<LlmFactors, String, Integer> sampling = Sampling
                .<LlmFactors, String, Integer>builder()
                .serviceContractFactory(f -> new LengthServiceContract())
                .inputs("a")
                .samples(1)
                .build();
        assertThatThrownBy(() -> Experiment.exploring(sampling)
                .grid(List.of(new LlmFactors("gpt-4o", 0.3)))
                .baseConfiguration(new LlmFactors("never-explored", 0.1))
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a grid element");
    }

    @Test
    @DisplayName("writeYaml emits the schema with factors / execution / statistics / cost / resultProjection blocks")
    void writeYamlAndFilenameForOneConfig() {
        // Drive a 1-config explore through the engine to produce a
        // real PerConfigSummary, then feed the writer directly.
        Sampling<LlmFactors, String, Integer> sampling = Sampling
                .<LlmFactors, String, Integer>builder()
                .serviceContractFactory(f -> new LengthServiceContract())
                .inputs("a", "bb")
                .samples(2)
                .build();
        Experiment experiment = Experiment.exploring(sampling)
                .grid(List.of(new LlmFactors("gpt-4o", 0.3)))
                .build();
        new Engine().run(experiment);

        PerConfigSummary<?, ?> entry = experiment.perConfigSummaries().get(0);
        FactorBundle bundle = FactorBundle.of(entry.factors());

        ExploreOutputWriter writer = new ExploreOutputWriter();
        String yaml = writer.writeYaml("LengthServiceContract", bundle, entry);

        Map<String, Object> parsed = new Yaml().load(yaml);
        assertThat(parsed).containsKeys(
                "schemaVersion", "serviceContractId", "configuration", "generatedAt",
                "factors", "execution", "statistics", "cost", "resultProjection");
        assertThat(parsed).containsEntry("schemaVersion", "mavai-explore-1");
        assertThat(parsed).containsEntry("serviceContractId", "LengthServiceContract");
        // The body carries the same display name the filename stem uses.
        assertThat(parsed).containsEntry("configuration", writer.filenameFor(bundle));

        @SuppressWarnings("unchecked")
        Map<String, Object> factors = (Map<String, Object>) parsed.get("factors");
        assertThat(factors).containsEntry("model", "gpt-4o");
        assertThat(factors).containsEntry("temperature", 0.3);

        @SuppressWarnings("unchecked")
        Map<String, Object> execution = (Map<String, Object>) parsed.get("execution");
        assertThat(execution).containsKey("samplesPlanned");
        assertThat(execution).containsKey("samplesExecuted");
        assertThat(execution).containsKey("terminationReason");

        @SuppressWarnings("unchecked")
        Map<String, Object> statistics = (Map<String, Object>) parsed.get("statistics");
        assertThat(statistics).containsKeys("observed", "successes", "failures", "failureDistribution");

        @SuppressWarnings("unchecked")
        Map<String, Object> projection = (Map<String, Object>) parsed.get("resultProjection");
        // 2 samples — one entry per sample, keyed by sample[N].
        assertThat(projection).containsKeys("sample[0]", "sample[1]");
        @SuppressWarnings("unchecked")
        Map<String, Object> sample0 = (Map<String, Object>) projection.get("sample[0]");
        // Per-sample fields: input, postconditions, executionTimeMs;
        // content present on success, failureDetail on failure (LengthServiceContract
        // never fails so content is the expected key here).
        assertThat(sample0).containsKeys("inputIndex", "postconditions", "executionTimeMs", "content");
        assertThat(sample0).doesNotContainKey("input");

        // Diff anchor comments must be injected before each sample[N]: line.
        // Two samples → two anchor comments. snakeyaml strips comments on
        // re-parse, so we assert against the raw YAML string, not the parsed
        // map.
        long anchorCount = yaml.lines()
                .filter(line -> line.contains("anchor:"))
                .count();
        assertThat(anchorCount).isEqualTo(2L);
    }

    @Test
    @DisplayName("Two runs of the same explore produce identical anchor comments — diff aligns")
    void anchorsAreContentDeterministic() {
        Sampling<LlmFactors, String, Integer> sampling1 = Sampling
                .<LlmFactors, String, Integer>builder()
                .serviceContractFactory(f -> new LengthServiceContract())
                .inputs("a", "bb")
                .samples(2)
                .build();
        Experiment run1 = Experiment.exploring(sampling1)
                .grid(List.of(new LlmFactors("gpt-4o", 0.3)))
                .build();
        new Engine().run(run1);

        Sampling<LlmFactors, String, Integer> sampling2 = Sampling
                .<LlmFactors, String, Integer>builder()
                .serviceContractFactory(f -> new LengthServiceContract())
                .inputs("a", "bb")
                .samples(2)
                .build();
        Experiment run2 = Experiment.exploring(sampling2)
                .grid(List.of(new LlmFactors("gpt-4o", 0.3)))
                .build();
        new Engine().run(run2);

        ExploreOutputWriter writer = new ExploreOutputWriter();
        String yaml1 = writer.writeYaml("LengthServiceContract",
                FactorBundle.of(run1.perConfigSummaries().get(0).factors()),
                run1.perConfigSummaries().get(0));
        String yaml2 = writer.writeYaml("LengthServiceContract",
                FactorBundle.of(run2.perConfigSummaries().get(0).factors()),
                run2.perConfigSummaries().get(0));

        // Strip generatedAt timestamps (run-specific) and compare anchor lines.
        List<String> anchors1 = yaml1.lines()
                .filter(line -> line.contains("anchor:"))
                .toList();
        List<String> anchors2 = yaml2.lines()
                .filter(line -> line.contains("anchor:"))
                .toList();
        assertThat(anchors1).isEqualTo(anchors2);
    }

    @Test
    @DisplayName("filenameFor produces a readable stem from {field}-{value} pairs joined by _")
    void filenameForReadableStem() {
        ExploreOutputWriter writer = new ExploreOutputWriter();
        FactorBundle bundle = FactorBundle.of(new LlmFactors("gpt-4o", 0.3));
        String stem = writer.filenameFor(bundle);
        assertThat(stem).isEqualTo("model-gpt-4o_temperature-0.3");
    }

    @Test
    @DisplayName("filenameFor truncates long values and appends a 4-char SHA-256 hash")
    void filenameForTruncatesLongValues() {
        record PromptFactors(String systemPrompt) {}
        ExploreOutputWriter writer = new ExploreOutputWriter();
        FactorBundle bundle = FactorBundle.of(new PromptFactors(
                "You are a helpful shopping assistant answering customer queries"));
        String stem = writer.filenameFor(bundle);
        // First 24 chars of the value, sanitised, then "-" + 4 hex.
        // Chars 0..23 of the prompt (counting from 0): "You are a helpful shoppi".
        assertThat(stem).startsWith("systemPrompt-You_are_a_helpful_shoppi-");
        assertThat(stem).matches("systemPrompt-.{24}-[0-9a-f]{4}");
    }

    @Test
    @DisplayName("ExploreEmitter keys the sink by serviceContractId/{sweptKeys}/{stem}.yaml")
    void emitterCapturesOnePerConfig() {
        Sampling<LlmFactors, String, Integer> sampling = Sampling
                .<LlmFactors, String, Integer>builder()
                .serviceContractFactory(f -> new IdServiceContract("explore-test"))
                .inputs("x", "y")
                .samples(2)
                .build();
        Experiment experiment = Experiment.exploring(sampling)
                .grid(List.of(
                        new LlmFactors("gpt-4o", 0.3),
                        new LlmFactors("gpt-4o", 0.7)))
                .build();
        new Engine().run(experiment);

        Map<String, String> sink = new LinkedHashMap<>();
        BiConsumer<String, String> capture = sink::put;
        ExploreEmitter.emit(experiment, capture);

        assertThat(sink).hasSize(2);
        // The middle segment names the swept factors — the model is
        // constant across the grid, so only temperature names the
        // experiment.
        assertThat(sink).containsKeys(
                "explore-test/temperature/model-gpt-4o_temperature-0.3.yaml",
                "explore-test/temperature/model-gpt-4o_temperature-0.7.yaml");
        // Each captured value parses as YAML carrying the explore-output schema header.
        for (String yaml : sink.values()) {
            Map<String, Object> parsed = new Yaml().load(yaml);
            assertThat(parsed).containsEntry("schemaVersion", "mavai-explore-1");
            assertThat(parsed).containsEntry("serviceContractId", "explore-test");
        }
    }

    @Test
    @DisplayName("the experiment directory names the swept factors — + joined, baseline-only when nothing varies")
    void experimentDirectoryNamesTheSweep() {
        ExploreOutputWriter writer = new ExploreOutputWriter();
        var a = org.mavai.punit.api.FactorBundle.of(new LlmFactors("gpt-4o", 0.3));
        var b = org.mavai.punit.api.FactorBundle.of(new LlmFactors("gpt-4o", 0.7));
        var c = org.mavai.punit.api.FactorBundle.of(new LlmFactors("o3", 0.7));
        // One varying factor names the question; two name both, in
        // factor-record declaration order; a grid where nothing varies
        // is baseline-only, so a no-sweep run and a sweep of the same
        // contract never share a directory.
        assertThat(writer.experimentDirectory(List.of(a, b))).isEqualTo("temperature");
        assertThat(writer.experimentDirectory(List.of(a, b, c))).isEqualTo("model+temperature");
        assertThat(writer.experimentDirectory(List.of(a))).isEqualTo("baseline-only");
    }

    @Test
    @DisplayName("ExploreEmitter rejects non-EXPLORE experiments")
    void emitterRejectsWrongKind() {
        Sampling<LlmFactors, String, Integer> sampling = Sampling
                .<LlmFactors, String, Integer>builder()
                .serviceContractFactory(f -> new LengthServiceContract())
                .inputs("a")
                .samples(1)
                .build();
        Experiment measure = Experiment.measuring(sampling, new LlmFactors("gpt-4o", 0.3)).build();

        try {
            ExploreEmitter.emit(measure, new HashMap<String, String>()::put);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("EXPLORE");
            return;
        }
        throw new AssertionError("expected IllegalArgumentException for non-EXPLORE experiment");
    }

    /** Service contract with a configured id, useful for asserting on the emitted relative path. */
    private static final class IdServiceContract implements ServiceContract<LlmFactors, String, Integer> {
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
}
