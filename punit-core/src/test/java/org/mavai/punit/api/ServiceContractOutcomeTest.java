package org.mavai.punit.api;

import static org.mavai.punit.api.criterion.Criteria.meeting;
import org.mavai.punit.api.criterion.Criteria;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.util.List;

import org.mavai.outcome.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ServiceContractOutcome — recipient-facing artefact assembled by Contract.apply")
class ServiceContractOutcomeTest {

    /** Stand-in contract used in the canonical-constructor tests. */
    private static final Contract<String, Integer> CONTRACT = new Contract<>() {
        @Override
        public Outcome<Integer> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input.length());
        }
        @Override public Criteria<Integer> criteria() {
            return meeting().<Integer>zeroFailures();
        }


    };

    @Test
    @DisplayName("canonical constructor copies the postcondition list")
    void postconditionResultsDefensivelyCopied() {
        var mutable = new java.util.ArrayList<PostconditionResult>();
        mutable.add(PostconditionResult.passed("a"));

        var outcome = new ServiceContractOutcome<>(
                Outcome.ok(5), CONTRACT, mutable, 0L, Duration.ZERO);

        mutable.add(PostconditionResult.failed("b", "intruder"));   // mutate after

        assertThat(outcome.postconditionResults()).hasSize(1);
        assertThat(outcome.postconditionResults().get(0).description()).isEqualTo("a");
    }

    @Test
    @DisplayName("negative tokens are rejected")
    void rejectsNegativeTokens() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new ServiceContractOutcome<>(
                        Outcome.ok(1), CONTRACT, List.of(),
                        -1L, Duration.ZERO));
    }

    @Test
    @DisplayName("negative duration is rejected")
    void rejectsNegativeDuration() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new ServiceContractOutcome<>(
                        Outcome.ok(1), CONTRACT, List.of(),
                        0L, Duration.ofMillis(-1)));
    }

    @Test
    @DisplayName("null fields are rejected")
    void rejectsNullFields() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new ServiceContractOutcome<>(
                        null, CONTRACT, List.of(), 0L, Duration.ZERO));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new ServiceContractOutcome<>(
                        Outcome.ok(1), null, List.of(), 0L, Duration.ZERO));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new ServiceContractOutcome<>(
                        Outcome.ok(1), CONTRACT, null, 0L, Duration.ZERO));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new ServiceContractOutcome<>(
                        Outcome.ok(1), CONTRACT, List.of(), 0L, null));
    }

    @Test
    @DisplayName("value() returns the apply-level Fail short-circuiting everything else")
    void valueReturnsApplyLevelFail() {
        Outcome<Integer> applyFail = Outcome.fail("apply-error", "boom");
        var outcome = new ServiceContractOutcome<>(
                applyFail, CONTRACT,
                List.of(PostconditionResult.passed("would-pass")),
                0L, Duration.ZERO);

        assertThat(outcome.value()).isSameAs(applyFail);
    }

    @Test
    @DisplayName("value() returns the first postcondition failure")
    void valueReturnsFirstClauseFail() {
        var outcome = new ServiceContractOutcome<>(
                Outcome.ok(5), CONTRACT,
                List.of(
                        PostconditionResult.passed("first ok"),
                        PostconditionResult.failed("second-clause", "tripped"),
                        PostconditionResult.failed("third-clause", "would also trip")),
                0L, Duration.ZERO);

        assertThat(outcome.value().isFail()).isTrue();
        assertThat(((Outcome.Fail<Integer>) outcome.value()).failure().message())
                .contains("tripped");
    }

    @Test
    @DisplayName("value() returns Ok when result is Ok and nothing else fails")
    void valueReturnsOkWhenAllPass() {
        var outcome = new ServiceContractOutcome<>(
                Outcome.ok(5), CONTRACT,
                List.of(PostconditionResult.passed("all good")),
                12L, Duration.ofMillis(7));

        assertThat(outcome.value().isOk()).isTrue();
        assertThat(outcome.value().getOrThrow()).isEqualTo(5);
    }
}
