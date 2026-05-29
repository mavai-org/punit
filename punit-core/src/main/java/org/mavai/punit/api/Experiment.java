package org.mavai.punit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Marks a method as an experiment — a measure, explore, or optimize
 * whose role is to produce an artefact (baseline file, exploration
 * grid, optimization history) rather than a verdict.
 *
 * <p>The annotated method is a normal JUnit {@code void} test. Its
 * body builds an experiment spec via the compositional API and runs
 * it:
 *
 * <pre>{@code
 * @Experiment
 * void shoppingBaseline() {
 *     PUnit.measuring(shoppingSampling(1000), shoppingFactors()).run();
 * }
 *
 * @Experiment
 * void shoppingAcrossModels() {
 *     PUnit.exploring(shoppingSampling(100))
 *             .grid(new Factors("gpt-4o", 0.0),
 *                   new Factors("gpt-4o", 0.5),
 *                   new Factors("claude-3-sonnet", 0.0))
 *             .run();
 * }
 *
 * @Experiment
 * void shoppingOptimize() {
 *     PUnit.optimizing(shoppingSampling(50))
 *             .initialFactors(new Factors("gpt-4o", 0.0))
 *             .stepper(stepper)
 *             .maximize(scorer)
 *             .maxIterations(10)
 *             .run();
 * }
 * }</pre>
 *
 * <p>One annotation, three kinds — the kind is chosen at the
 * method body's verb ({@code measuring} / {@code exploring} /
 * {@code optimizing}).
 *
 * <p>An experiment runs through the same JUnit Platform engine as a
 * {@link ProbabilisticTest} — both annotations are meta-annotated with
 * {@code @Test} and the method body drives sampling via the {@code PUnit}
 * entry point. What makes an experiment different is its runtime
 * contract: it is typically slow and expensive (e.g. an LLM-calling
 * 1000-sample MEASURE), it is side-effecting (writes baseline spec,
 * exploration, or optimization YAML to disk), and an INCONCLUSIVE
 * outcome is normal rather than a failure.
 *
 * <p>For that reason the annotation carries the
 * {@code "punit-experiment"} tag, and the punit Gradle plugin
 * configures {@code ./gradlew test} to exclude it. Without that
 * exclusion, a routine test run (or CI) would re-execute every
 * baseline experiment in the project and clobber the committed spec
 * files those experiments produce. Experiments run only when
 * explicitly invoked, via the {@code experiment} / {@code exp} Gradle
 * tasks the plugin registers — those tasks carry the cache, failure,
 * and output-dir defaults experiments need.
 */
@Test
@Tag("punit-experiment")
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Experiment {
}
