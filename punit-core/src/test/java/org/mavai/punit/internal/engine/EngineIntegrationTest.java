package org.mavai.punit.internal.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.ThresholdOrigin;
import org.mavai.punit.api.spec.TerminationReason;
import org.mavai.punit.api.criterion.Criteria;
import static org.mavai.punit.api.criterion.Criteria.empirical;
import static org.mavai.punit.api.criterion.Criteria.meeting;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.api.spec.EngineResult;
import org.mavai.punit.api.spec.ExperimentResult;
import org.mavai.punit.api.spec.Experiment;
import org.mavai.punit.api.spec.FactorsStepper;
import org.mavai.punit.api.spec.NextFactor;
import org.mavai.punit.api.spec.ProbabilisticTestResult;
import org.mavai.punit.api.spec.ProbabilisticTest;
import org.mavai.punit.api.spec.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Hand-wired end-to-end test for the engine.
 *
 * <p>No JUnit extensions, no annotation scanning, no reflection
 * into PUnit internals. Constructs specs, runs them through
 * {@link Engine}, and asserts the resulting {@link EngineResult}.
 */
@DisplayName("Engine end-to-end integration")
class EngineIntegrationTest {

    record LlmFactors(String model, double temperature) {}

    /** Always returns the length of the input. Used as a deterministic sample. */
    private static class LengthServiceContract implements ServiceContract<LlmFactors, String, Integer> {
        @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input.length());
        }
        @Override public Criteria<Integer> criteria() {
            return meeting().<Integer>zeroFailures();
        }

    }

    @Test
    @DisplayName("Experiment runs end-to-end and reports an artefact outcome")
    void measureSpecRunsEndToEnd() {
        Sampling<LlmFactors, String, Integer> sampling = Sampling
                .<LlmFactors, String, Integer>builder()
                .serviceContractFactory(f -> new LengthServiceContract())
                .inputs("a", "bb", "ccc")
                .samples(9)
                .build();
        Experiment spec = Experiment.measuring(sampling, new LlmFactors("gpt-4o", 0.3)).build();

        EngineResult outcome = new Engine().run(spec);

        assertThat(outcome).isInstanceOf(ExperimentResult.class);
        ExperimentResult art = (ExperimentResult) outcome;
        assertThat(art.destination().toString()).startsWith("specs");
        assertThat(art.message()).contains("samples=9");
    }

    @Test
    @DisplayName("ProbabilisticTest with PassRate.meeting() produces PASS when observed beats threshold")
    void contractualProducesPass() {
        // 100 samples: the declared-threshold rule judges the test
        // sample's Wilson lower bound against 0.95, and a perfect run
        // only supports 0.95 from n = 52 upward (Wilson lower of
        // 100/100 at 95% ≈ 0.974). 30 perfect samples support ≈ 0.917 —
        // not confidence-grade evidence for the declared 0.95.
        Sampling<LlmFactors, Integer, Boolean> sampling = Sampling
                .<LlmFactors, Integer, Boolean>builder()
                .serviceContractFactory(f -> new AlwaysPassesServiceContract())
                .inputs(1, 2, 3)
                .samples(100)
                .build();
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling, new LlmFactors("gpt-4o", 0.3))
                .build();

        EngineResult outcome = new Engine().run(spec);

        assertThat(outcome).isInstanceOf(ProbabilisticTestResult.class);
        var result = (ProbabilisticTestResult) outcome;
        assertThat(result.verdict()).isEqualTo(Verdict.PASS);
        assertThat(result.criterionResults()).hasSize(1);
        var detail = result.criterionResults().get(0).result().detail();
        assertThat(detail).containsEntry("origin", "SLA");
        assertThat(detail).containsEntry("threshold", 0.95);
        assertThat(detail).containsEntry("total", 100);
    }

    @Test
    @DisplayName("ProbabilisticTestResult carries engine-run summary populated from SampleSummary")
    void engineSummaryPopulated() {
        // 100 samples: feasible for the declared 0.95 under the Wilson
        // comparison, and the validity floor for 0.95 (= 100) keeps the
        // guaranteed-success short-circuit from firing before the final
        // sample, so all 100 planned samples execute → COMPLETED.
        Sampling<LlmFactors, Integer, Boolean> sampling = Sampling
                .<LlmFactors, Integer, Boolean>builder()
                .serviceContractFactory(f -> new AlwaysPassesServiceContract())
                .inputs(1, 2, 3)
                .samples(100)
                .build();
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling, new LlmFactors("gpt-4o", 0.3))
                .build();

        var result = (ProbabilisticTestResult) new Engine().run(spec);

        var summary = result.engineSummary();
        assertThat(summary.plannedSamples()).isEqualTo(100);
        assertThat(summary.samplesExecuted()).isEqualTo(100);
        assertThat(summary.successes()).isEqualTo(100);
        assertThat(summary.failures()).isZero();
        assertThat(summary.elapsedMs()).isGreaterThanOrEqualTo(0L);
        assertThat(summary.terminationReason())
                .isEqualTo(TerminationReason.COMPLETED);
        // Contractual mode runs at the framework's default confidence.
        assertThat(summary.confidence()).isEqualTo(0.95);
        assertThat(summary.baselineFilename()).isEmpty();
    }

    @Test
    @DisplayName("ProbabilisticTest with PassRate.meeting() produces FAIL when observed below threshold")
    void contractualProducesFail() {
        Sampling<LlmFactors, Integer, Boolean> sampling = Sampling
                .<LlmFactors, Integer, Boolean>builder()
                .serviceContractFactory(f -> new AlwaysReturnsFailServiceContract())
                .inputs(1, 2, 3)
                .samples(15)
                .build();
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling, new LlmFactors("gpt-4o", 0.3))
                // Opt this test out of statistical early termination —
                // its assertion exercises the verdict for an all-fail
                // run with the full declared sample count, not the
                // failure-inevitable short-circuit.
                .disableEarlyTermination()
                .build();

        EngineResult outcome = new Engine().run(spec);
        var result = (ProbabilisticTestResult) outcome;
        assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
        var detail = result.criterionResults().get(0).result().detail();
        assertThat(detail).containsEntry("successes", 0);
        assertThat(detail).containsEntry("failures", 15);
    }

    @Test
    @DisplayName("PassRate.empirical() yields INCONCLUSIVE under the Stage-3.5 stub baseline resolver")
    void empiricalYieldsInconclusiveUnderStubResolver() {
        Sampling<LlmFactors, Integer, Boolean> sampling = Sampling
                .<LlmFactors, Integer, Boolean>builder()
                .serviceContractFactory(f -> new AlwaysPassesEmpiricalServiceContract())
                .inputs(1, 2, 3)
                .samples(20)
                .build();
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling, new LlmFactors("gpt-4o", 0.3))
                .build();

        EngineResult outcome = new Engine().run(spec);
        var result = (ProbabilisticTestResult) outcome;
        assertThat(result.verdict()).isEqualTo(Verdict.INCONCLUSIVE);
        assertThat(result.criterionResults()).hasSize(1);
        assertThat(result.criterionResults().get(0).result().verdict())
                .isEqualTo(Verdict.INCONCLUSIVE);
        assertThat(result.criterionResults().get(0).result().explanation())
                .contains("baseline");
    }

    @Test
    @DisplayName("A thrown exception is treated as a defect and aborts the run")
    void defectAbortsTheRun() {
        Sampling<LlmFactors, Integer, Boolean> sampling = Sampling
                .<LlmFactors, Integer, Boolean>builder()
                .serviceContractFactory(f -> new DefectiveServiceContract())
                .inputs(1, 2, 3)
                .samples(5)
                .build();
        Experiment spec = Experiment.measuring(sampling, new LlmFactors("gpt-4o", 0.3)).build();

        assertThatThrownBy(() -> new Engine().run(spec))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("simulated defect");
    }

    @Test
    @DisplayName("Round-robin input cycling is deterministic across runs")
    void roundRobinInputCyclingIsDeterministic() {
        List<String> observedA = runAndRecord();
        List<String> observedB = runAndRecord();
        assertThat(observedA).isEqualTo(observedB);
    }

    @Test
    @DisplayName("Engine populates SampleSummary.trials with one entry per sample, in order, with the cycled input")
    void enginePopulatesOrderedTrials() {
        Sampling<LlmFactors, String, Integer> sampling = Sampling
                .<LlmFactors, String, Integer>builder()
                .serviceContractFactory(f -> new LengthServiceContract())
                .inputs("a", "bb", "ccc")
                .samples(7)
                .build();
        Experiment spec = Experiment.measuring(sampling, new LlmFactors("gpt-4o", 0.0)).build();

        new Engine().run(spec);
        var summary = spec.lastSummary().orElseThrow();

        assertThat(summary.trials()).hasSize(7);
        // Round-robin order: a, bb, ccc, a, bb, ccc, a
        assertThat(summary.trials().get(0).input()).isEqualTo("a");
        assertThat(summary.trials().get(1).input()).isEqualTo("bb");
        assertThat(summary.trials().get(2).input()).isEqualTo("ccc");
        assertThat(summary.trials().get(3).input()).isEqualTo("a");
        assertThat(summary.trials().get(6).input()).isEqualTo("a");
        // LengthServiceContract returns input.length()
        assertThat(summary.trials().get(2).outcome().result().getOrThrow()).isEqualTo(3);
    }

    @Test
    @DisplayName("Experiment.exploring(shape).grid(...) runs each factors instance once with the shape's sample count")
    void exploreSpecRunsEachFactorBundle() {
        var observedByModel = new java.util.LinkedHashMap<String, Integer>();
        ServiceContract<LlmFactors, String, Integer> counting = new ServiceContract<>() {
            @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
                return Outcome.ok(0);
            }
            @Override public Criteria<Integer> criteria() {
                return meeting().<Integer>zeroFailures();
            }

        };
        Sampling<LlmFactors, String, Integer> shape = Sampling
                .<LlmFactors, String, Integer>builder()
                .serviceContractFactory(f -> {
                    observedByModel.merge(f.model(), 0, (a, b) -> a);
                    return counting;
                })
                .inputs("a", "b")
                .samples(3)
                .build();
        Experiment spec = Experiment.exploring(shape)
                .grid(
                        new LlmFactors("gpt-4o", 0.0),
                        new LlmFactors("gpt-4o", 0.5),
                        new LlmFactors("claude-3-sonnet", 0.0))
                .build();

        EngineResult outcome = new Engine().run(spec);
        assertThat(outcome).isInstanceOf(ExperimentResult.class);
        assertThat(((ExperimentResult) outcome).message()).contains("configurations=3");
        assertThat(observedByModel).containsOnlyKeys("gpt-4o", "claude-3-sonnet");
    }

    @Test
    @DisplayName("Multi-cell explore: every cell starts its input cursor at zero (no cross-cell bleed)")
    void multiCellExploreStartsEachCellAtInputZero() {
        ServiceContract<LlmFactors, String, Integer> echo = new ServiceContract<>() {
            @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
                return Outcome.ok(0);
            }
            @Override public Criteria<Integer> criteria() {
                return meeting().<Integer>zeroFailures();
            }

        };
        // Five inputs, three samples per cell — fewer samples than
        // inputs so the legacy cumulative cursor would skip the first
        // two inputs of the second cell.
        Sampling<LlmFactors, String, Integer> shape = Sampling
                .<LlmFactors, String, Integer>builder()
                .serviceContractFactory(f -> echo)
                .inputs("i0", "i1", "i2", "i3", "i4")
                .samples(3)
                .build();
        Experiment spec = Experiment.exploring(shape)
                .grid(
                        new LlmFactors("gpt-4o", 0.0),
                        new LlmFactors("claude-3-sonnet", 0.0))
                .build();

        new Engine().run(spec);

        var perCellTrials = spec.perConfigSummaries();
        assertThat(perCellTrials).hasSize(2);
        for (var perCell : perCellTrials) {
            // Every cell must start at input 0 and walk forward.
            // Pre-fix: cell 1 started at input 3 because cell 0
            // consumed 3 samples, so the factor effect was confounded
            // with input difficulty.
            assertThat(perCell.summary().trials())
                    .extracting(t -> t.inputIndex())
                    .containsExactly(0, 1, 2);
        }
    }

    @Test
    @DisplayName("Experiment.optimizing(shape) runs the stepper/scorer loop up to maxIterations")
    void optimizeSpecRunsIterationLoop() {
        ServiceContract<LlmFactors, String, Integer> echo = new ServiceContract<>() {
            @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
                return Outcome.ok(input.length());
            }
            @Override public Criteria<Integer> criteria() {
                return meeting().<Integer>zeroFailures();
            }

        };
        Sampling<LlmFactors, String, Integer> shape = Sampling
                .<LlmFactors, String, Integer>builder()
                .serviceContractFactory(f -> echo)
                .inputs("a")
                .samples(1)
                .build();
        // Stepper that walks temperature up by 0.1 each iteration, capping at 1.0.
        FactorsStepper<LlmFactors> stepper = (current, history) ->
                current.temperature() >= 0.95
                        ? NextFactor.stop()
                        : NextFactor.next(new LlmFactors(current.model(), current.temperature() + 0.1));
        Experiment spec = Experiment.optimizing(shape)
                .initialFactors(new LlmFactors("gpt-4o", 0.0))
                .stepper(stepper)
                .maximize(s -> 1.0 / (1.0 + s.failures()))
                .maxIterations(5)
                .noImprovementWindow(10)
                .build();

        new Engine().run(spec);
        assertThat(spec.history()).hasSize(5);
    }

    @Test
    @DisplayName("disableEarlyTermination() runs to maxIterations even when scores are flat")
    void disableEarlyTerminationRunsToMaxIterations() {
        ServiceContract<LlmFactors, String, Integer> echo = new ServiceContract<>() {
            @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
                return Outcome.ok(input.length());
            }
            @Override public Criteria<Integer> criteria() {
                return meeting().<Integer>zeroFailures();
            }

        };
        Sampling<LlmFactors, String, Integer> shape = Sampling
                .<LlmFactors, String, Integer>builder()
                .serviceContractFactory(f -> echo)
                .inputs("a")
                .samples(1)
                .build();
        FactorsStepper<LlmFactors> alwaysAdvance = (current, history) ->
                NextFactor.next(new LlmFactors(current.model(), current.temperature() + 0.01));
        // Constant scorer: every iteration ties the previous, so the
        // default no-improvement window would terminate the run early.
        Experiment spec = Experiment.optimizing(shape)
                .initialFactors(new LlmFactors("gpt-4o", 0.0))
                .stepper(alwaysAdvance)
                .maximize(s -> 0.5)
                .maxIterations(8)
                .disableEarlyTermination()
                .build();

        new Engine().run(spec);
        assertThat(spec.history()).hasSize(8);
    }

    @Test
    @DisplayName("NextFactor.stop() ends the optimize loop after the iteration that returned it — no final-iteration sample")
    void nextFactorStopEndsRunCleanly() {
        ServiceContract<LlmFactors, String, Integer> echo = new ServiceContract<>() {
            @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
                return Outcome.ok(input.length());
            }
            @Override public Criteria<Integer> criteria() {
                return meeting().<Integer>zeroFailures();
            }

        };
        Sampling<LlmFactors, String, Integer> shape = Sampling
                .<LlmFactors, String, Integer>builder()
                .serviceContractFactory(f -> echo)
                .inputs("a")
                .samples(1)
                .build();
        // Stop after exactly 3 iterations so we can pin the count precisely.
        FactorsStepper<LlmFactors> stopAfterThree = (current, history) ->
                history.size() >= 3
                        ? NextFactor.stop()
                        : NextFactor.next(new LlmFactors(current.model(), current.temperature() + 0.1));

        Experiment spec = Experiment.optimizing(shape)
                .initialFactors(new LlmFactors("gpt-4o", 0.0))
                .stepper(stopAfterThree)
                .maximize(s -> 0.5)
                .maxIterations(20)             // headroom — Stop must take effect first
                .disableEarlyTermination()     // rule out the no-improvement window as the cause
                .build();

        new Engine().run(spec);
        // Initial factors + 2 stepper-produced advances = 3 iterations, then Stop.
        // No 4th iteration is sampled even though maxIterations(20) would otherwise allow it.
        assertThat(spec.history()).hasSize(3);
    }

    @Test
    @DisplayName("ProbabilisticTest threads its declared intent through to the result")
    void intentThreadsThroughToResult() {
        Sampling<LlmFactors, Integer, Boolean> sampling = Sampling
                .<LlmFactors, Integer, Boolean>builder()
                .serviceContractFactory(f -> new AlwaysPassesServiceContract())
                .inputs(1, 2, 3)
                .samples(10)
                .build();
        ProbabilisticTest verification = ProbabilisticTest
                .testing(sampling, new LlmFactors("gpt-4o", 0.0))
                .build();
        ProbabilisticTest smoke = ProbabilisticTest
                .testing(sampling, new LlmFactors("gpt-4o", 0.0))
                .intent(TestIntent.SMOKE)
                .build();

        var verResult = (ProbabilisticTestResult) new Engine().run(verification);
        var smkResult = (ProbabilisticTestResult) new Engine().run(smoke);

        assertThat(verResult.intent()).isEqualTo(TestIntent.VERIFICATION);
        assertThat(smkResult.intent()).isEqualTo(TestIntent.SMOKE);
    }

    private List<String> runAndRecord() {
        List<String> observed = new ArrayList<>();
        ServiceContract<LlmFactors, String, Integer> recording = new ServiceContract<>() {
            @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
                observed.add(input);
                return Outcome.ok(0);
            }
            @Override public Criteria<Integer> criteria() {
                return meeting().<Integer>zeroFailures();
            }
        };
        Sampling<LlmFactors, String, Integer> sampling = Sampling
                .<LlmFactors, String, Integer>builder()
                .serviceContractFactory(f -> recording)
                .inputs("x", "y", "z")
                .samples(7)
                .build();
        Experiment spec = Experiment.measuring(sampling, new LlmFactors("gpt-4o", 0.3)).build();
        new Engine().run(spec);
        return observed;
    }

    // ── Test doubles ────────────────────────────────────────────────

    private static class AlwaysPassesServiceContract implements ServiceContract<LlmFactors, Integer, Boolean> {
        @Override public Criteria<Boolean> criteria() {
            return meeting().<Boolean>passRate(0.95)
                    .contractRef(ThresholdOrigin.SLA, "test-contract-ref");
        }
        @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
            return Outcome.ok(Boolean.TRUE);
        }
    }

    /** Empirical-posture sibling of {@link AlwaysPassesServiceContract} for the empirical-path test. */
    private static class AlwaysPassesEmpiricalServiceContract implements ServiceContract<LlmFactors, Integer, Boolean> {
        @Override public Criteria<Boolean> criteria() {
            return empirical().passRate();
        }
        @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
            return Outcome.ok(Boolean.TRUE);
        }
    }

    /** Returns business-level Fail outcomes — the "contract didn't hold" signal. */
    private static class AlwaysReturnsFailServiceContract implements ServiceContract<LlmFactors, Integer, Boolean> {
        @Override public Criteria<Boolean> criteria() {
            return meeting().passRate(0.95);
        }
        @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
            return Outcome.fail("contract_violation", "scripted failure for input " + input);
        }
    }

    /** Throws — a defect, not a business-level failure. The engine aborts. */
    private static class DefectiveServiceContract implements ServiceContract<LlmFactors, Integer, Boolean> {
        @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
            throw new IllegalStateException("simulated defect — this should abort the run");
        }
        @Override public Criteria<Boolean> criteria() {
            return meeting().<Boolean>zeroFailures();
        }
    }
}
