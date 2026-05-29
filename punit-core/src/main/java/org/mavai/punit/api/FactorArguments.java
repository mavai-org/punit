package org.mavai.punit.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Represents a set of factor values for a single configuration in EXPLORE mode.
 *
 * <p>Each instance represents one treatment combination to test.
 *
 * <h2>Recommended: Explicit Configurations</h2>
 * <p>Define factor names once, then add each configuration explicitly.
 * This is the clearest and most practical approach for real experiments.
 *
 * <pre>{@code
 * static Stream<FactorArguments> configs() {
 *     return FactorArguments.configurations()
 *         .names("model", "temp", "query")
 *         .values("gpt-4", 0.0, "wireless headphones")
 *         .values("gpt-4", 0.7, "wireless headphones")
 *         .values("gpt-3.5-turbo", 0.0, "laptop stand")
 *         .stream();
 * }
 * }</pre>
 *
 * @see FactorSource
 * @see Factor
 */
public final class FactorArguments {
    
    private final String[] names;
    private final Object[] values;
    
    private FactorArguments(String[] names, Object[] values) {
        this.names = names;
        this.values = Objects.requireNonNull(values, "values must not be null");
    }
    
    /**
     * Creates a FactorArguments with values only (no names).
     *
     * <p>Use this when factor names are specified via {@code @FactorSource(factors = {...})}
     * or {@code @Factor} annotations.
     *
     * @param values the factor values
     * @return a new FactorArguments instance
     */
    public static FactorArguments of(Object... values) {
        return new FactorArguments(null, values);
    }
    
    /**
     * Starts building a set of configurations with explicit factor names and values.
     *
     * <p>This is the recommended approach for most experiments:
     * <ol>
     *   <li>Call {@code names(...)} to declare factor names</li>
     *   <li>Call {@code values(...)} for each configuration to test</li>
     *   <li>Call {@code stream()} to get the configurations</li>
     * </ol>
     *
     * <h2>Example</h2>
     * <pre>{@code
     * return FactorArguments.configurations()
     *     .names("model", "temp", "query")
     *     .values("gpt-4", 0.0, "headphones")
     *     .values("gpt-4", 0.7, "laptop")
     *     .stream();
     * }</pre>
     *
     * @return a configuration builder
     */
    public static ConfigurationBuilder configurations() {
        return new ConfigurationBuilder();
    }
    
    /**
     * Returns the factor names, or null if not specified.
     */
    public String[] names() {
        return names != null ? names.clone() : null;
    }
    
    /**
     * Returns true if this instance has embedded factor names.
     */
    public boolean hasNames() {
        return names != null && names.length > 0;
    }
    
    /**
     * Returns the factor values.
     */
    public Object[] get() {
        return values.clone();
    }
    
    /**
     * Returns the number of factor values.
     */
    public int size() {
        return values.length;
    }
    
    /**
     * Returns the factor value at the specified index.
     */
    public Object get(int index) {
        return values[index];
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FactorArguments that = (FactorArguments) o;
        return Arrays.equals(names, that.names) && Arrays.equals(values, that.values);
    }
    
    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(names) + Arrays.hashCode(values);
    }
    
    @Override
    public String toString() {
        if (names != null) {
            StringBuilder sb = new StringBuilder("FactorArguments{");
            for (int i = 0; i < values.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(names[i]).append("=").append(values[i]);
            }
            return sb.append("}").toString();
        }
        return "FactorArguments" + Arrays.toString(values);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // BUILDER
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Builder for defining factor configurations.
     *
     * <p>Usage:
     * <pre>{@code
     * FactorArguments.configurations()
     *     .names("model", "temp", "query")
     *     .values("gpt-4", 0.0, "headphones")
     *     .values("gpt-4", 0.7, "laptop")
     *     .stream();
     * }</pre>
     */
    public static class ConfigurationBuilder {
        private String[] names;
        private final List<Object[]> configs = new ArrayList<>();
        
        ConfigurationBuilder() {
        }
        
        /**
         * Declares the factor names. Must be called before {@code values()}.
         *
         * @param factorNames the names of each factor
         * @return this builder, ready for {@code values()} calls
         */
        public ConfigurationBuilder names(String... factorNames) {
            if (factorNames == null || factorNames.length == 0) {
                throw new IllegalArgumentException("At least one factor name is required");
            }
            this.names = factorNames;
            return this;
        }
        
        /**
         * Adds a configuration with the given values.
         *
         * <p>The values must match the order and count of factor names
         * declared in {@code names()}.
         *
         * @param factorValues the values for this configuration
         * @return this builder
         * @throws IllegalStateException if names() has not been called
         * @throws IllegalArgumentException if values count doesn't match names count
         */
        public ConfigurationBuilder values(Object... factorValues) {
            if (names == null) {
                throw new IllegalStateException(
                    "Call names(...) before values(...)");
            }
            if (factorValues.length != names.length) {
                throw new IllegalArgumentException(
                    "Expected " + names.length + " values for factors " + 
                    Arrays.toString(names) + ", got " + factorValues.length);
            }
            configs.add(factorValues);
            return this;
        }
        
        /**
         * Returns a stream of all configured factor combinations.
         *
         * @return stream of FactorArguments
         */
        public Stream<FactorArguments> stream() {
            if (names == null) {
                throw new IllegalStateException("Call names(...) before stream()");
            }
            return configs.stream()
                .map(values -> new FactorArguments(names, values));
        }
    }
}

