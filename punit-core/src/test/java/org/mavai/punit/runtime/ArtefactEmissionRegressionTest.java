package org.mavai.punit.runtime;

import static org.mavai.punit.api.criterion.Criteria.meeting;
import org.mavai.punit.api.criterion.Criteria;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.api.spec.Experiment;
import org.mavai.punit.api.spec.NextFactor;
import org.mavai.punit.internal.engine.Engine;
import org.mavai.punit.internal.runtime.BaselineEmitter;
import org.mavai.punit.internal.runtime.ExploreEmitter;
import org.mavai.punit.internal.runtime.OptimizeEmitter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Regression-guard meta-test: every {@link Experiment.Kind} must
 * produce at least one artefact when its {@code .run()} succeeds.
 *
 * <p>This is the test that <em>should</em> have caught the silent
 * EXPLORE / OPTIMIZE emission gap that this work restores. The
 * structure is deliberate: a switch expression over
 * {@link Experiment.Kind} forces the compiler to flag any new
 * kind added without a matching emitter dispatch — adding a new
 * kind without wiring an emitter fails at compile time, not
 * silently at runtime.
 *
 * <p>All emitters are exercised through their
 * {@code BiConsumer<String, String>} sink overload — no temp
 * directories, no disk I/O. The test verifies "the emit step
 * yields at least one artefact"; the per-kind writer tests
 * verify schema details.
 */
@DisplayName("Every Experiment.Kind must emit at least one artefact")
class ArtefactEmissionRegressionTest {

    record F(String label, double weight) {}

    private static class TrivialServiceContract implements ServiceContract<F, String, Integer> {
        @Override public String id() { return "regression-guard"; }
        @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input.length());
        }
        @Override public Criteria<Integer> criteria() {
            return meeting().<Integer>zeroFailures();
        }

    }

    @ParameterizedTest
    @EnumSource(Experiment.Kind.class)
    @DisplayName("Each Kind yields ≥ 1 artefact when run end-to-end")
    void everyKindEmitsAtLeastOneArtefact(Experiment.Kind kind) {
        Experiment experiment = buildMinimalFor(kind);
        new Engine().run(experiment);

        Map<String, String> sink = new LinkedHashMap<>();
        BiConsumer<String, String> capture = sink::put;

        // Switch expression — exhaustive over Experiment.Kind.
        // Adding a new kind without wiring an emitter here is a
        // compile-time fail, not a runtime fail.
        String dispatched = switch (kind) {
            case MEASURE -> {
                BaselineEmitter.emit(experiment, capture);
                yield "MEASURE";
            }
            case EXPLORE -> {
                ExploreEmitter.emit(experiment, capture);
                yield "EXPLORE";
            }
            case OPTIMIZE -> {
                OptimizeEmitter.emit(experiment, capture);
                yield "OPTIMIZE";
            }
        };

        assertThat(sink)
                .as("kind %s (%s) must produce at least one artefact", kind, dispatched)
                .isNotEmpty();
        // Each entry must carry a relativePath and non-empty content.
        sink.forEach((relativePath, content) -> {
            assertThat(relativePath).isNotBlank();
            assertThat(content).isNotBlank();
        });
    }

    private static Experiment buildMinimalFor(Experiment.Kind kind) {
        Sampling<F, String, Integer> sampling = Sampling
                .<F, String, Integer>builder()
                .serviceContractFactory(f -> new TrivialServiceContract())
                .inputs("a", "bb")
                .samples(2)
                .build();
        return switch (kind) {
            case MEASURE -> Experiment.measuring(sampling, new F("only", 0.0)).build();
            case EXPLORE -> Experiment.exploring(sampling)
                    .grid(List.of(new F("low", 0.0), new F("high", 0.5)))
                    .build();
            case OPTIMIZE -> Experiment.optimizing(sampling)
                    .initialFactors(new F("init", 0.0))
                    .stepper((cur, hist) -> hist.size() < 1
                            ? NextFactor.next(new F("step", 0.5))
                            : NextFactor.stop())
                    .maximize(s -> 1.0 * s.successes() / Math.max(1, s.total()))
                    .maxIterations(3)
                    .build();
        };
    }
}
