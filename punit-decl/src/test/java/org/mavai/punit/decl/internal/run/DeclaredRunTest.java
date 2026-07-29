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

        @Test
        @DisplayName("explicit latency ceilings are judged on the test run")
        void latencyBlockNotYetSupported() {
            // The contract name is historical: explicit ceilings now run.
            // The percentile-existence gate needs 59 samples for p95 at
            // the default confidence, so the budget is explicit; a fast
            // local binding sits well under the 500ms ceiling.
            assertThatCode(() ->
                    PUnit.declared("latency-block-not-yet-supported").samples(60).assertPasses())
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("value-comparison forms")
    class ValueComparisonForms {

        @Test
        @DisplayName("the scalar forms judge decimals, folded text, and null — per input too")
        void quoteServiceExtractsExactValues() {
            // Decimal semantics across spellings (2637.80 vs 2637.8,
            // "500.00" vs "500.00" string subject), equals-ci folding,
            // is-null on a null value and on an absent path.
            assertThatCode(() ->
                    PUnit.declared("quote-service-extracts-exact-values").assertPasses())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("the set forms judge the selection collectively")
        void annotatorReturnsTheGoldSet() {
            // Multiset equality with duplicates, containment across
            // decimal spellings (950.50 vs 950.5), cardinality, and
            // count-equals: 0 over an empty selection.
            assertThatCode(() ->
                    PUnit.declared("annotator-returns-the-gold-set").assertPasses())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("the boolean form judges JSON true/false by identity")
        void annotatorFlagsCoverage() {
            assertThatCode(() ->
                    PUnit.declared("annotator-flags-coverage").assertPasses())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a type mismatch is a per-sample failure, never an abort")
        void textUnderValueFormsFailsPerSample() {
            Declared run = PUnit.declared("text-under-value-forms-fails-per-sample").samples(30);
            assertThatThrownBy(run::assertPasses)
                    .isInstanceOf(AssertionFailedError.class);
        }
    }

    @Nested
    @DisplayName("measure")
    class MeasureVerb {

        @Test
        @DisplayName("a measurement's budget must be typed — never defaulted")
        void budgetRequired() {
            Declared run = PUnit.declared("greeting-service-is-polite");
            assertThatThrownBy(run::measure)
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("experimental-design decision")
                    .hasMessageContaining("1,000 is baseline-grade");
        }

        @Test
        @DisplayName("a measure run records and always persists the baseline artefact")
        void measurePersists(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory)
                throws java.io.IOException {
            System.setProperty("punit.baseline.dir", directory.toString());
            try {
                PUnit.declared("greeting-service-is-polite").samples(40).measure();
                try (var files = java.nio.file.Files.walk(directory)) {
                    assertThat(files.filter(java.nio.file.Files::isRegularFile).count())
                            .as("persisted baseline artefacts")
                            .isPositive();
                }
            } finally {
                System.clearProperty("punit.baseline.dir");
            }
        }

        @Test
        @DisplayName("assertMeets records, persists, then asserts the declared bars")
        void assertMeetsPersistsBeforeAsserting(
                @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory)
                throws java.io.IOException {
            System.setProperty("punit.baseline.dir", directory.toString());
            try {
                Declared failing = PUnit.declared("rude-service-fails-its-bar").samples(20);
                assertThatThrownBy(failing::assertMeets)
                        .isInstanceOf(AssertionFailedError.class);
                try (var files = java.nio.file.Files.walk(directory)) {
                    assertThat(files.filter(java.nio.file.Files::isRegularFile).count())
                            .as("the artefact is on disk whatever the outcome")
                            .isPositive();
                }
            } finally {
                System.clearProperty("punit.baseline.dir");
            }
        }

        @Test
        @DisplayName("measure then test: the empirical criterion is judged against the baseline")
        void measureThenTest(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) {
            System.setProperty("punit.baseline.dir", directory.toString());
            try {
                PUnit.declared("mostly-polite-holds-its-measured-form").samples(100).measure();
                // With the baseline in place the empirical criterion is
                // judged — no INCONCLUSIVE abort, and the always-polite
                // greeter clears both its bars.
                assertThatCode(() ->
                        PUnit.declared("mostly-polite-holds-its-measured-form").samples(100)
                                .assertPasses())
                        .doesNotThrowAnyException();
            } finally {
                System.clearProperty("punit.baseline.dir");
            }
        }

        @Test
        @DisplayName("tolerate and power overrides target the test posture, not measure")
        void overridesRefusedOnMeasure() {
            Declared run = PUnit.declared("greeting-service-is-polite")
                    .samples(50).atPower(0.9);
            assertThatThrownBy(run::measure)
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("test posture");
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
    @DisplayName("bindings artefact and service definitions")
    class BindingsAndServices {

        @Test
        @DisplayName("a services-file definition configures a user type through its factory signature")
        void triageAssistantRoutesRequests() {
            assertThatCode(() -> PUnit.declared().assertPasses())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a registered check answers the satisfies form")
        void satisfiesResolvesRegisteredCheck() {
            assertThatCode(() ->
                    PUnit.declared("triage-assistant-routes-requests").assertPasses())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a registered transformation is a view, with a dollar-rooted JSONPath")
        void judgedViewTakesAJsonpath() {
            assertThatCode(() -> PUnit.declared().assertPasses())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a registered view holding a document takes XPath by expression syntax")
        void registeredViewTakesAnXpath() {
            assertThatCode(() ->
                    PUnit.declared("registered-view-takes-an-xpath").assertPasses())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a definition wins service resolution over a same-named binding")
        void definitionWinsServiceResolution() {
            assertThatCode(() -> PUnit.declared().assertPasses())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a built-in type resolves via ServiceLoader")
        void builtInTypeResolvesViaServiceLoader() {
            assertThatCode(() -> PUnit.declared().assertPasses())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("an unknown type is refused naming the registered types")
        void unknownType(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory)
                throws java.io.IOException {
            java.nio.file.Path services = directory.resolve("mavai-services.yaml");
            java.nio.file.Files.writeString(services, """
                    format: mavai-services/1
                    services:
                      impossible:
                        type: warp-drive
                        configuration:
                          dilithium: high
                    """);
            Declared run = PUnit.declared("greeting-service-is-polite").services(services);
            assertThatThrownBy(run::assertPasses)
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("unknown `type: warp-drive`")
                    .hasMessageContaining("triage");
        }

        @Test
        @DisplayName("a configuration misfit is refused with the factory's rendered signature")
        void configurationMisfit(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory)
                throws java.io.IOException {
            java.nio.file.Path services = directory.resolve("mavai-services.yaml");
            java.nio.file.Files.writeString(services, """
                    format: mavai-services/1
                    services:
                      triage-assistant:
                        type: triage
                        configuration:
                          tone: matter-of-fact
                          certainty: "very"
                    """);
            Declared run = PUnit.declared("triage-assistant-routes-requests").services(services);
            assertThatThrownBy(run::assertPasses)
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("`certainty:` must be a double")
                    .hasMessageContaining("triage(tone: String, certainty: double)");
        }

        @Test
        @DisplayName("a missing configuration key is refused with the signature")
        void missingConfigurationKey(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory)
                throws java.io.IOException {
            java.nio.file.Path services = directory.resolve("mavai-services.yaml");
            java.nio.file.Files.writeString(services, """
                    format: mavai-services/1
                    services:
                      triage-assistant:
                        type: triage
                        configuration:
                          tone: matter-of-fact
                    """);
            Declared run = PUnit.declared("triage-assistant-routes-requests").services(services);
            assertThatThrownBy(run::assertPasses)
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("missing configuration key `certainty:`");
        }

        @Test
        @DisplayName("duplicate resolved grid points are refused naming the colliding entries")
        void duplicateGridPoint(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory)
                throws java.io.IOException {
            java.nio.file.Path services = directory.resolve("mavai-services.yaml");
            java.nio.file.Files.writeString(services, """
                    format: mavai-services/1
                    services:
                      triage-assistant:
                        type: triage
                        configuration:
                          tone: matter-of-fact
                          certainty: 0.9
                        explorations:
                          - certainty: 0.9
                    """);
            Declared run = PUnit.declared("triage-assistant-routes-requests").services(services);
            assertThatThrownBy(run::assertPasses)
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("same configuration as the baseline");
        }

        @Test
        @DisplayName("shadowing a built-in type name is refused")
        void shadowingBuiltInType() {
            Declared run = PUnit.declared("greeting-service-is-polite")
                    .bindings(ShadowingBindings.class);
            assertThatThrownBy(run::assertPasses)
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("shadows the built-in type");
        }
    }

    static class ShadowingBindings {
        @org.mavai.punit.decl.BindingFactory("echo-model")
        java.util.function.Function<String, String> echo(String prefix) {
            return input -> prefix + input;
        }
    }

    @Nested
    @DisplayName("explore")
    class ExploreVerb {

        @Test
        @DisplayName("an explore run emits one descriptive artefact per grid configuration")
        void exploreEmitsPerConfiguration(
                @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory)
                throws java.io.IOException {
            System.setProperty("punit.explorations.outputDir", directory.toString());
            try {
                PUnit.declared("triage-assistant-routes-requests").samplesPerConfig(3).explore();
                try (var files = java.nio.file.Files.walk(directory)) {
                    assertThat(files.filter(java.nio.file.Files::isRegularFile).count())
                            .as("one artefact per grid point: baseline + two explorations")
                            .isEqualTo(3);
                }
            } finally {
                System.clearProperty("punit.explorations.outputDir");
            }
        }

        @Test
        @DisplayName("a bare code binding cannot be explored — it carries no grid")
        void bareBindingRefused() {
            Declared run = PUnit.declared("greeting-service-is-polite");
            assertThatThrownBy(run::explore)
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("no configuration grid")
                    .hasMessageContaining("@BindingFactory");
        }

        @Test
        @DisplayName("an exploration is sized per configuration, not per run")
        void samplesRefusedOnExplore() {
            Declared run = PUnit.declared("triage-assistant-routes-requests").samples(50);
            assertThatThrownBy(run::explore)
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining(".samplesPerConfig(");
        }
    }

    @Nested
    @DisplayName("optimize")
    class OptimizeVerb {

        @Test
        @DisplayName("a named optimization runs its stepper and records the iteration history")
        void optimizeRunsAndRecords(
                @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory)
                throws java.io.IOException {
            System.setProperty("punit.optimizations.outputDir", directory.toString());
            try {
                PUnit.declared("triage-assistant-routes-requests")
                        .samplesPerIteration(3)
                        .optimize("certainty-sweep");
                try (var files = java.nio.file.Files.walk(directory)) {
                    assertThat(files.filter(java.nio.file.Files::isRegularFile).count())
                            .as("the optimization artefact")
                            .isPositive();
                }
            } finally {
                System.clearProperty("punit.optimizations.outputDir");
            }
        }

        @Test
        @DisplayName("with several optimizations declared, the run must name one")
        void severalEntriesNeedAName() {
            Declared run = PUnit.declared("triage-assistant-routes-requests");
            assertThatThrownBy(run::optimize)
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("certainty-sweep, tone-flip");
        }

        @Test
        @DisplayName("an unknown optimization id is refused naming the declared ones")
        void unknownId() {
            Declared run = PUnit.declared("triage-assistant-routes-requests");
            assertThatThrownBy(() -> run.optimize("no-such-run"))
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("no optimization 'no-such-run'")
                    .hasMessageContaining("certainty-sweep");
        }

        @Test
        @DisplayName("a definition without optimizations is refused constructively")
        void noOptimizations() {
            Declared run = PUnit.declared("built-in-type-resolves-via-service-loader");
            assertThatThrownBy(run::optimize)
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("no `optimizations:` entries");
        }

        @Test
        @DisplayName("an optimization is sized per iteration, not per run")
        void samplesRefusedOnOptimize() {
            Declared run = PUnit.declared("triage-assistant-routes-requests").samples(50);
            assertThatThrownBy(() -> run.optimize("certainty-sweep"))
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining(".samplesPerIteration(");
        }

        @Test
        @DisplayName("a stepper-config misfit is refused with the stepper factory's signature")
        void stepperConfigMisfit(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory)
                throws java.io.IOException {
            java.nio.file.Path services = directory.resolve("mavai-services.yaml");
            java.nio.file.Files.writeString(services, """
                    format: mavai-services/1
                    services:
                      triage-assistant:
                        type: triage
                        configuration:
                          tone: matter-of-fact
                          certainty: 0.9
                        optimizations:
                          - stepper: certainty-stepper
                            stepper-config: { step: "wide" }
                            max-iterations: 3
                    """);
            Declared run = PUnit.declared("triage-assistant-routes-requests").services(services);
            assertThatThrownBy(run::optimize)
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("certainty-stepper(step: double, stop: double)");
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
        @DisplayName("an unresolvable service names both populations")
        void unresolvableService(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory)
                throws java.io.IOException {
            java.nio.file.Path services = directory.resolve("mavai-services.yaml");
            java.nio.file.Files.writeString(services, """
                    format: mavai-services/1
                    services:
                      echo-service:
                        type: echo-model
                        configuration:
                          prefix: "echo:"
                    """);
            Declared run = PUnit.declared("greeting-service-is-polite")
                    .bindings(EmptyBindings.class)
                    .services(services);
            assertThatThrownBy(run::assertPasses)
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("greeting-service")
                    .hasMessageContaining("@Binding")
                    .hasMessageContaining("no mavai-services.yaml definition names it");
        }

        @Test
        @DisplayName("the empirical latency shape is refused until its phase arrives")
        void empiricalLatencyShape() {
            Declared run = PUnit.declared("empirical-latency-not-yet-supported");
            assertThatThrownBy(run::assertPasses)
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("empirical `latency:`");
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

    @Nested
    @DisplayName("two-tier capability rule")
    class TwoTierCapabilityRule {

        private java.nio.file.Path degradingServices(java.nio.file.Path directory)
                throws java.io.IOException {
            java.nio.file.Path services = directory.resolve("mavai-services.yaml");
            java.nio.file.Files.writeString(services, """
                    format: mavai-services/1
                    services:
                      degrading-service:
                        type: degrading-model
                        configuration:
                          tone: plain
                          boost: true
                        explorations:
                          - tone: warm
                    """);
            return services;
        }

        @Test
        @DisplayName("the strict tier refuses an unhonourable configuration under test")
        void strictTierRefuses(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory)
                throws java.io.IOException {
            Declared run = PUnit.declared("degrading-service-answers")
                    .bindings(EmptyBindings.class)
                    .services(degradingServices(directory))
                    .samples(5);
            assertThatThrownBy(run::assertPasses)
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("cannot honour `boost: true`");
        }

        @Test
        @DisplayName("the lenient tier degrades per explore point with an announced note")
        void lenientTierDegradesWithNote(
                @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory)
                throws java.io.IOException {
            System.setProperty("punit.explorations.outputDir",
                    directory.resolve("out").toString());
            java.io.PrintStream stdout = System.out;
            java.io.ByteArrayOutputStream captured = new java.io.ByteArrayOutputStream();
            System.setOut(new java.io.PrintStream(captured, true));
            try {
                PUnit.declared("degrading-service-answers")
                        .bindings(EmptyBindings.class)
                        .services(degradingServices(directory))
                        .samplesPerConfig(2)
                        .explore();
            } finally {
                System.setOut(stdout);
                System.clearProperty("punit.explorations.outputDir");
            }
            assertThat(captured.toString())
                    .contains("[PUNIT] note: service 'degrading-service'")
                    .contains("`boost:` is not honoured");
        }
    }

    @Nested
    @DisplayName("built-in steppers")
    class BuiltInSteppers {

        private java.nio.file.Path echoOptimizeServices(java.nio.file.Path directory,
                String stepperConfig) throws java.io.IOException {
            java.nio.file.Path services = directory.resolve("mavai-services.yaml");
            java.nio.file.Files.writeString(services, """
                    format: mavai-services/1
                    services:
                      echo-service:
                        type: echo-model
                        configuration:
                          prefix: "echo:"
                        optimizations:
                          - stepper: fixed-step
                    %s        max-iterations: 2
                    """.formatted(stepperConfig));
            return services;
        }

        @Test
        @DisplayName("a built-in stepper resolves via the ServiceLoader seam")
        void builtInStepperResolves(
                @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory)
                throws java.io.IOException {
            System.setProperty("punit.optimizations.outputDir",
                    directory.resolve("out").toString());
            try {
                assertThatCode(() -> PUnit.declared("built-in-type-resolves-via-service-loader")
                        .bindings(EmptyBindings.class)
                        .services(echoOptimizeServices(directory, ""))
                        .samplesPerIteration(2)
                        .optimize())
                        .doesNotThrowAnyException();
            } finally {
                System.clearProperty("punit.optimizations.outputDir");
            }
        }

        @Test
        @DisplayName("a built-in stepper validates its own optional-keyed stepper-config")
        void builtInStepperConfigMisfit(
                @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory)
                throws java.io.IOException {
            Declared run = PUnit.declared("built-in-type-resolves-via-service-loader")
                    .bindings(EmptyBindings.class)
                    .services(echoOptimizeServices(directory,
                            "        stepper-config: { pace: 3 }\n"));
            assertThatThrownBy(run::optimize)
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("stepper 'fixed-step'")
                    .hasMessageContaining("`pace:`");
        }

        @Test
        @DisplayName("a bindings stepper shadowing a built-in name is refused")
        void shadowingBuiltInStepperRefused(
                @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory)
                throws java.io.IOException {
            Declared run = PUnit.declared("built-in-type-resolves-via-service-loader")
                    .bindings(ShadowingStepperBindings.class)
                    .services(echoOptimizeServices(directory, ""));
            assertThatThrownBy(run::optimize)
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("shadows the built-in stepper");
        }

        @Test
        @DisplayName("an unknown stepper refusal names the built-ins alongside the registered")
        void unknownStepperNamesBuiltIns(
                @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory)
                throws java.io.IOException {
            java.nio.file.Path services = directory.resolve("mavai-services.yaml");
            java.nio.file.Files.writeString(services, """
                    format: mavai-services/1
                    services:
                      echo-service:
                        type: echo-model
                        configuration:
                          prefix: "echo:"
                        optimizations:
                          - stepper: no-such-stepper
                            max-iterations: 2
                    """);
            Declared run = PUnit.declared("built-in-type-resolves-via-service-loader")
                    .bindings(EmptyBindings.class)
                    .services(services);
            assertThatThrownBy(run::optimize)
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("names no registered stepper")
                    .hasMessageContaining("fixed-step (built in)");
        }
    }

    static class ShadowingStepperBindings {

        @org.mavai.punit.decl.Stepper("fixed-step")
        Object shadow() {
            return null;
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
