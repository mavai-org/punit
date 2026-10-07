package org.mavai.punit.statistics;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import org.apache.commons.numbers.gamma.LogBeta;
import org.apache.commons.numbers.gamma.LogGamma;

/**
 * The two latency decision rules and the gates around them (companion §12).
 *
 * <p>Latency is judged on the <em>successful</em> latencies — those of the
 * samples that passed every functional criterion (§12.2.1). A latency
 * constraint is decided by the rule for its threshold source:
 *
 * <ul>
 *   <li>an <b>explicit</b> threshold {@code τ} by
 *       {@code latency/compliance-exact-binomial} (§12.3.4): the count of
 *       latencies at or below {@code τ} judged by the exact one-sided
 *       binomial test of compliance with {@code p_req = p};</li>
 *   <li>a <b>baseline-derived</b> threshold by {@code latency/precedence}
 *       (§12.4.2): the smallest baseline rank whose exact
 *       no-degradation breach probability for the test's nearest-rank
 *       percentile is at most alpha, the threshold being the observed
 *       baseline latency at that rank.</li>
 * </ul>
 *
 * <p>Both decisions are made after the run on the actual number of
 * successful latencies. Before the run the same searches on the
 * <em>expected</em> number give warnings and planning figures (§12.5.3),
 * never a verdict. The raw comparison of the observed percentile with a
 * threshold is reported as a raw figure: it decides nothing.
 */
public final class LatencyRules {

    /**
     * Tolerance on the expected-count products of §12.5.3 (a planned size
     * times a passing rate), so a product that is an integer in exact
     * arithmetic is not floored to the integer below it.
     */
    private static final double EXPECTED_COUNT_SLACK = 1e-9;

    private LatencyRules() {
    }

    /** Where a latency threshold comes from (§12.3.3). */
    public enum ThresholdSource {
        /** Declared by the contract: decided by {@code latency/compliance-exact-binomial}. */
        EXPLICIT,
        /** Derived from a baseline: decided by {@code latency/precedence}. */
        BASELINE_DERIVED
    }

    // ── Supported percentiles and their non-degeneracy minimums ─────

    /**
     * The supported level as an integer percentage (50, 90, 95 or 99),
     * the only levels the precedence rank and the non-degeneracy gate are
     * defined for.
     */
    static int percent(double percentile) {
        long percent = Math.round(100 * percentile);
        boolean supported = percent == 50 || percent == 90 || percent == 95 || percent == 99;
        if (!supported || Math.abs(100 * percentile - percent) > 1e-9) {
            throw new IllegalArgumentException(
                    "latency percentile must be one of p50, p90, p95, p99, got " + percentile);
        }
        return (int) percent;
    }

    /**
     * The non-degeneracy minimum of a supported percentile (§12.5.2):
     * 5 at p50 (an engineering minimum; the mathematical one is 3), 10 at
     * p90, 20 at p95, 100 at p99. Below it the empirical percentile is
     * the sample maximum (or minimum).
     */
    public static int minimumContributingSamples(double percentile) {
        return switch (percent(percentile)) {
            case 50 -> 5;
            case 90 -> 10;
            case 95 -> 20;
            default -> 100;
        };
    }

    // ── latency/precedence ──────────────────────────────────────────

    /** The test's nearest rank {@code r = ⌈P n_t / 100⌉}, in integer arithmetic. */
    public static int nearestRank(int testSamples, double percentile) {
        if (testSamples <= 0) {
            throw new IllegalArgumentException("test samples must be positive, got " + testSamples);
        }
        return (percent(percentile) * testSamples + 99) / 100;
    }

    /**
     * The no-degradation breach probability of baseline rank {@code k}
     * (§12.4.2): the probability, for continuous i.i.d. latencies, that
     * fewer than {@code r} of the {@code n_t} test latencies fall at or
     * below the baseline's {@code k}-th order statistic — that the test's
     * nearest-rank percentile exceeds it.
     */
    public static double breachProbability(
            int baselineTrials, int rank, int testSamples, double percentile) {
        int r = nearestRank(testSamples, percentile);
        double logNt = LogGamma.value(testSamples + 1.0);
        double logDenominator = LogBeta.value(rank, baselineTrials - rank + 1.0);
        double sum = 0.0;
        for (int j = 0; j < r; j++) {
            double logTerm = logNt
                    - LogGamma.value(j + 1.0)
                    - LogGamma.value(testSamples - j + 1.0)
                    + LogBeta.value(rank + j, baselineTrials - rank + 1.0 + testSamples - j)
                    - logDenominator;
            sum += Math.exp(logTerm);
        }
        return sum;
    }

    private static boolean admitsRank(
            int baselineTrials, int rank, int testSamples, double percentile, double alpha) {
        return ExactBoundary.atMostAlpha(
                breachProbability(baselineTrials, rank, testSamples, percentile),
                alpha,
                () -> ExactBoundary.breachProbability(
                        baselineTrials, rank, testSamples, nearestRank(testSamples, percentile)));
    }

    /**
     * The rank of {@code latency/precedence}: the smallest
     * {@code k ≤ n_b} with {@code breach(k) ≤ alpha}; empty when none
     * achieves it (saturated). The breach probability decreases in
     * {@code k}, so a rank exists exactly when the top rank achieves
     * alpha, and the smallest is found by bisection.
     */
    public static OptionalInt precedenceRank(
            int baselineTrials, int testSamples, double percentile, double alpha) {
        if (baselineTrials <= 0) {
            throw new IllegalArgumentException(
                    "baseline trials must be positive, got " + baselineTrials);
        }
        if (!(alpha > 0.0 && alpha < 1.0)) {
            throw new IllegalArgumentException("alpha must be in (0, 1), got " + alpha);
        }
        if (!admitsRank(baselineTrials, baselineTrials, testSamples, percentile, alpha)) {
            return OptionalInt.empty();
        }
        int low = 0;
        int high = baselineTrials;
        while (high - low > 1) {
            int mid = (low + high) >>> 1;
            if (admitsRank(baselineTrials, mid, testSamples, percentile, alpha)) {
                high = mid;
            } else {
                low = mid;
            }
        }
        return OptionalInt.of(high);
    }

    /**
     * The {@code latency/precedence} threshold for a test of
     * {@code testSamples} successful latencies against a baseline's
     * successful latencies.
     */
    public static PrecedenceThreshold derivePrecedenceThreshold(
            double[] baselineLatencies, int testSamples, double percentile, double alpha) {
        Objects.requireNonNull(baselineLatencies, "baselineLatencies");
        if (baselineLatencies.length == 0) {
            throw new IllegalArgumentException(
                    "cannot derive a latency threshold from an empty baseline");
        }
        double[] ordered = baselineLatencies.clone();
        Arrays.sort(ordered);
        int nb = ordered.length;
        OptionalInt rank = precedenceRank(nb, testSamples, percentile, alpha);
        return new PrecedenceThreshold(
                rank,
                rank.isPresent() ? OptionalDouble.of(ordered[rank.getAsInt() - 1]) : OptionalDouble.empty(),
                rank.isEmpty(),
                rank.isPresent()
                        ? OptionalDouble.of(breachProbability(nb, rank.getAsInt(), testSamples, percentile))
                        : OptionalDouble.empty(),
                nearestRank(testSamples, percentile),
                nb,
                LatencyStatistics.nearestRankPercentile(ordered, percentile));
    }

    // ── Planning (§12.5.3): warnings and figures, never a verdict ───

    /** {@code ⌊n_planned · p_baseline⌋}: an expectation, not a lower bound. */
    public static int expectedSuccessfulCount(int plannedSamples, double baselineSuccessRate) {
        return (int) Math.floor(plannedSamples * baselineSuccessRate + EXPECTED_COUNT_SLACK);
    }

    /**
     * The rank search on the expected successful count: a warning and
     * planning figures, never a verdict — saturation is decided after the
     * run.
     */
    public static PrecedencePlanning planPrecedence(
            int baselineTrials, int plannedSamples, double baselineSuccessRate,
            double percentile, double alpha) {
        int expected = expectedSuccessfulCount(plannedSamples, baselineSuccessRate);
        if (expected == 0) {
            return new PrecedencePlanning(0, true, OptionalInt.empty(), OptionalInt.empty());
        }
        OptionalInt rank = precedenceRank(baselineTrials, expected, percentile, alpha);
        int minimum = expected;
        while (!admitsRank(minimum, minimum, expected, percentile, alpha)) {
            minimum++;
        }
        return new PrecedencePlanning(expected, rank.isEmpty(), rank, OptionalInt.of(minimum));
    }

    /** The expected successful count against the non-degeneracy minimum. */
    public static NondegeneracyPlanning planNondegeneracy(
            double percentile, int plannedSamples, double baselineSuccessRate) {
        if (!(baselineSuccessRate > 0.0 && baselineSuccessRate <= 1.0)) {
            throw new IllegalArgumentException(
                    "the baseline success rate must be in (0, 1], got " + baselineSuccessRate);
        }
        int minimum = minimumContributingSamples(percentile);
        int expected = expectedSuccessfulCount(plannedSamples, baselineSuccessRate);
        int needed = (int) Math.ceil(minimum / baselineSuccessRate - EXPECTED_COUNT_SLACK);
        while (expectedSuccessfulCount(needed, baselineSuccessRate) < minimum) {
            needed++;
        }
        return new NondegeneracyPlanning(expected, minimum, expected < minimum, needed);
    }

    // ── The post-run non-degeneracy decision (§12.5.2, §12.5.4) ─────

    /** The post-run non-degeneracy outcome. */
    public enum NondegeneracyOutcome {
        /** The gate does not apply, or the percentile is not degenerate: decided by its rule. */
        DECIDED,
        /** A baseline-derived assertion under verification with too few latencies. */
        INCONCLUSIVE,
        /** A baseline-derived assertion under smoke intent with too few latencies: a directional signal only. */
        INDICATIVE
    }

    /**
     * The non-degeneracy decision on the actual count of successful
     * latencies (§12.5.4). The gate applies where the decision statistic
     * is the empirical percentile — a baseline-derived assertion — and
     * not to an explicit requirement, which decides on the
     * within-threshold count and has its own feasibility condition. It
     * follows the threshold source and the intent, never whether the
     * latency dimension is enforced or advisory (§12.6).
     *
     * @param underVerification whether the test runs under VERIFICATION intent
     */
    public static NondegeneracyDecision decideNondegeneracy(
            double percentile, int testSamples, boolean underVerification, ThresholdSource source) {
        boolean applies = source == ThresholdSource.BASELINE_DERIVED;
        boolean degenerate = testSamples < minimumContributingSamples(percentile);
        NondegeneracyOutcome outcome;
        if (!applies || !degenerate) {
            outcome = NondegeneracyOutcome.DECIDED;
        } else if (underVerification) {
            outcome = NondegeneracyOutcome.INCONCLUSIVE;
        } else {
            outcome = NondegeneracyOutcome.INDICATIVE;
        }
        return new NondegeneracyDecision(applies, degenerate, outcome);
    }

    // ── latency/compliance-exact-binomial ───────────────────────────

    /**
     * Decides an explicit latency requirement on the successful latencies:
     * {@code Y} counts those at or below the threshold (a latency equal
     * to it counts as within), and the verdict is PASS iff
     * {@code Y ≥ y_min}; INCONCLUSIVE when no count of this many latencies
     * can pass.
     */
    public static LatencyCompliance evaluateCompliance(
            double[] latencies, double thresholdMs, double percentile, double alpha) {
        Objects.requireNonNull(latencies, "latencies");
        percent(percentile);
        if (!(alpha > 0.0 && alpha < 1.0)) {
            throw new IllegalArgumentException("alpha must be in (0, 1), got " + alpha);
        }
        int n = latencies.length;
        int within = 0;
        for (double latency : latencies) {
            if (latency <= thresholdMs) {
                within++;
            }
        }
        OptionalInt yMin = n > 0
                ? ComplianceRule.minimumPassingCount(percentile, n, alpha)
                : OptionalInt.empty();
        OptionalDouble observed = n > 0
                ? OptionalDouble.of(LatencyStatistics.nearestRankPercentile(latencies, percentile))
                : OptionalDouble.empty();
        RuleVerdict verdict = yMin.isEmpty()
                ? RuleVerdict.INCONCLUSIVE
                : within >= yMin.getAsInt() ? RuleVerdict.PASS : RuleVerdict.FAIL;
        return new LatencyCompliance(
                n,
                within,
                yMin,
                verdict,
                yMin.isEmpty()
                        ? OptionalDouble.empty()
                        : OptionalDouble.of(ComplianceRule.falseCompliance(percentile, n, yMin)),
                n > 0
                        ? OptionalDouble.of(ComplianceRule.clopperPearsonLower(within, n, alpha))
                        : OptionalDouble.empty(),
                observed,
                observed.isPresent()
                        ? Optional.of(observed.getAsDouble() <= thresholdMs)
                        : Optional.empty());
    }

    // ── Results ─────────────────────────────────────────────────────

    /**
     * A baseline-derived latency threshold under {@code latency/precedence}.
     *
     * @param rank               the precedence rank; empty when saturated
     * @param threshold          the baseline latency at {@code rank} — an
     *                           observed value; empty when saturated
     * @param saturated          no rank achieves alpha for a test of this
     *                           size: INCONCLUSIVE, and no rank is clamped
     *                           to manufacture a threshold
     * @param breachProbability  the breach probability at {@code rank}
     * @param testRank           the test's nearest rank {@code r}
     * @param n                  the number of baseline latencies
     * @param baselinePercentile the baseline's own nearest-rank percentile,
     *                           for reporting; never the threshold
     */
    public record PrecedenceThreshold(
            OptionalInt rank,
            OptionalDouble threshold,
            boolean saturated,
            OptionalDouble breachProbability,
            int testRank,
            int n,
            double baselinePercentile) {
    }

    /**
     * The pre-run existence check of a baseline-derived assertion.
     *
     * @param expectedTestSamples   the expected number of successful latencies
     * @param warning               no rank exists at the expected count
     * @param planningRank          the rank at the expected count; empty under a warning
     * @param minimumBaselineTrials the smallest baseline, no smaller than the
     *                              expected count, that supports a rank for it;
     *                              empty when no successful latency is expected
     */
    public record PrecedencePlanning(
            int expectedTestSamples,
            boolean warning,
            OptionalInt planningRank,
            OptionalInt minimumBaselineTrials) {
    }

    /**
     * The pre-run non-degeneracy check: a warning and a planning figure.
     *
     * @param plannedSamplesNeeded the smallest planned size whose expected
     *                             count reaches the minimum
     */
    public record NondegeneracyPlanning(
            int expectedTestSamples,
            int minimumContributingSamples,
            boolean warning,
            int plannedSamplesNeeded) {
    }

    /** Whether the gate applies, whether the percentile is degenerate, and the outcome. */
    public record NondegeneracyDecision(
            boolean applies, boolean degenerate, NondegeneracyOutcome outcome) {
    }

    /**
     * An explicit latency requirement decided under
     * {@code latency/compliance-exact-binomial}.
     *
     * @param testSamples            the number of successful latencies {@code n_s}
     * @param withinThreshold        {@code Y}, the latencies at or below the threshold
     * @param minimumWithin          {@code y_min}; empty when no count can pass
     * @param verdict                PASS iff {@code Y ≥ y_min}; INCONCLUSIVE
     *                               when no count can pass
     * @param falseCompliance        {@code P_p(Y ≥ y_min)}; empty when no count can pass
     * @param clopperPearsonLower    the one-sided lower bound on {@code F(τ)}
     * @param observedPercentileMs   the raw nearest-rank percentile
     * @param rawPercentilePass      the raw comparison {@code Q(p) ≤ τ}; decides nothing
     */
    public record LatencyCompliance(
            int testSamples,
            int withinThreshold,
            OptionalInt minimumWithin,
            RuleVerdict verdict,
            OptionalDouble falseCompliance,
            OptionalDouble clopperPearsonLower,
            OptionalDouble observedPercentileMs,
            Optional<Boolean> rawPercentilePass) {

        /** Whether any count of the successful latencies could pass. */
        public boolean passPossible() {
            return minimumWithin.isPresent();
        }
    }
}
