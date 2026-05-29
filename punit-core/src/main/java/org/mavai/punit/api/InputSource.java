package org.mavai.punit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies the source of test input data for experiments and probabilistic tests.
 *
 * <p>This annotation provides test inputs to experiment methods, distributing samples
 * across the input space. It supports two source types:
 * <ul>
 *   <li><b>Method source</b> — A static method returning {@code Stream<T>}, {@code Iterable<T>}, or {@code T[]}</li>
 *   <li><b>File source</b> — A classpath resource file in JSON or CSV format</li>
 * </ul>
 *
 * <h2>Method Source</h2>
 * <pre>{@code
 * @MeasureExperiment(samples = 1000)
 * @InputSource("testInstructions")
 * void measure(ShoppingBasketServiceContract serviceContract, String instruction) {
 *     serviceContract.translateInstruction(instruction);
 * }
 *
 * static Stream<String> testInstructions() {
 *     return Stream.of("add milk", "remove bread", "clear cart");
 * }
 * }</pre>
 *
 * <h2>Record Input (Recommended)</h2>
 * <p>For multiple fields or expected values, use a record:
 * <pre>{@code
 * record TranslationInput(String instruction, String expected) {}
 *
 * @MeasureExperiment(samples = 1000)
 * @InputSource("goldenInputs")
 * void measure(ShoppingBasketServiceContract serviceContract, TranslationInput input) {
 *     serviceContract.translateInstruction(input.instruction(), input.expected());
 * }
 *
 * static Stream<TranslationInput> goldenInputs() {
 *     return Stream.of(
 *         new TranslationInput("add milk", "{\"action\":\"add\"}"),
 *         new TranslationInput("remove bread", "{\"action\":\"remove\"}")
 *     );
 * }
 * }</pre>
 *
 * <h2>File Source</h2>
 * <p>Load inputs from JSON or CSV files (format detected by extension):
 * <pre>{@code
 * @MeasureExperiment(samples = 1000)
 * @InputSource(file = "golden/shopping.json")
 * void measure(ShoppingBasketServiceContract serviceContract, TranslationInput input) {
 *     serviceContract.translateInstruction(input.instruction(), input.expected());
 * }
 * }</pre>
 *
 * <h2>Sample Distribution</h2>
 * <p>Samples are distributed evenly across inputs:
 * <ul>
 *   <li>1000 samples with 100 inputs = 10 samples per input</li>
 *   <li>Remainders are distributed to early inputs</li>
 * </ul>
 *
 * @see MeasureExperiment
 * @see ProbabilisticTest
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
// javai-ref: JVI-QBEZ3W9 — do not remove (resolves in javai-orchestrator)
public @interface InputSource {

    /**
     * Name of a static method that provides input values.
     *
     * <p>The method must be static and return one of:
     * <ul>
     *   <li>{@code Stream<T>} where T matches the input parameter type</li>
     *   <li>{@code Iterable<T>} where T matches the input parameter type</li>
     *   <li>{@code T[]} where T matches the input parameter type</li>
     * </ul>
     *
     * <p>If not specified, {@link #file()} must be provided instead.
     *
     * @return the method name, or empty string if using file source
     */
    String value() default "";

    /**
     * Classpath resource path to a data file containing test inputs.
     *
     * <p>Supported formats (detected by file extension):
     * <ul>
     *   <li>{@code .json} — JSON array, each element deserialized to the input parameter type</li>
     *   <li>{@code .csv} — CSV with headers matching record component names</li>
     * </ul>
     *
     * <p>If not specified, {@link #value()} must be provided instead.
     *
     * @return the classpath resource path, or empty string if using method source
     */
    String file() default "";
}
