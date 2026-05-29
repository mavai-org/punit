package org.mavai.punit.api.criterion;

import java.util.List;

/**
 * The empty-criteria sentinel value. Returned by
 * {@link Criteria#empty()} and used as the default for
 * {@link org.mavai.punit.api.Contract#criteria()} when the contract
 * has not overridden the value-form authoring path. The framework's
 * lowering treats it as "no explicit criteria; fall back to the
 * postcondition-derived K=1 default."
 *
 * <p>A single instance backs every call to {@link Criteria#empty()};
 * authors do not see the type.
 */
final class EmptyCriteria<O> implements Criteria<O> {

    @SuppressWarnings("rawtypes")
    private static final EmptyCriteria INSTANCE = new EmptyCriteria<>();

    private EmptyCriteria() { }

    @SuppressWarnings("unchecked")
    static <O> EmptyCriteria<O> instance() {
        return (EmptyCriteria<O>) INSTANCE;
    }

    @Override
    public List<Criterion<O>> asList() {
        return List.of();
    }

    @Override
    public boolean isEmpty() {
        return true;
    }
}
