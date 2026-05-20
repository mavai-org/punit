package org.javai.punit.api;

import static org.assertj.core.api.Assertions.assertThat;

import static org.javai.punit.api.criterion.Criteria.meeting;

import org.javai.outcome.Outcome;
import org.javai.punit.api.criterion.Criteria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ServiceContract defaults")
class ServiceContractTest {

    private static class SimpleServiceContract implements ServiceContract<Object, String, Integer> {
        @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input.length());
        }
        @Override public Criteria<Integer> criteria() {
            return meeting().<Integer>zeroFailures().satisfies("ok", v -> Outcome.ok());
        }
    }

    private static class ShoppingBasketServiceContract implements ServiceContract<Object, String, String> {
        @Override public Outcome<String> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input);
        }
    }

    private static class HTTPClientServiceContract implements ServiceContract<Object, String, String> {
        @Override public Outcome<String> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input);
        }
    }

    private static class PaymentGateway implements ServiceContract<Object, String, String> {
        @Override public Outcome<String> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input);
        }
    }

    @Test
    @DisplayName("default id strips a ServiceContract suffix and kebab-cases the remainder")
    void defaultIdStripsSuffixAndKebabCases() {
        assertThat(new ShoppingBasketServiceContract().id()).isEqualTo("shopping-basket");
    }

    @Test
    @DisplayName("default id kebab-cases a name with no ServiceContract suffix")
    void defaultIdKebabCasesBareName() {
        assertThat(new PaymentGateway().id()).isEqualTo("payment-gateway");
    }

    @Test
    @DisplayName("default id treats consecutive capitals followed by lowercase as a boundary")
    void defaultIdHandlesAcronymBoundaries() {
        assertThat(new HTTPClientServiceContract().id()).isEqualTo("http-client");
    }

    @Test
    @DisplayName("default id falls back to simple name when there is no ServiceContract suffix to strip")
    void defaultIdForSimpleName() {
        assertThat(new SimpleServiceContract().id()).isEqualTo("simple");
    }

    @Test
    @DisplayName("description defaults to the empty string")
    void descriptionDefault() {
        assertThat(new SimpleServiceContract().description()).isEmpty();
    }

    @Test
    @DisplayName("warmup defaults to zero")
    void warmupDefault() {
        assertThat(new SimpleServiceContract().warmup()).isZero();
    }

    @Test
    @DisplayName("pacing defaults to unlimited")
    void pacingDefaultsUnlimited() {
        assertThat(new SimpleServiceContract().pacing()).isSameAs(Pacing.unlimited());
    }

    @Test
    @DisplayName("covariates defaults to an empty list")
    void covariatesDefaultsEmpty() {
        assertThat(new SimpleServiceContract().covariates()).isEmpty();
    }

    @Test
    @DisplayName("apply produces an Ok outcome wrapping the invoke result")
    void applyWrapsOutputValue() {
        var outcome = new SimpleServiceContract().apply("hello", TokenTracker.create());
        assertThat(outcome.value().isOk()).isTrue();
        assertThat(outcome.value().getOrThrow()).isEqualTo(5);
    }
}
