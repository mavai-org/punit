package org.mavai.punit.statistics;

import java.util.Optional;
import java.util.OptionalDouble;

import org.apache.commons.statistics.distribution.BinomialDistribution;
import org.apache.commons.statistics.distribution.HypergeometricDistribution;

/**
 * Empirical regression under {@code regression/fisher} (companion §3.4).
 *
 * <p>A test is judged against the baseline it was derived from by the
 * one-sided Fisher exact test, expressed as an integer cutoff on the
 * test's success count. For each test count {@code k_t} the p-value is
 * the hypergeometric lower tail {@code P(X ≤ k_t)}, {@code X} the number
 * of the {@code s = K_b + k_t} pooled successes that fall in the test's
 * {@code n_t} of the {@code n_b + n_t} trials; a count fails when its
 * p-value is at most alpha, and the cutoff {@code c} is the smallest
 * count whose p-value exceeds it. The test passes iff {@code K_t ≥ c}.
 *
 * <p>The cutoff is non-decreasing in the baseline count — a perfect
 * baseline needs no special case — and the rule never exceeds alpha in
 * the experiment where the baseline and the test are both random.
 * Everything here is an exact finite sum; nothing is approximated or
 * simulated.
 *
 * <p>Alongside the cutoff this class computes what a report discloses
 * about it: the size at the assumed common rate, the design power
 * (baseline and test both yet to be drawn), the resolved power (the
 * baseline observed, its cutoff fixed), the minimum detectable
 * degradation, and the threshold-first inversion (the implied alpha of a
 * declared cutoff, §6.3).
 */
public final class RegressionRule {

    /** The power at which the minimum detectable degradation is stated (§5.6). */
    public static final double MDD_POWER = 0.80;

    /** A declared cutoff whose implied alpha exceeds this is unsound (§6.3). */
    static final double SOUND_IMPLIED_ALPHA = 0.20;

    /**
     * The baseline counts carrying all but a negligible share of the
     * binomial mass: design power sums over them only (the truncation
     * moves the power by less than 2e-17).
     */
    private static final double WINDOW_MASS = 1e-17;

    private RegressionRule() {
    }

    // ── The rule ────────────────────────────────────────────────────

    /**
     * The one-sided Fisher p-value {@code P(X ≤ k_t)} of a test count.
     *
     * <p>The hypergeometric probability of {@code k_t} is taken from the
     * distribution, and the lower tail is summed downwards by the ratio
     * of successive probabilities,
     * {@code P(x − 1) / P(x) = x (N − K − n + x) / ((K − x + 1)(n − x + 1))},
     * until the terms no longer move the sum. Near a cutoff the tail is
     * small and the sum takes a handful of terms, which is what makes the
     * sizing searches affordable.
     */
    public static double pValue(
            int testSuccesses, int baselineSuccesses, int baselineTrials, int testSamples) {
        int population = baselineTrials + testSamples;
        int pooled = baselineSuccesses + testSuccesses;
        int lower = Math.max(0, testSamples + pooled - population);
        int upper = Math.min(testSamples, pooled);
        if (testSuccesses < lower) {
            return 0.0;
        }
        if (testSuccesses >= upper) {
            return 1.0;
        }
        double term = HypergeometricDistribution.of(population, pooled, testSamples)
                .probability(testSuccesses);
        double sum = term;
        for (int x = testSuccesses; x > lower && term > sum * 1e-18; x--) {
            term *= (double) x * (population - pooled - testSamples + x)
                    / ((double) (pooled - x + 1) * (testSamples - x + 1));
            sum += term;
        }
        return Math.min(1.0, sum);
    }

    /**
     * Whether a test count fails: its p-value is at most alpha
     * (inclusive, under the exact-boundary convention).
     */
    static boolean fails(
            int testSuccesses, int baselineSuccesses, int baselineTrials, int testSamples,
            double alpha) {
        if (testSuccesses < 0) {
            return true;
        }
        return ExactBoundary.atMostAlpha(
                pValue(testSuccesses, baselineSuccesses, baselineTrials, testSamples),
                alpha,
                () -> ExactBoundary.fisherPValue(
                        testSuccesses, baselineSuccesses, baselineTrials, testSamples));
    }

    /**
     * The integer cutoff {@code c} of {@code regression/fisher}: PASS iff
     * {@code K_t ≥ c}.
     *
     * @param baselineSuccesses {@code K_b}, in {@code 0..n_b}
     * @param baselineTrials    {@code n_b}, positive
     * @param testSamples       {@code n_t}, positive
     * @param alpha             the one-sided level, in (0, 1)
     */
    public static int cutoff(
            int baselineSuccesses, int baselineTrials, int testSamples, double alpha) {
        validate(baselineSuccesses, baselineTrials, testSamples, alpha);
        return cutoffUnchecked(baselineSuccesses, baselineTrials, testSamples, alpha);
    }

    /**
     * The p-value is non-decreasing in the test count, so the cutoff is
     * found by bisection between a virtual failing count −1 and
     * {@code n_t} (whose p-value is 1).
     */
    static int cutoffUnchecked(
            int baselineSuccesses, int baselineTrials, int testSamples, double alpha) {
        int low = -1;
        int high = testSamples;
        while (high - low > 1) {
            int mid = (low + high) >>> 1;
            if (fails(mid, baselineSuccesses, baselineTrials, testSamples, alpha)) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return high;
    }

    /**
     * The same cutoff, walked from a close guess. A sizing search
     * evaluates cutoffs at one test size after another; starting from the
     * neighbouring size's cutoff makes each a step or two of walking
     * rather than a full bisection. The answer is the definition's — the
     * smallest count whose p-value exceeds alpha — whatever the guess.
     */
    static int cutoffNear(
            int baselineSuccesses, int baselineTrials, int testSamples, double alpha, int guess) {
        int cut = Math.max(0, Math.min(guess, testSamples));
        while (fails(cut, baselineSuccesses, baselineTrials, testSamples, alpha)) {
            cut++;
        }
        while (cut > 0 && !fails(cut - 1, baselineSuccesses, baselineTrials, testSamples, alpha)) {
            cut--;
        }
        return cut;
    }

    /**
     * Derives the cutoff for one configuration together with its
     * informational size at the assumed common rate. The design rule that
     * a test may not exceed its baseline is judged before any derivation.
     */
    public static Derivation derive(
            int baselineSuccesses, int baselineTrials, int testSamples, double alpha) {
        int c = cutoff(baselineSuccesses, baselineTrials, testSamples, alpha);
        return new Derivation(c, testSamples, alpha,
                sizeAtAssumedCommonRate(baselineSuccesses, baselineTrials, testSamples, alpha));
    }

    /**
     * Decides a criterion against its baseline: PASS iff the observed
     * count meets the cutoff.
     */
    public static Decision decide(
            int testSuccesses, int testSamples,
            int baselineSuccesses, int baselineTrials, double alpha) {
        if (testSuccesses < 0 || testSuccesses > testSamples) {
            throw new IllegalArgumentException(
                    "test successes must be in 0.." + testSamples + ", got " + testSuccesses);
        }
        Derivation derivation = derive(baselineSuccesses, baselineTrials, testSamples, alpha);
        RuleVerdict verdict = testSuccesses >= derivation.cutoff()
                ? RuleVerdict.PASS : RuleVerdict.FAIL;
        return new Decision(verdict, testSuccesses, testSamples,
                baselineSuccesses, baselineTrials, derivation);
    }

    // ── What the report discloses ───────────────────────────────────

    /**
     * The procedure's false-degradation-signal probability were the
     * unknown common rate equal to the baseline's observed rate
     * {@code K_b / n_b} — a property of the procedure at that rate, not
     * of the run. Empty at a baseline rate of 0 or 1, where it is
     * degenerate.
     */
    public static OptionalDouble sizeAtAssumedCommonRate(
            int baselineSuccesses, int baselineTrials, int testSamples, double alpha) {
        if (baselineSuccesses == 0 || baselineSuccesses == baselineTrials) {
            return OptionalDouble.empty();
        }
        double rate = (double) baselineSuccesses / baselineTrials;
        return OptionalDouble.of(designPower(baselineTrials, testSamples, alpha, rate, rate));
    }

    /**
     * The exact power of {@code regression/fisher} with the baseline yet
     * to be drawn:
     * {@code Σ_k P_{p0}(K_b = k) P_{p_design}(K_t < c(k))} — the
     * probability that a service truly at the design alternative rate
     * fails the test, averaged over the baseline counts a baseline of
     * {@code n_b} at {@code p0} could return.
     */
    public static double designPower(
            int baselineTrials, int testSamples, double alpha,
            double baselineRate, double designAlternativeRate) {
        int[] counts = baselineWindow(baselineTrials, baselineRate);
        int[] cutoffs = cutoffs(counts, baselineTrials, testSamples, alpha);
        return failProbability(cutoffs, counts, baselineTrials, baselineRate,
                testSamples, designAlternativeRate);
    }

    /**
     * The power of the test resolved against an observed baseline, whose
     * count fixes the cutoff: {@code P_{p_design}(K_t < c(K_b))}. It
     * answers a different question from {@link #designPower} and is
     * reported beside it, named apart.
     */
    public static double resolvedPower(
            int baselineSuccesses, int baselineTrials, int testSamples, double alpha,
            double designAlternativeRate) {
        int c = cutoff(baselineSuccesses, baselineTrials, testSamples, alpha);
        return belowCutoff(c, testSamples, designAlternativeRate);
    }

    /**
     * The minimum detectable degradation: the smallest drop {@code δ} at
     * which the design power against {@code p_b − δ} reaches
     * {@code power}, by bisection to 1e-12. It inverts the design power.
     * Empty when even a test rate of 0 falls short — no degradation is
     * detectable at that power.
     */
    public static OptionalDouble minimumDetectableDegradation(
            int baselineTrials, int testSamples, double alpha, double baselineRate, double power) {
        int[] counts = baselineWindow(baselineTrials, baselineRate);
        int[] cutoffs = cutoffs(counts, baselineTrials, testSamples, alpha);
        if (failProbability(cutoffs, counts, baselineTrials, baselineRate, testSamples, 0.0)
                < power) {
            return OptionalDouble.empty();
        }
        double low = 0.0;
        double high = baselineRate;
        while (high - low > 1e-12) {
            double mid = (low + high) / 2;
            double rate = Math.max(0.0, baselineRate - mid);
            if (failProbability(cutoffs, counts, baselineTrials, baselineRate, testSamples, rate)
                    >= power) {
                high = mid;
            } else {
                low = mid;
            }
        }
        return OptionalDouble.of(high);
    }

    /**
     * The threshold-first inversion (§6.3): the smallest alpha at which
     * the rule yields a declared cutoff. The rule gives {@code c} exactly
     * when {@code P(X ≤ c − 1) ≤ alpha < P(X ≤ c)}, so the implied alpha
     * is the p-value at {@code c − 1}; 0 for a cutoff of 0.
     */
    public static ImpliedAlpha impliedAlpha(
            int baselineSuccesses, int baselineTrials, int testSamples, int declaredCutoff) {
        if (declaredCutoff < 0 || declaredCutoff > testSamples) {
            throw new IllegalArgumentException(
                    "the declared cutoff must be in 0.." + testSamples + ", got " + declaredCutoff);
        }
        if (declaredCutoff == 0) {
            return new ImpliedAlpha(OptionalDouble.of(0.0), Optional.of(true));
        }
        double below = pValue(declaredCutoff - 1, baselineSuccesses, baselineTrials, testSamples);
        double at = pValue(declaredCutoff, baselineSuccesses, baselineTrials, testSamples);
        if (!(below < at)) {
            return new ImpliedAlpha(OptionalDouble.empty(), Optional.empty());
        }
        return new ImpliedAlpha(OptionalDouble.of(below), Optional.of(below <= SOUND_IMPLIED_ALPHA));
    }

    // ── Shared with sizing ──────────────────────────────────────────

    /** The cutoff for each of several baseline counts. */
    static int[] cutoffs(int[] baselineCounts, int baselineTrials, int testSamples, double alpha) {
        int[] out = new int[baselineCounts.length];
        for (int i = 0; i < baselineCounts.length; i++) {
            out[i] = i == 0
                    ? cutoffUnchecked(baselineCounts[i], baselineTrials, testSamples, alpha)
                    : cutoffNear(baselineCounts[i], baselineTrials, testSamples, alpha, out[i - 1]);
        }
        return out;
    }

    /** The baseline counts carrying all but 2e-17 of {@code Bin(n_b, rate)}. */
    static int[] baselineWindow(int baselineTrials, double rate) {
        if (rate >= 1.0) {
            return new int[] { baselineTrials };
        }
        if (rate <= 0.0) {
            return new int[] { 0 };
        }
        int low = BinomialDistribution.of(baselineTrials, rate)
                .inverseCumulativeProbability(WINDOW_MASS);
        int high = baselineTrials - BinomialDistribution.of(baselineTrials, 1.0 - rate)
                .inverseCumulativeProbability(WINDOW_MASS);
        int[] counts = new int[high - low + 1];
        for (int i = 0; i < counts.length; i++) {
            counts[i] = low + i;
        }
        return counts;
    }

    /** {@code Σ_k P_{p_b}(K_b = k) P_{p_t}(K_t < c(k))} over the given counts. */
    static double failProbability(
            int[] cutoffs, int[] baselineCounts, int baselineTrials, double baselineRate,
            int testSamples, double testRate) {
        BinomialDistribution baseline = BinomialDistribution.of(baselineTrials, baselineRate);
        BinomialDistribution test = BinomialDistribution.of(testSamples, testRate);
        double sum = 0.0;
        for (int i = 0; i < baselineCounts.length; i++) {
            sum += baseline.probability(baselineCounts[i])
                    * test.cumulativeProbability(cutoffs[i] - 1);
        }
        return sum;
    }

    /** {@code P_rate(K_t < c)}. */
    static double belowCutoff(int cutoff, int testSamples, double rate) {
        return BinomialDistribution.of(testSamples, rate).cumulativeProbability(cutoff - 1);
    }

    private static void validate(
            int baselineSuccesses, int baselineTrials, int testSamples, double alpha) {
        if (baselineTrials <= 0) {
            throw new IllegalArgumentException(
                    "baseline trials must be positive, got " + baselineTrials);
        }
        if (testSamples <= 0) {
            throw new IllegalArgumentException("test samples must be positive, got " + testSamples);
        }
        if (baselineSuccesses < 0 || baselineSuccesses > baselineTrials) {
            throw new IllegalArgumentException(
                    "baseline successes must be in 0.." + baselineTrials + ", got "
                            + baselineSuccesses);
        }
        if (!(alpha > 0.0 && alpha < 1.0)) {
            throw new IllegalArgumentException("alpha must be in (0, 1), got " + alpha);
        }
    }

    // ── Results ─────────────────────────────────────────────────────

    /**
     * The cutoff of {@code regression/fisher} for one configuration, and
     * what the report discloses about it.
     *
     * @param cutoff the binding decision artefact: PASS iff {@code K_t ≥ cutoff}
     * @param testSamples {@code n_t}, the size the cutoff was derived for
     * @param alpha the one-sided level
     * @param sizeAtAssumedCommonRate see {@link RegressionRule#sizeAtAssumedCommonRate};
     *                                empty when the baseline rate is 0 or 1
     */
    public record Derivation(
            int cutoff, int testSamples, double alpha, OptionalDouble sizeAtAssumedCommonRate) {

        /** The cutoff as a rate, {@code c / n_t} — the displayed threshold. */
        public double thresholdReal() {
            return (double) cutoff / testSamples;
        }

        /** {@code c / n_t} rounded to six places, as a report displays it (§3.4). */
        public double displayedRate() {
            return Math.round(thresholdReal() * 1_000_000.0) / 1_000_000.0;
        }
    }

    /** A criterion decided by {@code regression/fisher}. */
    public record Decision(
            RuleVerdict verdict,
            int testSuccesses,
            int testSamples,
            int baselineSuccesses,
            int baselineTrials,
            Derivation derivation) {

        /** The rule that decided. */
        public DecisionRule rule() {
            return DecisionRule.REGRESSION_FISHER;
        }
    }

    /**
     * The threshold-first inversion of a declared cutoff.
     *
     * @param alpha   the implied alpha; empty when no alpha yields the cutoff
     * @param isSound whether the implied alpha is at most 0.20; empty
     *                with no implied alpha
     */
    public record ImpliedAlpha(OptionalDouble alpha, Optional<Boolean> isSound) {
    }
}
