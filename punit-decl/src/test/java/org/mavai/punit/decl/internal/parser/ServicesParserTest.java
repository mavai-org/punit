package org.mavai.punit.decl.internal.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mavai.punit.decl.ContractConfigurationException;
import org.mavai.punit.decl.internal.model.OptimizationDeclaration;
import org.mavai.punit.decl.internal.model.OptimizationDeclaration.Objective;
import org.mavai.punit.decl.internal.model.ServiceEntry;
import org.mavai.punit.decl.internal.model.ServicesDeclaration;

@DisplayName("Services-file parser")
class ServicesParserTest {

    private static final String MINIMAL = """
            format: mavai-services/1
            services:
              greeter:
                type: language-model
                configuration:
                  system-prompt: "You are a polite greeter."
            """;

    private static void assertRefused(String yaml, String messageFragment) {
        assertThatThrownBy(() -> ServicesParser.parse(yaml))
                .isInstanceOf(ContractConfigurationException.class)
                .hasMessageContaining(messageFragment);
    }

    @Nested
    @DisplayName("acceptance")
    class Acceptance {

        @Test
        @DisplayName("the specification's minimal definition parses")
        void minimalDefinition() {
            ServicesDeclaration declaration = ServicesParser.parse(MINIMAL);
            ServiceEntry greeter = declaration.services().get("greeter");
            assertThat(greeter.type()).isEqualTo("language-model");
            assertThat(greeter.configuration()).containsEntry("system-prompt", "You are a polite greeter.");
            assertThat(greeter.explorations()).isEmpty();
            assertThat(greeter.optimizations()).isEmpty();
        }

        @Test
        @DisplayName("the exploration grid's delta entries parse in order")
        void explorations() {
            ServicesDeclaration declaration = ServicesParser.parse("""
                    format: mavai-services/1
                    services:
                      support-agent:
                        type: language-model
                        configuration:
                          system-prompt: "You are a helpful support agent."
                          temperature: 0.2
                        explorations:
                          - temperature: 0.0
                          - temperature: 0.7
                          - model: other-model
                            temperature: 0.7
                    """);
            ServiceEntry agent = declaration.services().get("support-agent");
            assertThat(agent.explorations()).hasSize(3);
            assertThat(agent.explorations().get(2)).containsEntry("model", "other-model");
        }

        @Test
        @DisplayName("optimization entries parse with defaults resolved")
        void optimizations() {
            ServicesDeclaration declaration = ServicesParser.parse("""
                    format: mavai-services/1
                    services:
                      basket-builder:
                        type: language-model
                        configuration:
                          system-prompt: "You build baskets."
                          temperature: 0.2
                        optimizations:
                          - id: prompt-tuning
                            stepper: prompt-engineer
                            stepper-config: { model: gpt-4o, max-exemplars: 1 }
                            max-iterations: 8
                            no-improvement-window: 2
                          - id: temperature-linear
                            stepper: linear-sweep
                            stepper-config: { key: temperature, step: 0.1, stop: 1.0 }
                            initial: { temperature: 0.0 }
                            max-iterations: 11
                    """);
            var entries = declaration.services().get("basket-builder").optimizations();
            assertThat(entries).hasSize(2);
            OptimizationDeclaration tuning = entries.get(0);
            assertThat(tuning.scorer()).isEqualTo("pass-rate");
            assertThat(tuning.objective()).isEqualTo(Objective.MAXIMIZE);
            assertThat(tuning.noImprovementWindow()).isEqualTo(2);
            assertThat(entries.get(1).initial()).containsEntry("temperature", 0.0);
        }

        @Test
        @DisplayName("a sole optimization entry takes the service name as its id")
        void soleEntryIdDefaults() {
            ServicesDeclaration declaration = ServicesParser.parse("""
                    format: mavai-services/1
                    services:
                      basket-builder:
                        type: language-model
                        configuration:
                          system-prompt: "You build baskets."
                        optimizations:
                          - stepper: linear-sweep
                            max-iterations: 5
                    """);
            assertThat(declaration.services().get("basket-builder").optimizations().get(0).id())
                    .isEqualTo("basket-builder");
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @Test
        @DisplayName("a wrong format identifier is refused")
        void formatIdentifier() {
            assertRefused(MINIMAL.replace("mavai-services/1", "mavai-services/2"),
                    "`format:` must be 'mavai-services/1'");
        }

        @Test
        @DisplayName("a missing services block is refused")
        void servicesBlockMissing() {
            assertRefused("format: mavai-services/1\n", "`services:` must be a non-empty mapping");
        }

        @Test
        @DisplayName("unknown keys refuse at the file's top level too")
        void topLevelUnknownKey() {
            assertRefused("comment: not a services key\n" + MINIMAL,
                    "the services file has unknown key `comment:`");
        }

        @Test
        @DisplayName("an unknown definition key is refused with the uniformity rule")
        void definitionUnknownKey() {
            assertRefused(MINIMAL.replace("type: language-model",
                    "type: language-model\n    temperature: 0.2"),
                    "every covariate value lives inside `configuration:`");
        }

        @Test
        @DisplayName("a missing configuration block is refused")
        void configurationMissing() {
            assertRefused("""
                    format: mavai-services/1
                    services:
                      greeter:
                        type: language-model
                    """, "a `configuration:` block is required");
        }

        @Test
        @DisplayName("a missing type is refused")
        void typeMissing() {
            assertRefused("""
                    format: mavai-services/1
                    services:
                      greeter:
                        configuration:
                          system-prompt: "You are a polite greeter."
                    """, "`type:` names the service implementation");
        }

        @Test
        @DisplayName("an empty explorations list is refused")
        void explorationsEmpty() {
            assertRefused(MINIMAL.replace("type: language-model",
                    "type: language-model\n    explorations: []"),
                    "`explorations:` must be a non-empty list");
        }

        @Test
        @DisplayName("an exploration entry with a value-less key is refused")
        void explorationNullValue() {
            assertRefused("""
                    format: mavai-services/1
                    services:
                      support-agent:
                        type: language-model
                        configuration:
                          system-prompt: "You are a helpful support agent."
                          temperature: 0.2
                        explorations:
                          - temperature:
                    """, "declares no value");
        }

        @Test
        @DisplayName("an unknown optimization key is refused naming the accepted set")
        void optimizationUnknownKey() {
            assertRefused(optimization("stepper: linear-sweep\n        max-iterations: 5\n        epochs: 3"),
                    "unknown key `epochs:`");
        }

        @Test
        @DisplayName("a stepper-less optimization entry is refused")
        void stepperMissing() {
            assertRefused(optimization("max-iterations: 5"), "`stepper:` is required");
        }

        @Test
        @DisplayName("a cap-less optimization entry is refused")
        void maxIterationsMissing() {
            assertRefused(
                    optimization("stepper: linear-sweep\n        stepper-config: { key: temperature, step: 0.1, stop: 1.0 }"),
                    "`max-iterations:` is required");
        }

        @Test
        @DisplayName("a non-positive iteration cap is refused")
        void maxIterationsNotPositive() {
            assertRefused(
                    optimization("stepper: linear-sweep\n        max-iterations: 0"),
                    "`max-iterations:` must be a positive integer");
        }

        @Test
        @DisplayName("an id with characters that cannot name an artefact is refused")
        void idShape() {
            assertRefused(optimization("id: \"has spaces\"\n        stepper: linear-sweep\n        max-iterations: 5"),
                    "letters, digits, dots, underscores, or hyphens");
        }

        @Test
        @DisplayName("duplicate optimization ids are refused")
        void duplicateIds() {
            assertRefused("""
                    format: mavai-services/1
                    services:
                      basket-builder:
                        type: language-model
                        configuration:
                          system-prompt: "You build baskets."
                        optimizations:
                          - id: tune
                            stepper: linear-sweep
                            max-iterations: 5
                          - id: tune
                            stepper: refining-grid
                            max-iterations: 5
                    """, "already used by optimization entry 1");
        }

        @Test
        @DisplayName("ids are required as soon as a second optimization appears")
        void idRequiredWhenMultiple() {
            assertRefused("""
                    format: mavai-services/1
                    services:
                      basket-builder:
                        type: language-model
                        configuration:
                          system-prompt: "You build baskets."
                        optimizations:
                          - id: tune
                            stepper: linear-sweep
                            max-iterations: 5
                          - stepper: refining-grid
                            max-iterations: 5
                    """, "`id:` is required when");
        }

        @Test
        @DisplayName("an empty initial overlay is refused")
        void initialEmpty() {
            assertRefused(
                    optimization("stepper: linear-sweep\n        max-iterations: 5\n        initial: {}"),
                    "`initial:` must be a non-empty mapping");
        }

        private String optimization(String entryBody) {
            return """
                    format: mavai-services/1
                    services:
                      basket-builder:
                        type: language-model
                        configuration:
                          system-prompt: "You build baskets."
                          temperature: 0.2
                        optimizations:
                          - %s
                    """.formatted(entryBody);
        }
    }
}
