package org.mavai.punit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method parameter as a factor in EXPLORE mode experiments.
 *
 * <p>Factors are the independent variables in a Design of Experiments (DoE) approach.
 * Each factor can take multiple levels (values), and the experiment explores the
 * Cartesian product of all factor levels.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @Experiment(mode = ExperimentMode.EXPLORE, serviceContract = ShoppingServiceContract.class)
 * @FactorSource("configurations")
 * void exploreModelConfigs(
 *     @Factor("model") String model,
 *     @Factor("temperature") double temperature,
 *     ShoppingServiceContract serviceContract
 * ) {
 *     // Factors configure the service contract
 *     serviceContract.setModel(model);
 *     serviceContract.setTemperature(temperature);
 *     serviceContract.execute();
 * }
 *
 * static Stream<Arguments> configurations() {
 *     return Stream.of(
 *         Arguments.of("gpt-4", 0.0),
 *         Arguments.of("gpt-4", 0.7),
 *         Arguments.of("gpt-3.5-turbo", 0.0)
 *     );
 * }
 * }</pre>
 *
 * <h2>Naming in Output Files</h2>
 * <p>Factor values are used to derive output file names for each configuration:
 * <pre>
 * build/punit/baselines/
 * └── ShoppingServiceContract/
 *     ├── model-gpt-4_temperature-0.0.yaml
 *     ├── model-gpt-4_temperature-0.7.yaml
 *     └── model-gpt-3.5-turbo_temperature-0.0.yaml
 * </pre>
 *
 * @see FactorSource
 * @see ExperimentMode#EXPLORE
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Factor {
    
    /**
     * The name of this factor.
     *
     * <p>Used in output file naming and logging. Should be a short, descriptive
     * identifier (e.g., "model", "temperature", "query").
     *
     * @return the factor name
     */
    String value();
    
    /**
     * Optional prefix for file naming.
     *
     * <p>If specified, this prefix is used instead of the full factor name
     * when generating output file names. Useful for keeping file names short.
     *
     * <p>Example: {@code @Factor(value = "temperature", filePrefix = "t")}
     * produces file names like {@code model-gpt-4_t-0.7.yaml}
     *
     * @return the file prefix, or empty to use the factor name
     */
    String filePrefix() default "";
}

