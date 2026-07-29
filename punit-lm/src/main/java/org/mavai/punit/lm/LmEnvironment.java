package org.mavai.punit.lm;

/**
 * The environment tier in punit's resolution-order idiom: a system
 * property first, then the family environment variable. Where the
 * service <em>runs</em> — endpoint, credential, default model tier —
 * lives here; what the service <em>is</em> lives in the configuration.
 * Secrets never appear in the declarative files.
 */
final class LmEnvironment {

    static final String ENDPOINT_PROPERTY = "mavai.llm.endpoint";
    static final String ENDPOINT_VARIABLE = "MAVAI_LLM_ENDPOINT";
    static final String API_KEY_PROPERTY = "mavai.llm.api-key";
    static final String API_KEY_VARIABLE = "MAVAI_LLM_API_KEY";
    static final String MODEL_PROPERTY = "mavai.llm.model";
    static final String MODEL_VARIABLE = "MAVAI_LLM_MODEL";

    private LmEnvironment() {}

    static String endpoint() {
        return value(ENDPOINT_PROPERTY, ENDPOINT_VARIABLE);
    }

    static String apiKey() {
        return value(API_KEY_PROPERTY, API_KEY_VARIABLE);
    }

    static String model() {
        return value(MODEL_PROPERTY, MODEL_VARIABLE);
    }

    /** A vendor's conventional credential fallback — environment-only. */
    static String variable(String name) {
        String value = System.getenv(name);
        return value == null || value.isEmpty() ? null : value;
    }

    private static String value(String property, String environmentVariable) {
        String fromProperty = System.getProperty(property);
        if (fromProperty != null && !fromProperty.isEmpty()) {
            return fromProperty;
        }
        return variable(environmentVariable);
    }
}
