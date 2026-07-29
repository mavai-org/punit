package org.mavai.punit.lm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mavai.punit.decl.ContractConfigurationException;
import org.mavai.punit.decl.spi.ConfiguredService;
import org.mavai.punit.decl.spi.ServiceType;

/**
 * The language-model configuration schema: every refusal at load time
 * with zero samples, the strict capability tier, the vendor veto, and
 * the provenance projection of a valid configuration.
 */
@DisplayName("language-model configuration")
class LanguageModelValidationTest {

    private final ServiceType type = new LanguageModelServiceType();

    @BeforeEach
    void credential() {
        System.setProperty("mavai.llm.api-key", "test-key");
    }

    @AfterEach
    void clear() {
        System.clearProperty("mavai.llm.api-key");
        System.clearProperty("mavai.llm.endpoint");
        System.clearProperty("mavai.llm.model");
    }

    private Map<String, Object> configuration(Object... pairs) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("system-prompt", "You answer politely.");
        record.put("model", "test-model");
        for (int i = 0; i < pairs.length; i += 2) {
            if (pairs[i + 1] == null) {
                record.remove((String) pairs[i]);
            } else {
                record.put((String) pairs[i], pairs[i + 1]);
            }
        }
        return record;
    }

    @Nested
    @DisplayName("schema refusals")
    class SchemaRefusals {

        @Test
        @DisplayName("an unknown configuration key is refused naming it")
        void unknownKey() {
            assertThatThrownBy(() -> type.configure("svc", configuration("frequency", 0.5)))
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("unknown key `frequency:`");
        }

        @Test
        @DisplayName("a configuration without a system prompt is a model, not a service")
        void systemPromptRequired() {
            assertThatThrownBy(() -> type.configure("svc", configuration("system-prompt", null)))
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("`system-prompt:` is required")
                    .hasMessageContaining("no service to test");
        }

        @Test
        @DisplayName("an unknown provider is refused naming the supported set")
        void unknownProvider() {
            assertThatThrownBy(() -> type.configure("svc", configuration("provider", "acme")))
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("unknown `provider: acme`")
                    .hasMessageContaining("anthropic")
                    .hasMessageContaining("litellm")
                    .hasMessageContaining("omit `provider:`");
        }

        @Test
        @DisplayName("top-p is the nucleus mass — a number in (0, 1]")
        void topPBounds() {
            assertThatThrownBy(() -> type.configure("svc", configuration("top-p", 1.5)))
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("`top-p:` must be a number in (0, 1]");
        }

        @Test
        @DisplayName("the thinking vocabulary is closed")
        void thinkingValues() {
            assertThatThrownBy(() -> type.configure("svc", configuration("thinking", "deep")))
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("`thinking:` must be one of: adaptive, none");
        }

        @Test
        @DisplayName("prompt-caching is a boolean")
        void promptCachingBoolean() {
            assertThatThrownBy(() ->
                    type.configure("svc", configuration("prompt-caching", "yes")))
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("`prompt-caching:` must be a boolean");
        }

        @Test
        @DisplayName("a response schema is a mapping")
        void responseSchemaMapping() {
            assertThatThrownBy(() ->
                    type.configure("svc", configuration("response-schema", "object")))
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("`response-schema:` must be a mapping");
        }

        @Test
        @DisplayName("the output ceiling is bounded — larger needs streaming")
        void maxTokensBounds() {
            assertThatThrownBy(() -> type.configure("svc", configuration("max-tokens", 20000)))
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("between 1 and 16000")
                    .hasMessageContaining("defaults to 4096");
        }

        @Test
        @DisplayName("adaptive thinking under a tiny ceiling leaves no room to answer")
        void thinkingFloor() {
            assertThatThrownBy(() -> type.configure("svc", configuration(
                    "provider", "anthropic", "thinking", "adaptive", "max-tokens", 1024)))
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("too small for `thinking: adaptive`")
                    .hasMessageContaining("1024-token thinking floor");
        }
    }

    @Nested
    @DisplayName("the capability allowance")
    class CapabilityAllowance {

        @Test
        @DisplayName("an unknown capability name is refused naming the vocabulary")
        void unknownCapability() {
            assertThatThrownBy(() -> type.configure("svc",
                    configuration("capabilities", List.of("streaming"))))
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("unknown capability 'streaming'")
                    .hasMessageContaining("response-schema, prompt-caching, thinking");
        }

        @Test
        @DisplayName("a capability the adapter cannot encode is refused, never a silent no-op")
        void unencodableCapability() {
            assertThatThrownBy(() -> type.configure("svc", configuration(
                    "provider", "openai", "capabilities", List.of("thinking"))))
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("cannot encode `capabilities: [thinking]`")
                    .hasMessageContaining("litellm");
        }

        @Test
        @DisplayName("the gateway adapter takes the whole vocabulary — the alias decides")
        void gatewayDeclaresAll() {
            System.setProperty("mavai.llm.endpoint", "http://127.0.0.1:1/");
            ConfiguredService service = type.configure("svc", configuration(
                    "provider", "litellm",
                    "capabilities", List.of("thinking", "prompt-caching", "response-schema"),
                    "thinking", "adaptive",
                    "prompt-caching", true));
            assertThat(service.configurationCovariates())
                    .containsEntry("capabilities", "prompt-caching,response-schema,thinking");
        }

        @Test
        @DisplayName("declaring a statically supported capability is a redundant no-op")
        void redundantDeclaration() {
            ConfiguredService service = type.configure("svc", configuration(
                    "provider", "openai", "capabilities", List.of("response-schema")));
            assertThat(service.configurationCovariates())
                    .containsEntry("capabilities", "response-schema");
        }
    }

    @Nested
    @DisplayName("the strict tier and the vendor veto")
    class StrictTier {

        @Test
        @DisplayName("a schema the provider withholds is refused under test/measure")
        void schemaOnApertus() {
            assertThatThrownBy(() -> type.configure("svc", configuration(
                    "provider", "apertus", "response-schema", Map.of("type", "object"))))
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("no structured-output support")
                    .hasMessageContaining("silently dropping it would change what is being "
                            + "measured");
        }

        @Test
        @DisplayName("prompt caching the provider cannot honour is refused")
        void cachingOnOpenAi() {
            assertThatThrownBy(() -> type.configure("svc", configuration(
                    "provider", "openai", "prompt-caching", true)))
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("no prompt-caching support");
        }

        @Test
        @DisplayName("thinking the provider cannot honour is refused")
        void thinkingOnMistral() {
            assertThatThrownBy(() -> type.configure("svc", configuration(
                    "provider", "mistral", "thinking", "adaptive")))
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("no thinking support");
        }

        @Test
        @DisplayName("declared-off values are honoured trivially by every provider")
        void declaredOffValues() {
            ConfiguredService service = type.configure("svc", configuration(
                    "provider", "openai", "thinking", "none", "prompt-caching", false));
            assertThat(service.configurationCovariates())
                    .containsEntry("thinking", "none")
                    .containsEntry("promptCaching", "false");
        }

        @Test
        @DisplayName("anthropic vetoes adaptive thinking beside explicit sampling parameters")
        void anthropicVeto() {
            assertThatThrownBy(() -> type.configure("svc", configuration(
                    "provider", "anthropic", "thinking", "adaptive", "temperature", 0.2)))
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("constrains sampling parameters")
                    .hasMessageContaining("set `thinking: none`");
        }
    }

    @Nested
    @DisplayName("environment resolution")
    class EnvironmentResolution {

        @Test
        @DisplayName("a gateway has no canonical host — the endpoint must come from the environment")
        void gatewayNeedsEndpoint() {
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    System.getenv("MAVAI_LLM_ENDPOINT") == null);
            assertThatThrownBy(() -> type.configure("svc", configuration("provider", "litellm")))
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("no default endpoint")
                    .hasMessageContaining("MAVAI_LLM_ENDPOINT");
        }

        @Test
        @DisplayName("no declared model and no environment default is a refusal")
        void modelRequired() {
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    System.getenv("MAVAI_LLM_MODEL") == null);
            System.setProperty("mavai.llm.endpoint", "http://127.0.0.1:1/");
            assertThatThrownBy(() -> type.configure("svc", configuration("model", null)))
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("no model declared")
                    .hasMessageContaining("MAVAI_LLM_MODEL");
        }
    }

    @Nested
    @DisplayName("provenance projection")
    class Provenance {

        @BeforeEach
        void endpoint() {
            System.setProperty("mavai.llm.endpoint", "http://127.0.0.1:1/");
        }

        @Test
        @DisplayName("the resolved configuration lands as covariates — ceiling always stated")
        void resolvedCovariates() {
            ConfiguredService service = type.configure("svc", configuration(
                    "temperature", 0.3, "top-p", 0.9,
                    "response-schema", Map.of("type", "object")));
            Map<String, String> covariates = service.configurationCovariates();
            assertThat(covariates)
                    .containsEntry("serviceType", "language-model")
                    .containsEntry("provider", "openai-compatible")
                    .containsEntry("systemPrompt", "You answer politely.")
                    .containsEntry("model", "test-model")
                    .containsEntry("temperature", "0.3")
                    .containsEntry("topP", "0.9")
                    .containsEntry("maxTokens", "4096")
                    .containsKey("responseSchemaFingerprint");
            assertThat(covariates.get("responseSchemaFingerprint")).hasSize(64);
        }

        @Test
        @DisplayName("undeclared optional keys stay out of provenance — never fabricated")
        void undeclaredKeysAbsent() {
            ConfiguredService service = type.configure("svc", configuration());
            assertThat(service.configurationCovariates())
                    .doesNotContainKeys("temperature", "topP", "thinking", "promptCaching",
                            "capabilities", "responseSchemaFingerprint");
        }
    }
}
