package org.javai.punit.api.criterion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.javai.punit.api.criterion.Criteria.empirical;
import static org.javai.punit.api.criterion.Criteria.meeting;
import static org.javai.punit.api.criterion.Criteria.of;

import java.util.function.Predicate;

import org.javai.outcome.Outcome;
import org.javai.punit.api.Contract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Criteria-as-data — value-form authoring surface")
class CriteriaAsDataTest {

    @Test
    @DisplayName("K=1 bare decl IS a Criteria, lowers to one-entry list with default id 'default'")
    void bareDeclIsCriteria() {
        Criteria<String> c = meeting().<String>passRate(0.85);

        assertThat(c.asList()).hasSize(1);
        assertThat(c.asList().get(0).id()).isEqualTo(Criteria.DEFAULT_CRITERION_ID);
        assertThat(c.asList().get(0).posture().kind())
                .isEqualTo(CriterionPosture.Kind.STATISTICAL_CONTRACTUAL);
        assertThat(c.asList().get(0).posture().threshold().getAsDouble()).isEqualTo(0.85);
    }

    @Test
    @DisplayName("K=1 bare decl with .name(...) — explicit id replaces the default")
    void bareDeclWithExplicitName() {
        Criteria<String> c = meeting().<String>passRate(0.85)
                .name("custom-id");

        assertThat(c.asList()).hasSize(1);
        assertThat(c.asList().get(0).id()).isEqualTo("custom-id");
    }

    @Test
    @DisplayName("posture chain is value-preserving: .meeting().contractRef().atConfidence() composes via posture")
    void postureChainPreservesValues() {
        CriterionDecl<String> decl = empirical().<String>passRate()
                .atConfidence(0.99)
                .contractRef("doc-v1");

        CriterionPosture p = decl.posture();
        assertThat(p.kind()).isEqualTo(CriterionPosture.Kind.STATISTICAL_EMPIRICAL);
        assertThat(p.confidenceFloor().getAsDouble()).isEqualTo(0.99);
        assertThat(p.contractRef()).contains("doc-v1");
    }

    @Test
    @DisplayName(".where(name, Predicate) — synthesised failure carries the postcondition name")
    void wherePredicateSynthesisesFailure() {
        Predicate<String> isEmpty = String::isEmpty;
        Predicate<String> startsWithA = s -> s.startsWith("a");
        CriterionDecl<String> decl = meeting().<String>passRate(0.85)
                .where("non-empty", isEmpty)
                .where("starts-with-a", startsWithA);

        assertThat(decl.postconditions()).hasSize(2);
        assertThat(decl.postconditions().get(0).name()).isEqualTo("non-empty");

        Outcome<?> failResult = decl.postconditions().get(0).check().check("x");
        assertThat(failResult).isInstanceOf(Outcome.Fail.class);
        Outcome.Fail<?> fail = (Outcome.Fail<?>) failResult;
        assertThat(fail.failure().id().name()).isEqualTo("non-empty");
        assertThat(fail.failure().message()).contains("non-empty").contains("returned false").contains("x");
    }

    @Test
    @DisplayName(".satisfies(name, Function<O, Outcome<?>>) — author-supplied message preserved verbatim")
    void satisfiesRichFunctionPreservesMessage() {
        CriterionDecl<String> decl = meeting().<String>passRate(0.85)
                .satisfies("parseable", v -> Outcome.fail("notJson", "v=" + v));

        Outcome<?> result = decl.postconditions().get(0).check().check("garbage");
        assertThat(result).isInstanceOf(Outcome.Fail.class);
        Outcome.Fail<?> fail = (Outcome.Fail<?>) result;
        assertThat(fail.failure().id().name()).isEqualTo("notJson");
        assertThat(fail.failure().message()).isEqualTo("v=garbage");
    }

    @Test
    @DisplayName(".satisfies takes a bare lambda — no overload ambiguity, no type-witness needed")
    void satisfiesDisambiguatedWithoutHelp() {
        // The key Finding #1 regression-guard: a slide-friendly lambda
        // with a generic Outcome.fail in its body must compile without
        // a cast or a typed local.
        CriterionDecl<String> decl = meeting().<String>passRate(0.85)
                .satisfies("transaction succeeds", r -> r.startsWith("OK")
                        ? Outcome.ok()
                        : Outcome.fail("transaction-failed", "got=" + r));

        Outcome<?> okResult = decl.postconditions().get(0).check().check("OK-123");
        assertThat(okResult).isInstanceOf(Outcome.Ok.class);
        Outcome<?> failResult = decl.postconditions().get(0).check().check("ERR-500");
        assertThat(failResult).isInstanceOf(Outcome.Fail.class);
    }

    @Test
    @DisplayName(".transforming(parse).where/.satisfies — postconditions evaluate against derived value")
    void transformingChainEvaluatesAgainstDerived() {
        TransformingDecl<String, Integer> decl = empirical().<String>passRate()
                .transforming(s -> {
                    try {
                        return Outcome.ok(Integer.parseInt(s));
                    } catch (NumberFormatException e) {
                        return Outcome.fail("not-a-number", "s=" + s);
                    }
                })
                .where("positive", n -> n > 0)
                .satisfies("even", n -> n % 2 == 0
                        ? Outcome.ok()
                        : Outcome.fail("odd", "n=" + n));

        assertThat(decl.postconditions()).hasSize(2);

        Criterion<String> rt = decl.toRuntime("parsed-number");
        assertThat(rt.id()).isEqualTo("parsed-number");
        assertThat(rt.posture().kind()).isEqualTo(CriterionPosture.Kind.STATISTICAL_EMPIRICAL);

        // Successful transform → postcondition chain runs
        assertThat(rt.evaluate("4").outcome()).isEqualTo(CriterionSampleOutcome.PASS);
        // Transform ok, postcondition fails → FAIL
        assertThat(rt.evaluate("3").outcome()).isEqualTo(CriterionSampleOutcome.FAIL);
        // Transform fails → FAIL carrying the reason (chain skipped)
        CriterionSampleResult parseFail = rt.evaluate("xyz");
        assertThat(parseFail.outcome()).isEqualTo(CriterionSampleOutcome.FAIL);
        assertThat(parseFail.postconditionResults()).isEmpty();
        assertThat(parseFail.reason()).isPresent();
        assertThat(parseFail.reason().get().failure().id().name()).isEqualTo("not-a-number");
    }

    @Test
    @DisplayName(".transforming(...) rejects pre-transform postconditions on the same decl")
    void transformingRejectsPreTransformPostconditions() {
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> empirical().<String>passRate()
                        .where("non-empty", s -> !s.isEmpty())
                        .transforming(s -> Outcome.ok(Integer.parseInt(s))))
                .withMessageContaining(".transforming");
    }

    @Test
    @DisplayName("Criteria.of(...) mixes direct and transforming criteria under one composite")
    void ofMixesDirectAndTransforming() {
        Criteria<String> c = of(
                empirical().<String>passRate()
                        .name("response-not-empty")
                        .where("non-blank", s -> !s.isBlank()),
                empirical().<String>passRate()
                        .transforming(s -> {
                            try {
                                return Outcome.ok(Integer.parseInt(s));
                            } catch (NumberFormatException e) {
                                return Outcome.fail("not-a-number", "s=" + s);
                            }
                        })
                        .where("positive", n -> n > 0)
                        .name("parses"));

        assertThat(c.asList()).hasSize(2);
        assertThat(c.asList().get(0).id()).isEqualTo("response-not-empty");
        assertThat(c.asList().get(1).id()).isEqualTo("parses");
        assertThat(c.asList().get(1).evaluate("nope").outcome())
                .isEqualTo(CriterionSampleOutcome.FAIL);
    }

    @Test
    @DisplayName("Criteria.of(...) — K>1 composite, declaration order preserved")
    void ofMany() {
        Criteria<String> c = of(
                meeting().<String>passRate(0.99).name("a"),
                meeting().<String>passRate(0.95).name("b"),
                meeting().<String>zeroFailures().name("c"));

        assertThat(c.asList()).hasSize(3);
        assertThat(c.asList().get(0).id()).isEqualTo("a");
        assertThat(c.asList().get(1).id()).isEqualTo("b");
        assertThat(c.asList().get(2).id()).isEqualTo("c");
    }

    @Test
    @DisplayName("Criteria.of(oneDecl) — K=1 pass-through preserves the decl's name")
    void ofSinglePassThroughExplicitName() {
        Criteria<String> c = of(meeting().<String>passRate(0.9999)
                .name("payment-success"));

        assertThat(c.asList()).hasSize(1);
        assertThat(c.asList().get(0).id()).isEqualTo("payment-success");
    }

    @Test
    @DisplayName("duplicate criterion names in Criteria.of(...) rejected at construction")
    void duplicateNamesRejected() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> of(
                        meeting().<String>passRate(0.99).name("a"),
                        meeting().<String>passRate(0.95).name("a")))
                .withMessageContaining("duplicate");
    }

    @Test
    @DisplayName("K>1 Criteria.of(...) with any unnamed decl is rejected")
    void unnamedInKPlusOneRejected() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> of(
                        meeting().<String>passRate(0.99).name("a"),
                        meeting().<String>passRate(0.95)))
                .withMessageContaining("name");
    }

    @Test
    @DisplayName("calling .name(...) twice on the same decl is rejected")
    void duplicateNameOnSameDeclRejected() {
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> empirical().<String>passRate()
                        .name("first")
                        .name("second"))
                .withMessageContaining("already supplied");
    }

    @Test
    @DisplayName("Contract.criteria() default returns empty; effectiveCriteria() throws")
    void defaultContractRejectsEmptyCriteria() {
        Contract<Object, String> c = new Contract<>() {
            @Override public Outcome<String> invoke(Object input,
                    org.javai.punit.api.TokenTracker t) {
                return Outcome.ok("ok");
            }
        };

        assertThat(c.criteria().isEmpty()).isTrue();
        org.assertj.core.api.Assertions.assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(c::effectiveCriteria)
                .withMessageContaining("declares no criteria");
    }

    @Test
    @DisplayName("Contract.criteria() value-form: bare decl drives effectiveCriteria with default name")
    void valueFormDrivesEffectiveCriteria() {
        Contract<Object, String> c = new Contract<>() {
            @Override public Outcome<String> invoke(Object input,
                    org.javai.punit.api.TokenTracker t) {
                return Outcome.ok("ok");
            }
            @Override public Criteria<String> criteria() {
                return meeting().<String>passRate(0.85)
                        .contractRef("doc-v1");
            }
        };

        assertThat(c.effectiveCriteria()).hasSize(1);
        assertThat(c.effectiveCriteria().get(0).id()).isEqualTo(Criteria.DEFAULT_CRITERION_ID);
        assertThat(c.effectiveCriteria().get(0).posture().contractRef()).contains("doc-v1");
    }

}
