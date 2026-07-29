package org.mavai.punit.lm.providers.mistral;

import java.util.Set;
import org.mavai.punit.decl.spi.MediaKind;
import org.mavai.punit.lm.providers.LmProvider;
import org.mavai.punit.lm.providers.OpenAiCompatible;

/**
 * Mistral: OpenAI-compatible chat completions at api.mistral.ai.
 * Pixtral vision plus document understanding; no audio-to-chat form
 * (Mistral's audio is transcription, a deliver-to-binding path).
 */
public final class Mistral {

    public static final LmProvider PROVIDER = new LmProvider(
            "mistral", "https://api.mistral.ai/v1/chat/completions", "MISTRAL_API_KEY", true,
            true, false, false,
            Set.of(), Set.of(MediaKind.IMAGE, MediaKind.DOCUMENT),
            parameters -> null,
            OpenAiCompatible::body,
            OpenAiCompatible::bearerHeaders,
            OpenAiCompatible::extract);

    private Mistral() {}
}
