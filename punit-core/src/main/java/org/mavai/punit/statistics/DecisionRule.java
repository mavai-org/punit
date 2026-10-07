package org.mavai.punit.statistics;

import java.util.Objects;
import java.util.Optional;

/**
 * The versioned decision rules of the Statistical Companion's
 * methodology {@value Methodology#VERSION}. Every inferential verdict is
 * produced by exactly one of them, and the rule's identifier and version
 * travel with the verdict it decided.
 *
 * <ul>
 *   <li>{@link #REGRESSION_FISHER} — empirical regression: the one-sided
 *       Fisher exact test as an integer cutoff on the test's success
 *       count (companion §3.4).</li>
 *   <li>{@link #COMPLIANCE_EXACT_BINOMIAL} — normative compliance: the
 *       exact one-sided binomial test as the smallest passing count
 *       (companion §3.6).</li>
 *   <li>{@link #LATENCY_PRECEDENCE} — latency regression: the smallest
 *       baseline rank whose no-degradation breach probability is at most
 *       alpha (companion §12.4.2).</li>
 *   <li>{@link #LATENCY_COMPLIANCE_EXACT_BINOMIAL} — an explicit latency
 *       requirement: the exact binomial test on the count of latencies
 *       within the threshold (companion §12.3.4).</li>
 * </ul>
 */
public enum DecisionRule {

    REGRESSION_FISHER("regression/fisher", Direction.REGRESSION),
    COMPLIANCE_EXACT_BINOMIAL("compliance/exact-binomial", Direction.COMPLIANCE),
    LATENCY_PRECEDENCE("latency/precedence", Direction.REGRESSION),
    LATENCY_COMPLIANCE_EXACT_BINOMIAL("latency/compliance-exact-binomial", Direction.COMPLIANCE);

    /**
     * The error event a rule's alpha bounds, which decides the Type-I
     * envelope its decisions enter (companion §1.4.6, §12.3.1).
     */
    public enum Direction {
        /** A false claim of compliance. */
        COMPLIANCE,
        /** A false degradation signal. */
        REGRESSION
    }

    private final String id;
    private final Direction direction;

    DecisionRule(String id, Direction direction) {
        this.id = id;
        this.direction = direction;
    }

    /** The rule's identifier, as reports and fixtures spell it. */
    public String id() {
        return id;
    }

    /** The rule's version; every rule is at version 1 under methodology 1.6.0. */
    public int version() {
        return 1;
    }

    /** The envelope direction the rule's decisions enter. */
    public Direction direction() {
        return direction;
    }

    /** The rule with the given identifier, if there is one. */
    public static Optional<DecisionRule> fromId(String id) {
        Objects.requireNonNull(id, "id");
        for (DecisionRule rule : values()) {
            if (rule.id.equals(id)) {
                return Optional.of(rule);
            }
        }
        return Optional.empty();
    }
}
