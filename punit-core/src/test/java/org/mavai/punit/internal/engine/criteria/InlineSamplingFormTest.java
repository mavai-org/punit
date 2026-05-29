package org.mavai.punit.internal.engine.criteria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.ThresholdOrigin;
import static org.mavai.punit.api.criterion.Criteria.meeting;

import org.mavai.punit.api.criterion.Criteria;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.api.spec.Experiment;
import org.mavai.punit.api.spec.NextFactor;
import org.mavai.punit.api.spec.ProbabilisticTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Inline-sampling form")
class InlineSamplingFormTest {

    record Factors(String label) {}

    private static final ServiceContract<Factors, String, String> ECHO = new ServiceContract<>() {
        @Override public Criteria<String> criteria() {
            return meeting().passRate(0.95);
        }
        @Override public Outcome<String> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input);
        }
    };

    @Test
    @DisplayName("Experiment.measuring(serviceContract, factors) builds a measure with inline sampling")
    void measureInlineForm() {
        Experiment spec = Experiment.measuring(f -> ECHO, new Factors("m"))
                .inputs("a", "b", "c")
                .samples(50)
                .experimentId("inline-measure")
                .build();

        assertThat(spec.kind()).isEqualTo(Experiment.Kind.MEASURE);
        assertThat(spec.samples()).isEqualTo(50);
        assertThat(spec.experimentId()).isEqualTo("inline-measure");
    }

    @Test
    @DisplayName("Experiment.exploring(serviceContract) builds an explore with inline sampling")
    void exploreInlineForm() {
        Experiment spec = Experiment.exploring((Factors f) -> ECHO)
                .inputs("a", "b")
                .samples(10)
                .grid(new Factors("v1"), new Factors("v2"), new Factors("v3"))
                .build();

        assertThat(spec.kind()).isEqualTo(Experiment.Kind.EXPLORE);
        assertThat(spec.samples()).isEqualTo(10);
    }

    @Test
    @DisplayName("Experiment.optimizing(serviceContract) builds an optimize with inline sampling")
    void optimizeInlineForm() {
        Experiment spec = Experiment.optimizing((Factors f) -> ECHO)
                .inputs("a")
                .samples(5)
                .initialFactors(new Factors("seed"))
                .stepper((current, history) -> history.size() >= 3 ? NextFactor.stop() : NextFactor.next(new Factors(current.label() + "+")))
                .maximize(s -> 1.0)
                .maxIterations(5)
                .build();

        assertThat(spec.kind()).isEqualTo(Experiment.Kind.OPTIMIZE);
        assertThat(spec.samples()).isEqualTo(5);
    }

    @Test
    @DisplayName("inline measure rejects null factory at the entry point")
    void measureInlineRejectsNullFactory() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> Experiment.measuring((java.util.function.Function<Factors, ServiceContract<Factors, String, String>>) null,
                        new Factors("m")));
    }

    @Test
    @DisplayName("inline measure requires inputs at .build()")
    void measureInlineRequiresInputs() {
        var builder = Experiment.measuring((Factors f) -> ECHO, new Factors("m"));
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(builder::build)
                .withMessageContaining("inputs");
    }

    @Test
    @DisplayName("ProbabilisticTest.testing(serviceContract, factors) inline form works for contractual criteria")
    void contractualInlineFormBuilds() {
        ProbabilisticTest spec = ProbabilisticTest.testing((Factors f) -> ECHO, new Factors("m"))
                .inputs("a", "b")
                .samples(20)
                .criterion(PassRate.<String>meeting(ThresholdOrigin.SLA, 0.95))
                .build();

        assertThat(spec.samples()).isEqualTo(20);
    }

    @Test
    @DisplayName("ProbabilisticTest.testing inline form rejects empirical criteria with a teaching diagnostic")
    void empiricalInlineFormIsRejected() {
        var builder = ProbabilisticTest.testing((Factors f) -> ECHO, new Factors("m"))
                .inputs("a", "b")
                .samples(20)
                .criterion(PassRate.<String>empirical());

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(builder::build)
                .withMessageContaining("empirical")
                .withMessageContaining("Sampling")
                .withMessageContaining("baseline measure")
                .withMessageContaining("private Sampling");
    }

    @Test
    @DisplayName("ProbabilisticTest inline form rejects empirical criteria registered as report-only too")
    void empiricalReportOnlyInlineFormIsRejected() {
        var builder = ProbabilisticTest.testing((Factors f) -> ECHO, new Factors("m"))
                .inputs("a", "b")
                .samples(20)
                .criterion(PassRate.<String>meeting(ThresholdOrigin.SLA, 0.5))
                .reportOnly(PassRate.<String>empirical());

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(builder::build)
                .withMessageContaining("empirical");
    }

    @Test
    @DisplayName("Sampling-bound ProbabilisticTest path still accepts empirical criteria — the guard is inline-only")
    void samplingBoundEmpiricalIsAccepted() {
        var sampling = Sampling.of(
                (Factors f) -> ECHO, 20, "a", "b");
        ProbabilisticTest spec = ProbabilisticTest.testing(sampling, new Factors("m"))
                .criterion(PassRate.<String>empirical())
                .build();

        assertThat(spec.samples()).isEqualTo(20);
    }
}
