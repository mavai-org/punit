package org.javai.punit.internal.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import org.javai.outcome.Outcome;
import org.javai.punit.api.NoFactors;
import org.javai.punit.api.criterion.Criteria;
import static org.javai.punit.api.criterion.Criteria.meeting;
import org.javai.punit.api.Sampling;
import org.javai.punit.api.TokenTracker;
import org.javai.punit.api.ServiceContract;
import org.javai.punit.api.spec.Experiment;
import org.javai.punit.api.spec.NextFactor;
import org.javai.punit.internal.engine.Engine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

@DisplayName("BaselineEmitter — misuse contract and per-sample emission")
class BaselineEmitterTest {

    private static final ServiceContract<NoFactors, Integer, Boolean> ALWAYS_PASSES = new ServiceContract<>() {
        @Override public String id() { return "AlwaysPassesServiceContract"; }
        @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
            return Outcome.ok(true);
        }
    };

    private static Sampling<NoFactors, Integer, Boolean> sampling() {
        return Sampling.<NoFactors, Integer, Boolean>builder()
                .serviceContractFactory(f -> ALWAYS_PASSES)
                .inputs(1, 2, 3)
                .samples(10)
                .build();
    }

    @Test
    @DisplayName("rejects an EXPLORE experiment with IllegalArgumentException")
    void rejectsExplore(@TempDir Path dir) {
        Experiment explore = Experiment.exploring(sampling())
                .grid(new NoFactors())
                .build();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> BaselineEmitter.emit(explore, dir))
                .withMessageContaining("MEASURE")
                .withMessageContaining("EXPLORE");
    }

    @Test
    @DisplayName("rejects an OPTIMIZE experiment with IllegalArgumentException")
    void rejectsOptimize(@TempDir Path dir) {
        Experiment optimize = Experiment.optimizing(sampling())
                .initialFactors(new NoFactors())
                .stepper((current, history) -> history.size() >= 1 ? NextFactor.stop() : NextFactor.next(new NoFactors()))
                .maximize(s -> 0.0)
                .maxIterations(1)
                .build();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> BaselineEmitter.emit(optimize, dir))
                .withMessageContaining("MEASURE")
                .withMessageContaining("OPTIMIZE");
    }

    @Test
    @DisplayName("rejects a MEASURE experiment that has not yet been consumed by the engine")
    void rejectsUnconsumedMeasure(@TempDir Path dir) {
        Experiment measure = Experiment.measuring(sampling(), new NoFactors()).build();

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> BaselineEmitter.emit(measure, dir))
                .withMessageContaining("no recorded summary");
    }

    private static final ServiceContract<NoFactors, Integer, String> EVENS_PASS = new ServiceContract<>() {
        @Override public String id() { return "EvensPassServiceContract"; }
        @Override public Criteria<String> criteria() {
            return meeting().<String>zeroFailures()
                    .satisfies("non-blank", s -> s.isBlank()
                            ? Outcome.fail("blank", "value was blank")
                            : Outcome.ok(null));
        }
        @Override public Outcome<String> invoke(Integer input, TokenTracker tracker) {
            return input % 2 == 0 ? Outcome.ok("even-" + input) : Outcome.fail("odd", "got " + input);
        }
    };

    @Test
    @DisplayName("emits no per-sample resultProjection: block — MEASURE baselines carry "
            + "aggregate signal only; per-sample failure detail goes to System.err")
    void omitsResultProjection() {
        Sampling<NoFactors, Integer, String> sampling = Sampling
                .<NoFactors, Integer, String>builder()
                .serviceContractFactory(f -> EVENS_PASS)
                .inputs(2, 3) // alternating pass / fail by parity
                .samples(4)   // 4 samples cycle the 2-input list twice
                .build();
        Experiment measure = Experiment.measuring(sampling, new NoFactors()).build();
        new Engine().run(measure);

        Map<String, String> sink = new LinkedHashMap<>();
        BiConsumer<String, String> capture = sink::put;
        BaselineEmitter.emit(measure, capture);

        assertThat(sink).hasSize(1);
        String yaml = sink.values().iterator().next();
        Map<String, Object> root = new Yaml().load(yaml);

        assertThat(root)
                .as("MEASURE baseline must not carry a resultProjection: block — "
                        + "the probabilistic test consumes only aggregate signal "
                        + "(pass count, sample total, footprint, fingerprint, "
                        + "derived threshold)")
                .doesNotContainKey("resultProjection");

        // No sample[N] keys, no anchor comments anywhere in the body.
        assertThat(yaml).doesNotContain("sample[0]", "sample[1]", "anchor:");
    }
}
