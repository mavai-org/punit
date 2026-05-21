package org.javai.punit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.javai.punit.api.ThresholdOrigin.SLA;
import static org.javai.punit.api.criterion.Criteria.meeting;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.javai.outcome.Outcome;
import org.javai.punit.api.criterion.Criteria;
import org.javai.punit.api.spec.Experiment;
import org.javai.punit.api.spec.NextFactor;
import org.javai.punit.api.spec.ProbabilisticTest;
import org.javai.punit.api.spec.ProbabilisticTestResult;
import org.javai.punit.api.spec.Verdict;
import org.javai.punit.internal.engine.Engine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Behavioural coverage for the inline-contract authoring surface — an
 * anonymous {@link ServiceContract} declared at the call site, bound to
 * sampling via {@link Sampling#of(ServiceContract, int, Object[])}.
 */
@DisplayName("inline contract authoring surface")
class InlineContractTest {

    /** All even-length — every sample satisfies an even-length check. */
    private static final List<String> EVEN_INPUTS =
            List.of("ab", "cdef", "ghij", "klmn");

    /** Half even-length, half odd — observed pass rate around 0.5. */
    private static final List<String> MIXED_INPUTS =
            List.of("ab", "x", "cdef", "yz1");

    private static Verdict verdict(
            ServiceContract<NoFactors, String, Integer> contract, List<String> inputs) {
        Sampling<NoFactors, String, Integer> sampling = Sampling.of(contract, 60, inputs);
        ProbabilisticTest spec = ProbabilisticTest.testing(sampling, NoFactors.INSTANCE).build();
        return ((ProbabilisticTestResult) new Engine().run(spec)).verdict();
    }

    @Test
    @DisplayName("a met pass rate yields PASS")
    void metPassRatePasses() {
        ServiceContract<NoFactors, String, Integer> contract =
                Contract.<String, Integer>inline()
                        .returning(String::length)
                        .passRate(0.95)
                        .where("even length", n -> n % 2 == 0)
                        .build();
        assertThat(verdict(contract, EVEN_INPUTS)).isEqualTo(Verdict.PASS);
    }

    @Test
    @DisplayName("a missed pass rate yields FAIL")
    void missedPassRateFails() {
        ServiceContract<NoFactors, String, Integer> contract =
                Contract.<String, Integer>inline()
                        .returning(String::length)
                        .passRate(0.95)
                        .where("even length", n -> n % 2 == 0)
                        .build();
        assertThat(verdict(contract, MIXED_INPUTS)).isEqualTo(Verdict.FAIL);
    }

    @Test
    @DisplayName("invoking(...) Outcome form with a satisfies(...) postcondition and contract reference")
    void invokingOutcomeFormWithSatisfies() {
        ServiceContract<NoFactors, String, Integer> contract =
                Contract.<String, Integer>inline()
                        .invoking(in -> Outcome.ok(in.length()))
                        .passRate(0.95)
                        .contractRef(SLA, "Length SLA v1 §1")
                        .satisfies("even length",
                                n -> n % 2 == 0 ? Outcome.ok() : Outcome.fail("odd", "n=" + n))
                        .build();
        assertThat(verdict(contract, EVEN_INPUTS)).isEqualTo(Verdict.PASS);
    }

    @Test
    @DisplayName("invoking(...) with the cost channel records tokens and passes")
    void invokingWithCostChannel() {
        ServiceContract<NoFactors, String, Integer> contract =
                Contract.<String, Integer>inline()
                        .invoking((in, tracker) -> {
                            tracker.recordTokens(1);
                            return Outcome.ok(in.length());
                        })
                        .passRate(0.95)
                        .build();
        assertThat(verdict(contract, EVEN_INPUTS)).isEqualTo(Verdict.PASS);
    }

    @Test
    @DisplayName("zeroFailures fails on any failing sample, passes on none")
    void zeroFailures() {
        ServiceContract<NoFactors, String, Integer> failing =
                Contract.<String, Integer>inline()
                        .returning(String::length)
                        .zeroFailures()
                        .where("even length", n -> n % 2 == 0)
                        .build();
        assertThat(verdict(failing, MIXED_INPUTS)).isEqualTo(Verdict.FAIL);

        ServiceContract<NoFactors, String, Integer> clean =
                Contract.<String, Integer>inline()
                        .returning(String::length)
                        .zeroFailures()
                        .where("even length", n -> n % 2 == 0)
                        .build();
        assertThat(verdict(clean, EVEN_INPUTS)).isEqualTo(Verdict.PASS);
    }

    @Test
    @DisplayName("an inline latency ceiling participates in the verdict")
    void inlineLatencyParticipates() {
        ServiceContract<NoFactors, String, Integer> contract =
                Contract.<String, Integer>inline()
                        .returning(String::length)
                        .passRate(0.95)
                        .where("even length", n -> n % 2 == 0)
                        .latencyAtMost(PercentileKey.P95, Duration.ofHours(1))
                        .build();
        assertThat(verdict(contract, EVEN_INPUTS)).isEqualTo(Verdict.PASS);
    }

    @Test
    @DisplayName("inline contract and an equivalent named contract produce the same verdict")
    void inlineMatchesNamedContract() {
        ServiceContract<NoFactors, String, Integer> inline =
                Contract.<String, Integer>inline()
                        .returning(String::length)
                        .passRate(0.95)
                        .contractRef(SLA, "Length SLA v1 §1")
                        .satisfies("even length",
                                n -> n % 2 == 0 ? Outcome.ok() : Outcome.fail("odd", "n=" + n))
                        .build();
        ServiceContract<NoFactors, String, Integer> named = new NamedEquivalent();

        assertThat(verdict(inline, EVEN_INPUTS)).isEqualTo(verdict(named, EVEN_INPUTS));
        assertThat(verdict(inline, MIXED_INPUTS)).isEqualTo(verdict(named, MIXED_INPUTS));
    }

    /** The lift-and-name graduation of the inline contract above. */
    private static final class NamedEquivalent
            implements ServiceContract<NoFactors, String, Integer> {
        @Override
        public String id() {
            return "named-equivalent";
        }

        @Override
        public Outcome<Integer> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input.length());
        }

        @Override
        public Criteria<Integer> criteria() {
            return meeting().<Integer>passRate(0.95)
                    .contractRef(SLA, "Length SLA v1 §1")
                    .satisfies("even length",
                            n -> n % 2 == 0 ? Outcome.ok() : Outcome.fail("odd", "n=" + n));
        }
    }

    @Test
    @DisplayName("the contract-first sampling(...) terminal yields the same verdict as Sampling.of(build(), ...)")
    void samplingTerminalMatchesExplicitBinding() {
        Sampling<NoFactors, String, Integer> viaTerminal =
                Contract.<String, Integer>inline()
                        .returning(String::length)
                        .passRate(0.95)
                        .where("even length", n -> n % 2 == 0)
                        .sampling(60, EVEN_INPUTS);
        ProbabilisticTest spec = ProbabilisticTest.testing(viaTerminal, NoFactors.INSTANCE).build();
        Verdict viaTerminalVerdict = ((ProbabilisticTestResult) new Engine().run(spec)).verdict();
        assertThat(viaTerminalVerdict).isEqualTo(Verdict.PASS);
    }

    @Test
    @DisplayName("the builder exposes no empirical authoring path")
    void noEmpiricalPath() {
        Set<String> methodNames = Arrays.stream(Contract.Inline.class.getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
        assertThat(methodNames).contains("passRate", "zeroFailures");
        assertThat(methodNames).doesNotContain("empirical");

        boolean referencesEmpiricalType = Arrays.stream(Contract.Inline.class.getMethods())
                .flatMap(m -> Stream.concat(
                        Stream.of(m.getReturnType()), Arrays.stream(m.getParameterTypes())))
                .anyMatch(t -> t.getName().contains("Empirical"));
        assertThat(referencesEmpiricalType)
                .as("no inline-builder method returns or accepts an empirical decl type")
                .isFalse();
    }

    @Test
    @DisplayName("inline contracts bind to measure, explore, and optimize specs")
    void bindsAcrossExperimentKinds() {
        Sampling<NoFactors, String, Integer> measureSampling = Sampling.of(
                Contract.<String, Integer>inline()
                        .invoking(in -> Outcome.ok(in.length()))
                        .passRate(0.95)
                        .build(),
                20, EVEN_INPUTS);
        assertThat(Experiment.measuring(measureSampling, NoFactors.INSTANCE).build())
                .isNotNull();

        Sampling<Tuning, String, Integer> factoredSampling = Sampling.of(
                (Tuning f) -> Contract.<String, Integer>inline()
                        .invoking(in -> Outcome.ok(in.length() + f.bump()))
                        .passRate(0.95)
                        .build(),
                20, EVEN_INPUTS);
        assertThat(Experiment.exploring(factoredSampling)
                .grid(List.of(new Tuning(0), new Tuning(1)))
                .build())
                .isNotNull();
        assertThat(Experiment.optimizing(factoredSampling)
                .initialFactors(new Tuning(0))
                .stepper((current, history) -> NextFactor.stop())
                .maximize(summary -> 0.0)
                .maxIterations(1)
                .build())
                .isNotNull();
    }

    record Tuning(int bump) { }
}
