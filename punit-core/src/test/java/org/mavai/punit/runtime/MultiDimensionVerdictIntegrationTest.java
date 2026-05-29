package org.mavai.punit.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.criterion.Criteria;
import static org.mavai.punit.api.criterion.Criteria.meeting;
import org.mavai.punit.api.LatencySpec;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.ThresholdOrigin;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.api.spec.PercentileLatency;
import org.mavai.punit.api.spec.ProbabilisticTest;
import org.mavai.punit.api.spec.ProbabilisticTestResult;
import org.mavai.punit.internal.engine.Engine;
import org.mavai.punit.internal.runtime.VerdictAdapter;
import org.mavai.punit.verdict.PUnitVerdict;
import org.mavai.punit.verdict.ProbabilisticTestVerdict;
import org.mavai.punit.verdict.RunMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Live end-to-end test for the composite-verdict conjunction across
 * functional and temporal dimensions. Drives the engine with two
 * criteria — pass-rate for the functional dimension,
 * {@link PercentileLatency} for the temporal dimension — and asserts
 * that the composite {@code punitVerdict} is the conjunction:
 * {@code PASS} only if both dimensions pass; {@code FAIL} if either
 * fails.
 *
 * <p>Each scenario controls its inputs deterministically: the functional
 * outcome is scripted via the service contract's postcondition, and the latency
 * dimension is gated by asymmetric thresholds — trivially-passing
 * ({@code p95Millis = 10_000_000}) or trivially-failing ({@code p95Millis
 * = 0}) — so the dimensional verdict does not depend on wall-clock
 * variability.
 */
@DisplayName("composite verdict is the conjunction of functional and latency dimensions")
class MultiDimensionVerdictIntegrationTest {

    record F(String label) {}

    /**
     * Every sample sleeps {@value #SLEEP_MS}ms before returning, so
     * observed latency is reliably above the trivially-failing 1ms
     * threshold used in the latency-fail scenario.
     */
    private static final long SLEEP_MS = 5L;

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /** Always-pass: every sample's postcondition succeeds. */
    private static class AlwaysPassServiceContract implements ServiceContract<F, String, Integer> {
        @Override public String id() { return "always-pass"; }
        @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
            sleep(SLEEP_MS);
            return Outcome.ok(input.length());
        }
        @Override public Criteria<Integer> criteria() {
            return meeting().<Integer>passRate(FUNCTIONAL_THRESHOLD)
                    .satisfies("non-null length", n -> Outcome.ok());
        }
    }

    /**
     * Mixed-outcome: half the inputs pass, half fail — observed pass
     * rate around 0.5, below any reasonable functional threshold but
     * with enough passing samples that the latency dimension is
     * still populated.
     */
    private static class MixedOutcomeServiceContract implements ServiceContract<F, String, Integer> {
        @Override public String id() { return "mixed-outcome"; }
        @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
            sleep(SLEEP_MS);
            return Outcome.ok(input.length());
        }
        @Override public Criteria<Integer> criteria() {
            return meeting().<Integer>passRate(FUNCTIONAL_THRESHOLD)
                    .satisfies("length is even", n ->
                            n % 2 == 0 ? Outcome.ok() : Outcome.fail("odd-length", "n=" + n));
        }
    }

    /** Six inputs, three even-length (pass), three odd-length (fail). */
    private static final List<String> MIXED_INPUTS =
            List.of("ab", "cdef", "ghij", "x", "yz1", "pqrst");

    /** Even-length inputs only — pair with AlwaysPassServiceContract. */
    private static final List<String> EVEN_LENGTH_INPUTS =
            List.of("ab", "cdef", "ghij", "klmn", "opqr", "stuv");

    /** Trivially passes any non-negative wall-clock latency. */
    private static final LatencySpec TRIVIAL_PASS_LATENCY =
            LatencySpec.builder().p95Millis(10_000_000L).build();

    /**
     * Trivially fails: the 1ms threshold is below every sample's
     * {@value #SLEEP_MS}ms scripted invocation time.
     */
    private static final LatencySpec TRIVIAL_FAIL_LATENCY =
            LatencySpec.builder().p95Millis(1L).build();

    private static final double FUNCTIONAL_THRESHOLD = 0.95;

    private static Sampling<F, String, Integer> sampling(
            ServiceContract<F, String, Integer> serviceContract, List<String> inputs) {
        return Sampling.<F, String, Integer>builder()
                .serviceContractFactory(f -> serviceContract)
                .inputs(inputs)
                .samples(60)
                .build();
    }

    private static ProbabilisticTestVerdict runAndAdapt(ProbabilisticTest spec) {
        var result = (ProbabilisticTestResult) new Engine().run(spec);
        return VerdictAdapter.adapt(result,
                RunMetadata.of("MultiDimensionVerdictIntegrationTest", "scenario"));
    }

    @Test
    @DisplayName("both dimensions pass — composite PASS")
    void bothDimensionsPass() {
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling(new AlwaysPassServiceContract(), EVEN_LENGTH_INPUTS), new F("only"))
                .criterion(PercentileLatency.<Integer>meeting(TRIVIAL_PASS_LATENCY, ThresholdOrigin.SLA))
                .build();

        ProbabilisticTestVerdict verdict = runAndAdapt(spec);

        assertThat(verdict.punitVerdict()).isEqualTo(PUnitVerdict.PASS);
        assertThat(verdict.functional()).isPresent();
        assertThat(verdict.functional().get().passRate())
                .as("functional dimension passes at the declared threshold")
                .isGreaterThanOrEqualTo(FUNCTIONAL_THRESHOLD);
        assertThat(verdict.latency()).isPresent();
    }

    @Test
    @DisplayName("functional fails while latency passes — composite FAIL")
    void functionalFailsLatencyPasses() {
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling(new MixedOutcomeServiceContract(), MIXED_INPUTS), new F("only"))
                .criterion(PercentileLatency.<Integer>meeting(TRIVIAL_PASS_LATENCY, ThresholdOrigin.SLA))
                .build();

        ProbabilisticTestVerdict verdict = runAndAdapt(spec);

        assertThat(verdict.punitVerdict()).isEqualTo(PUnitVerdict.FAIL);
        assertThat(verdict.functional()).isPresent();
        assertThat(verdict.functional().get().passRate())
                .as("functional dimension fails its threshold")
                .isLessThan(FUNCTIONAL_THRESHOLD);
        // The mixed-outcome workload still yields ≥ 1 passing sample,
        // so the descriptive latency dimension is populated. Its
        // presence here demonstrates that the FAIL is driven by the
        // functional dimension, not by missing latency data.
        assertThat(verdict.latency()).isPresent();
    }

    @Test
    @DisplayName("latency fails while functional passes — composite FAIL")
    void functionalPassesLatencyFails() {
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling(new AlwaysPassServiceContract(), EVEN_LENGTH_INPUTS), new F("only"))
                .criterion(PercentileLatency.<Integer>meeting(TRIVIAL_FAIL_LATENCY, ThresholdOrigin.SLA))
                .build();

        ProbabilisticTestVerdict verdict = runAndAdapt(spec);

        assertThat(verdict.punitVerdict()).isEqualTo(PUnitVerdict.FAIL);
        assertThat(verdict.functional()).isPresent();
        assertThat(verdict.functional().get().passRate())
                .as("functional dimension passes — latency drove the composite FAIL")
                .isGreaterThanOrEqualTo(FUNCTIONAL_THRESHOLD);
        assertThat(verdict.latency()).isPresent();
        assertThat(verdict.latency().get().p95Ms())
                .as("observed p95 exceeds the trivially-failing 0ms threshold")
                .isGreaterThan(0L);
    }
}
