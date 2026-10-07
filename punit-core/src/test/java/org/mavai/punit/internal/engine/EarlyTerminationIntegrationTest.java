package org.mavai.punit.internal.engine;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mavai.punit.api.criterion.Criteria.empirical;
import static org.mavai.punit.api.criterion.Criteria.meeting;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.criterion.Criteria;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.api.spec.EngineResult;
import org.mavai.punit.api.spec.Experiment;
import org.mavai.punit.api.spec.ExperimentResult;
import org.mavai.punit.api.spec.ProbabilisticTest;
import org.mavai.punit.api.spec.ProbabilisticTestResult;
import org.mavai.punit.api.spec.TerminationReason;
import org.mavai.punit.api.spec.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Engine-level integration tests for the statistical early-termination
 * mechanism: failure-inevitable and success-guaranteed short-circuits
 * on a probabilistic test's sample loop.
 *
 * <p>The short-circuit fires on a contractual pass-rate criterion
 * with a {@code .meeting(rate, origin)} posture only.
 * Specs that have no up-front threshold — measure / explore / optimize
 * runs, empirical-mode probabilistic tests, and probabilistic tests
 * whose author called {@code disableEarlyTermination()} — run every
 * planned sample. Each variant has a coverage case below.
 */
@DisplayName("Statistical early termination")
class EarlyTerminationIntegrationTest {

    record Factors() {}

    /** Returns Outcome.ok every sample. Contract posture: meeting(0.5, SLA). */
    private static class AlwaysPass implements ServiceContract<Factors, Integer, Boolean> {
        @Override public Criteria<Boolean> criteria() {
            return meeting().passRate(0.5);
        }
        @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
            return Outcome.ok(Boolean.TRUE);
        }
    }

    /** Always-pass variant whose contract posture is meeting(0.05, SLA). */
    private static class AlwaysPassLowThreshold implements ServiceContract<Factors, Integer, Boolean> {
        @Override public Criteria<Boolean> criteria() {
            return meeting().passRate(0.05);
        }
        @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
            return Outcome.ok(Boolean.TRUE);
        }
    }

    /** Always-pass variant whose contract posture is empirical. */
    private static class AlwaysPassEmpirical implements ServiceContract<Factors, Integer, Boolean> {
        @Override public Criteria<Boolean> criteria() {
            return empirical().passRate();
        }
        @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
            return Outcome.ok(Boolean.TRUE);
        }
    }

    /** Returns Outcome.fail every sample — a clean failure-inevitable shape. */
    private static class AlwaysFail implements ServiceContract<Factors, Integer, Boolean> {
        @Override public Criteria<Boolean> criteria() {
            return meeting().passRate(0.95);
        }
        @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
            return Outcome.fail("contract_violation", "scripted failure");
        }
    }

    /**
     * Fails the first {@code failsFirst} invocations, then passes
     * indefinitely. The counter is per-instance, so one instance per
     * spec run is the intended use.
     */
    private static class FailsThenPasses implements ServiceContract<Factors, Integer, Boolean> {
        private final int failsFirst;
        private int seen = 0;
        FailsThenPasses(int failsFirst) { this.failsFirst = failsFirst; }
        @Override public Criteria<Boolean> criteria() {
            return meeting().passRate(0.5);
        }
        @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
            return ++seen <= failsFirst
                    ? Outcome.fail("contract_violation", "scripted failure")
                    : Outcome.ok(Boolean.TRUE);
        }
    }

    /**
     * Passes every other sample against a 0.95 requirement, with a
     * latency ceiling no sample can breach.
     */
    private static class AlternatingWithLatency implements ServiceContract<Factors, Integer, Boolean> {
        private int seen = 0;
        @Override public Criteria<Boolean> criteria() {
            return meeting().passRate(0.95);
        }
        @Override public org.mavai.punit.api.criterion.LatencyCriterion latency() {
            return meeting().atMost(org.mavai.punit.api.PercentileKey.P50, java.time.Duration.ofSeconds(10));
        }
        @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
            return ++seen % 2 == 0
                    ? Outcome.fail("contract_violation", "scripted failure")
                    : Outcome.ok(Boolean.TRUE);
        }
    }

    private static Sampling<Factors, Integer, Boolean> sampling(
            java.util.function.Function<Factors, ServiceContract<Factors, Integer, Boolean>> factory,
            int samples) {
        return Sampling.<Factors, Integer, Boolean>builder()
                .serviceContractFactory(factory)
                .inputs(1, 2, 3)
                .samples(samples)
                .build();
    }

    // ── Failure-inevitable short-circuit ─────────────────────────────

    @Test
    @DisplayName("failure-inevitable: all-fail with 0.95 threshold terminates at sample 2 of 100")
    void failureInevitableTerminatesEarly() {
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling(f -> new AlwaysFail(), 100), new Factors())
                .build();

        var result = (ProbabilisticTestResult) new Engine().run(spec);

        // requiredSuccesses = k_min of the exact binomial test of 0.95 at
        // n=100, alpha 0.05 → 99. After sample 2 the failure count is 2 →
        // max-possible-successes = 98 < 99 → fire.
        assertThat(result.engineSummary().samplesExecuted()).isEqualTo(2);
        assertThat(result.engineSummary().terminationReason())
                .isEqualTo(TerminationReason.IMPOSSIBILITY);
        assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
    }

    @Test
    @DisplayName("failure-inevitable with a latency ceiling: the run stops early, the test fails either way")
    void failureInevitableWithLatencyStops() {
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling(f -> new AlternatingWithLatency(), 100), new Factors())
                .build();

        var result = (ProbabilisticTestResult) new Engine().run(spec);

        assertThat(result.engineSummary().terminationReason())
                .isEqualTo(TerminationReason.IMPOSSIBILITY);
        assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
    }

    @Test
    @DisplayName("functional advisory: an inevitable functional failure does not cut short the latency samples")
    void functionalAdvisoryRunsEverySample() {
        System.setProperty(org.mavai.punit.api.spec.AssertionEnforcement.PROPERTY, "functional");
        ProbabilisticTest spec;
        try {
            spec = ProbabilisticTest
                    .testing(sampling(f -> new AlternatingWithLatency(), 100), new Factors())
                    .build();
        } finally {
            System.clearProperty(org.mavai.punit.api.spec.AssertionEnforcement.PROPERTY);
        }

        var result = (ProbabilisticTestResult) new Engine().run(spec);

        assertThat(result.engineSummary().samplesExecuted()).isEqualTo(100);
        assertThat(result.engineSummary().terminationReason()).isEqualTo(TerminationReason.COMPLETED);
        var composition = result.composition().orElseThrow();
        assertThat(composition.rateVerdict()).contains(Verdict.FAIL);
        assertThat(composition.latencyVerdict()).contains(Verdict.PASS);
        assertThat(result.verdict()).isEqualTo(Verdict.PASS);
    }

    @Test
    @DisplayName("failure-inevitable: mixed mid-run failures that remain recoverable do not terminate")
    void failureInevitableNoFireWhenRecoverable() {
        // threshold 0.5, samples 20 → required = k_min = 15 (the exact
        // binomial test of 0.5 at n=20, alpha 0.05). Five failures up front
        // leave max-possible-successes at 15 ≥ 15, so the
        // failure-inevitable shortcut never fires; the 15th success only
        // lands on the final sample, so the success-guaranteed shortcut
        // (which needs remaining > 0) never fires either, and the run
        // completes. Verdict is PASS (15 of 20 reaches k_min).
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling(f -> new FailsThenPasses(5), 20), new Factors())
                .build();

        var result = (ProbabilisticTestResult) new Engine().run(spec);

        assertThat(result.engineSummary().samplesExecuted()).isEqualTo(20);
        assertThat(result.engineSummary().terminationReason())
                .isEqualTo(TerminationReason.COMPLETED);
        assertThat(result.verdict()).isEqualTo(Verdict.PASS);
    }

    // ── Success-guaranteed short-circuit ─────────────────────────────

    @Test
    @DisplayName("success-guaranteed: all-pass at 0.5 threshold terminates at k_min")
    void successGuaranteedTerminatesAtThresholdCross() {
        // threshold 0.5, samples 100 → required = k_min = 59 (the exact
        // binomial test of 0.5 at n=100, alpha 0.05). With all-pass,
        // successes reach it at sample 59 → fire.
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling(f -> new AlwaysPass(), 100), new Factors())
                .build();

        var result = (ProbabilisticTestResult) new Engine().run(spec);

        assertThat(result.engineSummary().samplesExecuted()).isEqualTo(59);
        assertThat(result.engineSummary().terminationReason())
                .isEqualTo(TerminationReason.SUCCESS_GUARANTEED);
        assertThat(result.verdict()).isEqualTo(Verdict.PASS);
    }

    @Test
    @DisplayName("success-guaranteed: fires as soon as k_min is reached — the exact rule needs no validity floor")
    void successGuaranteedFiresAtKMin() {
        // threshold 0.05, samples 200 → required = k_min = 16 (the exact
        // binomial test of 0.05 at n=200, alpha 0.05). The exact rule is
        // valid at every size, so with all-pass the run stops at sample 16.
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling(f -> new AlwaysPassLowThreshold(), 200), new Factors())
                .build();

        var result = (ProbabilisticTestResult) new Engine().run(spec);

        assertThat(result.engineSummary().samplesExecuted()).isEqualTo(16);
        assertThat(result.engineSummary().terminationReason())
                .isEqualTo(TerminationReason.SUCCESS_GUARANTEED);
        assertThat(result.verdict()).isEqualTo(Verdict.PASS);
    }

    // ── Specs that do not engage the short-circuit ───────────────────

    @Test
    @DisplayName("non-regression: measure experiment runs every planned sample regardless of failure rate")
    void measureSpecRunsToCompletion() {
        Experiment spec = Experiment
                .measuring(sampling(f -> new AlwaysFail(), 50), new Factors())
                .build();

        EngineResult outcome = new Engine().run(spec);

        assertThat(outcome).isInstanceOf(ExperimentResult.class);
        // Measure specs don't publish an early-termination context, so the
        // sample loop runs the full count even with all-fail outcomes.
        // The artefact message records the planned sample count.
        ExperimentResult artefact = (ExperimentResult) outcome;
        assertThat(artefact.message()).contains("samples=50");
    }

    @Test
    @DisplayName("non-regression: empirical-mode probabilistic test runs to completion")
    void empiricalModeRunsToCompletion() {
        // The empirical pass-rate criterion derives its threshold from
        // a baseline at evaluate time, so no up-front threshold is
        // available to the engine; the spec returns Optional.empty()
        // from earlyTermination() and the run is not short-circuited.
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling(f -> new AlwaysPassEmpirical(), 30), new Factors())
                .build();

        var result = (ProbabilisticTestResult) new Engine().run(spec);

        assertThat(result.engineSummary().samplesExecuted()).isEqualTo(30);
        assertThat(result.engineSummary().terminationReason())
                .isEqualTo(TerminationReason.COMPLETED);
    }

    @Test
    @DisplayName("disableEarlyTermination(): a near-impossible run reaches the planned sample count")
    void disableEarlyTerminationDefeatsFailureInevitable() {
        // 59 samples: the fewest from which the declared 0.95 can be
        // demonstrated, so the configuration is not refused.
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling(f -> new AlwaysFail(), 59), new Factors())
                .disableEarlyTermination()
                .build();

        var result = (ProbabilisticTestResult) new Engine().run(spec);

        assertThat(result.engineSummary().samplesExecuted()).isEqualTo(59);
        assertThat(result.engineSummary().terminationReason())
                .isEqualTo(TerminationReason.COMPLETED);
        assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
    }

    @Test
    @DisplayName("disableEarlyTermination(): a near-guaranteed run reaches the planned sample count")
    void disableEarlyTerminationDefeatsSuccessGuaranteed() {
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling(f -> new AlwaysPass(), 100), new Factors())
                .disableEarlyTermination()
                .build();

        var result = (ProbabilisticTestResult) new Engine().run(spec);

        assertThat(result.engineSummary().samplesExecuted()).isEqualTo(100);
        assertThat(result.engineSummary().terminationReason())
                .isEqualTo(TerminationReason.COMPLETED);
        assertThat(result.verdict()).isEqualTo(Verdict.PASS);
    }
}
