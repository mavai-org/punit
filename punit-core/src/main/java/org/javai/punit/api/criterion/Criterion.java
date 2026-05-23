package org.javai.punit.api.criterion;

import java.util.Optional;

/**
 * A named, contract-level partition of the functional dimension. A
 * criterion is a unit that judges a single sample's produced value
 * and yields one of {PASS, FAIL, INCONCLUSIVE}; a
 * {@link org.javai.punit.api.Contract} may carry one criterion (the
 * common case today) or several, each evaluated independently.
 *
 * <h2>What lives at this interface</h2>
 *
 * <p>The public surface is deliberately minimal: an identifier and a
 * per-sample evaluation method. Anything else a downstream consumer
 * (verdict path, report, sentinel) needs about the criterion's
 * behaviour on a sample rides on the returned
 * {@link CriterionSampleResult} — per-postcondition pass/fail
 * details, the transform-failure reason when the trial is a
 * {@link CriterionSampleOutcome#FAIL} produced by a failed
 * transform, and any other diagnostic content the implementation
 * chooses to carry.
 *
 * <h2>Authoring</h2>
 *
 * <p>The idiomatic authoring path is the {@link Criteria} value-form
 * factory: {@link Criteria#meeting()} / {@link Criteria#empirical()}
 * yield a posture-bearing {@link Decl} whose
 * {@code .satisfies(name, predicate)} method attaches postconditions,
 * and {@link Decl#transforming(java.util.function.Function)} yields a
 * derived-typed decl for the transform-then-postconditions shape.
 * {@link Criteria#of(Decl[])} bundles multiple decls when a contract
 * partitions its acceptance across several criteria.
 *
 * <p>Direct implementation of this interface is supported but is
 * not the expected path; the factory entry points cover the
 * methodology's two shapes (transform / no-transform) and enforce
 * the one-transform-per-criterion cap structurally.
 *
 * @param <O> the contract's per-sample output value type
 */
public interface Criterion<O> {

    /**
     * A stable identifier for this criterion, used in reports, in the
     * verdict tuple, and wherever the criterion needs to be referenced
     * by name. Must be unique within the criteria of one contract and
     * must remain stable across runs of the same contract.
     *
     * <p>Conventionally a lowercase, hyphen-separated token. The
     * framework does not enforce a specific format.
     */
    String id();

    /**
     * Evaluate this criterion against one sample's produced value.
     *
     * <p>The returned {@link CriterionSampleResult} carries the
     * three-valued per-sample outcome, the per-postcondition results
     * for the chain that ran (empty on INCONCLUSIVE), and the
     * transform failure that caused INCONCLUSIVE (empty otherwise).
     *
     * <p>Whether this criterion carries an internal transform, and
     * over what derived type its postcondition chain evaluates, is
     * an implementation detail not visible at this interface. The
     * factory-produced implementations supply the per-sample
     * machinery: transform-fails-into-INCONCLUSIVE,
     * postcondition-fails-into-FAIL, all-passes-into-PASS.
     *
     * @param value the contract's produced output for this sample
     * @return the criterion's per-sample evaluation record
     */
    CriterionSampleResult evaluate(O value);

    /**
     * Evaluate this criterion against one sample's produced value
     * with an optional known-expected value of the same type
     * supplied alongside.
     *
     * <p>The expected value is present when the sampling's input
     * type implements {@link org.javai.punit.api.Expected} and the
     * criterion carries at least one reference-matching postcondition
     * (declared via {@code .matchedBy(...)} or
     * {@code .matchedByEquality()}). Criteria with no matching
     * postcondition ignore the expected value.
     *
     * <p>The default implementation delegates to {@link #evaluate(Object)}
     * and ignores the expected value — appropriate for hand-rolled
     * implementations that do not use the matching postcondition
     * shape. The factory-produced implementations override to route
     * the expected value into {@link org.javai.punit.api.Postcondition.Matching}
     * variants in their chain.
     *
     * @param value    the contract's produced output for this sample
     * @param expected the sample's known-correct output, present iff
     *                 the input implements {@link org.javai.punit.api.Expected}
     * @return the criterion's per-sample evaluation record
     */
    default CriterionSampleResult evaluate(O value, Optional<O> expected) {
        return evaluate(value);
    }

    /**
     * Whether this criterion needs the sample's known-expected value
     * to evaluate. Returns {@code true} when the criterion carries at
     * least one {@link org.javai.punit.api.Postcondition.Matching}
     * postcondition; {@code false} otherwise.
     *
     * <p>The framework's sample-dispatch path uses this to fail fast,
     * at the first sample, when criteria declare {@code .matchedBy(...)}
     * but the sampling's input type does not implement
     * {@link org.javai.punit.api.Expected}. Hand-rolled
     * {@link Criterion} implementations that do not use the matching
     * postcondition shape leave this at its default of {@code false}.
     */
    default boolean requiresExpected() {
        return false;
    }

    /**
     * The criterion's run-time commitment — what counts as acceptable,
     * and optionally how confidently to evaluate it. Authored via the
     * {@link Criteria#meeting()} / {@link Criteria#empirical()} factories
     * with the kind selectors ({@code .passRate}, {@code .zeroFailures},
     * {@code .atMost}); read by the framework's evaluation path when it
     * computes the per-criterion verdict from the run's sample counts.
     *
     * <p>The default is {@link CriterionPosture#implicit()} — used by
     * criteria constructed without an explicit posture (hand-rolled
     * {@link Criterion} implementations and the auto-derived K=1
     * default).
     */
    default CriterionPosture posture() {
        return CriterionPosture.implicit();
    }
}
