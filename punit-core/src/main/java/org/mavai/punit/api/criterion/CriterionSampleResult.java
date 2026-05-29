package org.mavai.punit.api.criterion;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.PostconditionResult;

/**
 * The full per-sample evaluation record for one criterion: the
 * two-valued outcome, the per-postcondition results when the chain
 * ran, and — for a FAIL that did not run the chain — the reason the
 * chain did not run.
 *
 * <p>Constructed by a {@link Criterion#evaluate(Object)} call. The
 * record is the single contract between a criterion and downstream
 * consumers (verdict path, reports, sentinel) — anything those
 * consumers need about the criterion's behaviour on this sample
 * rides here.
 *
 * <p>A FAIL takes one of two shapes:
 * <ul>
 *   <li><b>condition failure</b> — the postcondition chain ran and
 *       at least one postcondition failed. Carries the
 *       per-postcondition results; no reason.</li>
 *   <li><b>transform / no-value failure</b> — the criterion's
 *       pre-postcondition transform returned {@code Outcome.Fail} or
 *       threw, so no postcondition was reached. Carries a
 *       {@code reason} — an {@link Outcome.Fail} whose symbolic name
 *       names the kind of failure — and no postcondition results.
 *       Later steps may introduce other reason sources
 *       (availability-gate failure, apply-level failure) without
 *       changing this record's shape.</li>
 * </ul>
 * Both shapes count as a non-pass in the criterion's denominator.
 *
 * @param criterionId  the criterion's stable identifier, copied onto
 *                     the result for downstream addressing
 * @param outcome      the per-sample outcome
 * @param postconditionResults the per-postcondition results when the
 *                     chain ran (empty for a transform / no-value
 *                     FAIL — no postcondition was reached)
 * @param reason       the reason the chain did not run for a
 *                     transform / no-value FAIL (empty otherwise).
 *                     Preserves the failure name and message for
 *                     diagnostics.
 */
public record CriterionSampleResult(
        String criterionId,
        CriterionSampleOutcome outcome,
        List<PostconditionResult> postconditionResults,
        Optional<Outcome.Fail<?>> reason) {

    public CriterionSampleResult {
        Objects.requireNonNull(criterionId, "criterionId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(postconditionResults, "postconditionResults");
        Objects.requireNonNull(reason, "reason");
        postconditionResults = List.copyOf(postconditionResults);

        if (outcome == CriterionSampleOutcome.PASS) {
            if (reason.isPresent()) {
                throw new IllegalArgumentException(
                        "PASS result must not carry a reason");
            }
        } else {
            // FAIL: exactly one of postcondition results (condition
            // failure) or a reason (transform / no-value failure).
            if (reason.isPresent() && !postconditionResults.isEmpty()) {
                throw new IllegalArgumentException(
                        "FAIL result must not carry both a reason and "
                                + "postcondition results");
            }
        }
    }

    public static CriterionSampleResult pass(
            String criterionId, List<PostconditionResult> results) {
        return new CriterionSampleResult(
                criterionId, CriterionSampleOutcome.PASS, results, Optional.empty());
    }

    public static CriterionSampleResult fail(
            String criterionId, List<PostconditionResult> results) {
        return new CriterionSampleResult(
                criterionId, CriterionSampleOutcome.FAIL, results, Optional.empty());
    }

    /**
     * A FAIL produced because the criterion's transform failed (or
     * produced no value), so the postcondition chain never ran. The
     * trial is a non-pass; the failing reason is carried for
     * diagnostics.
     */
    public static CriterionSampleResult failedTransform(
            String criterionId, Outcome.Fail<?> reason) {
        Objects.requireNonNull(reason, "reason");
        return new CriterionSampleResult(
                criterionId,
                CriterionSampleOutcome.FAIL,
                List.of(),
                Optional.of(reason));
    }
}
