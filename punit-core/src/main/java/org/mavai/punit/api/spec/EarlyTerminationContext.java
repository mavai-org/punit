package org.mavai.punit.api.spec;

import org.mavai.punit.api.TestIntent;

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
 * @param minPassRate the declared requirement the spec must demonstrate
 * @param confidence  the confidence of the criterion's exact binomial test
 *                    (Statistical Companion §3.6). The engine derives the
 *                    required success count from it — the smallest passing
 *                    count the intent's rule decides with at the planned
 *                    sample size — so a short-circuited run reaches
 *                    exactly the verdict the full run would.
 * @param successStopAllowed whether a guaranteed success may end the run
 *                    early. False when a required latency constraint is
 *                    decided after the run on the successful latencies it
 *                    produced: stopping at the pass-rate guarantee would
 *                    starve that decision of samples. A failure that has
 *                    become inevitable still ends the run, since the test
 *                    verdict is FAIL whatever the latency dimension decides.
 * @param intent      the test's intent, which selects the rule the
 *                    required success count is derived under:
 *                    {@code compliance/exact-binomial} under VERIFICATION,
 *                    {@code compliance/smoke-exact-binomial} under SMOKE
 *                    (Statistical Companion §5.7.3)
 */
public record EarlyTerminationContext(
        double minPassRate,
        double confidence,
        boolean successStopAllowed,
        TestIntent intent) {

    /** A context in which both kinds of early termination are allowed, under VERIFICATION. */
    public EarlyTerminationContext(double minPassRate, double confidence) {
        this(minPassRate, confidence, true);
    }

    /** A context under VERIFICATION. */
    public EarlyTerminationContext(double minPassRate, double confidence, boolean successStopAllowed) {
        this(minPassRate, confidence, successStopAllowed, TestIntent.VERIFICATION);
    }

    public EarlyTerminationContext {
        java.util.Objects.requireNonNull(intent, "intent");
        if (Double.isNaN(minPassRate) || minPassRate < 0.0 || minPassRate > 1.0) {
            throw new IllegalArgumentException(
                    "minPassRate must be in [0, 1], got: " + minPassRate);
        }
        if (Double.isNaN(confidence) || confidence <= 0.0 || confidence >= 1.0) {
            throw new IllegalArgumentException(
                    "confidence must be in (0, 1), got: " + confidence);
        }
    }
}
