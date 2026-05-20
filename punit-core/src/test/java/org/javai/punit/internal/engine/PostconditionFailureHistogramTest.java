package org.javai.punit.internal.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.javai.outcome.Outcome;
import org.javai.punit.api.spec.FactorsStepper;
import org.javai.punit.api.spec.NextFactor;
import org.javai.punit.api.spec.ProbabilisticTest;
import org.javai.punit.api.spec.ProbabilisticTestResult;
import org.javai.punit.api.criterion.Criteria;
import static org.javai.punit.api.criterion.Criteria.meeting;
import org.javai.punit.api.Sampling;
import org.javai.punit.api.TokenTracker;
import org.javai.punit.api.ServiceContract;
import org.javai.punit.api.spec.Experiment;
import org.javai.punit.api.spec.ExperimentResult;
import org.javai.punit.api.spec.FailureCount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the engine's per-postcondition failure histogram behaviour:
 * the {@code failuresByPostcondition()} map on {@code SampleSummary}
 * accumulates counts and bounded exemplars by clause description as
 * samples roll through.
 */
@DisplayName("Engine — failuresByPostcondition histogram")
class PostconditionFailureHistogramTest {

    record Factors() {}

    /**
     * Service contract that always returns the input's value. Two postconditions:
     * one that always fails ("alwaysFails"), one that fails when input
     * is even ("evenFails").
     */
    private static class TwoClauseServiceContract implements ServiceContract<Factors, Integer, Integer> {
        @Override public Outcome<Integer> invoke(Integer input, TokenTracker tracker) {
            return Outcome.ok(input);
        }
        @Override public Criteria<Integer> criteria() {
            return meeting().<Integer>passRate(0.5)
                    .satisfies("alwaysFails", v ->
                            Outcome.fail("alwaysFails-mode", "input was " + v))
                    .satisfies("evenFails", v -> v % 2 == 0
                            ? Outcome.fail("even-mode", "input " + v + " is even")
                            : Outcome.ok());
        }
    }

    @Test
    @DisplayName("alwaysFails clause accumulates count == samples; evenFails counts only even inputs")
    void histogramAccumulates() {
        Sampling<Factors, Integer, Integer> sampling = Sampling
                .<Factors, Integer, Integer>builder()
                .serviceContractFactory(f -> new TwoClauseServiceContract())
                .inputs(1, 2, 3, 4, 5, 6)
                .samples(6)
                .build();
        Experiment spec = Experiment.measuring(sampling, new Factors()).build();

        new Engine().run(spec);
        var summary = spec.lastSummary().orElseThrow();

        var hist = summary.failuresByPostcondition();
        assertThat(hist).containsKeys("alwaysFails", "evenFails");
        assertThat(hist.get("alwaysFails").count()).isEqualTo(6);
        assertThat(hist.get("evenFails").count()).isEqualTo(3);   // 2, 4, 6
    }

    @Test
    @DisplayName("exemplars are capped at 3 per clause; counts continue past the cap")
    void exemplarsCapAtThree() {
        Sampling<Factors, Integer, Integer> sampling = Sampling
                .<Factors, Integer, Integer>builder()
                .serviceContractFactory(f -> new TwoClauseServiceContract())
                .inputs(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
                .samples(10)
                .build();
        Experiment spec = Experiment.measuring(sampling, new Factors()).build();

        new Engine().run(spec);
        var summary = spec.lastSummary().orElseThrow();

        FailureCount alwaysFails = summary.failuresByPostcondition().get("alwaysFails");
        assertThat(alwaysFails.count()).isEqualTo(10);
        assertThat(alwaysFails.exemplars()).hasSize(3);
        // Exemplars retained are the first three (cycled-input order: 1, 2, 3)
        assertThat(alwaysFails.exemplars().get(0).input()).isEqualTo("1");
        assertThat(alwaysFails.exemplars().get(0).reason()).contains("input was 1");
    }

    @Test
    @DisplayName("a service contract with no postconditions produces an empty histogram")
    void emptyHistogramWhenNoClauses() {
        ServiceContract<Factors, Integer, Integer> noClauses = new ServiceContract<>() {
            @Override public Outcome<Integer> invoke(Integer i, TokenTracker t) { return Outcome.ok(i); }
            @Override public Criteria<Integer> criteria() {
                return meeting().<Integer>zeroFailures();
            }
        };
        Sampling<Factors, Integer, Integer> sampling = Sampling
                .<Factors, Integer, Integer>builder()
                .serviceContractFactory(f -> noClauses)
                .inputs(1, 2, 3)
                .samples(3)
                .build();
        Experiment spec = Experiment.measuring(sampling, new Factors()).build();

        new Engine().run(spec);
        var summary = spec.lastSummary().orElseThrow();

        assertThat(summary.failuresByPostcondition()).isEmpty();
    }

    @Test
    @DisplayName("apply-level Outcome.Fail does not contribute to the postcondition histogram")
    void applyFailDoesNotAppearInHistogram() {
        ServiceContract<Factors, Integer, Integer> applyFail = new ServiceContract<>() {
            @Override public Outcome<Integer> invoke(Integer i, TokenTracker t) {
                return Outcome.fail("upstream-error", "always fails at apply");
            }
            @Override public Criteria<Integer> criteria() {
                return meeting().<Integer>zeroFailures()
                        .satisfies("would-have-checked", v -> Outcome.ok());
            }
        };
        Sampling<Factors, Integer, Integer> sampling = Sampling
                .<Factors, Integer, Integer>builder()
                .serviceContractFactory(f -> applyFail)
                .inputs(1)
                .samples(5)
                .build();
        Experiment spec = Experiment.measuring(sampling, new Factors()).build();

        new Engine().run(spec);
        var summary = spec.lastSummary().orElseThrow();

        // No postcondition was evaluated because invoke failed every sample.
        assertThat(summary.failuresByPostcondition()).isEmpty();
        assertThat(summary.failures()).isEqualTo(5);
    }

    @Test
    @DisplayName("ProbabilisticTestResult carries the same histogram as its summary")
    void resultMirrorsSummary() {
        Sampling<Factors, Integer, Integer> sampling = Sampling
                .<Factors, Integer, Integer>builder()
                .serviceContractFactory(f -> new TwoClauseServiceContract())
                .inputs(1, 2, 3, 4)
                .samples(4)
                .build();
        ProbabilisticTest spec =
                ProbabilisticTest
                        .testing(sampling, new Factors())
                        // Opt out of statistical early termination —
                        // this test exercises the histogram pipeline
                        // across all four declared samples, not the
                        // failure-inevitable short-circuit.
                        .disableEarlyTermination()
                        .build();

        var result = (ProbabilisticTestResult) new Engine().run(spec);

        assertThat(result.failuresByPostcondition()).containsKeys("alwaysFails", "evenFails");
        assertThat(result.failuresByPostcondition().get("alwaysFails").count()).isEqualTo(4);
        assertThat(result.failuresByPostcondition().get("evenFails").count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Optimize stepper sees the histogram on each IterationResult")
    void optimizeIterationCarriesHistogram() {
        Sampling<Factors, Integer, Integer> sampling = Sampling
                .<Factors, Integer, Integer>builder()
                .serviceContractFactory(f -> new TwoClauseServiceContract())
                .inputs(1, 2)
                .samples(2)
                .build();

        // Minimal stepper: produce one more candidate, then stop.
        var seenHistograms = new java.util.ArrayList<java.util.Map<String, FailureCount>>();
        FactorsStepper<Factors> stepper =
                (current, history) -> {
                    history.forEach(h -> seenHistograms.add(h.failuresByPostcondition()));
                    return history.size() >= 2 ? NextFactor.stop() : NextFactor.next(current);
                };

        Experiment spec = Experiment.optimizing(sampling)
                .initialFactors(new Factors())
                .stepper(stepper)
                .maximize(s -> 1.0 / (1.0 + s.failures()))
                .maxIterations(2)
                .noImprovementWindow(10)
                .experimentId("hist-test")
                .build();

        new Engine().run(spec);

        // The stepper saw at least one IterationResult with a populated histogram.
        assertThat(seenHistograms).isNotEmpty();
        var lastSeen = seenHistograms.get(seenHistograms.size() - 1);
        assertThat(lastSeen).containsKeys("alwaysFails", "evenFails");
        assertThat(lastSeen.get("alwaysFails").count()).isEqualTo(2);
        assertThat(lastSeen.get("evenFails").count()).isEqualTo(1);   // input 2
    }

    record GridFactors(String label) {}

    /** A two-clause service contract keyed on GridFactors so the explore test
     *  can use distinct grid points. */
    private static class GridTwoClauseServiceContract implements ServiceContract<GridFactors, Integer, Integer> {
        @Override public Outcome<Integer> invoke(Integer input, TokenTracker tracker) {
            return Outcome.ok(input);
        }
        @Override public Criteria<Integer> criteria() {
            return meeting().<Integer>zeroFailures()
                    .satisfies("alwaysFails", v -> Outcome.fail("alwaysFails", "input " + v));
        }
    }

    @Test
    @DisplayName("Explore artefact message renders per-config failure breakdown")
    void exploreArtefactSurfacesPerConfigHistogram() {
        var sampling = Sampling
                .<GridFactors, Integer, Integer>builder()
                .serviceContractFactory(f -> new GridTwoClauseServiceContract())
                .inputs(1)
                .samples(1)
                .build();

        Experiment spec = Experiment.exploring(sampling)
                .grid(new GridFactors("alpha"), new GridFactors("beta"))
                .build();

        var result = (ExperimentResult) new Engine().run(spec);

        assertThat(result.message())
                .contains("configurations=2")
                .contains("Failure breakdown by configuration:")
                .contains("config=GridFactors[label=alpha]")
                .contains("config=GridFactors[label=beta]")
                .contains("alwaysFails: 1 failure");
    }

    // Suppress the (unused) ExperimentResult import — it's referenced by the test bodies above
    @SuppressWarnings("unused") private static final Class<?> KEEP_IMPORT = ExperimentResult.class;
    @SuppressWarnings("unused") private static final Class<?> KEEP_IMPORT_LIST = List.class;
}
