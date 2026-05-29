package org.mavai.punit.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.covariate.CovariateAlignment;
import org.mavai.punit.api.spec.CriterionResult;
import org.mavai.punit.api.spec.CriterionRole;
import org.mavai.punit.api.spec.EngineRunSummary;
import org.mavai.punit.api.spec.EvaluatedCriterion;
import org.mavai.punit.api.spec.FailureCount;
import org.mavai.punit.api.spec.FailureExemplar;
import org.mavai.punit.api.spec.PerCriterionEvaluation;
import org.mavai.punit.api.spec.ProbabilisticTestResult;
import org.mavai.punit.api.spec.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused tests for {@link PUnit#formatMessage(ProbabilisticTestResult)}.
 * The method is package-private so these tests can call it directly with
 * synthetic results, without standing up the engine.
 */
@DisplayName("PUnit.formatMessage — JUnit assertion-message rendering")
class PUnitFormatMessageTest {

    record Factors(String label) {}

    private static final FactorBundle FACTORS = FactorBundle.of(new Factors("test"));

    private static EvaluatedCriterion failingCriterion() {
        return new EvaluatedCriterion(
                new CriterionResult(
                        "bernoulli-pass-rate",
                        Verdict.FAIL,
                        "observed=0.6500, threshold=0.9000",
                        Map.of()),
                CriterionRole.REQUIRED);
    }

    private static ProbabilisticTestResult resultWith(
            Map<String, FailureCount> histogram) {
        return new ProbabilisticTestResult(
                Verdict.FAIL,
                FACTORS,
                List.of(failingCriterion()),
                TestIntent.VERIFICATION,
                List.of(),
                CovariateAlignment.none(),
                Optional.empty(),
                histogram,
                EngineRunSummary.empty(),
                PerCriterionEvaluation.empty());
    }

    @Test
    @DisplayName("empty histogram → no Postcondition failures section")
    void emptyHistogramOmitsSection() {
        var result = resultWith(Map.of());
        String message = PUnit.formatMessage(result);

        assertThat(message).doesNotContain("Postcondition failures");
    }

    @Test
    @DisplayName("non-empty histogram → section appears with clause name and count")
    void nonEmptyHistogramRendersSection() {
        var hist = Map.of(
                "Valid JSON", new FailureCount(8, List.of(
                        new FailureExemplar("Add 2 apples", "Invalid JSON: trailing commentary"),
                        new FailureExemplar("Clear the basket", "Invalid JSON: unexpected end of input"),
                        new FailureExemplar("Remove the milk", "Invalid JSON: malformed brace"))));
        String message = PUnit.formatMessage(resultWith(hist));

        assertThat(message).contains("Postcondition failures:");
        assertThat(message).contains("\"Valid JSON\" → 8 failures");
        assertThat(message).contains("e.g. \"Add 2 apples\" → Invalid JSON: trailing commentary");
        assertThat(message).contains("e.g. \"Clear the basket\" → Invalid JSON: unexpected end of input");
    }

    @Test
    @DisplayName("displayed exemplars capped at 2 per clause")
    void exemplarsCappedAtTwo() {
        var hist = Map.of(
                "Valid JSON", new FailureCount(3, List.of(
                        new FailureExemplar("a", "first"),
                        new FailureExemplar("b", "second"),
                        new FailureExemplar("c", "third"))));
        String message = PUnit.formatMessage(resultWith(hist));

        assertThat(message).contains("e.g. \"a\" → first");
        assertThat(message).contains("e.g. \"b\" → second");
        assertThat(message).doesNotContain("\"c\" → third");
    }

    @Test
    @DisplayName("clauses sorted by descending count — most common first")
    void clausesSortedByDescendingCount() {
        var hist = Map.of(
                "Less common clause", new FailureCount(2, List.of(
                        new FailureExemplar("x", "less"))),
                "Most common clause", new FailureCount(10, List.of(
                        new FailureExemplar("y", "most"))),
                "Middle clause", new FailureCount(5, List.of(
                        new FailureExemplar("z", "middle"))));
        String message = PUnit.formatMessage(resultWith(hist));

        int mostIdx = message.indexOf("\"Most common clause\"");
        int middleIdx = message.indexOf("\"Middle clause\"");
        int lessIdx = message.indexOf("\"Less common clause\"");

        assertThat(mostIdx).isPositive();
        assertThat(middleIdx).isGreaterThan(mostIdx);
        assertThat(lessIdx).isGreaterThan(middleIdx);
    }

    @Test
    @DisplayName("clause with zero exemplars: count line appears, no e.g. lines")
    void clauseWithoutExemplars() {
        var hist = Map.of(
                "Synthetic clause", new FailureCount(0, List.of()));
        String message = PUnit.formatMessage(resultWith(hist));

        assertThat(message).contains("\"Synthetic clause\" → 0 failures");
        // No exemplars rendered for a bucket with empty exemplar list
        assertThat(message).doesNotContain("e.g.");
    }
}
