/**
 * Baseline-file machinery.
 *
 * <p>This package is the on-disk persistence layer for
 * {@link org.mavai.punit.api.spec.BaselineStatistics} values
 * produced by {@code Experiment.measuring(...)} and consumed by the
 * empirical variants of {@link org.mavai.punit.internal.engine.criteria.PassRate}
 * and {@link org.mavai.punit.api.spec.PercentileLatency}.
 *
 * <p>The schema is documented in
 * {@code docs/DES-BASELINE-YAML-SCHEMA.md}.
 */
package org.mavai.punit.internal.engine.baseline;
