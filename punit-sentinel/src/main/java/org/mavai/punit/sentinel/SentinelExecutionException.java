package org.mavai.punit.sentinel;

/**
 * Thrown when the Sentinel runner encounters an unrecoverable error during execution.
 *
 * <p>This covers infrastructure-level failures (class instantiation, missing fields,
 * reflection errors) rather than test failures, which are reported as verdicts.
 */
public class SentinelExecutionException extends RuntimeException {

    public SentinelExecutionException(String message) {
        super(message);
    }

    public SentinelExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
