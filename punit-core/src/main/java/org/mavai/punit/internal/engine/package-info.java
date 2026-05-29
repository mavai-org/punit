/**
 * The execution engine.
 *
 * <p>One {@link org.mavai.punit.internal.engine.Engine} drives every spec
 * flavour through the strategy methods declared on
 * {@link org.mavai.punit.api.spec.Spec}. Samples are invoked
 * through a
 * {@link org.mavai.punit.api.spec.SampleExecutor} — today the
 * shipped implementation is
 * {@link org.mavai.punit.internal.engine.SerialSampleExecutor}.
 */
package org.mavai.punit.internal.engine;
