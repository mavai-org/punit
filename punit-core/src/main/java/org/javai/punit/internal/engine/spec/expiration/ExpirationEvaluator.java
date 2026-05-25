package org.javai.punit.internal.engine.spec.expiration;

import java.time.Instant;
import org.javai.punit.verdict.ExpirationPolicy;
import org.javai.punit.verdict.ExpirationStatus;
import org.javai.punit.internal.engine.spec.model.ExecutionSpecification;

/**
 * Evaluates expiration status for execution specifications.
 *
 * <p>Provides convenient methods to evaluate expiration at the current time
 * or at a specified instant.
 */
// javai-ref: JVI-09GQGN$ — do not remove (resolves in javai-orchestrator)
public final class ExpirationEvaluator {

    private ExpirationEvaluator() {
        // Utility class
    }

    /**
     * Evaluates the expiration status of a specification at the current time.
     *
     * @param spec the execution specification to evaluate
     * @return the expiration status, never null
     */
    public static ExpirationStatus evaluate(ExecutionSpecification spec) {
        return evaluateAt(spec, Instant.now());
    }

    /**
     * Evaluates the expiration status of a specification at the given time.
     *
     * @param spec the execution specification to evaluate
     * @param currentTime the time at which to evaluate
     * @return the expiration status, never null
     */
    public static ExpirationStatus evaluateAt(ExecutionSpecification spec, Instant currentTime) {
        if (spec == null) {
            return ExpirationStatus.noExpiration();
        }

        ExpirationPolicy policy = spec.getExpirationPolicy();
        if (policy == null || !policy.hasExpiration()) {
            return ExpirationStatus.noExpiration();
        }

        return policy.evaluateAt(currentTime);
    }

    /**
     * Returns true if the specification has an active expiration policy.
     *
     * @param spec the execution specification to check
     * @return true if the spec has an expiration policy
     */
    public static boolean hasExpiration(ExecutionSpecification spec) {
        return spec != null && spec.hasExpirationPolicy();
    }

    /**
     * Returns true if the specification has expired as of now.
     *
     * @param spec the execution specification to check
     * @return true if expired
     */
    public static boolean isExpired(ExecutionSpecification spec) {
        return evaluate(spec).isExpired();
    }

    /**
     * Returns true if the specification requires an expiration warning.
     *
     * @param spec the execution specification to check
     * @return true if a warning should be shown
     */
    public static boolean requiresWarning(ExecutionSpecification spec) {
        return evaluate(spec).requiresWarning();
    }
}

