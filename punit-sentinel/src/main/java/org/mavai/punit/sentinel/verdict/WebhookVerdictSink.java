package org.mavai.punit.sentinel.verdict;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.mavai.punit.verdict.ProbabilisticTestVerdict;
import org.mavai.punit.verdict.VerdictSink;

/**
 * A {@link VerdictSink} that posts verdicts as JSON to an HTTP endpoint.
 *
 * <p>Uses {@link java.net.http.HttpClient} — no additional dependencies required.
 *
 * <pre>{@code
 * VerdictSink webhook = WebhookVerdictSink.builder("https://alerts.example.com/punit")
 *     .timeout(Duration.ofSeconds(10))
 *     .header("Authorization", "Bearer " + apiKey)
 *     .build();
 * }</pre>
 */
public final class WebhookVerdictSink implements VerdictSink {

    private final URI endpoint;
    private final Duration timeout;
    private final Map<String, String> headers;
    private final HttpClient httpClient;

    private WebhookVerdictSink(Builder builder) {
        this.endpoint = URI.create(builder.url);
        this.timeout = builder.timeout;
        this.headers = Map.copyOf(builder.headers);
        this.httpClient = builder.httpClient != null
                ? builder.httpClient
                : HttpClient.newBuilder()
                        .connectTimeout(timeout)
                        .build();
    }

    @Override
    public void accept(ProbabilisticTestVerdict verdict) {
        String json = toJson(verdict);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));

        headers.forEach(requestBuilder::header);

        try {
            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                System.err.println("Webhook " + endpoint + " returned HTTP "
                        + response.statusCode());
            }
        } catch (Exception e) {
            System.err.println("Webhook " + endpoint + " failed: " + e.getMessage());
        }
    }

    /**
     * Creates a builder for a webhook sink targeting the specified URL.
     *
     * @param url the webhook endpoint URL
     * @return a new builder
     */
    public static Builder builder(String url) {
        return new Builder(url);
    }

    static String toJson(ProbabilisticTestVerdict verdict) {
        String testName = verdict.identity().className() + "." + verdict.identity().methodName();
        String serviceContractId = verdict.identity().serviceContractId().orElse(testName);

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"correlationId\":").append(quote(verdict.correlationId())).append(",");
        sb.append("\"testName\":").append(quote(testName)).append(",");
        sb.append("\"serviceContractId\":").append(quote(serviceContractId)).append(",");
        sb.append("\"passed\":").append(verdict.junitPassed()).append(",");
        sb.append("\"punitVerdict\":").append(quote(verdict.punitVerdict().name())).append(",");
        sb.append("\"timestamp\":").append(quote(verdict.timestamp().toString())).append(",");
        sb.append("\"environmentMetadata\":").append(mapToJson(verdict.environmentMetadata()));
        sb.append("}");
        return sb.toString();
    }

    private static String mapToJson(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append(quote(entry.getKey())).append(":").append(quote(entry.getValue()));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    public static final class Builder {

        private final String url;
        private Duration timeout = Duration.ofSeconds(30);
        private final java.util.HashMap<String, String> headers = new java.util.HashMap<>();
        private HttpClient httpClient;

        private Builder(String url) {
            Objects.requireNonNull(url, "url must not be null");
            this.url = url;
        }

        /**
         * Sets the request timeout. Default: 30 seconds.
         */
        public Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout);
            return this;
        }

        /**
         * Adds a custom HTTP header (e.g., for authentication).
         */
        public Builder header(String name, String value) {
            this.headers.put(name, value);
            return this;
        }

        /**
         * Sets a custom HTTP client (primarily for testing).
         */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public WebhookVerdictSink build() {
            return new WebhookVerdictSink(this);
        }
    }
}
