package org.mavai.punit.lm.providers.apertus;

import java.util.Set;
import org.mavai.punit.lm.providers.LmProvider;
import org.mavai.punit.lm.providers.OpenAiCompatible;

/**
 * Apertus: the fully open Swiss model (EPFL, ETH Zürich, CSCS),
 * served OpenAI-compatibly. The default endpoint is the Public AI
 * inference utility, which hosts Apertus for public access;
 * self-hosters (the weights are open — vLLM serves them with the same
 * protocol) point the environment endpoint at their own deployment
 * instead.
 *
 * <p>Structured output is not asserted for the hosted endpoint, so a
 * declared {@code response-schema:} is refused at load rather than
 * passed through unverified; self-hosted deployments that support it
 * can use the generic OpenAI-compatible path (omit {@code provider:})
 * against their endpoint.
 */
public final class Apertus {

    public static final LmProvider PROVIDER = new LmProvider(
            "apertus", "https://api.publicai.co/v1/chat/completions", "PUBLICAI_API_KEY", true,
            false, false, false,
            Set.of(), Set.of(),
            parameters -> null,
            OpenAiCompatible::body,
            OpenAiCompatible::bearerHeaders,
            OpenAiCompatible::extract);

    private Apertus() {}
}
