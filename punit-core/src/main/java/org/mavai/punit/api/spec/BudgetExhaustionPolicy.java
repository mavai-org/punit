package org.mavai.punit.api.spec;

/**
 * What the engine does when a spec's time or token budget is exhausted
 * before the configured sample count has been reached.
 *
 * <p>The default is {@link #FAIL} — a statistical verdict requires
 * enough samples to support it, and silently proceeding on a partial
 * set would hide that the run never finished. Authors running under
 * tight cost constraints opt into {@link #PASS_INCOMPLETE} explicitly.
 */
public enum BudgetExhaustionPolicy {

    /**
     * Synthesise the final verdict from samples completed so far,
     * attach a warning, and return gracefully.
     */
    PASS_INCOMPLETE,

    /**
     * Fail the run — refuse to emit a statistical verdict. The engine
     * still produces a summary marked with the termination reason so
     * reporters can explain what happened.
     */
    FAIL
}
