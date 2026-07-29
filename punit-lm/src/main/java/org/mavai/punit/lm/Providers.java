package org.mavai.punit.lm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.mavai.punit.decl.ContractConfigurationException;
import org.mavai.punit.decl.spi.FileInput;
import org.mavai.punit.decl.spi.MediaKind;

/**
 * The named provider adapters — basic integrations, nothing clever —
 * and the shared OpenAI-compatible protocol shapes the vendor entries
 * compose. A vendor with a genuinely different wire protocol
 * (anthropic, ollama) declares its own body/extract functions.
 */
final class Providers {

    /**
     * The provider-neutral capability vocabulary an author may name in
     * a service's {@code capabilities:} allowance. The first three each
     * gate one configuration key; the media tokens gate an input
     * modality.
     */
    static final List<String> CAPABILITY_NAMES = List.of(
            "response-schema", "prompt-caching", "thinking",
            "image-input", "document-input", "audio-input");

    /**
     * The media kinds the OpenAI-compatible protocol can put on the
     * wire — shared by every adapter composing the generic body.
     */
    private static final Set<MediaKind> OPENAI_MEDIA_KINDS =
            Set.of(MediaKind.IMAGE, MediaKind.DOCUMENT, MediaKind.AUDIO);

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    /**
     * litellm normalises thinking to an OpenAI-style reasoning effort
     * across upstreams; {@code adaptive} maps to a mid effort — the
     * mapping, and whether the aliased upstream honours it, is the
     * wire-form fact to confirm against the live gateway.
     */
    private static final String LITELLM_ADAPTIVE_REASONING_EFFORT = "medium";

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    /** The generic OpenAI-compatible adapter, used when {@code provider:} is omitted. */
    static final LmProvider GENERIC = new LmProvider(
            "openai-compatible", null, null, false,
            true, false, false,
            Set.of(), OPENAI_MEDIA_KINDS,
            parameters -> null,
            Providers::openAiCompatibleBody,
            Providers::bearerHeaders,
            Providers::openAiCompatibleExtract);

    private static final Map<String, LmProvider> PROVIDERS = registry();

    private Providers() {}

    /** The adapter for a declared provider name, or the generic one. */
    static LmProvider resolve(String name) {
        if (name == null) {
            return GENERIC;
        }
        LmProvider provider = PROVIDERS.get(name);
        if (provider == null) {
            throw new ContractConfigurationException(
                    "unknown `provider: " + name + "` — supported: "
                            + String.join(", ", new TreeMap<>(PROVIDERS).keySet())
                            + " (or omit `provider:` for a generic OpenAI-compatible endpoint)");
        }
        return provider;
    }

    /** The capabilities the adapter honours without any author declaration. */
    static Set<String> staticallySupported(LmProvider provider) {
        Set<String> supported = new LinkedHashSet<>();
        if (provider.supportsResponseSchema()) {
            supported.add("response-schema");
        }
        if (provider.supportsPromptCaching()) {
            supported.add("prompt-caching");
        }
        if (provider.supportsThinking()) {
            supported.add("thinking");
        }
        return supported;
    }

    /**
     * The capabilities an author may assert via {@code capabilities:} —
     * the statically supported set, widened by what the adapter can
     * encode on demand, widened by the media token for each modality
     * the adapter's protocol carries. An adapter that deliberately
     * withholds a capability leaves it out, so declaring it there is
     * refused rather than silently overriding the caution.
     */
    static Set<String> declarableCapabilities(LmProvider provider) {
        Set<String> declarable = new LinkedHashSet<>(staticallySupported(provider));
        declarable.addAll(provider.extraDeclarableCapabilities());
        for (MediaKind kind : provider.mediaKinds()) {
            declarable.add(Media.CAPABILITY_FOR.get(kind));
        }
        return declarable;
    }

    /**
     * Effective support: honoured by default, or turned on by the
     * author — the single question both the refuse-at-load
     * (test/measure) and the degrade-with-note (explore) paths ask, so
     * they stay consistent.
     */
    static boolean honours(LmProvider provider, Set<String> declared, String capability) {
        if (staticallySupported(provider).contains(capability)) {
            return true;
        }
        return declared != null && declared.contains(capability);
    }

    // ── The shared OpenAI-compatible protocol ─────────────────────

    /** Authorization: Bearer — the OpenAI-compatible convention. */
    static Map<String, String> bearerHeaders(String key) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        if (key != null && !key.isEmpty()) {
            headers.put("Authorization", "Bearer " + key);
        }
        return headers;
    }

    /** No credential header (local inference). */
    static Map<String, String> plainHeaders(String key) {
        return Map.of("Content-Type", "application/json");
    }

    /** One OpenAI-compatible content block for a media part. */
    private static ObjectNode openAiBlock(FileInput part) {
        ObjectNode block = NODES.objectNode();
        switch (part.kind()) {
            case IMAGE -> block.put("type", "image_url")
                    .putObject("image_url").put("url", Media.dataUri(part));
            case AUDIO -> block.put("type", "input_audio")
                    .putObject("input_audio")
                    .put("data", Media.b64(part))
                    .put("format", Media.extension(part));
            case DOCUMENT -> block.put("type", "file")
                    .putObject("file")
                    .put("filename", part.path().getFileName().toString())
                    .put("file_data", Media.dataUri(part));
            default -> throw Media.unexpectedKind(part, "openai-compatible");
        }
        return block;
    }

    /**
     * The chat-completions request body, with structured output when
     * declared. The user message's content is the plain prompt when
     * the input is text-only, an ordered list of typed blocks when it
     * carries media.
     */
    static ObjectNode openAiCompatibleBody(
            LanguageModelParameters parameters, String model, Object input) {
        ObjectNode body = NODES.objectNode();
        body.put("model", model);
        var messages = body.putArray("messages");
        messages.addObject()
                .put("role", "system")
                .put("content", parameters.systemPrompt());
        messages.addObject()
                .put("role", "user")
                .set("content", Media.contentBlocks(input, Providers::openAiBlock));
        body.put("max_tokens", parameters.maxTokens());
        if (parameters.temperature() != null) {
            body.put("temperature", parameters.temperature());
        }
        if (parameters.topP() != null) {
            body.put("top_p", parameters.topP());
        }
        if (parameters.responseSchema() != null) {
            ObjectNode format = body.putObject("response_format");
            format.put("type", "json_schema");
            ObjectNode schema = format.putObject("json_schema");
            schema.put("name", "response");
            schema.set("schema", Json.node(parameters.responseSchema()));
        }
        return body;
    }

    /** {@code choices[0].message.content}. */
    static String openAiCompatibleExtract(JsonNode payload) {
        JsonNode content = payload.path("choices").path(0).path("message").path("content");
        if (!content.isTextual()) {
            throw new ServiceDeliveryException("service delivered a response with no text "
                    + "content (choices[0].message.content held "
                    + content.getNodeType().name().toLowerCase(java.util.Locale.ROOT) + ")");
        }
        return content.asText();
    }

    // ── Vendor adapters ───────────────────────────────────────────

    private static Map<String, LmProvider> registry() {
        Map<String, LmProvider> providers = new LinkedHashMap<>();
        providers.put("openai", openAi());
        providers.put("anthropic", anthropic());
        providers.put("ollama", ollama());
        providers.put("mistral", mistral());
        providers.put("apertus", apertus());
        providers.put("litellm", litellm());
        return providers;
    }

    /**
     * OpenAI: the reference chat-completions protocol. The current API
     * names the output ceiling {@code max_completion_tokens} (the older
     * {@code max_tokens} is rejected by reasoning models) — the one
     * place OpenAI diverges from the Mistral/Apertus dialect.
     */
    private static LmProvider openAi() {
        return new LmProvider(
                "openai", "https://api.openai.com/v1/chat/completions", "OPENAI_API_KEY", true,
                true, false, false,
                Set.of(), OPENAI_MEDIA_KINDS,
                parameters -> null,
                (parameters, model, input) -> {
                    ObjectNode body = openAiCompatibleBody(parameters, model, input);
                    body.set("max_completion_tokens", body.remove("max_tokens"));
                    return body;
                },
                Providers::bearerHeaders,
                Providers::openAiCompatibleExtract);
    }

    /**
     * Anthropic: the messages protocol — system top-level,
     * {@code x-api-key} header, a required generation cap carried by
     * the resolved {@code max-tokens:}. {@code prompt-caching: true}
     * marks the system block {@code cache_control: ephemeral};
     * {@code thinking: adaptive} passes the protocol's adaptive mode
     * through verbatim, and its sampling-parameter exclusion is the
     * vendor constraint. A declared {@code response-schema:} travels
     * verbatim through the structured-output mechanism. No audio
     * content block exists, so audio is refused at the gate.
     */
    private static LmProvider anthropic() {
        return new LmProvider(
                "anthropic", "https://api.anthropic.com/v1/messages", "ANTHROPIC_API_KEY", true,
                true, true, true,
                Set.of(), Set.of(MediaKind.IMAGE, MediaKind.DOCUMENT),
                parameters -> {
                    if (parameters.adaptiveThinking()
                            && (parameters.temperature() != null || parameters.topP() != null)) {
                        return "the anthropic API constrains sampling parameters when thinking "
                                + "is enabled — `thinking: adaptive` cannot be combined with an "
                                + "explicit `temperature:` or `top-p:`; remove the sampling key "
                                + "or set `thinking: none`";
                    }
                    return null;
                },
                Providers::anthropicBody,
                key -> Map.of(
                        "Content-Type", "application/json",
                        "x-api-key", key == null ? "" : key,
                        "anthropic-version", ANTHROPIC_VERSION),
                Providers::anthropicExtract);
    }

    private static ObjectNode anthropicBlock(FileInput part) {
        if (part.kind() != MediaKind.IMAGE && part.kind() != MediaKind.DOCUMENT) {
            throw Media.unexpectedKind(part, "anthropic");
        }
        ObjectNode block = NODES.objectNode();
        block.put("type", part.kind() == MediaKind.IMAGE ? "image" : "document");
        block.putObject("source")
                .put("type", "base64")
                .put("media_type", Media.mimeType(part))
                .put("data", Media.b64(part));
        return block;
    }

    private static ObjectNode anthropicBody(
            LanguageModelParameters parameters, String model, Object input) {
        ObjectNode body = NODES.objectNode();
        body.put("model", model);
        if (Boolean.TRUE.equals(parameters.promptCaching())) {
            body.putArray("system").addObject()
                    .put("type", "text")
                    .put("text", parameters.systemPrompt())
                    .putObject("cache_control").put("type", "ephemeral");
        } else {
            body.put("system", parameters.systemPrompt());
        }
        body.put("max_tokens", parameters.maxTokens());
        body.putArray("messages").addObject()
                .put("role", "user")
                .set("content", Media.contentBlocks(input, Providers::anthropicBlock));
        if (parameters.adaptiveThinking()) {
            body.putObject("thinking").put("type", "adaptive");
        }
        if (parameters.temperature() != null) {
            body.put("temperature", parameters.temperature());
        }
        if (parameters.topP() != null) {
            body.put("top_p", parameters.topP());
        }
        if (parameters.responseSchema() != null) {
            body.putObject("output_config").putObject("format")
                    .put("type", "json_schema")
                    .set("schema", Json.node(parameters.responseSchema()));
        }
        return body;
    }

    /**
     * With thinking enabled the assistant text is not the first content
     * block — thinking blocks precede it. The assistant text is the
     * first block of type {@code text}, wherever it sits.
     */
    private static String anthropicExtract(JsonNode payload) {
        for (JsonNode block : payload.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                return block.path("text").asText();
            }
        }
        StringBuilder kinds = new StringBuilder();
        for (JsonNode block : payload.path("content")) {
            kinds.append(kinds.isEmpty() ? "" : ", ").append(block.path("type").asText());
        }
        throw new ServiceDeliveryException("anthropic delivered a response with no text "
                + "content block (blocks: " + (kinds.isEmpty() ? "(none)" : kinds)
                + "; stop_reason: " + payload.path("stop_reason").asText(null) + ")");
    }

    /**
     * Ollama: local inference via {@code /api/chat} — no credential,
     * stream disabled, the output ceiling as {@code num_predict} under
     * {@code options}, a declared schema through the {@code format}
     * field. Images travel as a base64 array on the message, not as
     * content blocks; no document or audio form exists.
     */
    private static LmProvider ollama() {
        return new LmProvider(
                "ollama", "http://localhost:11434/api/chat", null, false,
                true, false, false,
                Set.of(), Set.of(MediaKind.IMAGE),
                parameters -> null,
                Providers::ollamaBody,
                Providers::plainHeaders,
                payload -> {
                    JsonNode content = payload.path("message").path("content");
                    if (!content.isTextual()) {
                        throw new ServiceDeliveryException("service delivered a response with "
                                + "no text content (the ollama message.content field held "
                                + content.getNodeType().name().toLowerCase(java.util.Locale.ROOT)
                                + ")");
                    }
                    return content.asText();
                });
    }

    private static ObjectNode ollamaBody(
            LanguageModelParameters parameters, String model, Object input) {
        ObjectNode body = NODES.objectNode();
        body.put("model", model);
        body.put("stream", false);
        var messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", parameters.systemPrompt());
        ObjectNode user = messages.addObject().put("role", "user");
        StringBuilder text = new StringBuilder();
        var images = NODES.arrayNode();
        for (Object part : Media.messageParts(input)) {
            if (part instanceof FileInput file) {
                images.add(Media.b64(file));
            } else {
                text.append(part);
            }
        }
        user.put("content", text.toString());
        if (!images.isEmpty()) {
            user.set("images", images);
        }
        ObjectNode options = body.putObject("options");
        options.put("num_predict", parameters.maxTokens());
        if (parameters.temperature() != null) {
            options.put("temperature", parameters.temperature());
        }
        if (parameters.topP() != null) {
            options.put("top_p", parameters.topP());
        }
        if (parameters.responseSchema() != null) {
            body.set("format", Json.node(parameters.responseSchema()));
        }
        return body;
    }

    /**
     * Mistral: OpenAI-compatible chat completions at api.mistral.ai.
     * Pixtral vision plus document understanding; no audio-to-chat
     * form (Mistral's audio is transcription, a deliver-to-binding
     * path).
     */
    private static LmProvider mistral() {
        return new LmProvider(
                "mistral", "https://api.mistral.ai/v1/chat/completions", "MISTRAL_API_KEY", true,
                true, false, false,
                Set.of(), Set.of(MediaKind.IMAGE, MediaKind.DOCUMENT),
                parameters -> null,
                Providers::openAiCompatibleBody,
                Providers::bearerHeaders,
                Providers::openAiCompatibleExtract);
    }

    /**
     * Apertus: the fully open Swiss model (EPFL, ETH Zürich, CSCS),
     * served OpenAI-compatibly via the Public AI inference utility.
     * Structured output is not asserted for the hosted endpoint, so a
     * declared schema is refused at load rather than passed through
     * unverified; self-hosted deployments that support it use the
     * generic path against their own endpoint.
     */
    private static LmProvider apertus() {
        return new LmProvider(
                "apertus", "https://api.publicai.co/v1/chat/completions", "PUBLICAI_API_KEY", true,
                false, false, false,
                Set.of(), Set.of(),
                parameters -> null,
                Providers::openAiCompatibleBody,
                Providers::bearerHeaders,
                Providers::openAiCompatibleExtract);
    }

    /**
     * litellm: an OpenAI-compatible LLM gateway — not a vendor. A model
     * alias names a capability the gateway resolves, so the adapter
     * honours nothing on its own; the author turns capabilities on with
     * the {@code capabilities:} allowance and this body encodes what
     * was turned on, following litellm's canonical pass-through forms.
     * A gateway has no canonical host, so the endpoint is required from
     * the environment.
     */
    private static LmProvider litellm() {
        return new LmProvider(
                "litellm", null, "LITELLM_API_KEY", true,
                false, false, false,
                Set.copyOf(CAPABILITY_NAMES), OPENAI_MEDIA_KINDS,
                parameters -> null,
                (parameters, model, input) -> {
                    ObjectNode body = openAiCompatibleBody(parameters, model, input);
                    if (Boolean.TRUE.equals(parameters.promptCaching())) {
                        var system = (ObjectNode) body.path("messages").path(0);
                        var content = system.putArray("content");
                        content.addObject()
                                .put("type", "text")
                                .put("text", parameters.systemPrompt())
                                .putObject("cache_control").put("type", "ephemeral");
                    }
                    if (parameters.adaptiveThinking()) {
                        body.put("reasoning_effort", LITELLM_ADAPTIVE_REASONING_EFFORT);
                    }
                    return body;
                },
                Providers::bearerHeaders,
                Providers::openAiCompatibleExtract);
    }
}
