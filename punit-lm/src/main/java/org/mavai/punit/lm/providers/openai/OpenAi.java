package org.mavai.punit.lm.providers.openai;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;
import org.mavai.punit.lm.LanguageModelParameters;
import org.mavai.punit.lm.providers.LmProvider;
import org.mavai.punit.lm.providers.OpenAiCompatible;

/**
 * OpenAI: the reference chat-completions protocol. The current API
 * names the output ceiling {@code max_completion_tokens} (the older
 * {@code max_tokens} is rejected by reasoning models), so the shared
 * body's field is renamed here — the one place OpenAI diverges from
 * the Mistral/Apertus dialect that keeps {@code max_tokens}.
 */
public final class OpenAi {

    public static final LmProvider PROVIDER = new LmProvider(
            "openai", "https://api.openai.com/v1/chat/completions", "OPENAI_API_KEY", true,
            true, false, false,
            Set.of(), OpenAiCompatible.MEDIA_KINDS,
            parameters -> null,
            OpenAi::body,
            OpenAiCompatible::bearerHeaders,
            OpenAiCompatible::extract);

    private OpenAi() {}

    private static ObjectNode body(LanguageModelParameters parameters, String model, Object input) {
        ObjectNode body = OpenAiCompatible.body(parameters, model, input);
        body.set("max_completion_tokens", body.remove("max_tokens"));
        return body;
    }
}
