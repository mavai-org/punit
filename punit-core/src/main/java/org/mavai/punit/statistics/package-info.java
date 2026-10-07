/**
 * Statistical engine for PUnit's probabilistic testing framework: the
 * decision rules of the Statistical Companion's methodology 1.6.0.
 *
 * <h2>Module Independence</h2>
 * <p>This package is intentionally isolated from the rest of the PUnit
 * framework. It depends only on the Java standard library and Apache
 * Commons Statistics and Numbers (distribution functions, log-gamma and
 * exact rationals), so a statistician can review the calculations
 * against the companion without understanding the broader framework.
 *
 * <h2>The decision rules</h2>
 * <ul>
 *   <li>{@link org.mavai.punit.statistics.RegressionRule} —
 *       {@code regression/fisher}: empirical regression against a
 *       baseline, decided by the one-sided Fisher exact test as an integer
 *       cutoff (§3.4), with its power, size at the assumed common rate,
 *       minimum detectable degradation and the threshold-first inversion;
 *       {@link org.mavai.punit.statistics.RegressionSizing} sizes it
 *       (design and resolved sizing, §5.4.1).</li>
 *   <li>{@link org.mavai.punit.statistics.ComplianceRule} —
 *       {@code compliance/exact-binomial}: a given requirement, decided by
 *       the exact one-sided binomial test as the smallest passing count
 *       (§3.6), with its feasibility minimum and sizing (§5.5, §5.7.1).</li>
 *   <li>{@link org.mavai.punit.statistics.LatencyRules} —
 *       {@code latency/precedence} for a baseline-derived latency
 *       threshold (§12.4.2) and {@code latency/compliance-exact-binomial}
 *       for an explicit one (§12.3.4), with the non-degeneracy and
 *       existence gates (§12.5).</li>
 * </ul>
 *
 * <p>{@link org.mavai.punit.statistics.DecisionRule} names the rules,
 * {@link org.mavai.punit.statistics.ConfigurationError} the two
 * configurations refused before any sample runs, and
 * {@link org.mavai.punit.statistics.Methodology} the methodology version.
 * Every exact rule compares a probability with alpha under the
 * exact-boundary convention of §10.6.
 *
 * <h2>Descriptive statistics</h2>
 * <p>{@link org.mavai.punit.statistics.BinomialProportionEstimator} (the
 * Wilson score interval) and
 * {@link org.mavai.punit.statistics.LatencyStatistics} (nearest-rank
 * percentiles) describe what a run observed. The Wilson interval decides
 * no verdict.
 *
 * <p>Every public computation here is validated against the mavai-R
 * reference fixtures by the conformance suite.
 */
package org.mavai.punit.statistics;
