package org.mavai.punit.lm.providers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.mavai.punit.decl.spi.MediaKind;
import org.mavai.punit.lm.LanguageModelParameters;

/**
 * One vendor adapter: protocol shape, defaults, and capability
 * support. Deliberately absent from every adapter, as a rule and not
 * an omission: retries, backoff, client-side response caching,
 * streaming, tool use — a silently retried failure is a resampled
 * trial and biases the observed rate; sampling independence outranks
 * API convenience. One invocation, one request.
 *
 * @param name the {@code provider:} value contract authors declare
 * @param defaultEndpoint where requests go when the environment does
 *        not override; {@code null} means an endpoint is mandatory
 * @param credentialFallbackVariable the vendor's conventional
 *        credential environment variable, consulted when the family
 *        variable is unset; {@code null} when the vendor has none
 * @param credentialRequired whether a missing credential is a
 *        load-time refusal
 * @param supportsResponseSchema whether a declared
 *        {@code response-schema:} can be honoured; when {@code false},
 *        declaring one is refused at load — never silently dropped
 * @param supportsPromptCaching whether {@code prompt-caching: true}
 *        can be honoured; same refusal rule
 * @param supportsThinking whether {@code thinking: adaptive} can be
 *        honoured; same refusal rule
 * @param extraDeclarableCapabilities capabilities beyond the
 *        statically supported set that an author may turn on with the
 *        {@code capabilities:} allowance — what the adapter's body can
 *        encode on demand but does not honour by default; empty for a
 *        vendor adapter, populated for a gateway adapter
 * @param mediaKinds the media kinds this adapter's protocol can put on
 *        the wire
 * @param constraint the vendor's own veto over an otherwise-valid
 *        configuration combination — a refusal message, or
 *        {@code null} when the combination is fine; checked at load
 * @param body composes one request body from (parameters, model, input)
 * @param headers composes the request headers from the resolved credential
 * @param extract pulls the reply — response text plus any reported
 *        token usage — out of the vendor's reply shape; a
 *        delivered-but-odd shape throws {@link ServiceDeliveryException}
 */
public record LmProvider(
        String name,
        String defaultEndpoint,
        String credentialFallbackVariable,
        boolean credentialRequired,
        boolean supportsResponseSchema,
        boolean supportsPromptCaching,
        boolean supportsThinking,
        Set<String> extraDeclarableCapabilities,
        Set<MediaKind> mediaKinds,
        Function<LanguageModelParameters, String> constraint,
        BodyBuilder body,
        Function<String, Map<String, String>> headers,
        Function<JsonNode, org.mavai.punit.lm.api.LmReply> extract) {

    /**
     * The provider-neutral capability vocabulary an author may name in
     * a service's {@code capabilities:} allowance. The first three each
     * gate one configuration key; the media tokens gate an input
     * modality. Lives on the provider model (not the registry) so a
     * gateway adapter can declare the whole vocabulary without a
     * class-initialisation cycle.
     */
    public static final java.util.List<String> CAPABILITY_NAMES = java.util.List.of(
            "response-schema", "prompt-caching", "thinking",
            "image-input", "document-input", "audio-input");

    public interface BodyBuilder {
        ObjectNode build(LanguageModelParameters parameters, String model, Object input);
    }
}
