package org.mavai.punit.api;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines a single ExperimentConfig: a concrete combination of factor levels.
 *
 * <p>Each config represents one executable configuration. When used within
 * {@link ExperimentDesign}, configs execute in listed order.
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * @ExperimentDesign({
 *     @Config(params = {"model = gpt-4", "temperature = 0.2"}),
 *     @Config(model = "gpt-3.5-turbo", temperature = 0.0),
 *     @Config(model = "gpt-4.1-mini", temperature = 0.5)
 * })
 * }</pre>
 *
 * @see ExperimentDesign
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface Config {
    
    /**
     * Key-value pairs defining this config's factor→level mappings.
     *
     * <p>Format: {@code "factor = level"}
     *
     * <p>Example: {@code @Config(params = {"model = gpt-4", "temperature = 0.2"})}
     *
     * @return the parameter definitions
     */
    String[] params() default {};
    
    /**
     * Convenience attribute for the LLM model.
     *
     * <p>Backend-specific; framework ignores if not applicable.
     *
     * @return the model identifier
     */
    String model() default "";
    
    /**
     * Convenience attribute for the sampling temperature.
     *
     * <p>Backend-specific; framework ignores if not applicable.
     *
     * @return the temperature value (NaN if not specified)
     */
    double temperature() default Double.NaN;
    
    /**
     * Convenience attribute for maximum tokens.
     *
     * <p>Backend-specific; framework ignores if not applicable.
     *
     * @return the max tokens (-1 if not specified)
     */
    int maxTokens() default -1;
    
    /**
     * Optional name for this config (for identification in reports).
     *
     * @return the config name
     */
    String name() default "";
}

