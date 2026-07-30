package org.mavai.punit.lm.providers.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Locale;
import java.util.Set;
import org.mavai.punit.decl.spi.FileInput;
import org.mavai.punit.decl.spi.MediaKind;
import org.mavai.punit.lm.Json;
import org.mavai.punit.lm.LanguageModelParameters;
import org.mavai.punit.lm.ServiceDeliveryException;
import org.mavai.punit.lm.providers.LmProvider;
import org.mavai.punit.lm.providers.Media;
import org.mavai.punit.lm.providers.OpenAiCompatible;

/**
 * Ollama: local inference via {@code /api/chat} — no credential,
 * stream disabled, the output ceiling as {@code num_predict} nested
 * under {@code options}, a declared {@code response-schema:} through
 * the {@code format} field (which accepts a JSON Schema). Images
 * travel as a base64 array on the message, not as inline content
 * blocks; no document or audio form exists, so those are refused at
 * the gate.
 */
public final class Ollama {

    public static final LmProvider PROVIDER = new LmProvider(
            "ollama", "http://localhost:11434/api/chat", null, false,
            true, false, false,
            Set.of(), Set.of(MediaKind.IMAGE),
            parameters -> null,
            Ollama::body,
            OpenAiCompatible::plainHeaders,
            Ollama::extract);

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private Ollama() {}

    private static ObjectNode body(LanguageModelParameters parameters, String model, Object input) {
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

    private static org.mavai.punit.lm.api.LmReply extract(JsonNode payload) {
        JsonNode content = payload.path("message").path("content");
        if (!content.isTextual()) {
            throw new ServiceDeliveryException("service delivered a response with no text "
                    + "content (the ollama message.content field held "
                    + content.getNodeType().name().toLowerCase(Locale.ROOT) + ")");
        }
        if (payload.path("prompt_eval_count").isNumber()
                && payload.path("eval_count").isNumber()) {
            return org.mavai.punit.lm.api.LmReply.of(content.asText(),
                    payload.path("prompt_eval_count").asLong(),
                    payload.path("eval_count").asLong());
        }
        return org.mavai.punit.lm.api.LmReply.of(content.asText());
    }
}
