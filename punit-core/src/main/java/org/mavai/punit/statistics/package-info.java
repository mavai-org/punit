/**
 * Statistical engine for PUnit's probabilistic testing framework.
 * 
 * <h2>Module Independence</h2>
 * <p>This module is intentionally isolated from the rest of the PUnit framework.
 * It depends only on:
 * <ul>
 *   <li>Java standard library</li>
 *   <li>Apache Commons Statistics (for distribution functions)</li>
 * </ul>
 * 
 * <p>This isolation enables:
 * <ul>
 *   <li><strong>Independent scrutiny:</strong> Statisticians can review the calculations
 *       without needing to understand the broader framework.</li>
 *   <li><strong>Rigorous testing:</strong> Each statistical concept has dedicated unit tests
 *       with worked examples using real-world variable names.</li>
 *   <li><strong>Trust building:</strong> The calculations map directly to the statistical
 *       formulations in the STATISTICAL-COMPANION document.</li>
 * </ul>
 * 
 * <h2>Core Components</h2>
 * 
 * <h3>Model Records</h3>
 * <ul>
 *   <li>{@link org.mavai.punit.statistics.ProportionEstimate} - Point estimate and 
 *       confidence interval for a binomial proportion</li>
 *   <li>{@link org.mavai.punit.statistics.DerivationContext} - Parameters used in 
 *       threshold derivation</li>
 *   <li>{@link org.mavai.punit.statistics.DerivedThreshold} - Statistically-derived 
 *       threshold with full context</li>
 *   <li>{@link org.mavai.punit.statistics.SampleSizeRequirement} - Result of power 
 *       analysis calculation</li>
 *   <li>{@link org.mavai.punit.statistics.VerdictWithConfidence} - Test verdict with 
 *       statistical qualification</li>
 * </ul>
 * 
 * <h3>Calculators</h3>
 * <ul>
 *   <li>{@link org.mavai.punit.statistics.BinomialProportionEstimator} - Wilson score
 *       confidence intervals and one-sided lower bounds</li>
 *   <li>{@link org.mavai.punit.statistics.ThresholdDeriver} - Derives pass/fail thresholds
 *       for Sample-Size-First and Threshold-First approaches</li>
 *   <li>{@link org.mavai.punit.statistics.SampleSizeCalculator} - Power analysis for
 *       Confidence-First approach</li>
 *   <li>{@link org.mavai.punit.statistics.TestVerdictEvaluator} - Evaluates test results
 *       and generates qualified verdicts</li>
 * </ul>
 * 
 * <h2>The Three Operational Approaches</h2>
 * <p>See {@link org.mavai.punit.statistics.OperationalApproach} for the mutually exclusive
 * approaches organizations can use to configure probabilistic tests:
 * <ol>
 *   <li><strong>Sample-Size-First:</strong> Fix n and α, derive threshold</li>
 *   <li><strong>Confidence-First:</strong> Fix α, δ, β, derive n</li>
 *   <li><strong>Threshold-First:</strong> Fix n and threshold, derive implied α</li>
 * </ol>
 * 
 * <h2>Statistical Foundation</h2>
 * <p>All calculations are based on:
 * <ul>
 *   <li><strong>Binomial distribution:</strong> Success/failure outcomes modeled as
 *       Bernoulli trials</li>
 *   <li><strong>Wilson score interval:</strong> Robust confidence intervals that work
 *       for all sample sizes and proportions, especially near 0 or 1</li>
 *   <li><strong>One-sided testing:</strong> Threshold derivation uses one-sided lower
 *       bounds to detect degradation (not improvement)</li>
 *   <li><strong>Power analysis:</strong> Sample size calculation based on Type I and
 *       Type II error rates</li>
 * </ul>
 * 
 * <h2>Handling Perfect Baselines (p̂ = 1)</h2>
 * <p>When a baseline experiment observes 100% success (k = n), naive approaches
 * would produce threshold = 1.0, causing any failure to fail the test. PUnit uses
 * the Wilson lower bound, which produces a sensible threshold below 1.0 even when
 * no failures were observed.
 * 
 * @see <a href="https://en.wikipedia.org/wiki/Binomial_proportion_confidence_interval">
 *      Binomial Proportion Confidence Interval</a>
 * @see <a href="https://en.wikipedia.org/wiki/Sample_size_determination">
 *      Sample Size Determination</a>
 */
package org.mavai.punit.statistics;

