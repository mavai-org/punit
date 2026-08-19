package org.mavai.punit.statistics;

import org.apache.commons.statistics.distribution.NormalDistribution;

/**
 * Sizes a regression-procedure test against its own moving acceptance floor.
 *
 * <h2>The Moving Floor</h2>
 * <p>A test whose threshold is derived from a measured baseline does not apply
 * a fixed bar: the acceptance floor is the baseline rate's one-sided Wilson
 * lower bound evaluated at the test's own sample size. As the sample size
 * shrinks, the floor falls — a small sample proves less, so less is demanded
 * of it. Fixed-threshold power formulas therefore overstate the power of small
 * designs: the bar the small test will actually apply is lower than the one
 * they price.
 *
 * <p>This calculator puts the moving floor inside the power calculation.
 * Given a declared <em>minimum acceptable rate</em> — the worst true rate the
 * operator is willing to tolerate; a declared tolerance, not a measured
 * estimate — the self-consistent power at a candidate sample size n is
 * <pre>
 *   floor(n) = WilsonLower(p₀, n, 1−α)
 *   Power(n) = Φ((floor(n) − p_min) / √(p_min(1−p_min)/n))
 * </pre>
 * read as: the probability that a service whose true rate is exactly the
 * tolerated minimum fails the test — that degradation at least this severe is
 * detected. The floor reuses the package's existing Wilson machinery
 * ({@link BinomialProportionEstimator#lowerBoundFromRate}) so the z inside the
 * Wilson bound and the Φ of the power form share one normal-distribution
 * convention.
 *
 * <h2>Domain</h2>
 * <p>The construction is defined for a minimum acceptable rate strictly below
 * the baseline rate. When the tolerated minimum sits at or above the measured
 * baseline, the floor lies below the tolerated rate at every sample size,
 * power falls as n grows, and no sample size achieves a useful target power —
 * such a design asks the test to detect a "degradation" the baseline already
 * exceeds. That regime is rejected as misuse.
 *
 * <h2>What This Class Offers</h2>
 * <ul>
 *   <li>{@link #powerAt}: self-consistent power at a candidate sample size</li>
 *   <li>{@link #requiredSamples}: smallest sample size meeting a target power</li>
 *   <li>{@link #detectableRate}: the inversion — the largest tolerable rate
 *       detectable at target power with a fixed, affordable sample size</li>
 * </ul>
 *
 * <p>Within the domain, increasing n both raises the floor toward the baseline
 * rate and shrinks the standard error, so power is increasing in n and the
 * required sample size is well defined.
 */
public class RiskDrivenSizingCalculator {

    /**
     * The standard normal distribution N(0, 1) — the same implementation the
     * rest of the statistics package uses, so the Wilson bound's z and the
     * power form's Φ share one convention.
     */
    private static final NormalDistribution STANDARD_NORMAL = NormalDistribution.of(0, 1);

    /**
     * Search ceiling for {@link #requiredSamples}: beyond this the declared
     * tolerance is too tight for the baseline to be sized at all.
     */
    private static final int MAX_REQUIRED_SAMPLES = 10_000_000;

    private final BinomialProportionEstimator estimator = new BinomialProportionEstimator();

    /**
     * Self-consistent power at a candidate sample size: the probability that
     * a service whose true rate is exactly the minimum acceptable rate fails
     * a test whose acceptance floor is derived from the baseline rate at this
     * very sample size.
     *
     * @param sampleSize            the candidate test sample size n, positive
     * @param baselineRate          the effective baseline rate p₀, in (0, 1)
     * @param minimumAcceptableRate the declared tolerance, in (0, baselineRate)
     * @param confidence            one-sided confidence level (1 − α), in (0, 1)
     * @return the probability that degradation to the tolerated minimum is detected
     */
    public double powerAt(
            int sampleSize,
            double baselineRate,
            double minimumAcceptableRate,
            double confidence) {
        validateSampleSize(sampleSize);
        validateSizingInputs(baselineRate, minimumAcceptableRate, confidence);

        double floor = estimator.lowerBoundFromRate(baselineRate, sampleSize, confidence);
        double standardError = Math.sqrt(
                minimumAcceptableRate * (1.0 - minimumAcceptableRate) / sampleSize);
        return STANDARD_NORMAL.cumulativeProbability(
                (floor - minimumAcceptableRate) / standardError);
    }

    /**
     * The smallest sample size whose self-consistent power meets the target.
     *
     * <p>Power is increasing in the sample size within the domain (the floor
     * rises toward the baseline rate and the standard error shrinks), so the
     * minimum is well defined; it is found by doubling until the target is
     * met, then bisecting.
     *
     * @param baselineRate          the effective baseline rate p₀, in (0, 1)
     * @param minimumAcceptableRate the declared tolerance, in (0, baselineRate)
     * @param confidence            one-sided confidence level (1 − α), in (0, 1)
     * @param targetPower           the required detection probability, in (0, 1)
     * @return the minimal sample size at which the target power is achieved
     */
    public int requiredSamples(
            double baselineRate,
            double minimumAcceptableRate,
            double confidence,
            double targetPower) {
        validateSizingInputs(baselineRate, minimumAcceptableRate, confidence);
        validateProbability(targetPower, "Target power");

        int hi = 2;
        while (powerAt(hi, baselineRate, minimumAcceptableRate, confidence) < targetPower) {
            hi *= 2;
            if (hi > MAX_REQUIRED_SAMPLES) {
                throw new IllegalArgumentException(
                        "Required sample size exceeds " + MAX_REQUIRED_SAMPLES
                                + "; the declared tolerance is too tight for this baseline");
            }
        }
        int lo = Math.max(1, hi / 2);
        while (lo + 1 < hi) {
            int mid = (lo + hi) >>> 1;
            if (powerAt(mid, baselineRate, minimumAcceptableRate, confidence) >= targetPower) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        return hi;
    }

    /**
     * The inversion: for a fixed, affordable sample size, the largest
     * tolerable rate (the smallest drop from the baseline) at which the
     * self-consistent power still meets the target. Found by bisection over
     * the open interval below the baseline rate, to an absolute tolerance
     * of 1e-10.
     *
     * @param sampleSize   the affordable test sample size N, positive
     * @param baselineRate the effective baseline rate p₀, in (0, 1)
     * @param confidence   one-sided confidence level (1 − α), in (0, 1)
     * @param targetPower  the required detection probability, in (0, 1)
     * @return the largest minimum acceptable rate detectable at target power
     */
    public double detectableRate(
            int sampleSize,
            double baselineRate,
            double confidence,
            double targetPower) {
        validateSampleSize(sampleSize);
        validateBaselineRate(baselineRate);
        validateProbability(confidence, "Confidence");
        validateProbability(targetPower, "Target power");

        double lo = 1e-9;
        double hi = baselineRate - 1e-9;
        while (hi - lo > 1e-10) {
            double mid = (lo + hi) / 2.0;
            if (powerAt(sampleSize, baselineRate, mid, confidence) >= targetPower) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private void validateSampleSize(int sampleSize) {
        if (sampleSize < 1) {
            throw new IllegalArgumentException(
                    "Sample size must be positive, got: " + sampleSize);
        }
    }

    private void validateSizingInputs(
            double baselineRate, double minimumAcceptableRate, double confidence) {
        validateBaselineRate(baselineRate);
        validateProbability(minimumAcceptableRate, "Minimum acceptable rate");
        validateProbability(confidence, "Confidence");
        if (minimumAcceptableRate >= baselineRate) {
            throw new SizingRefusedException(
                    SizingRefusedException.Cause.EMPTY_TOLERANCE_INTERVAL,
                    "Minimum acceptable rate (" + minimumAcceptableRate
                            + ") must sit below the measured baseline rate (" + baselineRate
                            + "): the moving-floor construction sizes the detection of a "
                            + "degradation below the baseline. To assert a rate at or above "
                            + "the baseline, re-measure the baseline rather than asserting "
                            + "improvement through the tolerance.");
        }
    }

    /**
     * The baseline rate, with the zero baseline named rather than folded into
     * the generic domain check.
     *
     * <p>A baseline that observed no successes has an effective rate of
     * exactly 0 at every sample size (companion §4.3.4), so no declared
     * tolerance can sit below it and no design is priceable. Saying so is
     * more use to the operator than "must be in (0, 1)", and it is a
     * different corrective action: measure a baseline, rather than adjust
     * the tolerance.
     */
    private void validateBaselineRate(double baselineRate) {
        if (baselineRate == 0.0) {
            throw new SizingRefusedException(
                    SizingRefusedException.Cause.ZERO_BASELINE,
                    "Baseline rate is exactly 0: the baseline observed no successes, so "
                            + "its effective rate is 0 at every sample size and there is no "
                            + "tolerated rate below it to detect. No sample size can price "
                            + "this design. Measure a baseline with at least one success "
                            + "before sizing against it.");
        }
        validateProbability(baselineRate, "Baseline rate");
    }

    private void validateProbability(double value, String name) {
        if (Double.isNaN(value) || value <= 0.0 || value >= 1.0) {
            throw new IllegalArgumentException(
                    name + " must be in (0, 1), got: " + value);
        }
    }
}
