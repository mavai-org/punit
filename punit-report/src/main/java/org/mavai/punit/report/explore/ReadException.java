package org.mavai.punit.report.explore;

/**
 * Signals an I/O failure while reading exploration YAML or writing the
 * comparison report. Thrown for genuine defects (unreadable files,
 * unwritable output) — an absent or empty explorations directory is not
 * a defect and does not raise this.
 */
class ReadException extends RuntimeException {

    ReadException(String message, Throwable cause) {
        super(message, cause);
    }
}
