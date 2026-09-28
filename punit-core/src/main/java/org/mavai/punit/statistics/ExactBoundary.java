package org.mavai.punit.statistics;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.function.Supplier;

import org.apache.commons.numbers.fraction.BigFraction;

/**
 * The exact-boundary convention every exact decision rule shares
 * (companion §10.6).
 *
 * <p>Each exact rule compares a probability with alpha by an inclusive
 * rule: a Fisher p-value at most alpha fails a test count, a binomial
 * upper tail at most alpha admits a count, a precedence breach
 * probability at most alpha admits a rank. At an exact boundary the
 * probability <em>equals</em> alpha, and double-precision evaluation can
 * land on either side of it. The convention:
 *
 * <ol>
 *   <li>compute the probability in double precision;</li>
 *   <li>when {@code |value − alpha| ≤ GUARD · alpha}, recompute it
 *       exactly, in rational arithmetic, from the declared inputs;</li>
 *   <li>apply the inclusive rule to the exact value.</li>
 * </ol>
 *
 * <p>Declared rates and levels are read as the exact decimals they are
 * written as: 0.05 is 1/20, 0.995 is 199/200.
 */
final class ExactBoundary {

    /** Relative band around alpha inside which a probability is recomputed exactly. */
    static final double GUARD = 1e-9;

    private static final MathContext DECLARED_DIGITS = new MathContext(15);

    private ExactBoundary() {
    }

    /**
     * Whether {@code value ≤ alpha} under the convention. {@code exact}
     * computes the probability in rational arithmetic and is called only
     * inside the guard band.
     */
    static boolean atMostAlpha(double value, double alpha, Supplier<BigFraction> exact) {
        if (Math.abs(value - alpha) <= GUARD * alpha) {
            return exact.get().compareTo(exactDecimal(alpha)) <= 0;
        }
        return value <= alpha;
    }

    /**
     * A declared decimal as the exact rational it was written as. Fifteen
     * significant digits recover what was typed from the nearest double
     * ({@code 0.05} becomes 1/20, not its binary neighbour).
     */
    static BigFraction exactDecimal(double value) {
        BigDecimal decimal = new BigDecimal(value).round(DECLARED_DIGITS).stripTrailingZeros();
        if (decimal.scale() <= 0) {
            return BigFraction.of(decimal.toBigIntegerExact());
        }
        return BigFraction.of(decimal.unscaledValue(), BigInteger.TEN.pow(decimal.scale()));
    }

    /**
     * The one-sided Fisher p-value {@code P(X ≤ k_t)} as a rational:
     * {@code X} hypergeometric, the number of the {@code s = k_b + k_t}
     * pooled successes falling in the test's {@code n_t} of the
     * {@code n_b + n_t} trials.
     */
    static BigFraction fisherPValue(
            int testSuccesses, int baselineSuccesses, int baselineTrials, int testSamples) {
        int total = baselineTrials + testSamples;
        int pooled = baselineSuccesses + testSuccesses;
        int low = Math.max(0, pooled - baselineTrials);
        if (testSuccesses < low) {
            return BigFraction.ZERO;
        }
        BigInteger numerator = BigInteger.ZERO;
        for (int x = low; x <= testSuccesses; x++) {
            numerator = numerator.add(
                    choose(pooled, x).multiply(choose(total - pooled, testSamples - x)));
        }
        return BigFraction.of(numerator, choose(total, testSamples));
    }

    /** {@code P(K ≥ count)} for {@code K ~ Bin(trials, rate)} as a rational. */
    static BigFraction binomialUpperTail(int count, int trials, double rate) {
        if (count <= 0) {
            return BigFraction.ONE;
        }
        if (count > trials) {
            return BigFraction.ZERO;
        }
        BigFraction q = exactDecimal(rate);
        BigInteger a = q.getNumerator();
        BigInteger b = q.getDenominator();
        BigInteger bMinusA = b.subtract(a);
        BigInteger numerator = BigInteger.ZERO;
        for (int j = count; j <= trials; j++) {
            numerator = numerator.add(
                    choose(trials, j).multiply(a.pow(j)).multiply(bMinusA.pow(trials - j)));
        }
        return BigFraction.of(numerator, b.pow(trials));
    }

    /**
     * The precedence breach probability of baseline rank {@code k} as a
     * rational:
     * {@code Σ_{j<r} C(n_t, j) B(k + j, n_b − k + 1 + n_t − j) / B(k, n_b − k + 1)}
     * with {@code B(a, b) = (a − 1)! (b − 1)! / (a + b − 1)!}, collected
     * over one common denominator.
     */
    static BigFraction breachProbability(int baselineTrials, int rank, int testSamples, int testRank) {
        int nb = baselineTrials;
        int k = rank;
        int nt = testSamples;
        BigInteger sum = BigInteger.ZERO;
        for (int j = 0; j < testRank; j++) {
            sum = sum.add(choose(nt, j)
                    .multiply(factorial(k + j - 1))
                    .multiply(factorial(nb - k + nt - j)));
        }
        BigInteger numerator = factorial(nb).multiply(sum);
        BigInteger denominator = factorial(nb + nt)
                .multiply(factorial(k - 1))
                .multiply(factorial(nb - k));
        return BigFraction.of(numerator, denominator);
    }

    static BigInteger choose(int n, int k) {
        if (k < 0 || k > n) {
            return BigInteger.ZERO;
        }
        int m = Math.min(k, n - k);
        BigInteger result = BigInteger.ONE;
        for (int i = 1; i <= m; i++) {
            result = result.multiply(BigInteger.valueOf(n - m + i)).divide(BigInteger.valueOf(i));
        }
        return result;
    }

    private static BigInteger factorial(int n) {
        BigInteger result = BigInteger.ONE;
        for (int i = 2; i <= n; i++) {
            result = result.multiply(BigInteger.valueOf(i));
        }
        return result;
    }
}
