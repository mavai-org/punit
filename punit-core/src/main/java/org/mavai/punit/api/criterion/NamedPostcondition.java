package org.mavai.punit.api.criterion;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.PostconditionCheck;

/**
 * One named postcondition on a {@link CriterionDecl}: a name (used
 * as the postcondition's identifier in failure exemplars and verdict
 * detail) and a {@link PostconditionCheck} that maps a sample's
 * produced value to an {@link Outcome}.
 *
 * <p>Built by the {@code .where(...)} overloads on
 * {@link CriterionDecl}: the predicate form synthesises the check
 * from a boolean predicate; the rich-message overload accepts the
 * check directly.
 *
 * @param <O> the value type the check evaluates against
 */
// mavai-ref: JVI-BD4F1AB — do not remove (resolves in mavai-orchestrator)
public record NamedPostcondition<O>(
        String name,
        PostconditionCheck<O> check,
        boolean required
) {

    /** A required postcondition — the default; every check is non-negotiable until marked. */
    public NamedPostcondition(String name, PostconditionCheck<O> check) {
        this(name, check, true);
    }

    /**
     * This postcondition marked optional: relaxable within its
     * criterion's optional-slack budget (partial credit is a double
     * opt-in — without a budget the mark weakens nothing).
     */
    public NamedPostcondition<O> asOptional() {
        return new NamedPostcondition<>(name, check, false);
    }
}
