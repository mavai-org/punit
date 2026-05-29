package org.mavai.punit.api.criterion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mavai.punit.api.criterion.Criteria.empirical;

import java.util.List;
import java.util.Optional;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.Expected;
import org.mavai.punit.api.Postcondition;
import org.mavai.punit.api.PostconditionResult;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.api.ServiceContractOutcome;
import org.mavai.punit.api.TokenTracker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Reference-matching criterion — .matchedBy / .matchedByEquality")
class MatchedByTest {

    private record AudioSample(String data, String expected) implements Expected<String> {
        @Override
        public String expected() {
            return expected;
        }
    }

    private record PlainSample(String data) {
    }

    private static class SttContract implements ServiceContract<Object, AudioSample, String> {
        @Override
        public Outcome<String> invoke(AudioSample input, TokenTracker tracker) {
            return Outcome.ok(input.data());
        }

        @Override
        public Criteria<String> criteria() {
            return empirical().<String>passRate()
                    .name("transcript-matches-expected")
                    .matchedByEquality();
        }
    }

    private static class SttContractPlain implements ServiceContract<Object, PlainSample, String> {
        @Override
        public Outcome<String> invoke(PlainSample input, TokenTracker tracker) {
            return Outcome.ok(input.data());
        }

        @Override
        public Criteria<String> criteria() {
            return empirical().<String>passRate()
                    .name("transcript-matches-expected")
                    .matchedByEquality();
        }
    }

    @Test
    @DisplayName("ValueMatcher.equality — equal values produce Outcome.ok")
    void equalityOnEqualValues() {
        Outcome<Void> result = ValueMatcher.<String>equality().match("hello", "hello");

        assertThat(result).isInstanceOf(Outcome.Ok.class);
    }

    @Test
    @DisplayName("ValueMatcher.equality — unequal values produce Outcome.fail with 'not-equal' name")
    void equalityOnUnequalValues() {
        Outcome<Void> result = ValueMatcher.<String>equality().match("hello", "world");

        assertThat(result).isInstanceOf(Outcome.Fail.class);
        Outcome.Fail<Void> fail = (Outcome.Fail<Void>) result;
        assertThat(fail.failure().id().name()).isEqualTo("not-equal");
        assertThat(fail.failure().message()).contains("hello").contains("world");
    }

    @Test
    @DisplayName("Postcondition.Matching.match folds matcher success into a passed PostconditionResult")
    void matchingPostconditionPassedResult() {
        Postcondition.Matching<String> m = new Postcondition.Matching<>(
                "label", ValueMatcher.equality());

        PostconditionResult r = m.match("a", "a");

        assertThat(r.failed()).isFalse();
        assertThat(r.description()).isEqualTo("label");
    }

    @Test
    @DisplayName("Postcondition.Matching.match folds matcher failure into a failed PostconditionResult carrying the matcher's failure name")
    void matchingPostconditionFailedResult() {
        Postcondition.Matching<String> m = new Postcondition.Matching<>(
                "label", ValueMatcher.equality());

        PostconditionResult r = m.match("a", "b");

        assertThat(r.failed()).isTrue();
        assertThat(r.description()).isEqualTo("label");
    }

    @Test
    @DisplayName("Postcondition.Matching.evaluate(value) throws — interface method requires expected; engine must pattern-match")
    void matchingEvaluateByValueAloneThrows() {
        Postcondition.Matching<String> m = new Postcondition.Matching<>(
                "label", ValueMatcher.equality());

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> m.evaluate("a"))
                .withMessageContaining("Matching postcondition");
    }

    @Test
    @DisplayName(".matchedByEquality returns a MatchingDecl that lowers to a DirectCriterion with one Matching postcondition")
    void matchedByEqualityProducesMatchingDecl() {
        MatchingDecl<String> decl = empirical().<String>passRate()
                .name("equality")
                .matchedByEquality();

        List<Criterion<String>> runtime = decl.asList();
        assertThat(runtime).hasSize(1);
        Criterion<String> c = runtime.get(0);
        assertThat(c.id()).isEqualTo("equality");
        assertThat(c.requiresExpected()).isTrue();
    }

    @Test
    @DisplayName(".matchedBy(supplier) produces a MatchingDecl using the supplied matcher")
    void matchedByCustomMatcher() {
        ValueMatcher<String> caseInsensitive = (expected, actual) ->
                expected.equalsIgnoreCase(actual)
                        ? Outcome.ok()
                        : Outcome.fail("case-mismatch", expected + " vs " + actual);

        Criterion<String> c = empirical().<String>passRate()
                .name("ci-eq")
                .matchedBy(() -> caseInsensitive)
                .asList()
                .get(0);

        CriterionSampleResult ok = c.evaluate("HELLO", Optional.of("hello"));
        assertThat(ok.outcome()).isEqualTo(CriterionSampleOutcome.PASS);

        CriterionSampleResult bad = c.evaluate("hello", Optional.of("world"));
        assertThat(bad.outcome()).isEqualTo(CriterionSampleOutcome.FAIL);
    }

    @Test
    @DisplayName(".matchedBy after .satisfies is rejected — matching criteria are equivalence-only")
    void matchedByAfterSatisfiesIsRejected() {
        CriterionDecl<String> withSatisfies = empirical().<String>passRate()
                .satisfies("non-empty", v -> v.isEmpty()
                        ? Outcome.fail("empty", "empty")
                        : Outcome.ok());

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> withSatisfies.matchedByEquality())
                .withMessageContaining("cannot follow");
    }

    @Test
    @DisplayName("DirectCriterion.requiresExpected — false when the chain has no Matching postcondition")
    void requiresExpectedFalseForPlainSatisfies() {
        Criterion<String> c = empirical().<String>passRate()
                .satisfies("ok", v -> Outcome.ok())
                .asList()
                .get(0);

        assertThat(c.requiresExpected()).isFalse();
    }

    @Test
    @DisplayName("Engine end-to-end — Expected input routes to matcher; matching values produce PASS")
    void endToEndPass() {
        SttContract contract = new SttContract();
        AudioSample sample = new AudioSample("hello", "hello");

        ServiceContractOutcome<AudioSample, String> outcome =
                contract.apply(sample, TokenTracker.create());

        assertThat(outcome.value()).isInstanceOf(Outcome.Ok.class);
        assertThat(outcome.criterionSampleResults()).hasSize(1);
        assertThat(outcome.criterionSampleResults().get(0).outcome())
                .isEqualTo(CriterionSampleOutcome.PASS);
    }

    @Test
    @DisplayName("Engine end-to-end — mismatched values produce FAIL with the matcher's failure name")
    void endToEndFail() {
        SttContract contract = new SttContract();
        AudioSample sample = new AudioSample("hello", "world");

        ServiceContractOutcome<AudioSample, String> outcome =
                contract.apply(sample, TokenTracker.create());

        assertThat(outcome.criterionSampleResults().get(0).outcome())
                .isEqualTo(CriterionSampleOutcome.FAIL);
        assertThat(outcome.postconditionResults())
                .anySatisfy(r -> assertThat(r.failed()).isTrue());
    }

    @Test
    @DisplayName("Sampling-construction guard — input not implementing Expected, paired with .matchedBy, fails fast with input-typed message")
    void inputNotImplementingExpectedIsRejected() {
        SttContractPlain contract = new SttContractPlain();
        PlainSample sample = new PlainSample("hello");

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> contract.apply(sample, TokenTracker.create()))
                .withMessageContaining(".matchedBy")
                .withMessageContaining("Expected")
                .withMessageContaining(PlainSample.class.getName());
    }
}
