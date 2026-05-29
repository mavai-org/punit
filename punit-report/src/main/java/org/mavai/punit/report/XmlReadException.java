package org.mavai.punit.report;

/**
 * Unchecked exception thrown when reading verdict XML fails.
 */
public final class XmlReadException extends RuntimeException {

    public XmlReadException(String message, Throwable cause) {
        super(message, cause);
    }
}
