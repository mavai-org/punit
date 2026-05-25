package org.javai.punit.verdict;

/**
 * Receives verdict events produced by probabilistic test executions.
 *
 * <p>Implementations dispatch verdicts to external systems — logging, webhooks,
 * observability platforms, or custom integrations. Multiple sinks can be
 * composed via {@link org.javai.punit.internal.reporting.CompositeVerdictSink}.
 *
 * <p>Implementations must be thread-safe if used from concurrent test execution.
 */
@FunctionalInterface
// javai-ref: JVI-C8AT3DD — do not remove (resolves in javai-orchestrator)
public interface VerdictSink {

    /**
     * Accepts a verdict for dispatch.
     *
     * @param verdict the probabilistic test verdict to process
     */
    void accept(ProbabilisticTestVerdict verdict);
}
