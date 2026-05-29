package org.mavai.punit.api;

import java.util.Objects;

/**
 * A named, immutable service contract configuration for EXPLORE experiments.
 *
 * <p>Each instance pairs a human-readable name with a fully-constructed service contract.
 * The name is used for output file naming and display; the instance carries its
 * own configuration (model, temperature, etc.) — it <em>is</em> the factor
 * specification.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * static Stream<NamedConfig<MyServiceContract>> configs() {
 *     return Stream.of(
 *         NamedConfig.of("low-temp", new MyServiceContract(llm, "gpt-4o", 0.1)),
 *         NamedConfig.of("high-temp", new MyServiceContract(llm, "gpt-4o", 0.9))
 *     );
 * }
 * }</pre>
 *
 * @param name     display name, also used in output file paths
 * @param instance the fully-configured service contract instance
 * @param <T>      service contract type
 * @see ConfigSource
 */
public record NamedConfig<T>(String name, T instance) {

    public NamedConfig {
        Objects.requireNonNull(name, "config name must not be null");
        Objects.requireNonNull(instance, "config instance must not be null");
    }

    /**
     * Creates a named configuration.
     *
     * @param name     the configuration name
     * @param instance the service contract instance
     * @param <T>      service contract type
     * @return a new NamedConfig
     */
    public static <T> NamedConfig<T> of(String name, T instance) {
        return new NamedConfig<>(name, instance);
    }
}
