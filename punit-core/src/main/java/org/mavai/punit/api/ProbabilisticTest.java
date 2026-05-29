package org.mavai.punit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.mavai.punit.api.spec.Verdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Marks a method as a probabilistic test.
 *
 * <p>The annotated method is a normal JUnit {@code void} test. Its
 * body builds a test spec via the compositional API and asserts that
 * it reaches a {@code PASS} verdict:
 *
 * <pre>{@code
 * @ProbabilisticTest
 * void shoppingMeetsBaseline() {
 *     PUnit.testing(this::shoppingBaseline)
 *             .samples(100)
 *             .criterion(PassRate.empirical())
 *             .assertPasses();
 * }
 * }</pre>
 *
 * <p>The framework runs the spec exactly once through its sampling
 * loop (the spec's {@code samples} count drives how many invocations
 * the engine performs internally — JUnit sees a single test). The
 * resulting {@link Verdict} translates:
 *
 * <ul>
 *   <li>{@code PASS} → JUnit pass.</li>
 *   <li>{@code FAIL} → {@link org.opentest4j.AssertionFailedError}
 *       carrying the verdict's explanation and per-criterion detail.</li>
 *   <li>{@code INCONCLUSIVE} → {@link org.opentest4j.TestAbortedException}
 *       — configuration / environment problem (no baseline, identity
 *       mismatch, …), not a service degradation.</li>
 * </ul>
 *
 * <p>The annotation is attribute-free; every parameter lives on the
 * spec the method body builds and runs.
 *
 * <p>Tagged {@code "punit"} for suite-level filtering.
 */
@Test
@Tag("punit")
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
// javai-ref: JVI-DKY6R8R — do not remove (resolves in javai-orchestrator)
public @interface ProbabilisticTest {
}
