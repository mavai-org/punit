package org.mavai.punit.lm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mavai.outcome.Outcome;
import org.mavai.punit.decl.spi.ConfiguredService;
import org.mavai.punit.decl.spi.ServiceType;

/**
 * The adapters' wire shapes and the delivery taxonomy, exercised end
 * to end against a local stub endpoint: one plain request per
 * invocation, each vendor's own body and headers, and the
 * rejection-versus-failed-delivery boundary.
 */
@DisplayName("provider wire shapes and delivery")
class WireShapeTest {

    private final ServiceType type = new LanguageModelServiceType();
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
        System.clearProperty("mavai.llm.model");
    }

    private ConfiguredService service(Object... pairs) {
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("system-prompt", "You answer politely.");
        configuration.put("model", "test-model");
        for (int i = 0; i < pairs.length; i += 2) {
            configuration.put((String) pairs[i], pairs[i + 1]);
        }
        return type.configure("svc", configuration);
    }

    @Nested
    @DisplayName("request bodies")
    class RequestBodies {

        @Test
        @DisplayName("the generic body is the chat-completions shape with a bearer credential")
        void genericBody() {
            stub.completeWith("hello");
            Outcome<String> outcome = service("temperature", 0.2, "top-p", 0.9).invoke("hi");
            assertThat(outcome).isEqualTo(Outcome.ok("hello"));
            JsonNode body = stub.lastRequest();
            assertThat(body.get("model").asText()).isEqualTo("test-model");
            assertThat(body.get("messages").get(0).get("role").asText()).isEqualTo("system");
            assertThat(body.get("messages").get(0).get("content").asText())
                    .isEqualTo("You answer politely.");
            assertThat(body.get("messages").get(1).get("content").asText()).isEqualTo("hi");
            assertThat(body.get("max_tokens").asInt()).isEqualTo(4096);
            assertThat(body.get("temperature").asDouble()).isEqualTo(0.2);
            assertThat(body.get("top_p").asDouble()).isEqualTo(0.9);
            assertThat(stub.lastHeaders().get("Authorization"))
                    .containsExactly("Bearer test-key");
        }

        @Test
        @DisplayName("a declared schema rides the OpenAI-compatible response_format")
        void genericSchema() {
            stub.completeWith("{}");
            service("response-schema", Map.of("type", "object")).invoke("hi");
            JsonNode format = stub.lastRequest().get("response_format");
            assertThat(format.get("type").asText()).isEqualTo("json_schema");
            assertThat(format.get("json_schema").get("schema").get("type").asText())
                    .isEqualTo("object");
        }

        @Test
        @DisplayName("openai renames the ceiling to max_completion_tokens")
        void openAiCeiling() {
            stub.completeWith("ok");
            service("provider", "openai", "max-tokens", 512).invoke("hi");
            JsonNode body = stub.lastRequest();
            assertThat(body.get("max_completion_tokens").asInt()).isEqualTo(512);
            assertThat(body.has("max_tokens")).isFalse();
        }

        @Test
        @DisplayName("anthropic speaks the messages protocol — system top-level, x-api-key")
        void anthropicBody() {
            stub.anthropicWith("{\"type\":\"text\",\"text\":\"routed\"}");
            Outcome<String> outcome = service("provider", "anthropic").invoke("hi");
            assertThat(outcome).isEqualTo(Outcome.ok("routed"));
            JsonNode body = stub.lastRequest();
            assertThat(body.get("system").asText()).isEqualTo("You answer politely.");
            assertThat(body.get("max_tokens").asInt()).isEqualTo(4096);
            assertThat(stub.lastHeaders().get("X-api-key")).containsExactly("test-key");
            assertThat(stub.lastHeaders().get("Anthropic-version")).isNotNull();
        }

        @Test
        @DisplayName("anthropic prompt caching marks the system block cache_control ephemeral")
        void anthropicCaching() {
            stub.anthropicWith("{\"type\":\"text\",\"text\":\"ok\"}");
            service("provider", "anthropic", "prompt-caching", true).invoke("hi");
            JsonNode system = stub.lastRequest().get("system").get(0);
            assertThat(system.get("cache_control").get("type").asText()).isEqualTo("ephemeral");
        }

        @Test
        @DisplayName("anthropic adaptive thinking rides the thinking block; text extraction skips thinking blocks")
        void anthropicThinking() {
            stub.anthropicWith("{\"type\":\"thinking\",\"thinking\":\"hmm\"},"
                    + "{\"type\":\"text\",\"text\":\"answer\"}");
            Outcome<String> outcome =
                    service("provider", "anthropic", "thinking", "adaptive").invoke("hi");
            assertThat(outcome).isEqualTo(Outcome.ok("answer"));
            assertThat(stub.lastRequest().get("thinking").get("type").asText())
                    .isEqualTo("adaptive");
        }

        @Test
        @DisplayName("ollama nests the ceiling as num_predict and disables streaming")
        void ollamaBody() {
            stub.ollamaWith("pong");
            Outcome<String> outcome = service("provider", "ollama").invoke("ping");
            assertThat(outcome).isEqualTo(Outcome.ok("pong"));
            JsonNode body = stub.lastRequest();
            assertThat(body.get("stream").asBoolean()).isFalse();
            assertThat(body.get("options").get("num_predict").asInt()).isEqualTo(4096);
        }

        @Test
        @DisplayName("litellm encodes what the author declared — reasoning effort for thinking")
        void litellmThinking() {
            stub.completeWith("ok");
            service("provider", "litellm",
                    "capabilities", List.of("thinking"), "thinking", "adaptive").invoke("hi");
            assertThat(stub.lastRequest().get("reasoning_effort").asText()).isEqualTo("medium");
        }
    }

    @Nested
    @DisplayName("the delivery taxonomy")
    class DeliveryTaxonomy {

        @Test
        @DisplayName("a provider rejection aborts — a defect, never a sample")
        void rejectionAborts() {
            stub.respond(400, "{\"error\":\"unknown model\"}");
            ConfiguredService service = service();
            assertThatThrownBy(() -> service.invoke("hi"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("HTTP 400")
                    .hasMessageContaining("a defect, not a sample")
                    .hasMessageContaining("unknown model");
        }

        @Test
        @DisplayName("a server-side error is a failed delivery — a failed sample with its cause")
        void serverErrorIsFailedSample() {
            stub.respond(503, "overloaded");
            Outcome<String> outcome = service().invoke("hi");
            assertThat(outcome).isInstanceOf(Outcome.Fail.class);
            assertThat(((Outcome.Fail<String>) outcome).failure().message())
                    .contains("HTTP 503").contains("overloaded");
        }

        @Test
        @DisplayName("an unreachable endpoint is a failed delivery")
        void unreachableIsFailedSample() {
            System.setProperty("mavai.llm.endpoint", "http://127.0.0.1:1/");
            Outcome<String> outcome = service().invoke("hi");
            assertThat(outcome).isInstanceOf(Outcome.Fail.class);
            assertThat(((Outcome.Fail<String>) outcome).failure().message())
                    .contains("service unreachable");
        }

        @Test
        @DisplayName("a delivered body off the vendor shape is a failed sample, not an abort")
        void oddShapeIsFailedSample() {
            stub.respond(200, "{\"unexpected\":true}");
            Outcome<String> outcome = service().invoke("hi");
            assertThat(outcome).isInstanceOf(Outcome.Fail.class);
            assertThat(((Outcome.Fail<String>) outcome).failure().message())
                    .contains("no text content");
        }

        @Test
        @DisplayName("one invocation, one request — never a retry")
        void oneRequestPerInvocation() {
            stub.respond(503, "overloaded");
            ConfiguredService service = service();
            service.invoke("hi");
            assertThat(stub.requestCount()).isEqualTo(1);
        }
    }
}
