package org.mavai.punit.lm;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mavai.punit.decl.ContractConfigurationException;
import org.mavai.punit.lm.providers.LmProvider;
import org.mavai.punit.lm.providers.Providers;

/**
 * Validation of one resolved {@code language-model} configuration
 * record against the type's schema — every refusal at load time, with
 * zero samples, speaking the author's vocabulary.
 */
final class ParameterValidation {

    private static final Set<String> CONFIGURATION_KEYS = Set.of(
            "system-prompt", "provider", "capabilities", "model", "temperature",
            "top-p", "thinking", "prompt-caching", "response-schema", "max-tokens");

    private static final List<String> THINKING_VALUES = List.of("adaptive", "none");

    private ParameterValidation() {}

    static ContractConfigurationException fail(String serviceName, String problem) {
        return new ContractConfigurationException("service '" + serviceName + "': " + problem);
    }

    static LanguageModelParameters validated(String serviceName, Map<String, Object> configuration) {
        for (String key : configuration.keySet()) {
            if (!CONFIGURATION_KEYS.contains(key)) {
                throw fail(serviceName, "the language-model configuration has unknown key `"
                        + key + ":`");
            }
        }
        String systemPrompt = systemPrompt(serviceName, configuration);
        String providerName = string(serviceName, configuration, "provider");
        LmProvider provider = Providers.resolve(providerName);
        Set<String> capabilities = capabilities(serviceName, configuration, provider);
        String model = string(serviceName, configuration, "model");
        Double temperature = number(serviceName, configuration, "temperature",
                "the sampling temperature");
        Double topP = topP(serviceName, configuration);
        String thinking = thinking(serviceName, configuration);
        Boolean promptCaching = promptCaching(serviceName, configuration);
        Map<String, Object> responseSchema = responseSchema(serviceName, configuration);
        int maxTokens = maxTokens(serviceName, configuration);
        if ("adaptive".equals(thinking)
                && maxTokens <= LanguageModelParameters.THINKING_MIN_MAX_TOKENS) {
            throw fail(serviceName, "`max-tokens: " + maxTokens + "` is too small for "
                    + "`thinking: adaptive` — the ceiling bounds thinking and answer together, "
                    + "so at or below the model's "
                    + LanguageModelParameters.THINKING_MIN_MAX_TOKENS + "-token thinking floor "
                    + "the reasoning consumes the whole budget and the answer is truncated to "
                    + "nothing. Raise the ceiling or set `thinking: none`.");
        }
        return new LanguageModelParameters(systemPrompt, providerName, capabilities, model,
                temperature, topP, thinking, promptCaching, responseSchema, maxTokens);
    }

    private static String systemPrompt(String serviceName, Map<String, Object> configuration) {
        Object value = configuration.get("system-prompt");
        if (!(value instanceof String prompt) || prompt.isEmpty()) {
            throw fail(serviceName, "`system-prompt:` is required in the configuration — a "
                    + "language-model service is a model given a job; without a system prompt "
                    + "there is a model, but no service to test");
        }
        return prompt;
    }

    /**
     * The author-declared capability allowance, validated twice at
     * load: every name against the vocabulary, then the set against
     * what the resolved adapter can actually encode. Declaring a
     * capability the adapter has no wire form for is a refusal, never
     * a silent no-op; declaring one it already supports statically is
     * a redundant no-op, accepted.
     */
    private static Set<String> capabilities(
            String serviceName, Map<String, Object> configuration, LmProvider provider) {
        Object raw = configuration.get("capabilities");
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof List<?> list)
                || list.stream().anyMatch(item -> !(item instanceof String))) {
            throw fail(serviceName, "`capabilities:` must be a list of capability names (any "
                    + "of: " + String.join(", ", LmProvider.CAPABILITY_NAMES) + ")");
        }
        Set<String> declared = new LinkedHashSet<>();
        for (Object item : list) {
            String capability = (String) item;
            if (!LmProvider.CAPABILITY_NAMES.contains(capability)) {
                throw fail(serviceName, "unknown capability '" + capability + "' — supported "
                        + "names: " + String.join(", ", LmProvider.CAPABILITY_NAMES));
            }
            declared.add(capability);
        }
        Set<String> unencodable = new LinkedHashSet<>(declared);
        unencodable.removeAll(Providers.declarableCapabilities(provider));
        if (!unencodable.isEmpty()) {
            throw fail(serviceName, "provider '" + provider.name() + "' cannot encode "
                    + "`capabilities: [" + String.join(", ", unencodable) + "]` — its request "
                    + "body has no wire form for that. Declare a capability only on an adapter "
                    + "that can send it (a gateway adapter such as `litellm`), or remove it.");
        }
        return declared;
    }

    private static Double topP(String serviceName, Map<String, Object> configuration) {
        Object value = configuration.get("top-p");
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean || !(value instanceof Number number)
                || number.doubleValue() <= 0 || number.doubleValue() > 1) {
            throw fail(serviceName, "`top-p:` must be a number in (0, 1] — the cumulative "
                    + "probability mass nucleus sampling draws from");
        }
        return number.doubleValue();
    }

    private static String thinking(String serviceName, Map<String, Object> configuration) {
        Object value = configuration.get("thinking");
        if (value == null) {
            return null;
        }
        if (!(value instanceof String mode) || !THINKING_VALUES.contains(mode)) {
            throw fail(serviceName, "`thinking:` must be one of: "
                    + String.join(", ", THINKING_VALUES));
        }
        return mode;
    }

    private static Boolean promptCaching(String serviceName, Map<String, Object> configuration) {
        Object value = configuration.get("prompt-caching");
        if (value == null) {
            return null;
        }
        if (!(value instanceof Boolean caching)) {
            throw fail(serviceName, "`prompt-caching:` must be a boolean");
        }
        return caching;
    }

    private static Map<String, Object> responseSchema(
            String serviceName, Map<String, Object> configuration) {
        Object value = configuration.get("response-schema");
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map<?, ?> schema)) {
            throw fail(serviceName, "`response-schema:` must be a mapping (the JSON Schema the "
                    + "model is instructed to satisfy)");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> typed = (Map<String, Object>) schema;
        return typed;
    }

    private static int maxTokens(String serviceName, Map<String, Object> configuration) {
        Object value = configuration.get("max-tokens");
        if (value == null) {
            return LanguageModelParameters.DEFAULT_MAX_TOKENS;
        }
        if (value instanceof Boolean || !(value instanceof Integer ceiling)
                || ceiling < 1 || ceiling > LanguageModelParameters.MAX_TOKENS_CEILING) {
            throw fail(serviceName, "`max-tokens:` must be a whole number of output tokens "
                    + "between 1 and " + LanguageModelParameters.MAX_TOKENS_CEILING + " — the "
                    + "ceiling on what the model may return; ceilings above this need "
                    + "streaming, which this reader does not yet do. Unstated, it defaults to "
                    + LanguageModelParameters.DEFAULT_MAX_TOKENS + " and is recorded as such.");
        }
        return ceiling;
    }

    private static String string(
            String serviceName, Map<String, Object> configuration, String key) {
        Object value = configuration.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw fail(serviceName, "`" + key + ":` must be a string");
        }
        return text;
    }

    private static Double number(String serviceName, Map<String, Object> configuration,
            String key, String description) {
        Object value = configuration.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean || !(value instanceof Number number)) {
            throw fail(serviceName, "`" + key + ":` must be a number — " + description);
        }
        return number.doubleValue();
    }
}
