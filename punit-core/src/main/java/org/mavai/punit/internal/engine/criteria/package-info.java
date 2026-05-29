/**
 * Concrete {@link org.mavai.punit.api.spec.Criterion} implementations.
 *
 * <p>The {@code Criterion} interface lives in {@code api package} so authors
 * compose specs without pulling in the engine. Implementations
 * whose {@code evaluate(...)} delegates to statistical machinery live
 * here, in {@code punit-core}, where they can call into
 * {@link org.mavai.punit.statistics.BinomialProportionEstimator} and
 * other members of the dedicated, isolated statistics package.
 *
 * <p>Statistical calculations belong in {@code org.mavai.punit.statistics}
 * and nowhere else; criterion implementations consume that package's
 * tested machinery rather than reimplementing it. See punit's
 * {@code CLAUDE.md} §"Statistics isolation rule".
 */
package org.mavai.punit.internal.engine.criteria;
