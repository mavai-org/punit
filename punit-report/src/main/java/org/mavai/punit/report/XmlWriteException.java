package org.mavai.punit.report;

/**
 * Wraps checked {@link javax.xml.stream.XMLStreamException} as an unchecked exception
 * for use within lambda contexts.
 */
public final class XmlWriteException extends RuntimeException {

    public XmlWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
