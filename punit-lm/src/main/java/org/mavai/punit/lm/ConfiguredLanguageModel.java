package org.mavai.punit.lm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import org.mavai.outcome.Outcome;
import org.mavai.punit.decl.ContractConfigurationException;
import org.mavai.punit.decl.spi.ConfiguredService;
import org.mavai.punit.decl.spi.MediaKind;
import org.mavai.punit.lm.providers.LmProvider;
import org.mavai.punit.lm.providers.Media;
import org.mavai.punit.lm.providers.Providers;

/**
 * One resolved language-model service: the per-sample invocation —
 * one plain request per call, never a retry — plus the resolved
 * configuration's provenance projection and the media capability gate.
 *
 * <p>The delivery taxonomy: a provider <em>rejection</em> (HTTP 4xx)
 * is a defect and aborts ({@link ProviderResponseException}); a failed
 * <em>delivery</em> (HTTP 5xx, an unreachable endpoint, a delivered
 * body not matching the vendor shape) is a failed sample on the
 * {@code Outcome} channel with its cause as the reason.
 */
final class ConfiguredLanguageModel implements ConfiguredService {

    private final LmProvider provider;
    private final LanguageModelParameters parameters;
    private final String endpoint;
    private final String model;
    private final Map<String, String> headers;
    private final HttpClient client;

    private ConfiguredLanguageModel(LmProvider provider, LanguageModelParameters parameters,
            String endpoint, String model, Map<String, String> headers) {
        this.provider = provider;
        this.parameters = parameters;
        this.endpoint = endpoint;
        this.model = model;
        this.headers = headers;
        this.client = HttpClient.newHttpClient();
    }

    /**
     * Resolves the environment tier — endpoint, model, credential —
     * and returns the invocable service. Every resolution failure
     * refuses here, at load, so it never costs a sample.
     */
    static ConfiguredLanguageModel of(String serviceName, LanguageModelParameters parameters) {
        LmProvider provider = Providers.resolve(parameters.provider());
        String endpoint = resolveEndpoint(serviceName, provider);
        String model = resolveModel(serviceName, parameters);
        String key = resolveCredential(serviceName, provider);
        return new ConfiguredLanguageModel(
                provider, parameters, endpoint, model, provider.headers().apply(key));
    }

    @Override
    public Outcome<String> invoke(Object input) {
        String body = Json.write(provider.body().build(parameters, model, input));
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(endpoint))
                .POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(request::header);
        final HttpResponse<String> response;
        try {
            response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException unreachable) {
            // No response at all — DNS, refused connection, broken pipe:
            // the service did not deliver, a failed sample with its cause.
            return Outcome.fail("service-delivery",
                    "service unreachable at " + endpoint + ": " + unreachable.getMessage());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "interrupted while awaiting the " + provider.name() + " response",
                    interrupted);
        }
        if (response.statusCode() >= 500) {
            // The service answered that it is failing: a failed delivery.
            String detail = Json.excerpt(response.body(), 200);
            return Outcome.fail("service-delivery",
                    "service failed to deliver: " + provider.name() + " answered HTTP "
                            + response.statusCode() + " at " + endpoint
                            + (detail.isEmpty() ? "" : " — " + detail));
        }
        if (response.statusCode() >= 400) {
            throw new ProviderResponseException(
                    provider.name(), response.statusCode(), Json.excerpt(response.body(), 2000));
        }
        // A 2xx body that does not match the vendor's shape is still a
        // delivered response — counted as a failed sample with the cause.
        try {
            return Outcome.ok(provider.extract().apply(Json.read(response.body(), provider.name())));
        } catch (ServiceDeliveryException undelivered) {
            return Outcome.fail("service-delivery", undelivered.getMessage());
        }
    }

    @Override
    public Map<String, String> configurationCovariates() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("serviceType", "language-model");
        entries.put("provider", provider.name());
        entries.put("systemPrompt", parameters.systemPrompt());
        entries.put("model", model);
        // The capability allowance is part of identity: widening what a
        // service was permitted to send changes what was measured.
        // Recorded sorted for a deterministic fingerprint.
        if (parameters.capabilities() != null && !parameters.capabilities().isEmpty()) {
            entries.put("capabilities",
                    String.join(",", new TreeSet<>(parameters.capabilities())));
        }
        if (parameters.temperature() != null) {
            entries.put("temperature", String.valueOf(parameters.temperature()));
        }
        if (parameters.topP() != null) {
            entries.put("topP", String.valueOf(parameters.topP()));
        }
        if (parameters.thinking() != null) {
            entries.put("thinking", parameters.thinking());
        }
        if (parameters.promptCaching() != null) {
            entries.put("promptCaching", String.valueOf(parameters.promptCaching()));
        }
        if (parameters.responseSchema() != null) {
            entries.put("responseSchemaFingerprint", Json.fingerprint(parameters.responseSchema()));
        }
        // The output ceiling shapes the response as strongly as any
        // sampling parameter, so it is fingerprinted like one.
        entries.put("maxTokens", String.valueOf(parameters.maxTokens()));
        return entries;
    }

    /**
     * The media capability gate: a media kind is carried when the
     * adapter's protocol can encode it <em>and</em> the service
     * declared the matching capability — an undeclared capability is
     * never sent silently. The {@code file} kind is deliver-to-binding
     * only; it has no model wire form and is always refused.
     */
    @Override
    public void admit(Object input) {
        for (MediaKind kind : Media.mediaKindsPresent(input)) {
            String token = Media.CAPABILITY_FOR.get(kind);
            if (token == null || !provider.mediaKinds().contains(kind)) {
                throw new ContractConfigurationException(
                        "provider '" + provider.name() + "' cannot carry " + kind.key()
                                + " input to the model — its request protocol has no content "
                                + "block for it. Choose a provider that does, or hand the file "
                                + "to a bound service instead.");
            }
            if (!Providers.honours(provider, parameters.capabilities(), token)) {
                throw new ContractConfigurationException(
                        "provider '" + provider.name() + "' can carry " + kind.key()
                                + " input, but the service has not allowed it — add "
                                + "`capabilities: [" + token + "]` to the service configuration "
                                + "so the media is sent (an undeclared capability is never sent "
                                + "silently).");
            }
        }
    }

    private static String resolveEndpoint(String serviceName, LmProvider provider) {
        String endpoint = LmEnvironment.endpoint();
        if (endpoint != null) {
            return endpoint;
        }
        if (provider.defaultEndpoint() == null) {
            throw new ContractConfigurationException(
                    "service '" + serviceName + "': provider '" + provider.name() + "' has no "
                            + "default endpoint — set " + LmEnvironment.ENDPOINT_VARIABLE
                            + " (an OpenAI-compatible chat-completions endpoint)");
        }
        return provider.defaultEndpoint();
    }

    private static String resolveModel(String serviceName, LanguageModelParameters parameters) {
        if (parameters.model() != null) {
            return parameters.model();
        }
        String model = LmEnvironment.model();
        if (model == null) {
            throw new ContractConfigurationException(
                    "service '" + serviceName + "': no model declared and "
                            + LmEnvironment.MODEL_VARIABLE + " is not set — declare `model:` in "
                            + "the service configuration or set the environment default");
        }
        return model;
    }

    private static String resolveCredential(String serviceName, LmProvider provider) {
        String key = LmEnvironment.apiKey();
        if (key == null && provider.credentialFallbackVariable() != null) {
            key = LmEnvironment.variable(provider.credentialFallbackVariable());
        }
        if (key == null && provider.credentialRequired()) {
            throw new ContractConfigurationException(
                    "service '" + serviceName + "': provider '" + provider.name() + "' needs a "
                            + "credential — set " + LmEnvironment.API_KEY_VARIABLE
                            + (provider.credentialFallbackVariable() == null ? ""
                                    : " or " + provider.credentialFallbackVariable()));
        }
        return key;
    }
}
