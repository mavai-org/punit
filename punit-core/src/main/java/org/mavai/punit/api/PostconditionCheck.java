package org.mavai.punit.api;

import org.mavai.outcome.Outcome;

/**
 * The check function carried by a {@link Postcondition}. Inspects a
 * value of type {@code T} and returns an {@link Outcome.Ok} if the
 * postcondition holds or an {@link Outcome.Fail} carrying the
 * specific reason if it does not.
 *
 * <p>Authors who don't need a bespoke failure reason can construct
 * postconditions from a plain {@link java.util.function.Predicate}
 * via {@link Postcondition#of(String, java.util.function.Predicate)};
 * the synthesised reason is just the postcondition's description.
 */
@FunctionalInterface
public interface PostconditionCheck<T> {

    Outcome<Void> check(T value);
}
