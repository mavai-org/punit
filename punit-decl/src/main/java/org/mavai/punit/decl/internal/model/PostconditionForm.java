package org.mavai.punit.decl.internal.model;

/**
 * The postcondition form vocabulary of the contract format — a real
 * criterion declares a compatible subset, since all its forms must hold
 * together (a conjunction).
 */
public enum PostconditionForm {
    EQUALS("equals"),
    ONE_OF("one-of"),
    CONTAINS("contains"),
    MATCHES("matches"),
    PARSES("parses"),
    SATISFIES("satisfies");

    private final String key;

    PostconditionForm(String key) {
        this.key = key;
    }

    /** The form's key as written in the contract file. */
    public String key() {
        return key;
    }
}
