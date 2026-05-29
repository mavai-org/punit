/**
 * Spec builders and the strategy contract the engine dispatches
 * through.
 *
 * <p>Each concrete spec ({@link org.mavai.punit.api.spec.Experiment},
 * {@link org.mavai.punit.api.spec.Experiment},
 * {@link org.mavai.punit.api.spec.Experiment},
 * {@link org.mavai.punit.api.spec.ProbabilisticTest}) implements
 * {@link org.mavai.punit.api.spec.Spec}. The engine iterates
 * {@link org.mavai.punit.api.spec.Spec#configurations()},
 * samples each configuration through a
 * {@link org.mavai.punit.api.spec.SampleExecutor}, hands the
 * resulting {@link org.mavai.punit.api.spec.SampleSummary} back to
 * {@link org.mavai.punit.api.spec.Spec#consume(Configuration, SampleSummary)
 * spec.consume(...)}, and finishes by invoking
 * {@link org.mavai.punit.api.spec.Spec#conclude() spec.conclude()}
 * — which yields a {@link org.mavai.punit.api.spec.EngineResult}.
 *
 * <p>The engine never inspects the concrete spec subtype. All
 * flavour-specific behaviour reaches the engine through the strategy
 * methods on {@code Spec}.
 */
package org.mavai.punit.api.spec;
