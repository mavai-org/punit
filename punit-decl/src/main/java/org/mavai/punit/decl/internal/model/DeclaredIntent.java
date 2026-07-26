package org.mavai.punit.decl.internal.model;

/**
 * The declared test intent: {@code verification} (the default — the run
 * must be able to support its bars) or {@code smoke} (an informal check
 * that renders no statistical verdict at the derived minimum).
 */
public enum DeclaredIntent {
    VERIFICATION("verification"),
    SMOKE("smoke");

    private final String key;

    DeclaredIntent(String key) {
        this.key = key;
    }

    /** The intent as written in the contract file. */
    public String key() {
        return key;
    }

    /** The intent for a file value, or {@code null} when unknown. */
    public static DeclaredIntent forKey(String key) {
        for (DeclaredIntent intent : values()) {
            if (intent.key.equals(key)) {
                return intent;
            }
        }
        return null;
    }
}
