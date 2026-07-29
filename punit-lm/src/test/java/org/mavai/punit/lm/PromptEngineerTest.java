package org.mavai.punit.lm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mavai.punit.api.spec.FactorsStepper;
import org.mavai.punit.api.spec.FactorsStepper.IterationResult;
import org.mavai.punit.api.spec.FailureCount;
import org.mavai.punit.api.spec.FailureExemplar;
import org.mavai.punit.api.spec.NextFactor;
import org.mavai.punit.decl.ContractConfigurationException;

/**
 * The prompt-engineer stepper: the previous iteration's failures drive
 * the next prompt through a meta model — exercised against the stub
 * endpoint, never the network.
 */
@DisplayName("the prompt-engineer stepper")
class PromptEngineerTest {

    private final PromptEngineer provider = new PromptEngineer();
    private StubLlm stub;

    @BeforeEach
    void start() {
        stub = StubLlm.start();
        System.setProperty("mavai.llm.endpoint", stub.endpoint());
        System.setProperty("mavai.llm.api-key", "test-key");
    }

    @AfterEach
    void stop() {
        stub.close();
        System.clearProperty("mavai.llm.endpoint");
        System.clearProperty("mavai.llm.api-key");
    }

    private static Map<String, Object> configuration(String prompt) {
        Map<String, Object> current = new LinkedHashMap<>();
        current.put("system-prompt", prompt);
        current.put("model", "test-model");
        current.put("temperature", 0.7);
        return current;
    }

    private static IterationResult<Map<String, Object>> iteration(
            Map<String, Object> factors) {
        return new IterationResult<>(factors, 0.4,
                Map.of("response-is-valid", new FailureCount(3, List.of(
                        new FailureExemplar("a dozen eggs", "no quantity field"),
                        new FailureExemplar("two bottles of milk", "commentary in answer"),
                        new FailureExemplar("a loaf of bread", "missing name")))),
                List.of(), 2, 3, 5);
    }

    @Test
    @DisplayName("the meta answer becomes the next configuration's prompt")
    void proposesTheMetaAnswer() {
        stub.completeWith("You translate orders into strict JSON baskets.");
        FactorsStepper<Map<String, Object>> stepper = provider.create(Map.of());
        Map<String, Object> current = configuration("You build baskets.");
        NextFactor<Map<String, Object>> next =
                stepper.next(current, List.of(iteration(current)));
        assertThat(next).isInstanceOf(NextFactor.Continue.class);
        Map<String, Object> proposed = ((NextFactor.Continue<Map<String, Object>>) next).factor();
        assertThat(proposed)
                .containsEntry("system-prompt", "You translate orders into strict JSON baskets.")
                .containsEntry("model", "test-model");
    }

    @Test
    @DisplayName("the meta message carries the prompt, the rate, and the bounded breakdown")
    void metaMessageCarriesTheBreakdown() {
        stub.completeWith("improved");
        FactorsStepper<Map<String, Object>> stepper =
                provider.create(Map.of("max-exemplars", 2));
        Map<String, Object> current = configuration("You build baskets.");
        stepper.next(current, List.of(iteration(current)));
        String message = stub.lastRequest().get("messages").get(1).get("content").asText();
        assertThat(message)
                .contains("You build baskets.")
                .contains("Pass rate achieved: 0.40 (2 of 5 samples passed)")
                .contains("criterion \"response-is-valid\" failed 3 time(s)")
                .contains("a dozen eggs")
                .contains("two bottles of milk")
                .doesNotContain("a loaf of bread");
        assertThat(stub.lastRequest().get("messages").get(0).get("content").asText())
                .contains("You are a prompt engineer");
    }

    @Test
    @DisplayName("a meta model with nothing to propose stops the run")
    void emptyAnswerStops() {
        stub.completeWith("   ");
        FactorsStepper<Map<String, Object>> stepper = provider.create(Map.of());
        Map<String, Object> current = configuration("You build baskets.");
        assertThat(stepper.next(current, List.of(iteration(current))))
                .isInstanceOf(NextFactor.Stop.class);
    }

    @Test
    @DisplayName("a retuned target key rewrites that key, not the prompt")
    void targetKeyRetunes() {
        stub.completeWith("terse");
        FactorsStepper<Map<String, Object>> stepper =
                provider.create(Map.of("target-key", "tone"));
        Map<String, Object> current = configuration("You build baskets.");
        current.put("tone", "chatty");
        NextFactor<Map<String, Object>> next =
                stepper.next(current, List.of(iteration(current)));
        Map<String, Object> proposed = ((NextFactor.Continue<Map<String, Object>>) next).factor();
        assertThat(proposed)
                .containsEntry("tone", "terse")
                .containsEntry("system-prompt", "You build baskets.");
    }

    @Test
    @DisplayName("an unknown stepper-config key is refused rendering the schema")
    void unknownConfigKey() {
        assertThatThrownBy(() -> provider.create(Map.of("verbosity", "high")))
                .isInstanceOf(ContractConfigurationException.class)
                .hasMessageContaining("unknown `stepper-config:` key `verbosity:`")
                .hasMessageContaining("max-exemplars");
    }

    @Test
    @DisplayName("max-exemplars is a non-negative count")
    void maxExemplarsBounds() {
        assertThatThrownBy(() -> provider.create(Map.of("max-exemplars", -1)))
                .isInstanceOf(ContractConfigurationException.class)
                .hasMessageContaining("`max-exemplars:` must be at least 0");
    }
}
