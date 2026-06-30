package org.mavai.punit.report.optimize;

/**
 * The {@code convergence:} summary of an OPTIMIZE run — the framework's
 * own record of which iteration it chose and why the loop stopped.
 *
 * <p>{@link #bestIteration} is authoritative: it is the iteration the
 * optimisation selected per its objective. The report surfaces it as the
 * chosen winner; when several iterations tie its score, they are presented
 * as a too-close-to-call cluster with this iteration identified as the
 * framework's pick.
 *
 * @param totalIterations  number of iterations executed
 * @param bestIteration    index of the chosen winning iteration, or -1 if absent
 * @param bestScore        the winning score
 * @param terminationReason why the iteration loop stopped (e.g. {@code MAX_ITERATIONS})
 */
record Convergence(
        int totalIterations,
        int bestIteration,
        double bestScore,
        String terminationReason) {
}
