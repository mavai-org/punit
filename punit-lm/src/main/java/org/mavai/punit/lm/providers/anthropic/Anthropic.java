package org.mavai.punit.lm.providers.anthropic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.Set;
import org.mavai.punit.decl.spi.FileInput;
import org.mavai.punit.decl.spi.MediaKind;
import org.mavai.punit.lm.Json;
import org.mavai.punit.lm.LanguageModelParameters;
import org.mavai.punit.lm.ServiceDeliveryException;
import org.mavai.punit.lm.providers.LmProvider;
import org.mavai.punit.lm.providers.Media;

/**
 * Anthropic: the messages protocol — system top-level,
 * {@code x-api-key} header, a required generation cap carried by the
 * resolved {@code max-tokens:} configuration key.
 *
 * <p>This adapter is the first realisation of the provider-neutral
 * {@code prompt-caching:} and {@code thinking:} keys.
 * {@code prompt-caching: true} marks the system block
 * {@code cache_control: ephemeral} — the first, cache-writing
 * invocation simply lands as the slowest recorded latency point.
 * {@code thinking: adaptive} passes the protocol's adaptive mode
 * through verbatim; with thinking enabled the protocol constrains
 * sampling parameters, so the combination with an explicit
 * {@code temperature:} or {@code top-p:} is the vendor constraint. A
 * declared {@code response-schema:} travels verbatim through the
 * structured-output mechanism; the endpoint validates it, per the
 * basic-adapter rule. No audio content block exists, so audio is
 * refused at the gate.
 */
public final class Anthropic {

    public static final LmProvider PROVIDER = new LmProvider(
            "anthropic", "https://api.anthropic.com/v1/messages", "ANTHROPIC_API_KEY", true,
            true, true, true,
            Set.of(), Set.of(MediaKind.IMAGE, MediaKind.DOCUMENT),
            Anthropic::constraint,
            Anthropic::body,
            Anthropic::headers,
            Anthropic::extract);

    private static final String VERSION = "2023-06-01";
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private Anthropic() {}

    private static String constraint(LanguageModelParameters parameters) {
        if (parameters.adaptiveThinking()
                && (parameters.temperature() != null || parameters.topP() != null)) {
            return "the anthropic API constrains sampling parameters when thinking is "
                    + "enabled — `thinking: adaptive` cannot be combined with an explicit "
                    + "`temperature:` or `top-p:`; remove the sampling key or set "
                    + "`thinking: none`";
        }
        return null;
    }

    /** One Anthropic content block for a media part (base64 source). */
    private static ObjectNode block(FileInput part) {
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

    private static ObjectNode body(LanguageModelParameters parameters, String model, Object input) {
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
                .set("content", Media.contentBlocks(input, Anthropic::block));
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

    private static Map<String, String> headers(String key) {
        return Map.of(
                "Content-Type", "application/json",
                "x-api-key", key == null ? "" : key,
                "anthropic-version", VERSION);
    }

    /**
     * With thinking enabled the assistant text is not the first content
     * block — thinking blocks precede it. The assistant text is the
     * first block of type {@code text}, wherever it sits.
     */
    private static org.mavai.punit.lm.api.LmReply extract(JsonNode payload) {
        for (JsonNode block : payload.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                JsonNode usage = payload.path("usage");
                if (usage.path("input_tokens").isNumber()
                        && usage.path("output_tokens").isNumber()) {
                    return org.mavai.punit.lm.api.LmReply.of(block.path("text").asText(),
                            usage.path("input_tokens").asLong(),
                            usage.path("output_tokens").asLong());
                }
                return org.mavai.punit.lm.api.LmReply.of(block.path("text").asText());
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
}
