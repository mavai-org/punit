package org.mavai.punit.api.criterion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.mavai.punit.api.Postcondition;
import org.mavai.punit.api.PostconditionResult;

/**
 * A criterion whose postconditions evaluate directly against the
 * contract's output. No transform — the per-sample outcome is
 * {@link CriterionSampleOutcome#PASS PASS} or
 * {@link CriterionSampleOutcome#FAIL FAIL}; with no transform there
 * is no transform-failure reason, so every FAIL here is a condition
 * failure carrying postcondition results.
 *
 * <p>Package-private; constructed when a {@link CriterionDecl} is
 * lowered to its runtime criterion. Authors do not reference this
 * type directly.
 */
final class DirectCriterion<O> implements Criterion<O> {

    private final String id;
    private final List<Postcondition<O>> postconditions;
    private final CriterionPosture posture;

    DirectCriterion(String id, List<Postcondition<O>> postconditions) {
        this(id, postconditions, CriterionPosture.implicit());
    }

    DirectCriterion(String id, List<Postcondition<O>> postconditions, CriterionPosture posture) {
        this.id = Objects.requireNonNull(id, "id");
        this.postconditions = List.copyOf(
                Objects.requireNonNull(postconditions, "postconditions"));
        this.posture = Objects.requireNonNull(posture, "posture");
    }

    DirectCriterion<O> withPosture(CriterionPosture replacement) {
        return new DirectCriterion<>(id, postconditions, replacement);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public CriterionPosture posture() {
        return posture;
    }

    @Override
    public CriterionSampleResult evaluate(O value) {
        return evaluateChain(id, postconditions, value, Optional.empty());
    }

    @Override
    public CriterionSampleResult evaluate(O value, Optional<O> expected) {
        return evaluateChain(id, postconditions, value, expected);
    }

    @Override
    public boolean requiresExpected() {
        for (Postcondition<O> p : postconditions) {
            if (p instanceof Postcondition.Matching<O>) {
                return true;
            }
        }
        return false;
    }

    /**
     * Evaluate a postcondition chain over a value. Used by both
     * {@link DirectCriterion} and {@link TransformingCriterion} once
     * the transform has produced a derived value.
     *
     * <p>The walk does not short-circuit on first failure: every
     * postcondition is evaluated and its result preserved on the
     * record, so a downstream consumer can see the full diagnostic
     * picture for the sample.
     *
     * <p>{@link Postcondition.Matching} variants in the chain route
     * through {@link Postcondition.Matching#match(Object, Object)}
     * with the supplied expected value. A matching postcondition
     * without an expected value (caller passed {@link Optional#empty()})
     * is an engine-side defect and surfaces as an
     * {@link IllegalStateException}.
     */
    static <T> CriterionSampleResult evaluateChain(
            String id, List<Postcondition<T>> chain, T value, Optional<T> expected) {
        List<PostconditionResult> results = new ArrayList<>();
        boolean anyFailed = false;
        for (Postcondition<T> p : chain) {
            List<PostconditionResult> pResults = switch (p) {
                case Postcondition.Leaf<T> leaf -> leaf.evaluateAll(value);
                case Postcondition.Matching<T> m -> List.of(m.match(
                        expected.orElseThrow(() -> new IllegalStateException(
                                "criterion '" + id + "' has matching postcondition '"
                                        + m.description() + "' but no expected value was "
                                        + "supplied; the sampling-construction guard "
                                        + "should have caught this — the sampling's input "
                                        + "type must implement org.mavai.punit.api.Expected.")),
                        value));
            };
            results.addAll(pResults);
            for (PostconditionResult r : pResults) {
                if (r.failed()) {
                    anyFailed = true;
                }
            }
        }
        return anyFailed
                ? CriterionSampleResult.fail(id, results)
                : CriterionSampleResult.pass(id, results);
    }
}
