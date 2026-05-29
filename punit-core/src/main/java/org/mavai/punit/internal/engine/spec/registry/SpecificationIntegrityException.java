package org.mavai.punit.internal.engine.spec.registry;

/**
 * Exception thrown when a specification file fails integrity validation.
 * 
 * <p>This occurs when:
 * <ul>
 *   <li>The schema version is missing or unsupported</li>
 *   <li>The content fingerprint is missing</li>
 *   <li>The content fingerprint doesn't match the file content (indicating tampering)</li>
 * </ul>
 * 
 * <p>This exception is intended to cause test assumption failures, indicating
 * that the test environment is not properly configured rather than a test failure.
 */
public class SpecificationIntegrityException extends RuntimeException {

    /**
     * Creates a new integrity exception with the given message.
     *
     * @param message the detail message
     */
    public SpecificationIntegrityException(String message) {
        super(message);
    }

    /**
     * Creates a new integrity exception with the given message and cause.
     *
     * @param message the detail message
     * @param cause the underlying cause
     */
    public SpecificationIntegrityException(String message, Throwable cause) {
        super(message, cause);
    }
}

