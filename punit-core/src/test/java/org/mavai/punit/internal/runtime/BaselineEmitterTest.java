package org.mavai.punit.internal.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.NoFactors;
import org.mavai.punit.api.criterion.Criteria;
import static org.mavai.punit.api.criterion.Criteria.meeting;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.api.spec.Experiment;
import org.mavai.punit.api.spec.NextFactor;
import org.mavai.punit.internal.engine.Engine;
import org.mavai.punit.internal.engine.baseline.BaselineReader;
import org.mavai.punit.internal.engine.baseline.BaselineRecord;
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

    private static Sampling<NoFactors, Integer, String> samplingWithCriteria() {
        return Sampling.<NoFactors, Integer, String>builder()
                .serviceContractFactory(f -> EVENS_PASS)
                .inputs(2, 4)   // both even → all pass
                .samples(4)
                .build();
    }

    @Test
    @DisplayName("emits expiresInDays + derived expiresAt when the measure declared a "
            + "validity window, and round-trips through BaselineReader")
    void emitsExpirationMetadataWhenDeclared() {
        Experiment measure = Experiment.measuring(samplingWithCriteria(), new NoFactors())
                .experimentId("baseline-v1")
                .expiresInDays(30)
                .build();
        new Engine().run(measure);

        Map<String, String> sink = new LinkedHashMap<>();
        BaselineEmitter.emit(measure, sink::put);
        String yaml = sink.values().iterator().next();
        Map<String, Object> root = new Yaml().load(yaml);

        assertThat(root)
                .as("a measure declaring .expiresInDays(30) must persist the validity "
                        + "window — dropping it silently disables baseline expiration")
                .containsEntry("expiresInDays", 30)
                .containsKey("expiresAt");

        // expiresAt is generatedAt + 30 days.
        Instant generatedAt = Instant.parse(root.get("generatedAt").toString());
        Instant expiresAt = Instant.parse(root.get("expiresAt").toString());
        assertThat(expiresAt).isEqualTo(generatedAt.plus(Duration.ofDays(30)));

        // Both expiration fields precede contentFingerprint, so they fall
        // under the integrity hash — a hand-edit would be detected.
        assertThat(yaml.indexOf("expiresInDays"))
                .isLessThan(yaml.indexOf("contentFingerprint"));
        assertThat(yaml.indexOf("expiresAt"))
                .isLessThan(yaml.indexOf("contentFingerprint"));

        // Round-trips back to the same window.
        BaselineRecord parsed = new BaselineReader().parse(yaml);
        assertThat(parsed.expiresInDays()).isEqualTo(30);
    }

    @Test
    @DisplayName("omits expiresInDays / expiresAt when no validity window was declared — "
            + "absent means no expiration, not 0")
    void omitsExpirationMetadataWhenNotDeclared() {
        Experiment measure = Experiment.measuring(samplingWithCriteria(), new NoFactors())
                .experimentId("baseline-v1")
                .build();
        new Engine().run(measure);

        Map<String, String> sink = new LinkedHashMap<>();
        BaselineEmitter.emit(measure, sink::put);
        String yaml = sink.values().iterator().next();
        Map<String, Object> root = new Yaml().load(yaml);

        assertThat(root)
                .doesNotContainKey("expiresInDays")
                .doesNotContainKey("expiresAt");

        // A baseline with no window reads back as 0 (no expiration).
        assertThat(new BaselineReader().parse(yaml).expiresInDays()).isZero();
    }
}
