package org.mavai.punit.api;

/**
 * Marks a per-sample input type that carries a known expected output.
 *
 * <p>Implementing {@code Expected<OT>} on a sample type unlocks the
 * reference-matching criterion shape on the contract's criteria:
 * {@link org.mavai.punit.api.criterion.CriterionDecl#matchedBy(
 * java.util.function.Supplier)} and
 * {@link org.mavai.punit.api.criterion.CriterionDecl#matchedByEquality()}
 * route the value returned by {@link #expected()} to a
 * {@link org.mavai.punit.api.criterion.ValueMatcher} alongside the
 * actual value produced by {@link Contract#invoke}.
 *
 * <p>The expected value's type is the contract's output type
 * {@code OT}. A sample whose ground truth has a different shape
 * should narrow its contract's {@code OT} to match, rather than
 * smuggling a projection through this interface.
 *
 * <p>A sample type either carries ground truth (implements this
 * interface) or it does not; this interface does not return an
 * {@link java.util.Optional}. Sampling runs that mix expected-bearing
 * and expected-less samples should split into two sample types
 * rather than loosening the contract.
 *
 * @param <OT> the expected output value type — must match the
 *             contract's per-sample output type
 */
public interface Expected<OT> {

    /**
     * The known-correct output for this input. Routed to a
     * {@link org.mavai.punit.api.criterion.ValueMatcher} when the
     * criterion declares {@code .matchedBy(...)} or
     * {@code .matchedByEquality()}.
     */
    OT expected();
}
