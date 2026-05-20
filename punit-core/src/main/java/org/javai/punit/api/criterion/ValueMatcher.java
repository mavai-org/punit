package org.javai.punit.api.criterion;

import java.util.Objects;

import org.javai.outcome.Outcome;

/**
 * Equivalence between an expected and an actual value of the same
 * type — the unit of work behind a criterion's
 * {@link CriterionDecl#matchedBy(java.util.function.Supplier) .matchedBy(...)}
 * declaration.
 *
 * <p>The framework invokes {@link #match(Object, Object)} once per
 * sample, with the expected value sourced from the sample's
 * {@link org.javai.punit.api.Expected#expected()} implementation and
 * the actual value sourced from the contract's
 * {@link org.javai.punit.api.Contract#invoke}. Return
 * {@link Outcome#ok()} when the two values are equivalent under this
 * matcher's notion of equivalence; return
 * {@link Outcome#fail(String, String)} with a stable failure name and
 * a human-readable message otherwise. The failure name and message
 * are surfaced verbatim through the verdict's
 * {@code failuresByPostcondition} histogram — there is no framework
 * synthesis.
 *
 * <p>The signature is deliberately {@code (expected, actual)} only.
 * A matcher that needs to read the sample's input or other context
 * is no longer a value matcher; declare a postcondition via
 * {@link CriterionDecl#satisfies(String, java.util.function.Function)}
 * instead.
 *
 * <p>Per the framework's {@code Outcome}-vs-exception convention, a
 * {@code ValueMatcher} that throws is a defect. The framework treats
 * a thrown exception from {@code match} as an aborting condition,
 * not a sample failure.
 *
 * @param <OT> the value type compared
 */
@FunctionalInterface
public interface ValueMatcher<OT> {

    /**
     * Decide whether {@code actual} is equivalent to {@code expected}
     * under this matcher's notion of equivalence.
     *
     * @return {@link Outcome#ok()} on equivalence;
     *         {@link Outcome#fail(String, String)} carrying a stable
     *         failure name and a human-readable message otherwise
     */
    Outcome<Void> match(OT expected, OT actual);

    /**
     * Default value matcher backed by {@link Objects#equals(Object, Object)}.
     * Use as {@code .matchedBy(ValueMatcher::equality)} when the
     * comparison is plain object equality; the criterion-builder
     * convenience {@link CriterionDecl#matchedByEquality()} is the
     * shortest path to the same matcher.
     *
     * <p>On mismatch, fails as
     * {@code Outcome.fail("not-equal", "expected " + expected + " but got " + actual)}.
     */
    static <OT> ValueMatcher<OT> equality() {
        return (expected, actual) -> Objects.equals(expected, actual)
                ? Outcome.ok()
                : Outcome.fail("not-equal",
                        "expected " + expected + " but got " + actual);
    }
}
