package org.mavai.punit.lm.providers.litellm;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;
import org.mavai.punit.lm.LanguageModelParameters;
import org.mavai.punit.lm.providers.LmProvider;
import org.mavai.punit.lm.providers.OpenAiCompatible;

/**
 * litellm: an OpenAI-compatible LLM gateway — not a vendor. It fronts
 * many upstream models behind one surface, and a model <em>alias</em>
 * names a capability the gateway resolves — so the adapter cannot know
 * from its protocol whether the aliased upstream honours structured
 * output, prompt caching, or thinking. The three static capability
 * flags are therefore off: the adapter honours nothing on its own. The
 * contract author turns a capability on with the service's
 * {@code capabilities:} allowance, and this body encodes what was
 * turned on, following litellm's canonical pass-through forms
 * ({@code response_format} for a schema, {@code cache_control} on the
 * system block, a reasoning parameter for thinking). The exact wire
 * form a given gateway version and upstream honour is an operational
 * fact to confirm against the live gateway before a baseline is
 * trusted.
 *
 * <p>Two hazards a gateway invites: routing that changes which model
 * answers (fallback, load-balancing, mid-run failover) is inadmissible
 * in a measured run — the adapter sends one plain request per
 * invocation and configures none of it; and alias mutability — an
 * operator repointing an alias leaves the recorded {@code model:}
 * string byte-identical while the measured service has changed, which
 * the party operating the gateway owns not doing silently.
 */
public final class LiteLlm {

    public static final LmProvider PROVIDER = new LmProvider(
            "litellm", null, "LITELLM_API_KEY", true,
            false, false, false,
            Set.copyOf(LmProvider.CAPABILITY_NAMES), OpenAiCompatible.MEDIA_KINDS,
            parameters -> null,
            LiteLlm::body,
            OpenAiCompatible::bearerHeaders,
            OpenAiCompatible::extract);

    /**
     * litellm normalises thinking to an OpenAI-style reasoning effort
     * across upstreams; {@code adaptive} maps to a mid effort — the
     * mapping, and whether the aliased upstream honours it, is the
     * wire-form fact to confirm against the live gateway.
     */
    private static final String ADAPTIVE_REASONING_EFFORT = "medium";

    private LiteLlm() {}

    private static ObjectNode body(LanguageModelParameters parameters, String model, Object input) {
        ObjectNode body = OpenAiCompatible.body(parameters, model, input);
        if (Boolean.TRUE.equals(parameters.promptCaching())) {
            ObjectNode system = (ObjectNode) body.path("messages").path(0);
            system.putArray("content").addObject()
                    .put("type", "text")
                    .put("text", parameters.systemPrompt())
                    .putObject("cache_control").put("type", "ephemeral");
        }
        if (parameters.adaptiveThinking()) {
            body.put("reasoning_effort", ADAPTIVE_REASONING_EFFORT);
        }
        return body;
    }
}
