package org.mavai.punit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines an optional early termination goal for an experiment.
 *
 * <p>When any ExperimentConfig achieves this goal, remaining configs are skipped.
 * This enables cost-effective experimentation by stopping as soon as an acceptable
 * configuration is found.
 *
 * <h2>Example: Stop When 90% Success Rate Achieved</h2>
 * <pre>{@code
 * @Experiment(serviceContract = "usecase.json.generation", samplesPerConfig = 100)
 * @ExperimentGoal(successRate = 0.90)
 * @ExperimentDesign({
 *     @Config(model = "gpt-3.5-turbo", temperature = 0.0),
 *     @Config(model = "gpt-4", temperature = 0.0)
 * })
 * void findAcceptableModel() {
 *     // If gpt-3.5-turbo achieves 90%+, gpt-4 is not tested
 * }
 * }</pre>
 *
 * <h2>Ordering Matters</h2>
 * <p>The framework executes configs in listed order. The developer is responsible
 * for ordering configs to meet their objectives:
 * <ul>
 *   <li><strong>Cheapest first</strong>: Find most economical acceptable option</li>
 *   <li><strong>Quality first</strong>: Find best quality quickly</li>
 *   <li><strong>Interleaved</strong>: Explore tradeoff space</li>
 * </ul>
 *
 * @see ExperimentDesign
 * @see ExperimentMode
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExperimentGoal {
    
    /**
     * Minimum success rate to consider goal achieved.
     *
     * <p>Value in range [0.0, 1.0]. Use {@code Double.NaN} to ignore.
     *
     * @return the minimum success rate
     */
    double successRate() default Double.NaN;
    
    /**
     * Maximum average latency (ms) to consider goal achieved.
     *
     * <p>Use {@code Long.MAX_VALUE} to ignore.
     *
     * @return the maximum average latency in milliseconds
     */
    long maxLatencyMs() default Long.MAX_VALUE;
    
    /**
     * Maximum average tokens per sample to consider goal achieved.
     *
     * <p>Use {@code Long.MAX_VALUE} to ignore.
     *
     * @return the maximum average tokens per sample
     */
    long maxTokensPerSample() default Long.MAX_VALUE;
    
    /**
     * Custom goal expression (for complex criteria).
     *
     * <p>Example: {@code "successRate >= 0.90 && avgLatencyMs <= 500"}
     *
     * @return the custom goal expression
     */
    String when() default "";
}

