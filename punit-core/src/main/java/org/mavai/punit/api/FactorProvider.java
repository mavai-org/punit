package org.mavai.punit.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a static method as a provider of factor values for experiments.
 *
 * <p>Factor providers supply the input data that experiments iterate over.
 * They are referenced by experiments via {@code @FactorSource} annotations
 * using the format {@code "ClassName#methodName"}.
 *
 * <h2>Method Requirements</h2>
 * <ul>
 *   <li>Must be {@code static}</li>
 *   <li>Must be {@code public}</li>
 *   <li>Must return {@code List<FactorArguments>}</li>
 *   <li>Must take no parameters</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * public class ShoppingBasketServiceContract {
 *
 *     @FactorProvider
 *     public static List<FactorArguments> singleInstruction() {
 *         return FactorArguments.configurations()
 *             .names("instruction")
 *             .values("Add 2 apples and remove the bread")
 *             .stream().toList();
 *     }
 *
 *     @FactorProvider
 *     public static List<FactorArguments> standardInstructions() {
 *         return FactorArguments.configurations()
 *             .names("instruction")
 *             .values("Add 2 apples")
 *             .values("Remove the milk")
 *             .values("Clear the basket")
 *             .stream().toList();
 *     }
 * }
 * }</pre>
 *
 * <h2>Usage in Experiments</h2>
 * <pre>{@code
 * @MeasureExperiment(samples = 1000, serviceContract = ShoppingBasketServiceContract.class)
 * @FactorSource("ShoppingBasketServiceContract#singleInstruction")
 * void measureBaseline(ShoppingBasketServiceContract serviceContract, @Factor("instruction") String instruction) {
 *     // All 1000 samples use the same instruction
 * }
 *
 * @MeasureExperiment(samples = 1000, serviceContract = ShoppingBasketServiceContract.class)
 * @FactorSource("ShoppingBasketServiceContract#standardInstructions")
 * void measureWithVariety(ShoppingBasketServiceContract serviceContract, @Factor("instruction") String instruction) {
 *     // Samples cycle through the standard instructions
 * }
 * }</pre>
 *
 * <h2>Placement</h2>
 * <p>Factor providers can be placed in:
 * <ul>
 *   <li><b>Service contract classes</b> - When the service contract author wants to provide
 *       canonical, domain-appropriate factor values that experimenters can use</li>
 *   <li><b>Experiment classes</b> - When factor values are specific to a
 *       particular experiment or exploration</li>
 *   <li><b>Dedicated factor source classes</b> - When factor values are
 *       shared across multiple service contracts or experiments</li>
 * </ul>
 *
 * @see FactorSource
 * @see FactorArguments
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FactorProvider {

    /**
     * Optional description of what this factor provider represents.
     *
     * <p>This is purely for documentation purposes and does not affect
     * runtime behavior.
     *
     * @return description of the factor provider
     */
    String value() default "";
}
