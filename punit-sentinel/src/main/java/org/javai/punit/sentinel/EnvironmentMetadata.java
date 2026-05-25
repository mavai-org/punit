package org.javai.punit.sentinel;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

/**
 * Captures metadata about the environment in which the Sentinel is running.
 *
 * <p>This information is attached to every {@link org.javai.punit.verdict.ProbabilisticTestVerdict}
 * produced by the Sentinel, enabling operators to distinguish verdicts from different
 * environments (staging vs production) and instances (in multi-instance deployments).
 *
 * <p>Values are resolved from environment variables or system properties, with
 * sensible defaults when neither is set.
 *
 * @param environmentId identifies the environment, e.g., "prod", "staging", "us-east-1"
 * @param instanceId identifies the instance, e.g., hostname or pod name
 */
// javai-ref: JVI-R7NTQ6~ — do not remove (resolves in javai-orchestrator)
public record EnvironmentMetadata(
        String environmentId,
        String instanceId
) {

    private static final String ENV_ENVIRONMENT = "PUNIT_ENVIRONMENT";
    private static final String PROP_ENVIRONMENT = "punit.environment";
    private static final String ENV_INSTANCE_ID = "PUNIT_INSTANCE_ID";
    private static final String PROP_INSTANCE_ID = "punit.instanceId";

    /**
     * Creates an {@code EnvironmentMetadata} from the current environment.
     *
     * <p>Resolution order for each field: system property, then environment variable,
     * then default.
     *
     * @return metadata resolved from the current environment
     */
    public static EnvironmentMetadata fromEnvironment() {
        return new EnvironmentMetadata(
                resolve(PROP_ENVIRONMENT, ENV_ENVIRONMENT, "unknown"),
                resolve(PROP_INSTANCE_ID, ENV_INSTANCE_ID, hostname())
        );
    }

    /**
     * Returns this metadata as a map suitable for inclusion in a
     * {@link org.javai.punit.verdict.ProbabilisticTestVerdict}.
     *
     * @return an unmodifiable map of metadata key-value pairs
     */
    public Map<String, String> toMap() {
        return Map.of(
                "environment", environmentId,
                "instance", instanceId
        );
    }

    private static String resolve(String sysProp, String envVar, String defaultValue) {
        String value = System.getProperty(sysProp);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        value = System.getenv(envVar);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        return defaultValue;
    }

    private static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }
}
