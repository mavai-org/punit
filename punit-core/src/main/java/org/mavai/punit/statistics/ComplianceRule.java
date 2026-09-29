package org.mavai.punit.statistics;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import org.apache.commons.statistics.distribution.BetaDistribution;
import org.apache.commons.statistics.distribution.BinomialDistribution;

/**
 * Normative compliance under {@code compliance/exact-binomial}
 * (companion §3.6, §5.5, §5.7).
 *
 * <p>A requirement {@code p_req} is demonstrated by the exact one-sided
 * binomial test of {@code H0: p ≤ p_req} against {@code H1: p > p_req}.
 * The decision artefact is the smallest passing count
 * {@code k_min = min{k : P_{p_req}(K ≥ k) ≤ alpha}}; the test passes iff
 * {@code K ≥ k_min}. A pass means the evidence supports compliance at the
 * configured level; a fail means compliance was not demonstrated — not
 * that the rate is below the requirement.
 *
 * <p>A design can pass at all only if the all-success outcome clears the
 * test, {@code p_req^n ≤ alpha} — the feasibility minimum
 * {@code ⌈log alpha / log p_req⌉}. Compliance tests are sized for the
 * chance of a pass when the service is in fact better than required, at
 * a design alternative rate above {@code p_req}.
 *
 * <p>{@code latency/compliance-exact-binomial} applies the same test to
 * the count of latencies within an explicit threshold
 * ({@link LatencyRules#evaluateCompliance}).
 */
public final class ComplianceRule {

    /** How far the exact sizing search looks before declaring a design unsettled. */
    public static final int DEFAULT_SIZING_HORIZON = 20_000;

    private ComplianceRule() {
    }

    // ── The rule ────────────────────────────────────────────────────

    /**
     * {@code k_min} of {@code compliance/exact-binomial}; empty when no
     * count of this size can pass.
     */
    public static OptionalInt minimumPassingCount(double requirement, int samples, double alpha) {
        if (samples <= 0) {
            throw new IllegalArgumentException("samples must be positive, got " + samples);
        }
        validate(requirement, alpha);
        if (!admits(samples, samples, requirement, alpha)) {
            return OptionalInt.empty();
        }
        int low = 0;          // never admitted: P(K ≥ 0) = 1 > alpha
        int high = samples;   // admitted
        while (high - low > 1) {
            int mid = (low + high) >>> 1;
            if (admits(mid, samples, requirement, alpha)) {
                high = mid;
            } else {
                low = mid;
            }
        }
        return OptionalInt.of(high);
    }

    /**
     * Whether a count is admitted: its upper tail
     * {@code P_{p_req}(K ≥ count)} is at most alpha (inclusive, under the
     * exact-boundary convention).
     */
    static boolean admits(int count, int samples, double requirement, double alpha) {
        return ExactBoundary.atMostAlpha(
                upperTail(count, samples, requirement),
                alpha,
                () -> ExactBoundary.binomialUpperTail(count, samples, requirement));
    }

    private static double upperTail(int count, int samples, double rate) {
        if (count <= 0) {
            return 1.0;
        }
        if (count > samples) {
            return 0.0;
        }
        return BinomialDistribution.of(samples, rate).survivalProbability(count - 1);
    }

    /**
     * The smallest size at which a pass is possible,
     * {@code ⌈log alpha / log p_req⌉}, confirmed against the definition
     * ({@code p_req^n ≤ alpha}, under the exact-boundary convention) so
     * floating point cannot move it by one.
     */
    public static int minimumFeasibleSamples(double requirement, double alpha) {
        validate(requirement, alpha);
        int samples = Math.max(1, (int) Math.ceil(Math.log(alpha) / Math.log(requirement)));
        while (samples > 1 && feasible(samples - 1, requirement, alpha)) {
            samples--;
        }
        while (!feasible(samples, requirement, alpha)) {
            samples++;
        }
        return samples;
    }

    private static boolean feasible(int samples, double requirement, double alpha) {
        return ExactBoundary.atMostAlpha(
                Math.pow(requirement, samples),
                alpha,
                () -> ExactBoundary.binomialUpperTail(samples, samples, requirement));
    }

    /**
     * The one-sided Clopper–Pearson lower bound at level {@code 1 − alpha}.
     * It coincides with the exact test — PASS iff the bound reaches
     * {@code p_req} — and is reported beside the verdict; it decides
     * nothing. Zero at no successes.
     */
    public static double clopperPearsonLower(int successes, int samples, double alpha) {
        if (successes == 0) {
            return 0.0;
        }
        return BetaDistribution.of(successes, samples - successes + 1)
                .inverseCumulativeProbability(alpha);
    }

    /**
     * {@code P_{p_req}(K ≥ k_min)}, the false-compliance probability of the
     * discrete decision at the requirement; 0 when no count can pass.
     */
    public static double falseCompliance(double requirement, int samples, OptionalInt minimumPassing) {
        return minimumPassing.isEmpty()
                ? 0.0
                : upperTail(minimumPassing.getAsInt(), samples, requirement);
    }

    /**
     * Decides a criterion against a given requirement. When no count of
     * this size can pass the verdict is FAIL, certain before the run and
     * carrying no evidence about the service.
     */
    public static Decision decide(int successes, int samples, double requirement, double alpha) {
        if (successes < 0 || successes > samples) {
            throw new IllegalArgumentException(
                    "successes must be in 0.." + samples + ", got " + successes);
        }
        OptionalInt kMin = minimumPassingCount(requirement, samples, alpha);
        boolean passed = kMin.isPresent() && successes >= kMin.getAsInt();
        return new Decision(
                passed ? RuleVerdict.PASS : RuleVerdict.FAIL,
                successes, samples, requirement, alpha, kMin,
                falseCompliance(requirement, samples, kMin),
                clopperPearsonLower(successes, samples, alpha));
    }

    // ── Sizing (§5.5) ───────────────────────────────────────────────

    /** Where a compliance sizing alternative came from; the report names it. */
    public enum AlternativeKind {
        /** {@code p_req + δ}: the declared assurance margin above the requirement. */
        MARGIN,
        /** {@code (p_req + 1) / 2}: the default where {@code p_req + δ ≥ 1}. */
        MIDWAY,
        /** The design alternative rate the contract declared directly. */
        DECLARED
    }

    /** The design alternative rate a compliance design is sized at, and its kind. */
    public record SizingAlternative(double rate, AlternativeKind kind) {
        public SizingAlternative {
            Objects.requireNonNull(kind, "kind");
        }
    }

    /** The declared alternative, else {@code p_req + δ}, else the midway rate. */
    public static SizingAlternative sizingAlternative(
            double requirement, double margin, OptionalDouble declaredRate) {
        if (declaredRate.isPresent()) {
            double rate = declaredRate.getAsDouble();
            if (!(rate > requirement && rate <= 1.0)) {
                throw new IllegalArgumentException(
                        "a declared alternative rate must lie in (requirement, 1], got " + rate);
            }
            return new SizingAlternative(rate, AlternativeKind.DECLARED);
        }
        if (requirement + margin < 1.0) {
            return new SizingAlternative(requirement + margin, AlternativeKind.MARGIN);
        }
        return new SizingAlternative((requirement + 1.0) / 2, AlternativeKind.MIDWAY);
    }

    /**
     * The smallest size from which {@code P(PASS | alternative)} stays at
     * the target, up to the horizon. Power is a sawtooth in {@code n},
     * and zero wherever the design is infeasible, so the feasibility gate
     * sits inside the search.
     */
    public static Sizing size(
            double requirement, double margin, double alpha, double power,
            OptionalDouble declaredRate, int horizon) {
        validate(requirement, alpha);
        SizingAlternative alternative = sizingAlternative(requirement, margin, declaredRate);
        double[] powers = new double[horizon];
        int kMin = 1;
        for (int n = 1; n <= horizon; n++) {
            kMin = minimumPassingCountNear(requirement, n, alpha, kMin);
            powers[n - 1] = kMin > n
                    ? 0.0
                    : upperTail(kMin, n, alternative.rate());
        }
        int lastBelow = -1;
        int firstReached = -1;
        for (int i = 0; i < horizon; i++) {
            if (powers[i] < power) {
                lastBelow = i;
            } else if (firstReached < 0) {
                firstReached = i;
            }
        }
        OptionalInt firstCrossing = firstReached < 0
                ? OptionalInt.empty() : OptionalInt.of(firstReached + 1);
        if (lastBelow == horizon - 1) {
            return new Sizing(OptionalInt.empty(), firstCrossing, OptionalDouble.empty(), alternative);
        }
        int start = lastBelow + 1;
        return new Sizing(OptionalInt.of(start + 1), firstCrossing,
                OptionalDouble.of(powers[start]), alternative);
    }

    /**
     * {@code k_min} at size {@code n} walked from a neighbouring size's;
     * {@code n + 1} stands for "no count can pass".
     */
    private static int minimumPassingCountNear(double requirement, int samples, double alpha, int guess) {
        int k = Math.max(1, Math.min(guess, samples + 1));
        while (k <= samples && !admits(k, samples, requirement, alpha)) {
            k++;
        }
        while (k > 1 && admits(k - 1, samples, requirement, alpha)) {
            k--;
        }
        return k;
    }

    private static void validate(double requirement, double alpha) {
        if (!(requirement > 0.0 && requirement < 1.0)) {
            throw new IllegalArgumentException(
                    "the requirement must be in (0, 1), got " + requirement);
        }
        if (!(alpha > 0.0 && alpha < 1.0)) {
            throw new IllegalArgumentException("alpha must be in (0, 1), got " + alpha);
        }
    }

    // ── Results ─────────────────────────────────────────────────────

    /**
     * A criterion decided by {@code compliance/exact-binomial}.
     *
     * @param verdict              PASS iff {@code K ≥ k_min}
     * @param minimumPassing       {@code k_min}; empty when no count can pass
     * @param falseCompliance      {@code P_{p_req}(K ≥ k_min)}, 0 when no count can pass
     * @param clopperPearsonLower  the one-sided lower bound, reported beside the verdict
     */
    public record Decision(
            RuleVerdict verdict,
            int successes,
            int samples,
            double requirement,
            double alpha,
            OptionalInt minimumPassing,
            double falseCompliance,
            double clopperPearsonLower) {

        /** Whether any outcome of this size could have passed. */
        public boolean passPossible() {
            return minimumPassing.isPresent();
        }

        /** The rule that decided. */
        public DecisionRule rule() {
            return DecisionRule.COMPLIANCE_EXACT_BINOMIAL;
        }
    }

    /**
     * Exact sizing of a compliance design.
     *
     * @param requiredSamples the smallest {@code n} from which power at the
     *                        alternative stays at the target up to the
     *                        horizon; empty when the design has not settled
     *                        by the horizon (it is expensive, not invalid)
     * @param firstCrossing   the smallest {@code n} whose power first
     *                        reaches the target — reported, never the answer
     * @param achievedPower   the power at {@code requiredSamples}
     * @param alternative     the design alternative rate used, and its kind
     */
    public record Sizing(
            OptionalInt requiredSamples,
            OptionalInt firstCrossing,
            OptionalDouble achievedPower,
            SizingAlternative alternative) {
    }
}
