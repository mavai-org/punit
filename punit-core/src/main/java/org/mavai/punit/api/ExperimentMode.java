package org.mavai.punit.api;

/**
 * Defines the execution mode for an experiment.
 *
 * <p>Experiments serve two distinct purposes that share execution machinery
 * but have different intents and outputs. Each mode has a sensible default
 * sample size accessible via {@link #getDefaultSampleSize()}.
 *
 * <h2>Experiment Types</h2>
 * <pre>
 * ┌──────────────────────────────────────────────────────────────────────────────────────────┐
 * │                             Experiment Annotations                                        │
 * ├──────────────────────────────────────────────────────────────────────────────────────────┤
 * │  Annotation:   @MeasureExperiment  │ @ExploreExperiment      │ @OptimizeExperiment        │
 * │  Intent:       Precise estimation  │ Factor comparison       │ Iterative factor tuning    │
 * │  Configs:      1 (implicit)        │ N (from factor source)  │ 1 (mutating treatment)     │
 * │  Samples:      1000+ (default)     │ 1+/config (default: 1)  │ 20/iteration (default)     │
 * │  Output:       baseline file       │ specs in explorations/  │ history in optimizations/  │
 * │  Decision:     "True success rate?"│ "Which config is best?" │ "What's the best value?"   │
 * │  Task:         ./gradlew exp -Prun=… (all modes; long form: experiment)                   │
 * └──────────────────────────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * @see MeasureExperiment
 * @see ExperimentMode
 * @see FactorSource
 */
public enum ExperimentMode {

    /**
     * MEASURE establishes reliable statistics for a single configuration.
     *
     * <p>Use {@link MeasureExperiment} when you want to:
     * <ul>
     *   <li>Measure the true success rate with high precision</li>
     *   <li>Generate an empirical spec for deriving test thresholds</li>
     *   <li>Establish confidence intervals for probabilistic assertions</li>
     * </ul>
     *
     * <h3>Typical Configuration</h3>
     * <ul>
     *   <li><b>Samples:</b> 1000+ (default: 1000)</li>
     *   <li><b>Output:</b> Baseline file in {@code src/test/resources/punit/baselines/}</li>
     *   <li><b>Task:</b> {@code ./gradlew exp -Prun=<name>}</li>
     * </ul>
     *
     * <h3>Example</h3>
     * <pre>{@code
     * @MeasureExperiment(serviceContract = ShoppingServiceContract.class, samples = 1000)
     * void measureShoppingSearch(ShoppingServiceContract serviceContract) {
     *     serviceContract.searchProducts("headphones");
     * }
     * }</pre>
     *
     * <h3>Anti-pattern</h3>
     * <p>Using &lt; 100 samples produces imprecise specs with wide confidence intervals.
     */
    MEASURE(1000),

    /**
     * EXPLORE compares multiple configurations to understand factor effects.
     *
     * <p>Use {@code ExploreExperiment} when you want to:
     * <ul>
     *   <li>Compare different LLM models</li>
     *   <li>Evaluate temperature/prompt variations</li>
     *   <li>Find which configuration works best</li>
     * </ul>
     *
     * <h3>Typical Configuration</h3>
     * <ul>
     *   <li><b>Samples per config:</b> 1-10 (default: 1)</li>
     *   <li><b>Output:</b> Multiple specs in {@code build/punit/explorations/}</li>
     *   <li><b>Task:</b> {@code ./gradlew exp -Prun=<name>}</li>
     * </ul>
     *
     * <h3>Typical Workflow</h3>
     * <p>Exploration usually happens in two phases:
     *
     * <p><b>Phase 1: "Which configs work at all?"</b>
     * <pre>{@code
     * @ExploreExperiment(samplesPerConfig = 1)
     * }</pre>
     * <p>Fast pass through all configurations to filter out broken ones.
     *
     * <p><b>Phase 2: "Which config is best?"</b>
     * <pre>{@code
     * @ExploreExperiment(samplesPerConfig = 10)
     * }</pre>
     * <p>More samples for remaining configs to gauge stochastic behaviors.
     *
     * <h3>Output</h3>
     * <p>One spec file per configuration in {@code explorations/}, enabling comparison via:
     * <ul>
     *   <li>IDE diff tools</li>
     *   <li>Command-line diff</li>
     *   <li>Future PUnit comparison tooling</li>
     * </ul>
     *
     * <h3>Anti-pattern</h3>
     * <p>Using &gt; 50 samples per config during exploration is wasteful.
     * Use @MeasureExperiment once you've chosen the best configuration.
     */
    EXPLORE(1),

    /**
     * OPTIMIZE iteratively refines a single treatment factor to find its optimal value.
     *
     * <p>Use {@code OptimizeExperiment} when you want to:
     * <ul>
     *   <li>Automatically tune a parameter (e.g., system prompt)</li>
     *   <li>Find the best value through iterative mutation and evaluation</li>
     *   <li>After exploration has identified a promising configuration</li>
     * </ul>
     *
     * <h3>Typical Configuration</h3>
     * <ul>
     *   <li><b>Samples per iteration:</b> 20 (default)</li>
     *   <li><b>Max iterations:</b> 20 (default)</li>
     *   <li><b>Output:</b> Optimization history in {@code build/punit/optimizations/}</li>
     *   <li><b>Task:</b> {@code ./gradlew exp -Prun=<name>}</li>
     * </ul>
     *
     * <h3>Workflow Context</h3>
     * <pre>{@code
     * EXPLORE → Select winning config → OPTIMIZE one factor → MEASURE (establish baseline)
     * }</pre>
     *
     * @see ExperimentMode#OPTIMIZE
     */
    OPTIMIZE(20);

    /**
     * The default number of samples for this mode when not explicitly specified.
     *
     * <ul>
     *   <li>{@link #MEASURE}: 1000 samples for statistically reliable specs</li>
     *   <li>{@link #EXPLORE}: 1 sample per config for fast initial filtering</li>
     * </ul>
     */
    private final int defaultSampleSize;

    ExperimentMode(int defaultSampleSize) {
        this.defaultSampleSize = defaultSampleSize;
    }

    /**
     * Returns the default sample size for this experiment mode.
     *
     * <p>This value is used when the sample count is not explicitly specified
     * in the experiment annotation:
     * <ul>
     *   <li>{@link #MEASURE}: Returns 1000 - sufficient for tight confidence intervals</li>
     *   <li>{@link #EXPLORE}: Returns 1 - fast pass to filter broken configurations</li>
     * </ul>
     *
     * @return the default number of samples for this mode
     */
    public int getDefaultSampleSize() {
        return defaultSampleSize;
    }

    /**
     * Resolves the effective sample size, using the mode's default if not specified.
     *
     * <p>Resolution logic:
     * <ul>
     *   <li>If {@code tentativeSampleSize > 0}: returns the explicit value</li>
     *   <li>If {@code tentativeSampleSize <= 0}: returns this mode's {@link #getDefaultSampleSize()}</li>
     * </ul>
     *
     * <p>This allows annotation defaults of {@code 0} to act as "use mode default":
     * <pre>{@code
     * // Uses MEASURE default (1000)
     * @Experiment(mode = MEASURE, serviceContract = MyServiceContract.class)
     *
     * // Explicit override (500 samples)
     * @Experiment(mode = MEASURE, serviceContract = MyServiceContract.class, samples = 500)
     * }</pre>
     *
     * @param tentativeSampleSize the sample size from the annotation (0 = use default)
     * @return the effective sample size to use
     */
    public int getEffectiveSampleSize(int tentativeSampleSize) {
        return tentativeSampleSize > 0 ? tentativeSampleSize : defaultSampleSize;
    }
}
