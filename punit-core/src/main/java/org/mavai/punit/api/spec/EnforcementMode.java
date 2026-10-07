package org.mavai.punit.api.spec;

/**
 * Whether a dimension's verdict binds the test verdict (Statistical
 * Companion §12.6).
 *
 * <p>An {@link #ENFORCED} dimension enters the test verdict
 * {@code V_test}. An {@link #ADVISORY} dimension is decided by the same
 * rules on the same evidence and its verdict is reported beside
 * {@code V_test}, but it never enters it, adds nothing to the Type-I
 * envelopes, and never fails the test.
 */
public enum EnforcementMode {

    /** The dimension's verdict enters the test verdict: the default. */
    ENFORCED("enforced"),

    /** The dimension is decided and reported, but never fails the test. */
    ADVISORY("advisory");

    private final String label;

    EnforcementMode(String label) {
        this.label = label;
    }

    /** The label the verdict record spells. */
    public String label() {
        return label;
    }
}
