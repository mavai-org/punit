package org.mavai.punit.verdict;

/**
 * How token cost was tracked during a probabilistic-test run.
 *
 * <p>Carried on the verdict's cost summary so a reader can tell,
 * without external context, how the token count in the verdict was
 * arrived at.
 *
 * <p>The mode is a property of the verdict's provenance, not of the
 * engine machinery that produced it — the engine's internal budget
 * monitor reads this value, but the value itself is a verdict-side
 * label.
 */
public enum TokenMode {
    /** Token tracking was disabled. */
    NONE,
    /** A fixed token charge per sample was applied. */
    STATIC,
    /** Tokens were recorded dynamically via the token-charge recorder. */
    DYNAMIC
}
