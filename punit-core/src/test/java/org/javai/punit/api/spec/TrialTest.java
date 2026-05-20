package org.javai.punit.api.spec;

import static org.javai.punit.api.criterion.Criteria.meeting;
import org.javai.punit.api.criterion.Criteria;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.util.List;

import org.javai.outcome.Outcome;
import org.javai.punit.api.Contract;
import org.javai.punit.api.LatencyResult;
import org.javai.punit.api.TokenTracker;
import org.javai.punit.api.ServiceContractOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Trial + SampleSummary.trials")
class TrialTest {

    /** Stand-in contract for outcome construction. */
    private static final Contract<String, Integer> CONTRACT = new Contract<>() {
        @Override
        public Outcome<Integer> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input.length());
        }
        @Override public Criteria<Integer> criteria() {
            return meeting().<Integer>zeroFailures();
        }


    };

    private static ServiceContractOutcome<String, Integer> ok(int value) {
        return new ServiceContractOutcome<>(
                Outcome.ok(value), CONTRACT,
                List.of(),
                0L, Duration.ZERO);
    }

    @Test
    @DisplayName("Trial round-trips its components")
    void trialRoundTrip() {
        ServiceContractOutcome<String, Integer> outcome = ok(42);
        Trial<String, Integer> trial = new Trial<>("hello", outcome, Duration.ofMillis(7));

        assertThat(trial.input()).isEqualTo("hello");
        assertThat(trial.outcome()).isSameAs(outcome);
        assertThat(trial.duration()).isEqualTo(Duration.ofMillis(7));
    }

    @Test
    @DisplayName("Trial accepts a null input — input is the only nullable component")
    void trialAllowsNullInput() {
        ServiceContractOutcome<String, Integer> outcome = ok(42);
        Trial<String, Integer> trial = new Trial<>(null, outcome, Duration.ZERO);
        assertThat(trial.input()).isNull();
    }

    @Test
    @DisplayName("Trial rejects a null outcome")
    void trialRejectsNullOutcome() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new Trial<String, Integer>("hello", null, Duration.ZERO));
    }

    @Test
    @DisplayName("Trial rejects a null duration")
    void trialRejectsNullDuration() {
        ServiceContractOutcome<String, Integer> outcome = ok(42);
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new Trial<>("hello", outcome, null));
    }

    @Test
    @DisplayName("SampleSummary.trials() returns the supplied list")
    void summaryTrialsAccessor() {
        ServiceContractOutcome<String, Integer> ok1 = ok(1);
        ServiceContractOutcome<String, Integer> ok2 = ok(2);
        List<Trial<?, Integer>> trials = List.of(
                new Trial<>("a", ok1, Duration.ofMillis(5)),
                new Trial<>("b", ok2, Duration.ofMillis(7)));

        SampleSummary<Integer> summary = new SampleSummary<>(
                List.of(ok1, ok2),
                Duration.ofMillis(12),
                2, 0, 0L, 0,
                LatencyResult.empty(),
                TerminationReason.COMPLETED,
                trials,
                java.util.Map.of(), LatencyResult.empty(), List.of());

        assertThat(summary.trials()).hasSize(2);
        assertThat(summary.trials().get(0).input()).isEqualTo("a");
        assertThat(summary.trials().get(1).input()).isEqualTo("b");
    }

    @Test
    @DisplayName("SampleSummary rejects a trials list whose size does not match successes + failures")
    void summaryRejectsMismatchedTrialCount() {
        ServiceContractOutcome<String, Integer> okOutcome = ok(1);
        List<Trial<?, Integer>> oneTrial = List.of(
                new Trial<>("a", okOutcome, Duration.ofMillis(5)));

        // successes + failures = 2, but trials list has 1 entry
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new SampleSummary<>(
                        List.of(okOutcome, okOutcome),
                        Duration.ofMillis(12),
                        2, 0, 0L, 0,
                        LatencyResult.empty(),
                        TerminationReason.COMPLETED,
                        oneTrial,
                        java.util.Map.of(), LatencyResult.empty(), List.of()))
                .withMessageContaining("trials");
    }

    @Test
    @DisplayName("SampleSummary accepts an empty trials list")
    void summaryAcceptsEmptyTrials() {
        ServiceContractOutcome<String, Integer> okOutcome = ok(1);
        SampleSummary<Integer> summary = new SampleSummary<>(
                List.of(okOutcome),
                Duration.ofMillis(5),
                1, 0, 0L, 0,
                LatencyResult.empty(),
                TerminationReason.COMPLETED,
                List.of(),
                java.util.Map.of(), LatencyResult.empty(), List.of());

        assertThat(summary.trials()).isEmpty();
    }
}
