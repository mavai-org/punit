package org.mavai.punit.verdict;

import java.util.Objects;

/**
 * One per-criterion row inside a {@link PerCriterionStructure}:
 * the criterion's identifier, its three-valued aggregate verdict,
 * the per-outcome sample counts, the observed pass-rate, and the
 * threshold the verdict was judged against.
 *
 * <p>Persistence-layer cousin of
 * {@code org.mavai.punit.api.spec.PerCriterionVerdict}; the two
 * carry the same data but live in different abstraction layers
 * (spec vs. verdict record) so renderers and persistence stay
 * decoupled from api-package types.
 *
 * @param criterionId   stable identifier for the criterion
 * @param verdict       PASS / FAIL / INCONCLUSIVE under the
 *                      contract-inherited threshold
 * @param pass          count of PASS samples
 * @param fail          count of FAIL samples (condition failures plus
 *                      transform / no-value failures)
 * @param inconclusive  retained per-trial inconclusive count for the
 *                      cross-framework verdict-XML schema; always
 *                      zero since a per-trial inconclusive is now a
 *                      FAIL
 * @param observedRate  observed marginal pass-rate ({@code pass / total})
 *                      or {@link Double#NaN} when {@code total} is zero
 * @param threshold     the contract-inherited threshold the verdict
 *                      was judged against or {@link Double#NaN} when
 *                      no threshold was resolved (e.g. the run gated
 *                      INCONCLUSIVE before threshold derivation);
 *                      for a regression criterion the cutoff as a rate,
 *                      {@code c / n_t}
 * @param decisionRule  the rule that decided the criterion, when one did
 * @param requiredPass  the smallest passing count under the rule that
 *                      decided the criterion — the Fisher cutoff {@code c}
 *                      for {@code regression/fisher}, {@code k_min} for
 *                      {@code compliance/exact-binomial} — so PASS iff
 *                      {@code pass >= requiredPass}; the count the rule
 *                      decided with, never derived from {@code threshold}.
 *                      Empty when no rule decided the criterion or when no
 *                      count can pass (a compliance design too small)
 */
// mavai-ref: JVI-8E4WNW5 — do not remove (resolves in mavai-orchestrator)
public record CriterionRow(
        String criterionId,
        org.mavai.punit.api.spec.Verdict verdict,
        int pass,
        int fail,
        int inconclusive,
        double observedRate,
        double threshold,
        java.util.Optional<org.mavai.punit.statistics.DecisionRule> decisionRule,
        java.util.OptionalInt requiredPass) {

    /** A row with its deciding rule but no stated passing count. */
    public CriterionRow(
            String criterionId,
            org.mavai.punit.api.spec.Verdict verdict,
            int pass,
            int fail,
            int inconclusive,
            double observedRate,
            double threshold,
            java.util.Optional<org.mavai.punit.statistics.DecisionRule> decisionRule) {
        this(criterionId, verdict, pass, fail, inconclusive, observedRate, threshold,
                decisionRule, java.util.OptionalInt.empty());
    }

    /** A row no rule decided (zero-failures, or a gate that fired first). */
    public CriterionRow(
            String criterionId,
            org.mavai.punit.api.spec.Verdict verdict,
            int pass,
            int fail,
            int inconclusive,
            double observedRate,
            double threshold) {
        this(criterionId, verdict, pass, fail, inconclusive, observedRate, threshold,
                java.util.Optional.empty(), java.util.OptionalInt.empty());
    }

    public CriterionRow {
        Objects.requireNonNull(criterionId, "criterionId");
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(decisionRule, "decisionRule");
        Objects.requireNonNull(requiredPass, "requiredPass");
        if (requiredPass.isPresent() && requiredPass.getAsInt() < 0) {
            throw new IllegalArgumentException(
                    "requiredPass must be non-negative; got " + requiredPass.getAsInt());
        }
        if (pass < 0 || fail < 0 || inconclusive < 0) {
            throw new IllegalArgumentException(
                    "counts must be non-negative; got pass=" + pass
                            + ", fail=" + fail
                            + ", inconclusive=" + inconclusive);
        }
    }

    /** Sum of all per-criterion sample outcomes — the marginal denominator. */
    public int total() {
        return pass + fail + inconclusive;
    }
}
