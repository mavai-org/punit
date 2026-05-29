package org.mavai.punit.api.spec;

import static org.mavai.punit.api.criterion.Criteria.meeting;
import org.mavai.punit.api.criterion.Criteria;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.PostconditionResult;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.api.ServiceContractOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SampleClassification — engine's per-sample summary")
class SampleClassificationTest {

    record Factors() {}

    /** Service contract stand-in with no max-latency bound. */
    private static final ServiceContract<Factors, String, Integer> USE_CASE = new ServiceContract<>() {
        @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input.length());
        }
        @Override public Criteria<Integer> criteria() {
            return meeting().<Integer>zeroFailures();
        }

    };

    private static ServiceContractOutcome<String, Integer> outcomeOk(int value, Duration duration, long tokens) {
        return new ServiceContractOutcome<>(
                Outcome.ok(value), USE_CASE,
                List.of(),
                tokens, duration);
    }

    private static ServiceContractOutcome<String, Integer> outcomeOkWith(
            List<PostconditionResult> results,
            Duration duration, long tokens) {
        return new ServiceContractOutcome<>(
                Outcome.ok(0), USE_CASE,
                results,
                tokens, duration);
    }

    private static ServiceContractOutcome<String, Integer> outcomeFail(
            String name, String message, Duration duration, long tokens) {
        return new ServiceContractOutcome<>(
                Outcome.fail(name, message), USE_CASE,
                List.of(),
                tokens, duration);
    }

    @Nested
    @DisplayName("classify")
    class Classify {

        @Test
        @DisplayName("apply-level Outcome.Fail produces failedAtApply classification")
        void applyFail() {
            var outcome = outcomeFail("llm-error", "boom", Duration.ofMillis(50), 0L);

            SampleClassification c = SampleClassification.classify(USE_CASE, outcome);

            assertThat(c.applyFailed()).isTrue();
            assertThat(c.applyFailureName()).contains("llm-error");
            assertThat(c.applyFailureMessage()).contains("boom");
            assertThat(c.postconditionResults()).isEmpty();
            assertThat(c.durationViolation()).isEmpty();
            assertThat(c.duration()).isEqualTo(Duration.ofMillis(50));
        }

        @Test
        @DisplayName("apply-level Outcome.Ok with no max-latency yields no duration violation")
        void okNoMaxLatency() {
            var outcome = outcomeOk(42, Duration.ofSeconds(5), 100L);

            SampleClassification c = SampleClassification.classify(USE_CASE, outcome);

            assertThat(c.applyFailed()).isFalse();
            assertThat(c.durationViolation()).isEmpty();
            assertThat(c.duration()).isEqualTo(Duration.ofSeconds(5));
            assertThat(c.tokens()).isEqualTo(100L);
        }

        @Test
        @DisplayName("apply-level Outcome.Ok exceeding max-latency surfaces a DurationViolation")
        void okExceedingMaxLatency() {
            ServiceContract<Factors, String, Integer> bounded = new ServiceContract<>() {
                @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) { return Outcome.ok(0); }
                @Override public Criteria<Integer> criteria() {
                    return meeting().<Integer>zeroFailures();
                }

                @Override public Optional<Duration> maxLatency() {
                    return Optional.of(Duration.ofMillis(100));
                }
            };
            var outcome = new ServiceContractOutcome<>(
                    Outcome.ok(0), bounded, List.<PostconditionResult>of(),
                    0L, Duration.ofMillis(250));

            SampleClassification c = SampleClassification.classify(bounded, outcome);

            assertThat(c.durationViolation()).isPresent();
            assertThat(c.durationViolation().get().observed()).isEqualTo(Duration.ofMillis(250));
            assertThat(c.durationViolation().get().max()).isEqualTo(Duration.ofMillis(100));
        }

        @Test
        @DisplayName("apply-level Outcome.Ok at exactly max-latency does not violate")
        void okAtMaxLatencyExact() {
            ServiceContract<Factors, String, Integer> bounded = new ServiceContract<>() {
                @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) { return Outcome.ok(0); }
                @Override public Criteria<Integer> criteria() {
                    return meeting().<Integer>zeroFailures();
                }

                @Override public Optional<Duration> maxLatency() {
                    return Optional.of(Duration.ofMillis(100));
                }
            };
            var outcome = new ServiceContractOutcome<>(
                    Outcome.ok(0), bounded, List.<PostconditionResult>of(),
                    0L, Duration.ofMillis(100));

            SampleClassification c = SampleClassification.classify(bounded, outcome);

            assertThat(c.durationViolation()).isEmpty();
        }

        @Test
        @DisplayName("postcondition results are pre-evaluated and surfaced unchanged")
        void postconditionResultsCarried() {
            var results = List.of(
                    PostconditionResult.passed("first"),
                    PostconditionResult.failed("second", "tripped"));
            var outcome = outcomeOkWith(results, Duration.ofMillis(20), 5L);

            SampleClassification c = SampleClassification.classify(USE_CASE, outcome);

            assertThat(c.postconditionResults()).hasSize(2);
            assertThat(c.postconditionResults().get(1).failed()).isTrue();
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("negative tokens are rejected")
        void rejectsNegativeTokens() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> SampleClassification.failedAtApply(
                            "x", "y", Duration.ZERO, -1L));
        }

        @Test
        @DisplayName("negative duration is rejected")
        void rejectsNegativeDuration() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> SampleClassification.failedAtApply(
                            "x", "y", Duration.ofMillis(-1), 0L));
        }

        @Test
        @DisplayName("postcondition list is defensively copied")
        void postconditionResultsCopied() {
            var mutable = new java.util.ArrayList<PostconditionResult>();
            mutable.add(PostconditionResult.passed("a"));

            SampleClassification c = SampleClassification.from(
                    mutable, Optional.empty(),
                    Duration.ZERO, 0L);

            mutable.add(PostconditionResult.failed("b", "intruder"));

            assertThat(c.postconditionResults()).hasSize(1);
        }
    }
}
