package org.mavai.punit.api.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

import org.mavai.punit.statistics.DecisionRule;

/**
 * The test's verdict and the two dimensions it composes (Statistical
 * Companion §1.4.6, §12.3.2).
 *
 * <p>The functional dimension {@code V_rate} is the structural composite
 * of the test's functional criteria; the latency dimension
 * {@code V_latency} is the same composite over the enforced latency
 * constraints. The test verdict {@code V_test} composes the dimensions
 * present by the same rule — PASS if every one passes, FAIL if any
 * fails, INCONCLUSIVE otherwise — so a FAIL in either dimension outweighs
 * an INCONCLUSIVE in the other. A FAIL or an INCONCLUSIVE names what
 * decided it.
 *
 * @param rateVerdict    {@code V_rate}; empty for a test with no functional criteria
 * @param latencyVerdict {@code V_latency}; empty for a test that enforces no latency constraint
 * @param testVerdict    {@code V_test}
 * @param triggering     for a FAIL or an INCONCLUSIVE, the criteria and latency
 *                       constraints whose verdict is the test's, criteria first
 * @param falseComplianceEnvelope        the union bound on one or more false
 *                       compliance claims: the sum of alpha over the compliance
 *                       decisions (requirements and explicit latency ceilings);
 *                       empty when the test makes none
 * @param falseDegradationSignalEnvelope the union bound on one or more false
 *                       degradation signals: the sum of alpha over the
 *                       regression decisions (baseline-derived criteria and
 *                       latency thresholds); empty when the test makes none
 */
public record VerdictComposition(
        Optional<Verdict> rateVerdict,
        Optional<Verdict> latencyVerdict,
        Verdict testVerdict,
        List<Trigger> triggering,
        OptionalDouble falseComplianceEnvelope,
        OptionalDouble falseDegradationSignalEnvelope) {

    public VerdictComposition {
        Objects.requireNonNull(rateVerdict, "rateVerdict");
        Objects.requireNonNull(latencyVerdict, "latencyVerdict");
        Objects.requireNonNull(testVerdict, "testVerdict");
        Objects.requireNonNull(falseComplianceEnvelope, "falseComplianceEnvelope");
        Objects.requireNonNull(falseDegradationSignalEnvelope, "falseDegradationSignalEnvelope");
        triggering = List.copyOf(triggering);
    }

    /**
     * One decision entering the composition: a criterion or a latency
     * constraint, with the rule that decided it and its alpha where one
     * did.
     */
    public record Decided(String id, Verdict verdict, Optional<DecisionRule> rule, OptionalDouble alpha) {
        public Decided {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(verdict, "verdict");
            Objects.requireNonNull(rule, "rule");
            Objects.requireNonNull(alpha, "alpha");
        }

        /** A decision no rule made (zero-failures, a gate that fired before any rule). */
        public Decided(String id, Verdict verdict) {
            this(id, verdict, Optional.empty(), OptionalDouble.empty());
        }
    }

    /** What decided a test's FAIL or INCONCLUSIVE. */
    public record Trigger(Kind kind, String id) {

        /** Whether the trigger is a functional criterion or a latency constraint. */
        public enum Kind { CRITERION, LATENCY }

        public Trigger {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(id, "id");
        }
    }

    /**
     * Composes {@code V_test} from the functional criteria's verdicts and
     * the enforced latency constraints' verdicts (advisory constraints
     * never enter).
     *
     * @throws IllegalArgumentException when there is neither a criterion
     *         nor an enforced latency constraint to compose
     */
    public static VerdictComposition compose(List<Decided> criteria, List<Decided> latency) {
        Objects.requireNonNull(criteria, "criteria");
        Objects.requireNonNull(latency, "latency");
        if (criteria.isEmpty() && latency.isEmpty()) {
            throw new IllegalArgumentException(
                    "a test verdict needs at least one criterion or enforced latency constraint");
        }
        Optional<Verdict> rate = criteria.isEmpty()
                ? Optional.empty() : Optional.of(Verdict.aggregate(verdicts(criteria)));
        Optional<Verdict> lat = latency.isEmpty()
                ? Optional.empty() : Optional.of(Verdict.aggregate(verdicts(latency)));
        List<Verdict> dimensions = new ArrayList<>(2);
        rate.ifPresent(dimensions::add);
        lat.ifPresent(dimensions::add);
        Verdict test = Verdict.aggregate(dimensions);
        List<Trigger> triggering = new ArrayList<>();
        if (test != Verdict.PASS) {
            for (Decided d : criteria) {
                if (d.verdict() == test) {
                    triggering.add(new Trigger(Trigger.Kind.CRITERION, d.id()));
                }
            }
            for (Decided d : latency) {
                if (d.verdict() == test) {
                    triggering.add(new Trigger(Trigger.Kind.LATENCY, d.id()));
                }
            }
        }
        return new VerdictComposition(rate, lat, test, triggering,
                envelope(DecisionRule.Direction.COMPLIANCE, criteria, latency),
                envelope(DecisionRule.Direction.REGRESSION, criteria, latency));
    }

    /**
     * The sum of alpha over the rule-decided decisions of one direction —
     * a union bound, valid under arbitrary dependence (§1.4.6, §12.3.1).
     */
    private static OptionalDouble envelope(
            DecisionRule.Direction direction, List<Decided> criteria, List<Decided> latency) {
        double sum = 0.0;
        boolean any = false;
        for (List<Decided> decisions : List.of(criteria, latency)) {
            for (Decided d : decisions) {
                if (d.rule().isPresent() && d.alpha().isPresent()
                        && d.rule().get().direction() == direction) {
                    sum += d.alpha().getAsDouble();
                    any = true;
                }
            }
        }
        return any ? OptionalDouble.of(sum) : OptionalDouble.empty();
    }

    private static List<Verdict> verdicts(List<Decided> decided) {
        return decided.stream().map(Decided::verdict).toList();
    }
}
