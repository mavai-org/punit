package org.mavai.punit.api.criterion;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.Postcondition;

/**
 * A criterion that first transforms the contract's output {@code O}
 * to a derived form {@code D}, then evaluates its postcondition
 * chain against the derived value. Transform failure
 * ({@link Outcome.Fail} or a thrown exception) classifies the sample
 * as a {@link CriterionSampleOutcome#FAIL FAIL} carrying the failing
 * reason; the postcondition chain is not evaluated.
 *
 * <p>Package-private; constructed when a {@link TransformingDecl}
 * is lowered to its runtime criterion. Authors do not reference this
 * type directly.
 *
 * @param <O> the contract's per-sample output value type
 * @param <D> the type the postcondition chain evaluates against
 *            after the transform succeeds
 */
final class TransformingCriterion<O, D> implements Criterion<O> {

    private final String id;
    private final Function<O, Outcome<D>> transform;
    private final List<Postcondition<D>> postconditions;
    private final CriterionPosture posture;

    TransformingCriterion(
            String id,
            Function<O, Outcome<D>> transform,
            List<Postcondition<D>> postconditions) {
        this(id, transform, postconditions, CriterionPosture.implicit());
    }

    TransformingCriterion(
            String id,
            Function<O, Outcome<D>> transform,
            List<Postcondition<D>> postconditions,
            CriterionPosture posture) {
        this.id = Objects.requireNonNull(id, "id");
        this.transform = Objects.requireNonNull(transform, "transform");
        this.postconditions = List.copyOf(
                Objects.requireNonNull(postconditions, "postconditions"));
        this.posture = Objects.requireNonNull(posture, "posture");
    }

    TransformingCriterion<O, D> withPosture(CriterionPosture replacement) {
        return new TransformingCriterion<>(id, transform, postconditions, replacement);
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
        Outcome<D> derived;
        try {
            derived = transform.apply(value);
        } catch (RuntimeException e) {
            String message = e.getMessage() != null
                    ? e.getMessage()
                    : e.getClass().getSimpleName();
            // Outcome.fail returns Outcome<D>; the sealed hierarchy
            // guarantees it is an Outcome.Fail<D> instance.
            derived = Outcome.fail("transform-threw", message);
        }
        return switch (derived) {
            case Outcome.Ok<D> ok ->
                    DirectCriterion.evaluateChain(id, postconditions, ok.value(), Optional.empty());
            case Outcome.Fail<D> f ->
                    CriterionSampleResult.failedTransform(id, f);
        };
    }
}
