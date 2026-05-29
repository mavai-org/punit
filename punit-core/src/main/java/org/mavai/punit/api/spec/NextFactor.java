package org.mavai.punit.api.spec;

import java.util.Objects;

/**
 * Result of a {@link FactorsStepper}'s decision for the next
 * optimize iteration: either {@link Continue continue with the
 * supplied next factors}, or {@link Stop stop the optimisation
 * loop}.
 *
 * <p>Replaces the older {@code null}-as-stop convention so that
 * "produce a candidate" and "give up" are distinguishable in the
 * type system rather than via an implicit sentinel.
 *
 * @param <FT> the factor record type
 */
public sealed interface NextFactor<FT> {

    /** The stepper proposes the supplied factors for the next iteration. */
    record Continue<FT>(FT factor) implements NextFactor<FT> {
        public Continue {
            Objects.requireNonNull(factor, "factor");
        }
    }

    /** The stepper has nothing more to propose; the optimiser stops. */
    record Stop<FT>() implements NextFactor<FT> {}

    /** Continue with the supplied factors. */
    static <FT> NextFactor<FT> next(FT factor) {
        return new Continue<>(factor);
    }

    /** Stop the optimisation loop. */
    static <FT> NextFactor<FT> stop() {
        return new Stop<>();
    }
}
