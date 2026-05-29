package org.mavai.punit.api;

import java.util.List;

/**
 * Evaluates the postconditions of a service contract against a use
 * case's raw result. Exposed as a first-class collaborator on
 * {@link ServiceContractOutcome} so the outcome can be interrogated lazily —
 * the engine counts pass/fail through one evaluation per sample; a
 * reporter or diagnostic consumer can later ask the same outcome for
 * the full enumeration without re-running the service.
 *
 * <p>Implementations are pure with respect to the passed-in result:
 * the same result handed to the same evaluator twice must produce
 * equal {@link PostconditionResult} lists. The engine relies on this
 * to make per-sample evaluation idempotent.
 *
 * @param <R> the raw result type produced by the service contract
 */
public interface PostconditionEvaluator<R> {

    /** Evaluate every postcondition against {@code result}. */
    List<PostconditionResult> evaluate(R result);

    /** The number of postconditions this evaluator would report on. */
    int postconditionCount();

    /**
     * The trivial evaluator — declares no postconditions and returns an
     * empty list for every result. Used by {@link ServiceContractOutcome#ok(Object)}
     * when the author just wants to hand back a value without a
     * contract.
     */
    static <R> PostconditionEvaluator<R> trivial() {
        return new PostconditionEvaluator<>() {
            @Override public List<PostconditionResult> evaluate(R result) {
                return List.of();
            }
            @Override public int postconditionCount() {
                return 0;
            }
        };
    }

    /**
     * An evaluator that declares a single always-failing postcondition
     * with the given failure code name and message. Used by
     * {@link ServiceContractOutcome#fail(String, String)} so a contract-free
     * failure still reports via the standard postcondition channel.
     */
    static <R> PostconditionEvaluator<R> alwaysFailing(String name, String message) {
        PostconditionResult single = PostconditionResult.failed(name, message);
        List<PostconditionResult> singleton = List.of(single);
        return new PostconditionEvaluator<>() {
            @Override public List<PostconditionResult> evaluate(R result) {
                return singleton;
            }
            @Override public int postconditionCount() {
                return 1;
            }
        };
    }
}
