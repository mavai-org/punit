package org.mavai.punit.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mavai.punit.api.criterion.Criteria.empirical;
import static org.mavai.punit.api.criterion.Criteria.meeting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mavai.outcome.Outcome;
import org.mavai.punit.api.NoFactors;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.criterion.Criteria;
import org.mavai.punit.internal.engine.baseline.BaselineResolver;
import org.opentest4j.AssertionFailedError;
import org.mavai.punit.api.spec.UnsupportableJudgementException;
import org.opentest4j.TestAbortedException;

/**
 * The measure builder's gating terminal — normative judgement at
 * experiment time, made binding. The neutral {@code run()} terminal
 * never fails on a failed judgement; the gating terminal performs the
 * same run and the same persistence, then asserts with the same
 * opentest4j mapping {@code assertPasses()} uses. Persistence
 * strictly precedes assertion: the baseline artefact is on disk
 * before any throw.
 */
@DisplayName("Measure gating terminal — run-and-assert over normative judgements")
class MeasureGatingTerminalTest {

    @TempDir
    Path baselineDir;

    private String savedProperty;

    @BeforeEach
    void pointBaselineDirAtTempDir() {
        savedProperty = System.getProperty(BaselineResolver.BASELINE_DIR_PROPERTY);
        System.setProperty(BaselineResolver.BASELINE_DIR_PROPERTY, baselineDir.toString());
    }

    @AfterEach
    void restoreProperty() {
        if (savedProperty == null) {
            System.clearProperty(BaselineResolver.BASELINE_DIR_PROPERTY);
        } else {
            System.setProperty(BaselineResolver.BASELINE_DIR_PROPERTY, savedProperty);
        }
    }

    private static Outcome<?> even(String value) {
        int n = Integer.parseInt(value.substring("value-".length()));
        return n % 2 == 0 ? Outcome.ok(null) : Outcome.fail("odd", "odd input " + n);
    }

    private static ServiceContract<NoFactors, Integer, String> contract(
            String id, Criteria<String> criteria) {
        return new ServiceContract<>() {
            @Override public String id() { return id; }
            @Override public Criteria<String> criteria() { return criteria; }
            @Override public Outcome<String> invoke(Integer input, TokenTracker tracker) {
                return Outcome.ok("value-" + input);
            }
        };
    }

    private static Sampling<NoFactors, Integer, String> sampling(
            ServiceContract<NoFactors, Integer, String> contract, Integer... inputs) {
        return Sampling.<NoFactors, Integer, String>builder()
                .serviceContractFactory(f -> contract)
                .inputs(inputs)
                .samples(8)
                .build();
    }

    private long baselineFileCount() throws IOException {
        try (Stream<Path> files = Files.list(baselineDir)) {
            return files.filter(p -> p.toString().endsWith(".yaml")).count();
        }
    }

    @Test
    @DisplayName("completes normally when every normative criterion is met, with the "
            + "baseline artefact on disk")
    void completesWhenAllNormativeCriteriaMet() throws IOException {
        var uc = contract("GatedMetMeasure",
                meeting().<String>passRate(0.5)
                        .satisfies("value is even", MeasureGatingTerminalTest::even));

        assertThatCode(() -> PUnit.measuring(sampling(uc, 2, 4)).assertMeets())
                .doesNotThrowAnyException();
        assertThat(baselineFileCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("throws AssertionFailedError on a failed judgement — after the "
            + "baseline artefact is on disk")
    void failsOnFailedJudgementWithBaselinePersisted() throws IOException {
        var uc = contract("GatedFailedMeasure",
                meeting().<String>passRate(0.5)
                        .satisfies("value is even", MeasureGatingTerminalTest::even));

        assertThatExceptionOfType(AssertionFailedError.class)
                .isThrownBy(() -> PUnit.measuring(sampling(uc, 2, 3)).assertMeets())
                .withMessageContaining("did not clear its stipulated 0.5");
        assertThat(baselineFileCount())
                .as("persistence strictly precedes assertion — a failed "
                        + "stipulation never costs the baseline")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("throws UnsupportableJudgementException (aborting the harness), stating "
            + "the feasible minimum, on an unsupportable judgement — after the baseline "
            + "artefact is on disk")
    void abortsOnUnsupportableJudgementWithBaselinePersisted() throws IOException {
        var uc = contract("GatedUndersizedMeasure",
                meeting().<String>passRate(0.99)
                        .satisfies("value is even", MeasureGatingTerminalTest::even));

        assertThatExceptionOfType(UnsupportableJudgementException.class)
                .isThrownBy(() -> PUnit.measuring(sampling(uc, 2, 4)).assertMeets())
                .isInstanceOf(TestAbortedException.class)
                .withMessageContaining("unsupportable at 8 samples")
                .withMessageContaining("requires at least");
        assertThat(baselineFileCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("run() keeps its neutral completion semantics — it never fails on a "
            + "failed judgement")
    void runCompletesOnFailedJudgement() throws IOException {
        var uc = contract("NeutralFailedMeasure",
                meeting().<String>passRate(0.5)
                        .satisfies("value is even", MeasureGatingTerminalTest::even));

        assertThatCode(() -> PUnit.measuring(sampling(uc, 2, 3)).run())
                .doesNotThrowAnyException();
        assertThat(baselineFileCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("run() also completes normally on an unsupportable judgement")
    void runCompletesOnUnsupportableJudgement() throws IOException {
        var uc = contract("NeutralUndersizedMeasure",
                meeting().<String>passRate(0.99)
                        .satisfies("value is even", MeasureGatingTerminalTest::even));

        assertThatCode(() -> PUnit.measuring(sampling(uc, 2, 4)).run())
                .doesNotThrowAnyException();
        assertThat(baselineFileCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("judges only the normative criteria of a mixed contract — an "
            + "empirical criterion cannot fail the gating terminal")
    void mixedContractGatesOnNormativeCriteriaOnly() throws IOException {
        var uc = contract("GatedMixedMeasure",
                Criteria.of(
                        meeting().<String>passRate(0.5)
                                .name("stipulated-evenness")
                                .satisfies("value is even", MeasureGatingTerminalTest::even),
                        empirical().<String>passRate()
                                .name("observed-evenness")
                                .satisfies("value is even", MeasureGatingTerminalTest::even)));

        assertThatCode(() -> PUnit.measuring(sampling(uc, 2, 4)).assertMeets())
                .doesNotThrowAnyException();
        assertThat(baselineFileCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("rejects a contract with no normative criteria as a configuration "
            + "defect, before spending any samples")
    void rejectsContractWithNoNormativeCriteriaBeforeSampling() throws IOException {
        AtomicInteger invocations = new AtomicInteger();
        ServiceContract<NoFactors, Integer, String> uc = new ServiceContract<>() {
            @Override public String id() { return "GatedEmpiricalOnlyMeasure"; }
            @Override public Criteria<String> criteria() {
                return empirical().<String>passRate()
                        .satisfies("value is even", MeasureGatingTerminalTest::even);
            }
            @Override public Outcome<String> invoke(Integer input, TokenTracker tracker) {
                invocations.incrementAndGet();
                return Outcome.ok("value-" + input);
            }
        };

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> PUnit.measuring(sampling(uc, 2, 4)).assertMeets())
                .withMessageContaining("nothing to")
                .withMessageContaining("run()");
        assertThat(invocations)
                .as("the configuration defect surfaces before sampling")
                .hasValue(0);
        assertThat(baselineFileCount()).isZero();
    }

    @Test
    @DisplayName("gating and neutral terminals are alternatives over the same run and "
            + "the same artefact — the gated artefact carries the judgement marker")
    void gatedRunPersistsTheJudgementMarker() throws IOException {
        var uc = contract("GatedMarkerMeasure",
                meeting().<String>passRate(0.5)
                        .satisfies("value is even", MeasureGatingTerminalTest::even));

        assertThatExceptionOfType(AssertionFailedError.class)
                .isThrownBy(() -> PUnit.measuring(sampling(uc, 2, 3)).assertMeets());

        try (Stream<Path> files = Files.list(baselineDir)) {
            List<Path> yamls = files.filter(p -> p.toString().endsWith(".yaml")).toList();
            assertThat(yamls).hasSize(1);
            assertThat(Files.readString(yamls.get(0)))
                    .contains("normativeJudgement")
                    .contains("state: failed");
        }
    }
}
