package org.mavai.punit.statistics;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import org.apache.commons.statistics.distribution.BinomialDistribution;

/**
 * Risk-driven sizing of a regression test against the operative rule
 * (companion §5.4.1).
 *
 * <p>A regression test's cutoff is derived at the test's own size, so
 * sizing is done against the cutoff the test will actually apply. Two
 * operations answer different questions and are named apart:
 *
 * <ul>
 *   <li><b>Design sizing</b>, before the baseline exists: the baseline is
 *       planned at {@code n_b} trials and an expected rate {@code p0},
 *       and the design power averages over the baseline count yet to be
 *       drawn.</li>
 *   <li><b>Resolved sizing</b>, against an existing baseline: its
 *       observed count fixes the cutoff for every candidate test size,
 *       and the resolved power decides. Once a baseline exists,
 *       {@link Refusal#BASELINE_TOO_SMALL} is judged by it.</li>
 * </ul>
 *
 * <p>The <b>design alternative rate</b> {@code p_design} is the true rate
 * at which the test must reach its target power — a declared design
 * input, not a measured estimate and not a tolerance: the test still
 * flags any degradation from the baseline, including one to a rate above
 * {@code p_design}.
 *
 * <p>Exact power is a sawtooth in the test size, so the required size is
 * the smallest {@code n_t} from which power <em>stays</em> at or above the
 * target for every larger test up to the baseline size — not the first
 * crossing — subject to the design rule {@code n_t ≤ n_b}.
 */
public final class RegressionSizing {

    private static final double DETECTABLE_RATE_TOLERANCE = 1e-10;

    private RegressionSizing() {
    }

    /** Why a sizing design cannot be priced; the name is the reported category. */
    public enum Refusal {
        /**
         * The baseline observed (or expects) no successes (§4.3.4): there
         * is no rate below it to detect. Measure a baseline before sizing
         * against it.
         */
        ZERO_BASELINE,
        /**
         * The design alternative rate is not below the baseline rate, so
         * there is no degradation to detect. Re-measure rather than raise
         * the rate.
         */
        ALTERNATIVE_NOT_BELOW_BASELINE,
        /** A candidate test larger than the baseline the design rule admits. */
        TEST_LARGER_THAN_BASELINE,
        /**
         * No test the design rule admits reaches and holds the target
         * power: a larger baseline is needed.
         */
        BASELINE_TOO_SMALL
    }

    /**
     * The refusal a sizing design meets before any power is computed, or
     * empty when the design is inside the domain.
     *
     * @param designAlternativeRate the declared rate, or empty when the
     *                              operation takes none
     * @param testSamples           the candidate test size, or empty when
     *                              the operation searches for one
     */
    public static Optional<Refusal> checkDomain(
            double baselineRate, int baselineTrials,
            OptionalDouble designAlternativeRate, OptionalInt testSamples) {
        if (baselineRate == 0.0) {
            return Optional.of(Refusal.ZERO_BASELINE);
        }
        if (designAlternativeRate.isPresent() && designAlternativeRate.getAsDouble() >= baselineRate) {
            return Optional.of(Refusal.ALTERNATIVE_NOT_BELOW_BASELINE);
        }
        if (testSamples.isPresent() && testSamples.getAsInt() > baselineTrials) {
            return Optional.of(Refusal.TEST_LARGER_THAN_BASELINE);
        }
        return Optional.empty();
    }

    // ── Design sizing ───────────────────────────────────────────────

    /**
     * The smallest {@code n_t ≤ n_b} from which design power stays at the
     * target, and the power there. Scans down from {@code n_b} to the
     * first size whose power falls short; the answer is the next size up.
     * Empty ({@link Refusal#BASELINE_TOO_SMALL}) when the power at
     * {@code n_b} itself is short. The domain is the caller's to check
     * first.
     */
    public static Optional<Sized> designRequiredSamples(
            double baselineRate, int baselineTrials, double designAlternativeRate,
            double alpha, double targetPower) {
        int[] counts = RegressionRule.baselineWindow(baselineTrials, baselineRate);
        int[] cutoffs = RegressionRule.cutoffs(counts, baselineTrials, baselineTrials, alpha);
        Sized held = null;
        for (int testSamples = baselineTrials; testSamples >= 1; testSamples--) {
            if (testSamples < baselineTrials) {
                for (int i = 0; i < counts.length; i++) {
                    int guess = (int) Math.rint((double) cutoffs[i] * testSamples / (testSamples + 1));
                    cutoffs[i] = RegressionRule.cutoffNear(
                            counts[i], baselineTrials, testSamples, alpha, guess);
                }
            }
            double power = RegressionRule.failProbability(
                    cutoffs, counts, baselineTrials, baselineRate, testSamples,
                    designAlternativeRate);
            if (power < targetPower) {
                return Optional.ofNullable(held);
            }
            held = new Sized(testSamples, power, OptionalInt.empty());
        }
        return Optional.ofNullable(held);
    }

    /** The design power at a candidate test size (see {@link RegressionRule#designPower}). */
    public static double designPowerAt(
            int testSamples, double baselineRate, int baselineTrials,
            double designAlternativeRate, double alpha) {
        return RegressionRule.designPower(
                baselineTrials, testSamples, alpha, baselineRate, designAlternativeRate);
    }

    /**
     * The largest design alternative rate detectable at the target design
     * power with a test of {@code n_t}. Power falls as {@code p_design}
     * rises toward {@code p0}, so bisection over {@code (0, p0)} to 1e-10;
     * empty when even {@code p_design = 0} falls short.
     */
    public static OptionalDouble designDetectableRate(
            int testSamples, double baselineRate, int baselineTrials, double alpha,
            double targetPower) {
        int[] counts = RegressionRule.baselineWindow(baselineTrials, baselineRate);
        int[] cutoffs = RegressionRule.cutoffs(counts, baselineTrials, testSamples, alpha);
        if (RegressionRule.failProbability(cutoffs, counts, baselineTrials, baselineRate,
                testSamples, 0.0) < targetPower) {
            return OptionalDouble.empty();
        }
        double low = 0.0;
        double high = baselineRate;
        while (high - low > DETECTABLE_RATE_TOLERANCE) {
            double mid = (low + high) / 2;
            if (RegressionRule.failProbability(cutoffs, counts, baselineTrials, baselineRate,
                    testSamples, mid) >= targetPower) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return OptionalDouble.of(low);
    }

    // ── Resolved sizing ─────────────────────────────────────────────

    /**
     * The cutoff against an observed baseline at every test size
     * {@code 1..n_b}. Element {@code i} is the cutoff at
     * {@code n_t = i + 1}; each is walked from its predecessor's.
     */
    static int[] resolvedCutoffs(int baselineSuccesses, int baselineTrials, double alpha) {
        int[] cutoffs = new int[baselineTrials];
        cutoffs[0] = RegressionRule.cutoffUnchecked(baselineSuccesses, baselineTrials, 1, alpha);
        for (int testSamples = 2; testSamples <= baselineTrials; testSamples++) {
            cutoffs[testSamples - 1] = RegressionRule.cutoffNear(
                    baselineSuccesses, baselineTrials, testSamples, alpha, cutoffs[testSamples - 2]);
        }
        return cutoffs;
    }

    /**
     * Resolved sizing: the smallest {@code n_t ≤ n_b} from which the
     * resolved power stays at the target, the power there, and the first
     * crossing (reported, never the answer). Empty
     * ({@link Refusal#BASELINE_TOO_SMALL}) when no {@code n_t ≤ n_b}
     * reaches and holds the target. The domain is the caller's to check
     * first.
     */
    public static Optional<Sized> resolvedSizing(
            int baselineSuccesses, int baselineTrials, double designAlternativeRate,
            double alpha, double targetPower) {
        int[] cutoffs = resolvedCutoffs(baselineSuccesses, baselineTrials, alpha);
        double[] powers = new double[baselineTrials];
        for (int i = 0; i < baselineTrials; i++) {
            powers[i] = BinomialDistribution.of(i + 1, designAlternativeRate)
                    .cumulativeProbability(cutoffs[i] - 1);
        }
        int lastBelow = -1;
        int firstReached = -1;
        for (int i = 0; i < baselineTrials; i++) {
            if (powers[i] < targetPower) {
                lastBelow = i;
            } else if (firstReached < 0) {
                firstReached = i;
            }
        }
        if (lastBelow == baselineTrials - 1) {
            return Optional.empty();
        }
        int start = lastBelow + 1;
        return Optional.of(new Sized(start + 1, powers[start], OptionalInt.of(firstReached + 1)));
    }

    /**
     * A required test size and the power there.
     *
     * @param requiredSamples the smallest size from which power stays at
     *                        the target
     * @param power           the power at {@code requiredSamples}
     * @param firstCrossing   the smallest size whose power first reaches
     *                        the target — reported, never the answer;
     *                        stated by resolved sizing only
     */
    public record Sized(int requiredSamples, double power, OptionalInt firstCrossing) {
    }
}
