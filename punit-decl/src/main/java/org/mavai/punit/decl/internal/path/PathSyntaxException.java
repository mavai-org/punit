package org.mavai.punit.decl.internal.path;

/**
 * A selection expression that does not parse in its language — raised at
 * compile time, wrapped into the declarative layer's load-time refusal
 * by the caller (the engine itself is language-level, not
 * author-facing).
 */
public class PathSyntaxException extends RuntimeException {

    public PathSyntaxException(String message) {
        super(message);
    }
}
