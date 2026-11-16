package org.mavai.punit.verdict;

import java.util.Objects;

/**
 * One per-criterion row inside a {@link PerCriterionStructure}:
 * the criterion's identifier, its three-valued aggregate verdict,
 * the per-outcome sample counts, the observed pass-rate, the
 * threshold the verdict was judged against, and the criterion's mode.
 *
 * <p>An observational row ({@link #observational}) states its counts
 * alone: it estimates no rate and carries no threshold, rule or required
 * count (Statistical Companion §1.4.5, §10.2), so a zero-failures
 * criterion cannot be represented as an inferential row at a threshold
 * of 1.
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
 * @param mode          inferential, or observational for a zero-failures
 *                      criterion
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
        java.util.OptionalInt requiredPass,
        CriterionMode mode) {

    /** An inferential row. */
    public CriterionRow(
            String criterionId,
            org.mavai.punit.api.spec.Verdict verdict,
            int pass,
            int fail,
            int inconclusive,
            double observedRate,
            double threshold,
            java.util.Optional<org.mavai.punit.statistics.DecisionRule> decisionRule,
            java.util.OptionalInt requiredPass) {
        this(criterionId, verdict, pass, fail, inconclusive, observedRate, threshold,
                decisionRule, requiredPass, CriterionMode.INFERENTIAL);
    }

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

    /** An inferential row no rule decided (a gate that fired first). */
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
        Objects.requireNonNull(mode, "mode");
        if (mode == CriterionMode.OBSERVATIONAL
                && (!Double.isNaN(observedRate) || !Double.isNaN(threshold)
                        || decisionRule.isPresent() || requiredPass.isPresent())) {
            throw new IllegalArgumentException(
                    "an observational row states its counts alone: no observed rate, "
                            + "threshold, decision rule or required count");
        }
        if (pass < 0 || fail < 0 || inconclusive < 0) {
            throw new IllegalArgumentException(
                    "counts must be non-negative; got pass=" + pass
                            + ", fail=" + fail
                            + ", inconclusive=" + inconclusive);
        }
    }

    /**
     * An observational (zero-failures) row: its verdict and counts, and
     * nothing a rate would carry.
     */
    public static CriterionRow observational(
            String criterionId,
            org.mavai.punit.api.spec.Verdict verdict,
            int pass,
            int fail,
            int inconclusive) {
        return new CriterionRow(criterionId, verdict, pass, fail, inconclusive,
                Double.NaN, Double.NaN, java.util.Optional.empty(),
                java.util.OptionalInt.empty(), CriterionMode.OBSERVATIONAL);
    }

    /** Whether the criterion estimates its rate and was decided by a rule. */
    public boolean inferential() {
        return mode == CriterionMode.INFERENTIAL;
    }

    /** Sum of all per-criterion sample outcomes — the marginal denominator. */
    public int total() {
        return pass + fail + inconclusive;
    }
}
