package org.mavai.punit.api.spec;

/**
 * Engine-visible context for statistical early termination of a
 * probabilistic test's sample loop.
 *
 * <p>Specs that carry a contractual pass-rate threshold publish this
 * context through {@link TypedSpec#earlyTermination()}; the engine
 * then short-circuits the sample loop when the threshold becomes
 * mathematically unreachable (failure inevitable) or mathematically
 * guaranteed (success guaranteed). Specs that have no up-front
 * threshold — measure / explore / optimize runs, and empirical-mode
 * probabilistic tests — return {@link java.util.Optional#empty()}
 * from that accessor and run every declared sample.
 *
 * @param minPassRate           the contractual pass rate the spec must meet
 * @param minSamplesForValidity the floor below which a guaranteed-success
 *                              short-circuit must not fire — the run continues
 *                              until the normal approximation is meaningful.
 *                              Zero disables the floor (failure-inevitable
 *                              termination never needs it).
 * @param confidence            the confidence of the criterion's declared-
 *                              threshold Wilson comparison (companion
 *                              §3.2/§3.6). The engine derives the guaranteed-
 *                              success count from it: a success count is only
 *                              "locked in" when its Wilson lower bound at this
 *                              confidence already clears {@code minPassRate}.
 */
public record EarlyTerminationContext(
        double minPassRate,
        int minSamplesForValidity,
        double confidence) {

    public EarlyTerminationContext {
        if (Double.isNaN(minPassRate) || minPassRate < 0.0 || minPassRate > 1.0) {
            throw new IllegalArgumentException(
                    "minPassRate must be in [0, 1], got: " + minPassRate);
        }
        if (minSamplesForValidity < 0) {
            throw new IllegalArgumentException(
                    "minSamplesForValidity must be non-negative, got: " + minSamplesForValidity);
        }
        if (Double.isNaN(confidence) || confidence <= 0.0 || confidence >= 1.0) {
            throw new IllegalArgumentException(
                    "confidence must be in (0, 1), got: " + confidence);
        }
    }
}
