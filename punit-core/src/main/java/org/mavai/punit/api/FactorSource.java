package org.mavai.punit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies the source of factor combinations for EXPLORE mode experiments.
 *
 * <p>Similar to JUnit's {@code @MethodSource}, this annotation references a static
 * method that provides the factor combinations to explore. The method must return
 * a {@code Stream<FactorArguments>} where each {@link FactorArguments} instance 
 * represents one configuration to test.
 *
 * <h2>Design of Experiments (DoE) Perspective</h2>
 * <p>In DoE terminology:
 * <ul>
 *   <li><b>Factors</b> are the independent variables (model, temperature, etc.)</li>
 *   <li><b>Levels</b> are the values each factor can take</li>
 *   <li><b>Treatment combinations</b> are the configurations to explore</li>
 * </ul>
 *
 * <p>The factor source method defines which treatment combinations to test.
 * Common strategies include:
 * <ul>
 *   <li><b>Full factorial</b>: All possible combinations</li>
 *   <li><b>Fractional factorial</b>: A subset for efficiency</li>
 *   <li><b>One-factor-at-a-time</b>: Vary one factor while holding others constant</li>
 * </ul>
 *
 * <h2>Example: Full Factorial Design</h2>
 * <pre>{@code
 * @Experiment(mode = ExperimentMode.EXPLORE, serviceContract = ShoppingServiceContract.class)
 * @FactorSource("fullFactorial")
 * void exploreAllCombinations(
 *     @Factor("model") String model,
 *     @Factor("temperature") double temperature,
 *     ServiceContractProvider provider
 * ) {
 *     // Configure service contract with factor values
 *     provider.register(ShoppingServiceContract.class, () ->
 *         new ShoppingServiceContract(createAssistant(model, temperature)));
 *     ShoppingServiceContract serviceContract = provider.getInstance(ShoppingServiceContract.class);
 *     serviceContract.searchProducts("headphones");
 * }
 *
 * static Stream<FactorArguments> fullFactorial() {
 *     List<String> models = List.of("gpt-4", "gpt-3.5-turbo");
 *     List<Double> temps = List.of(0.0, 0.7);
 *     
 *     return models.stream()
 *         .flatMap(m -> temps.stream()
 *             .map(t -> FactorArguments.of(m, t)));
 * }
 * }</pre>
 *
 * <h2>Example: Hand-Picked Configurations</h2>
 * <pre>{@code
 * static Stream<FactorArguments> selectedConfigs() {
 *     return Stream.of(
 *         FactorArguments.of("gpt-4", 0.0),      // Baseline: best model, deterministic
 *         FactorArguments.of("gpt-4", 0.7),      // Creative variant
 *         FactorArguments.of("gpt-3.5-turbo", 0.0)  // Cost-optimized
 *     );
 * }
 * }</pre>
 *
 * @see Factor
 * @see ExperimentMode#EXPLORE
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FactorSource {
    
    /**
     * The name of the static method providing factor combinations.
     *
     * <p>The method must:
     * <ul>
     *   <li>Be static</li>
     *   <li>Be in the same class as the experiment method</li>
     *   <li>Return {@code Stream<FactorArguments>}</li>
     *   <li>Accept no parameters</li>
     * </ul>
     *
     * @return the method name
     */
    String value();
    
    /**
     * Names for each factor, in order.
     *
     * <p>These names are used for:
     * <ul>
     *   <li>Output file naming (e.g., {@code model-gpt-4_temp-0.7.yaml})</li>
     *   <li>Logging and debugging</li>
     * </ul>
     *
     * <p>The names must be in the same order as the values in each
     * {@link FactorArguments} instance.
     *
     * <h2>Example</h2>
     * <pre>{@code
     * @FactorSource(value = "configs", factors = {"model", "temp", "query"})
     * void exploreConfigs(ShoppingServiceContract serviceContract) {
     *     // serviceContract is pre-configured with model/temp
     *     // query can be obtained from the factor values if needed
     * }
     *
     * static Stream<FactorArguments> configs() {
     *     return Stream.of(
     *         FactorArguments.of("gpt-4", 0.0, "headphones"),  // [model, temp, query]
     *         FactorArguments.of("gpt-4", 0.7, "laptop")
     *     );
     * }
     * }</pre>
     *
     * @return array of factor names
     */
    String[] factors() default {};
}

