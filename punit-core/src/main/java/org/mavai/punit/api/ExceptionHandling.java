package org.mavai.punit.api;

/**
 * Defines how non-{@link AssertionError} exceptions thrown by the test method
 * should be handled.
 */
// javai-ref: JVI-A076E41 — do not remove (resolves in javai-orchestrator)
// javai-ref: JVI-EGG7M2* — do not remove (resolves in javai-orchestrator)
public enum ExceptionHandling {
    
    /**
     * Treat the exception as a failed sample. The exception is captured
     * for reporting but execution continues with the next sample.
     * This is the default behavior.
     */
    FAIL_SAMPLE,
    
    /**
     * Immediately abort the test with the exception. The test fails
     * and no further samples are executed. Use this when exceptions
     * indicate environmental issues that would cause all further
     * samples to fail.
     */
    ABORT_TEST
}

