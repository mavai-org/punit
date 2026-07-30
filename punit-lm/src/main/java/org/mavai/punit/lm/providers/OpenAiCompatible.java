package org.mavai.punit.lm.providers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.mavai.punit.decl.spi.FileInput;
import org.mavai.punit.decl.spi.MediaKind;
import org.mavai.punit.lm.Json;
import org.mavai.punit.lm.LanguageModelParameters;
import org.mavai.punit.lm.ServiceDeliveryException;

/**
 * The shared OpenAI-compatible chat-completions protocol shapes.
 * Vendor adapters compose these; a vendor with a genuinely different
 * wire protocol (anthropic, ollama) declares its own body and extract
 * functions in its own package instead.
 */
public final class OpenAiCompatible {

    /**
     * The media kinds the OpenAI-compatible protocol can put on the
     * wire — shared by every adapter composing {@link #body}.
     */
    public static final Set<MediaKind> MEDIA_KINDS =
            Set.of(MediaKind.IMAGE, MediaKind.DOCUMENT, MediaKind.AUDIO);

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private OpenAiCompatible() {}

    /** Authorization: Bearer — the OpenAI-compatible convention. */
    public static Map<String, String> bearerHeaders(String key) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        if (key != null && !key.isEmpty()) {
            headers.put("Authorization", "Bearer " + key);
        }
        return headers;
    }

    /** No credential header (local inference). */
    public static Map<String, String> plainHeaders(String key) {
        return Map.of("Content-Type", "application/json");
    }

    /** One OpenAI-compatible content block for a media part. */
    static ObjectNode block(FileInput part) {
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
    public static ObjectNode body(LanguageModelParameters parameters, String model, Object input) {
        ObjectNode body = NODES.objectNode();
        body.put("model", model);
        var messages = body.putArray("messages");
        messages.addObject()
                .put("role", "system")
                .put("content", parameters.systemPrompt());
        messages.addObject()
                .put("role", "user")
                .set("content", Media.contentBlocks(input, OpenAiCompatible::block));
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

    /**
     * {@code choices[0].message.content}, with the protocol's
     * {@code usage.prompt_tokens}/{@code completion_tokens} beside it
     * when the endpoint reports them (a gateway may not).
     */
    public static org.mavai.punit.lm.api.LmReply extract(JsonNode payload) {
        JsonNode content = payload.path("choices").path(0).path("message").path("content");
        if (!content.isTextual()) {
            throw new ServiceDeliveryException("service delivered a response with no text "
                    + "content (choices[0].message.content held "
                    + content.getNodeType().name().toLowerCase(Locale.ROOT) + ")");
        }
        JsonNode usage = payload.path("usage");
        if (usage.path("prompt_tokens").isNumber() && usage.path("completion_tokens").isNumber()) {
            return org.mavai.punit.lm.api.LmReply.of(content.asText(),
                    usage.path("prompt_tokens").asLong(),
                    usage.path("completion_tokens").asLong());
        }
        return org.mavai.punit.lm.api.LmReply.of(content.asText());
    }
}
