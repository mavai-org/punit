package org.mavai.punit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines an experiment design: an explicit list of ExperimentConfigs to execute.
 *
 * <p>Configs execute in the order listed, enabling strategic ordering for
 * goal-based early termination.
 *
 * <h2>Example: Finding the Cheapest Acceptable Model</h2>
 * <pre>{@code
 * @Experiment(serviceContract = "usecase.json.generation", samplesPerConfig = 100)
 * @ExperimentGoal(successRate = 0.90)
 * @ExperimentDesign({
 *     // Ordered cheapest-first
 *     @Config(model = "gpt-3.5-turbo", temperature = 0.0),
 *     @Config(model = "gpt-3.5-turbo", temperature = 0.2),
 *     @Config(model = "gpt-4.1-mini", temperature = 0.0),
 *     @Config(model = "gpt-4.1-mini", temperature = 0.2),
 *     @Config(model = "gpt-4", temperature = 0.0),
 *     @Config(model = "gpt-4", temperature = 0.2)
 * })
 * void findCheapestAcceptableModel() {
 *     // Stops early if any config achieves 90% success rate
 * }
 * }</pre>
 *
 * <h2>Explicit Config Lists</h2>
 * <p>Rather than computing a Cartesian product of factors, experiments define
 * an explicit list of configs:
 * <ul>
 *   <li><strong>Transparency</strong>: Every config is visible</li>
 *   <li><strong>Control</strong>: Developer chooses exactly which configurations to test</li>
 *   <li><strong>Ordering</strong>: Configs execute top-to-bottom</li>
 *   <li><strong>Dependency handling</strong>: Invalid combinations simply aren't listed</li>
 * </ul>
 *
 * @see Config
 * @see ExperimentGoal
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExperimentDesign {
    
    /**
     * The list of configs to execute.
     *
     * <p>Configs execute in the order listed.
     *
     * @return the experiment configs
     */
    Config[] value();
}

