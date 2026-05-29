package org.mavai.punit.internal.criteria;

import java.util.ArrayList;
import java.util.List;

import org.mavai.punit.api.Postcondition;
import org.mavai.punit.api.PostconditionCheck;

/**
 * Internal accumulator for a postcondition chain. Framework-internal:
 * authors never construct one directly, and the type is not part of
 * the public authoring surface. The modern {@link
 * org.mavai.punit.api.criterion.Criteria} value-form authoring path
 * (decls with {@code .satisfies(...)}) is the only way to declare
 * acceptance criteria; this builder backs the lowering of those decls
 * into runtime criteria.
 *
 * @param <O> the value type the postcondition chain evaluates against
 */
public final class PostconditionBuilder<O> {

    private final List<Postcondition<O>> clauses = new ArrayList<>();

    /**
     * Add a leaf clause. The {@code check} returns
     * {@link org.mavai.outcome.Outcome.Ok} when the aspect holds, or
     * {@link org.mavai.outcome.Outcome.Fail} carrying a stable name
     * and a free-text reason when it does not. Both name and reason
     * are preserved on the resulting
     * {@link org.mavai.punit.api.PostconditionResult}.
     *
     * @param description human-readable description of what is checked
     * @param check       the predicate, returning an outcome
     * @return this builder
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if {@code description} is blank
     */
    public PostconditionBuilder<O> ensure(String description, PostconditionCheck<O> check) {
        clauses.add(new Postcondition.Leaf<>(description, check));
        return this;
    }

    /**
     * Returns the accumulated clauses as an immutable list.
     */
    public List<Postcondition<O>> build() {
        return List.copyOf(clauses);
    }
}
