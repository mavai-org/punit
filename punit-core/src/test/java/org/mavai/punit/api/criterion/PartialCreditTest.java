package org.mavai.punit.api.criterion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mavai.punit.api.criterion.Criteria.meeting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mavai.outcome.Outcome;

@DisplayName("Partial credit — optional checks and the slack budget")
class PartialCreditTest {

    /** A criterion whose second and third checks fail on any value. */
    private CriterionDecl<String> twoFailingOptionals() {
        return meeting().<String>passRate(0.5)
                .where("has content", v -> !v.isEmpty())
                .where("always trips", v -> false).optional()
                .where("also trips", v -> false).optional();
    }

    @Nested
    @DisplayName("the acceptance predicate")
    class AcceptancePredicate {

        @Test
        @DisplayName("an optional mark alone weakens nothing — the budget defaults to zero")
        void doubleOptIn() {
            Criterion<String> criterion = meeting().<String>passRate(0.5)
                    .where("always trips", v -> false).optional()
                    .toRuntime("c");
            assertThat(criterion.evaluate("value").outcome())
                    .isEqualTo(CriterionSampleOutcome.FAIL);
        }

        @Test
        @DisplayName("failed optional checks within the budget pass the trial")
        void withinBudget() {
            Criterion<String> criterion = twoFailingOptionals().optionalSlack(2).toRuntime("c");
            assertThat(criterion.evaluate("value").outcome())
                    .isEqualTo(CriterionSampleOutcome.PASS);
        }

        @Test
        @DisplayName("failed optional checks beyond the budget fail the trial")
        void beyondBudget() {
            Criterion<String> criterion = twoFailingOptionals().optionalSlack(1).toRuntime("c");
            assertThat(criterion.evaluate("value").outcome())
                    .isEqualTo(CriterionSampleOutcome.FAIL);
        }

        @Test
        @DisplayName("a failed required check fails the trial regardless of any budget")
        void requiredIsNonNegotiable() {
            Criterion<String> criterion = meeting().<String>passRate(0.5)
                    .where("required trips", v -> false)
                    .where("optional trips", v -> false).optional()
                    .optionalSlack(5)
                    .toRuntime("c");
            assertThat(criterion.evaluate("value").outcome())
                    .isEqualTo(CriterionSampleOutcome.FAIL);
        }

        @Test
        @DisplayName("a percentage budget resolves by floor of the applicable optional checks")
        void percentageFloor() {
            // 50% of 3 applicable optional checks -> floor(1.5) = 1.
            CriterionDecl<String> threeOptionals = meeting().<String>passRate(0.5)
                    .where("trips", v -> false).optional()
                    .where("holds", v -> true).optional()
                    .where("trips too", v -> false).optional();
            assertThat(threeOptionals.optionalSlack("50%").toRuntime("c")
                    .evaluate("value").outcome())
                    .isEqualTo(CriterionSampleOutcome.FAIL);
            assertThat(threeOptionals.optionalSlack("67%").toRuntime("c")
                    .evaluate("value").outcome())
                    .isEqualTo(CriterionSampleOutcome.PASS);
        }

        @Test
        @DisplayName("a budget beyond the optional count means all may fail — never impossible")
        void budgetBeyondCount() {
            Criterion<String> criterion = twoFailingOptionals().optionalSlack(9).toRuntime("c");
            assertThat(criterion.evaluate("value").outcome())
                    .isEqualTo(CriterionSampleOutcome.PASS);
        }

        @Test
        @DisplayName("the recorded per-check outcomes stay true under a softened verdict")
        void standingsSeeReality() {
            Criterion<String> criterion = twoFailingOptionals().optionalSlack(2).toRuntime("c");
            CriterionSampleResult result = criterion.evaluate("value");
            assertThat(result.outcome()).isEqualTo(CriterionSampleOutcome.PASS);
            assertThat(result.postconditionResults())
                    .filteredOn(r -> !r.required())
                    .hasSize(2)
                    .allMatch(r -> r.failed());
        }

        @Test
        @DisplayName("the softened per-sample outcome flows through a transforming criterion")
        void transformingChain() {
            Criteria<String> criteria = meeting().<String>passRate(0.5)
                    .transforming(v -> Outcome.ok(v.length()))
                    .where("long enough", length -> length > 3)
                    .where("very long", length -> length > 100).optional()
                    .optionalSlack(1);
            assertThat(criteria.asList().get(0).evaluate("value").outcome())
                    .isEqualTo(CriterionSampleOutcome.PASS);
        }
    }

    @Nested
    @DisplayName("the authoring surface")
    class AuthoringSurface {

        @Test
        @DisplayName("the optional mark follows the check it relaxes")
        void optionalNeedsACheck() {
            assertThatIllegalStateException()
                    .isThrownBy(() -> meeting().<String>passRate(0.5).optional())
                    .withMessageContaining("declare a");
        }

        @Test
        @DisplayName("a bare fraction is never guessed at")
        void bareFractionRefused() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> OptionalSlack.percent("0.2"))
                    .withMessageContaining("never guessed at");
        }

        @Test
        @DisplayName("a negative count is refused")
        void negativeCountRefused() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> OptionalSlack.count(-1));
        }

        @Test
        @DisplayName("the slack keeps the author's spelling verbatim")
        void verbatimDisplay() {
            assertThat(OptionalSlack.count(2).display()).isEqualTo("2");
            assertThat(OptionalSlack.percent("20%").display()).isEqualTo("20%");
        }

        @Test
        @DisplayName("the slack survives posture chaining")
        void slackSurvivesChaining() {
            CriterionDecl<String> decl = meeting().<String>passRate(0.5)
                    .where("holds", v -> true).optional()
                    .optionalSlack(1)
                    .contractRef("SLA v1 §2");
            assertThat(decl.optionalSlack()).isPresent();
        }
    }
}
