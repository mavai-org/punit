package org.mavai.punit.decl.model;

/**
 * The postcondition form vocabulary of the contract format — a real
 * criterion declares a compatible subset, since all its forms must hold
 * together (a conjunction).
 */
public enum Form {
    EQUALS("equals"),
    ONE_OF("one-of"),
    CONTAINS("contains"),
    MATCHES("matches"),
    PARSES("parses"),
    SATISFIES("satisfies");

    private final String key;

    Form(String key) {
        this.key = key;
    }

    /** The form's key as written in the contract file. */
    public String key() {
        return key;
    }
}
