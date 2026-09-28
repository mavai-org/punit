package org.mavai.punit.verdict;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import org.mavai.punit.statistics.DecisionRule;

/**
 * One enforced latency constraint as the verdict records it
 * (Statistical Companion §12.3, §12.4; verdict-1.7 {@code <evaluation>}).
 *
 * @param percentile         the percentile label ({@code p50}, {@code p90},
 *                           {@code p95}, {@code p99})
 * @param observedMs         the test's nearest-rank percentile, when there
 *                           were successful latencies
 * @param thresholdMs        the explicit ceiling or the derived threshold;
 *                           empty when {@link Status#SATURATED}
 * @param provenance         where the threshold comes from
 * @param status             the evaluation's outcome
 * @param baselineConfidence the confidence a baseline-derived threshold is
 *                           derived at
 * @param baselineRank       the precedence rank; empty when saturated or explicit
 * @param baselineN          the number of baseline latencies, baseline-derived only
 * @param decisionRule       the rule that decided the constraint
 * @param withinThreshold    the latencies at or below an explicit ceiling
 * @param requiredWithin     the smallest such count that demonstrates
 *                           compliance; empty when none can
 * @param indicative         evaluated below the percentile's non-degeneracy
 *                           minimum under SMOKE: a directional signal only
 */
public record LatencyEvaluation(
        String percentile,
        OptionalLong observedMs,
        OptionalLong thresholdMs,
        Provenance provenance,
        Status status,
        OptionalDouble baselineConfidence,
        OptionalInt baselineRank,
        OptionalInt baselineN,
        DecisionRule decisionRule,
        OptionalInt withinThreshold,
        OptionalInt requiredWithin,
        boolean indicative) {

    /** Where a latency threshold comes from. */
    public enum Provenance {
        EXPLICIT("explicit"),
        BASELINE_DERIVED("baseline-derived");

        private final String label;

        Provenance(String label) {
            this.label = label;
        }

        /** The label the verdict record spells. */
        public String label() {
            return label;
        }
    }

    /** The outcome of an enforced constraint. */
    public enum Status {
        /** Decided and passed. */
        PASS,
        /** Decided and failed: the test fails. */
        STRICT_FAIL,
        /** Too few successful latencies to decide: inconclusive. */
        INFEASIBLE,
        /** No baseline rank achieves alpha: no threshold, inconclusive. */
        SATURATED
    }

    public LatencyEvaluation {
        Objects.requireNonNull(percentile, "percentile");
        Objects.requireNonNull(observedMs, "observedMs");
        Objects.requireNonNull(thresholdMs, "thresholdMs");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(baselineConfidence, "baselineConfidence");
        Objects.requireNonNull(baselineRank, "baselineRank");
        Objects.requireNonNull(baselineN, "baselineN");
        Objects.requireNonNull(decisionRule, "decisionRule");
        Objects.requireNonNull(withinThreshold, "withinThreshold");
        Objects.requireNonNull(requiredWithin, "requiredWithin");
        if (status == Status.SATURATED && (thresholdMs.isPresent() || baselineRank.isPresent())) {
            throw new IllegalArgumentException(
                    "a saturated evaluation has no threshold and no baseline rank");
        }
    }
}
