package org.mavai.punit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mavai.punit.api.Postcondition.ensure;

import java.util.List;

import org.mavai.outcome.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Postcondition — unopinionated evaluation of one aspect")
class PostconditionTest {

    @Nested
    @DisplayName("ensure")
    class Ensure {

        @Test
        @DisplayName("Outcome.ok → passed; description carried through")
        void passes() {
            Postcondition<String> p = ensure("non-empty",
                    s -> s.isEmpty()
                            ? Outcome.fail("empty", "string was empty")
                            : Outcome.ok());

            PostconditionResult r = p.evaluate("hello");

            assertThat(r.passed()).isTrue();
            assertThat(r.description()).isEqualTo("non-empty");
        }

        @Test
        @DisplayName("Outcome.fail's reason is preserved")
        void preservesFailureReason() {
            Postcondition<Integer> p = ensure("positive",
                    v -> v > 0
                            ? Outcome.ok()
                            : Outcome.fail("negative", "got " + v));

            PostconditionResult r = p.evaluate(-3);

            assertThat(r.failed()).isTrue();
            assertThat(r.failureReason()).contains("got -3");
        }

        @Test
        @DisplayName("Outcome.fail's name is preserved (distinct from description)")
        void preservesFailureName() {
            Postcondition<Integer> p = ensure("positive",
                    v -> v > 0
                            ? Outcome.ok()
                            : Outcome.fail("negative", "got " + v));

            PostconditionResult r = p.evaluate(-3);

            assertThat(r.failureName()).contains("negative");
            assertThat(r.description()).isEqualTo("positive");
        }

        @Test
        @DisplayName("a thrown exception fills failureName with the description (synthetic)")
        void thrownExceptionUsesSyntheticName() {
            Postcondition<Integer> p = ensure("checked", v -> {
                throw new IllegalStateException("boom");
            });

            PostconditionResult r = p.evaluate(0);

            assertThat(r.failed()).isTrue();
            assertThat(r.failureName()).contains("checked");
            assertThat(r.failureReason()).contains("boom");
        }

        @Test
        @DisplayName("passed result has empty failureName")
        void passedResultEmptyFailureName() {
            Postcondition<String> p = ensure("non-empty",
                    s -> s.isEmpty()
                            ? Outcome.fail("empty", "was empty")
                            : Outcome.ok());

            PostconditionResult r = p.evaluate("hello");

            assertThat(r.passed()).isTrue();
            assertThat(r.failureName()).isEmpty();
        }

        @Test
        @DisplayName("a thrown RuntimeException is captured as a failure")
        void thrownExceptionCaptured() {
            Postcondition<Integer> p = ensure("checked", v -> {
                throw new IllegalStateException("boom");
            });

            PostconditionResult r = p.evaluate(0);

            assertThat(r.failed()).isTrue();
            assertThat(r.failureReason()).contains("boom");
        }

        @Test
        @DisplayName("evaluateAll on a leaf returns a singleton list")
        void evaluateAllLeaf() {
            Postcondition<String> p = ensure("non-empty",
                    s -> s.isEmpty()
                            ? Outcome.fail("empty", "")
                            : Outcome.ok());

            List<PostconditionResult> results = p.evaluateAll("hello");

            assertThat(results).hasSize(1);
            assertThat(results.get(0).passed()).isTrue();
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("blank description rejected")
        void blankDescriptionRejected() {
            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> ensure("", (Object v) -> Outcome.ok()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
