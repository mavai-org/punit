package org.mavai.punit.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mavai.outcome.Outcome;
import org.mavai.punit.api.NoFactors;
import org.mavai.punit.api.PercentileKey;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.criterion.Criteria;
import org.mavai.punit.api.criterion.LatencyCriterion;
import org.mavai.punit.api.spec.AssertionEnforcement;
import org.mavai.punit.api.spec.ConfigurationRefusedException;
import org.mavai.punit.internal.reporting.VerdictSinkBus;
import org.mavai.punit.verdict.ProbabilisticTestVerdict;
import org.opentest4j.AssertionFailedError;

/**
 * The three asserting terminals — {@code assertPasses()},
 * {@code assertContract()} and {@code assertLatency()} — on live runs,
 * under every setting of the run's advisory switch. Each scenario is
 * deterministic: the functional outcome is scripted per sample, and the
 * latency ceiling is either far above or below the scripted invocation
 * time.
 */
@DisplayName("assertPasses / assertContract / assertLatency under the advisory switch")
class AssertionMethodsIntegrationTest {

    /** Every sample sleeps this long, above the failing ceiling. */
    private static final long SLEEP_MS = 3L;

    private static final int SAMPLES = 80;

    /** Samples before the functional-failure scenario starts failing. */
    private static final int PASSING_FIRST = 70;

    private static final LatencyCriterion CEILING_MET =
            Criteria.meeting().atMost(PercentileKey.P50, Duration.ofSeconds(60));

    private static final LatencyCriterion CEILING_BREACHED =
            Criteria.meeting().atMost(PercentileKey.P50, Duration.ofMillis(1));

    @AfterEach
    void clearSwitch() {
        System.clearProperty(AssertionEnforcement.PROPERTY);
        VerdictSinkBus.reset();
    }

    /** The asserting terminals, as an author calls them. */
    enum Terminal {
        PASSES, CONTRACT, LATENCY;

        void call(PUnit.TestBuilder<NoFactors, Integer, Boolean> test) {
            switch (this) {
                case PASSES -> test.assertPasses();
                case CONTRACT -> test.assertContract();
                case LATENCY -> test.assertLatency();
            }
        }
    }

    /** The scripted dimension outcomes. */
    enum Scenario {
        /** V_rate FAIL, V_latency PASS. */
        FUNCTIONAL_FAILS,
        /** V_rate PASS, V_latency FAIL. */
        LATENCY_FAILS
    }

    private static ServiceContract<NoFactors, Integer, Boolean> contract(
            String id, Criteria<Boolean> criteria, LatencyCriterion latency, AtomicInteger seen,
            int passingFirst) {
        return new ServiceContract<>() {
            @Override public String id() { return id; }
            @Override public Criteria<Boolean> criteria() { return criteria; }
            @Override public LatencyCriterion latency() { return latency; }
            @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
                sleep();
                return seen.incrementAndGet() <= passingFirst
                        ? Outcome.ok(Boolean.TRUE)
                        : Outcome.fail("scripted", "scripted failure");
            }
        };
    }

    private static void sleep() {
        try {
            Thread.sleep(SLEEP_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static Criteria<Boolean> holds() {
        return Criteria.meeting().<Boolean>zeroFailures()
                .satisfies("output is true", out -> Outcome.ok());
    }

    private static PUnit.TestBuilder<NoFactors, Integer, Boolean> test(Scenario scenario) {
        AtomicInteger seen = new AtomicInteger();
        ServiceContract<NoFactors, Integer, Boolean> contract = scenario == Scenario.FUNCTIONAL_FAILS
                ? contract("functional-fails", holds(), CEILING_MET, seen, PASSING_FIRST)
                : contract("latency-fails", holds(), CEILING_BREACHED, seen, Integer.MAX_VALUE);
        return PUnit.testing(sampling(contract, SAMPLES));
    }

    private static Sampling<NoFactors, Integer, Boolean> sampling(
            ServiceContract<NoFactors, Integer, Boolean> contract, int samples) {
        return Sampling.<NoFactors, Integer, Boolean>builder()
                .serviceContractFactory(f -> contract)
                .inputs(1, 2, 3)
                .samples(samples)
                .build();
    }

    private static void advisory(String setting) {
        if (setting.isEmpty()) {
            System.clearProperty(AssertionEnforcement.PROPERTY);
        } else {
            System.setProperty(AssertionEnforcement.PROPERTY, setting);
        }
    }

    /**
     * The switch table: for each scenario and setting, whether
     * assertPasses, assertContract and assertLatency fail.
     */
    static Stream<Arguments> switchTable() {
        return Stream.of(
                Arguments.of(Scenario.FUNCTIONAL_FAILS, "", true, true, false),
                Arguments.of(Scenario.FUNCTIONAL_FAILS, "functional", false, false, false),
                Arguments.of(Scenario.FUNCTIONAL_FAILS, "latency", true, true, false),
                Arguments.of(Scenario.FUNCTIONAL_FAILS, "functional,latency", false, false, false),
                Arguments.of(Scenario.LATENCY_FAILS, "", true, false, true),
                Arguments.of(Scenario.LATENCY_FAILS, "functional", true, false, true),
                Arguments.of(Scenario.LATENCY_FAILS, "latency", false, false, false),
                Arguments.of(Scenario.LATENCY_FAILS, "functional,latency", false, false, false));
    }

    @ParameterizedTest(name = "{0}, switch ''{1}'': assertPasses fails={2}, assertContract fails={3}, assertLatency fails={4}")
    @MethodSource("switchTable")
    @DisplayName("each terminal fails exactly where its dimension is enforced and not PASS")
    void switchTableHolds(Scenario scenario, String setting,
                          boolean passesFails, boolean contractFails, boolean latencyFails) {
        advisory(setting);

        assertOutcome(scenario, Terminal.PASSES, passesFails);
        assertOutcome(scenario, Terminal.CONTRACT, contractFails);
        assertOutcome(scenario, Terminal.LATENCY, latencyFails);
    }

    private static void assertOutcome(Scenario scenario, Terminal terminal, boolean fails) {
        Throwable thrown = catchThrowable(() -> terminal.call(test(scenario)));
        if (fails) {
            assertThat(thrown).as(terminal + " on " + scenario).isInstanceOf(AssertionFailedError.class);
        } else {
            assertThat(thrown).as(terminal + " on " + scenario).isNull();
        }
    }

    @Nested
    @DisplayName("failure messages")
    class Messages {

        @Test
        @DisplayName("assertContract names the functional dimension and marks latency not asserted")
        void contractMessage() {
            Throwable thrown = catchThrowable(() -> test(Scenario.FUNCTIONAL_FAILS).assertContract());

            assertThat(thrown).isInstanceOf(AssertionFailedError.class);
            assertThat(thrown.getMessage())
                    .startsWith("FAIL (functional dimension, assertContract())")
                    .contains("percentile-latency → PASS (not asserted)");
        }

        @Test
        @DisplayName("assertLatency names the latency dimension and lists an advisory functional verdict as advisory")
        void latencyMessageWithAdvisoryFunctional() {
            advisory("functional");
            AtomicInteger seen = new AtomicInteger();
            var test = PUnit.testing(sampling(
                    contract("both-fail", holds(), CEILING_BREACHED, seen, PASSING_FIRST), SAMPLES));

            Throwable thrown = catchThrowable(test::assertLatency);

            assertThat(thrown).isInstanceOf(AssertionFailedError.class);
            assertThat(thrown.getMessage())
                    .startsWith("FAIL (latency dimension, assertLatency())")
                    .contains("percentile-latency → FAIL:")
                    .contains("→ FAIL (advisory)");
        }
    }

    @Nested
    @DisplayName("a test with no latency constraint")
    class NoLatencyConstraint {

        @ParameterizedTest(name = "switch ''{0}''")
        @EnumSource(value = Setting.class)
        @DisplayName("assertLatency passes on the latency dimension while assertContract judges the functional one")
        void assertLatencyIsANoOpOnTheDimension(Setting setting) {
            advisory(setting.value);
            AtomicInteger seen = new AtomicInteger();
            var failing = contract("no-latency", holds(), LatencyCriterion.none(), seen, PASSING_FIRST);

            Throwable latency = catchThrowable(() -> PUnit.testing(sampling(failing, SAMPLES)).assertLatency());
            seen.set(0);
            Throwable functional = catchThrowable(() -> PUnit.testing(sampling(failing, SAMPLES)).assertContract());

            assertThat(latency).isNull();
            if (setting.functionalAdvisory) {
                assertThat(functional).isNull();
            } else {
                assertThat(functional).isInstanceOf(AssertionFailedError.class);
            }
        }
    }

    /** The settings of the advisory switch. */
    enum Setting {
        UNSET("", false),
        FUNCTIONAL("functional", true),
        LATENCY("latency", false),
        BOTH("functional,latency", true);

        final String value;
        final boolean functionalAdvisory;

        Setting(String value, boolean functionalAdvisory) {
            this.value = value;
            this.functionalAdvisory = functionalAdvisory;
        }
    }

    @Nested
    @DisplayName("a refused configuration")
    class Refusal {

        @ParameterizedTest(name = "switch ''{0}''")
        @EnumSource(value = Setting.class)
        @DisplayName("every terminal throws ConfigurationRefusedException, whatever the switch")
        void everyTerminalRefuses(Setting setting) {
            advisory(setting.value);
            // A 99.99% requirement cannot be demonstrated from 50 samples
            // under VERIFICATION: refused before any sample runs.
            Criteria<Boolean> infeasible = Criteria.meeting().<Boolean>passRate(0.9999)
                    .satisfies("output is true", out -> Outcome.ok());
            for (Terminal terminal : Terminal.values()) {
                var contract = contract("refused", infeasible, CEILING_MET,
                        new AtomicInteger(), Integer.MAX_VALUE);

                Throwable thrown = catchThrowable(() -> terminal.call(
                        PUnit.testing(sampling(contract, 50))));

                assertThat(thrown).as(terminal.name())
                        .isInstanceOf(ConfigurationRefusedException.class);
                assertThat(thrown.getMessage()).contains("COMPLIANCE_INFEASIBLE");
            }
        }
    }

    @Nested
    @DisplayName("the verdict record")
    class VerdictRecord {

        @ParameterizedTest(name = "switch ''{0}''")
        @EnumSource(value = Setting.class)
        @DisplayName("is the same whichever terminal is called")
        void unchangedByTerminal(Setting setting) {
            advisory(setting.value);
            List<ProbabilisticTestVerdict> records = new ArrayList<>();
            VerdictSinkBus.replaceAll(records::add);

            for (Terminal terminal : Terminal.values()) {
                catchThrowable(() -> terminal.call(test(Scenario.FUNCTIONAL_FAILS)));
            }

            assertThat(records).hasSize(3);
            List<List<Object>> decided = records.stream().map(VerdictRecord::decided).toList();
            assertThat(decided.get(1)).isEqualTo(decided.get(0));
            assertThat(decided.get(2)).isEqualTo(decided.get(0));
        }

        /** The decided content of a record: verdicts, modes, rows and decision. */
        private static List<Object> decided(ProbabilisticTestVerdict v) {
            return List.of(
                    v.punitVerdict(),
                    v.verdictReason(),
                    v.decision(),
                    v.functional(),
                    v.perCriterion(),
                    v.latency().map(l -> List.of(l.verdict(), l.mode(), l.successfulSamples())),
                    v.execution().samplesExecuted());
        }
    }
}
