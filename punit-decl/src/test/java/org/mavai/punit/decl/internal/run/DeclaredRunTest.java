package org.mavai.punit.decl.internal.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mavai.punit.decl.ContractConfigurationException;
import org.mavai.punit.runtime.PUnit.Declared;
import org.mavai.punit.runtime.PUnit;
import org.opentest4j.AssertionFailedError;

/**
 * End-to-end declarative runs: two YAML files and a bindings class in,
 * punit's standard verdict out — driven through the same
 * {@code PUnit.declared()} entry an author writes, resolved against
 * this package's resources.
 */
@DisplayName("Declarative runs")
class DeclaredRunTest {

    @Nested
    @DisplayName("verdicts")
    class Verdicts {

        @Test
        @DisplayName("a passing contract resolves by the calling method's name and passes")
        void greetingServiceIsPolite() {
            // The method name kebab-cases to the contract name — the
            // zero-strings common case.
            assertThatCode(() -> PUnit.declared().assertPasses())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a failing contract fails through the standard opentest4j mapping")
        void failingContract() {
            Declared run = PUnit.declared("rude-service-fails-its-bar").samples(20);
            assertThatThrownBy(run::assertPasses)
                    .isInstanceOf(AssertionFailedError.class);
        }

        @Test
        @DisplayName("views, path selection, and per-input expectations pass end to end")
        void basketContract() {
            assertThatCode(() ->
                    PUnit.declared("basket-builder-returns-valid-baskets").assertPasses())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a wrong per-input expectation fails only its own input's samples — and the bar")
        void wrongExpectation() {
            Declared run = PUnit.declared("basket-builder-disappoints-one-input").samples(30);
            assertThatThrownBy(run::assertPasses)
                    .isInstanceOf(AssertionFailedError.class);
        }

        @Test
        @DisplayName("a transform failure is a per-sample failure, never an abort")
        void brokenJson() {
            Declared run = PUnit.declared("broken-json-fails-per-sample").samples(10);
            assertThatThrownBy(run::assertPasses)
                    .isInstanceOf(AssertionFailedError.class);
        }

        @Test
        @DisplayName("an argument-list input splats across the binding's parameters")
        void tupleInputSplatsAcrossParameters() {
            assertThatCode(() ->
                    PUnit.declared("tuple-input-splats-across-parameters").assertPasses())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("several criteria are several Bernoulli streams over one run")
        void fortuneTellerIsUsuallyEncouraging() {
            assertThatCode(() -> PUnit.declared().assertPasses())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("an xml view takes XPath selection")
        void receiptCarriesATotal() {
            assertThatCode(() -> PUnit.declared().assertPasses())
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("sizing")
    class SizingRules {

        @Test
        @DisplayName("a silently derived budget above the gate is a constructive refusal")
        void derivationGate() {
            Declared run = PUnit.declared("demanding-bar-trips-the-gate");
            assertThatThrownBy(run::assertPasses)
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("derivation gate")
                    .hasMessageContaining("nearly-perfect")
                    .hasMessageContaining(".samples(")
                    .hasMessageContaining("-Dpunit.samples.demanding-bar-trips-the-gate");
        }

        @Test
        @DisplayName("an explicit budget sails through the gate")
        void explicitBudgetLiftsTheGate() {
            assertThatCode(() ->
                    PUnit.declared("demanding-bar-trips-the-gate").samples(300).assertPasses())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("the per-contract system property outranks everything")
        void perContractProperty() {
            System.setProperty("punit.samples.demanding-bar-trips-the-gate", "300");
            try {
                assertThatCode(() ->
                        PUnit.declared("demanding-bar-trips-the-gate").assertPasses())
                        .doesNotThrowAnyException();
            } finally {
                System.clearProperty("punit.samples.demanding-bar-trips-the-gate");
            }
        }
    }

    @Nested
    @DisplayName("risk claims")
    class RiskClaimRules {

        @Test
        @DisplayName("an explicit budget contradicts a tolerate/power override — one sizing source")
        void oneSizingSource() {
            Declared run = PUnit.declared("tolerated-claim-rides-in-the-file")
                    .samples(20).tolerating(0.85);
            assertThatThrownBy(run::assertPasses)
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("one sizing source");
        }

        @Test
        @DisplayName("an explicit budget against file-declared claims is legitimate explicit sizing")
        void explicitBudgetAgainstFileClaims() {
            // The empirical criterion has no baseline, so punit's existing
            // preflight aborts INCONCLUSIVE — the run was sized and driven,
            // never refused at the declarative layer.
            Declared run = PUnit.declared("tolerated-claim-rides-in-the-file").samples(20);
            assertThatThrownBy(run::assertPasses)
                    .isInstanceOf(org.opentest4j.TestAbortedException.class);
        }

        @Test
        @DisplayName("a bare tolerate needs exactly one empirical criterion")
        void bareTolerateNeedsSoleEmpiricalCriterion() {
            Declared run = PUnit.declared("greeting-service-is-polite").tolerating(0.85);
            assertThatThrownBy(run::assertPasses)
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("sole empirical criterion");
        }

        @Test
        @DisplayName("a named tolerate must target an empirical criterion")
        void namedTolerateTargetsEmpirical() {
            Declared run = PUnit.declared("tolerated-claim-rides-in-the-file")
                    .tolerating("clears-its-bar", 0.85);
            assertThatThrownBy(run::assertPasses)
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("no baseline claim to protect");
        }

        @Test
        @DisplayName("power without any tolerate claim is refused")
        void powerWithoutClaims() {
            Declared run = PUnit.declared("greeting-service-is-polite").atPower(0.9);
            assertThatThrownBy(run::assertPasses)
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("risk-driven");
        }

        @Test
        @DisplayName("risk-driven derivation without a budget refuses until the measure phase")
        void riskDrivenDerivationPending() {
            Declared run = PUnit.declared("tolerated-claim-rides-in-the-file");
            assertThatThrownBy(run::assertPasses)
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("measured baseline")
                    .hasMessageContaining(".samples(");
        }

        @Test
        @DisplayName("a non-default contract confidence cannot rebind thresholded criteria")
        void nonDefaultConfidenceWithThresholds() {
            Declared run = PUnit.declared("nonstandard-confidence-with-thresholds").samples(20);
            assertThatThrownBy(run::assertPasses)
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("framework confidence");
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @Test
        @DisplayName("an unresolvable contract name names the searched package and candidates")
        void unresolvableContract() {
            Declared run = PUnit.declared("no-such-contract");
            assertThatThrownBy(run::assertPasses)
                    .isInstanceOf(ContractConfigurationException.class)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no-such-contract")
                    .hasMessageContaining(DeclaredRunTest.class.getPackageName())
                    .hasMessageContaining("greeting-service-is-polite");
        }

        @Test
        @DisplayName("an unresolvable service names the known bindings")
        void unresolvableService() {
            Declared run = PUnit.declared("greeting-service-is-polite")
                    .bindings(EmptyBindings.class);
            assertThatThrownBy(run::assertPasses)
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("greeting-service")
                    .hasMessageContaining("@Binding");
        }

        @Test
        @DisplayName("the latency block is refused until its phase arrives")
        void latencyBlock() {
            Declared run = PUnit.declared("latency-block-not-yet-supported");
            assertThatThrownBy(run::assertPasses)
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("latency");
        }

        @Test
        @DisplayName("refusals never cost a sample")
        void refusalsCostNoSamples() {
            CountingBindings.INVOCATIONS = 0;
            Declared run = PUnit.declared("demanding-bar-trips-the-gate")
                    .bindings(CountingBindings.class);
            assertThatThrownBy(run::assertPasses)
                    .isInstanceOf(ContractConfigurationException.class);
            assertThat(CountingBindings.INVOCATIONS).isZero();
        }
    }

    static class EmptyBindings {
    }

    static class CountingBindings {
        static int INVOCATIONS;

        @org.mavai.punit.decl.Binding("greeting-service")
        String greet(String name) {
            INVOCATIONS++;
            return "hello " + name + "!";
        }
    }
}
