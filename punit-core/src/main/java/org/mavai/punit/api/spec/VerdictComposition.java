package org.mavai.punit.api.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

import org.mavai.punit.statistics.DecisionRule;

/**
 * The test's verdict and the two dimensions it composes (Statistical
 * Companion §1.4.6, §12.3.2, §12.6).
 *
 * <p>The functional dimension {@code V_rate} is the structural composite
 * of the test's functional criteria; the latency dimension
 * {@code V_latency} is the same composite over its latency constraints,
 * each decided by its rule. Each dimension is enforced or advisory as
 * the run's {@link AssertionEnforcement} says. The test verdict
 * {@code V_test} composes the enforced dimensions only, by the same rule
 * — PASS if every one passes, FAIL if any fails, INCONCLUSIVE otherwise
 * — so a FAIL in either outweighs an INCONCLUSIVE in the other; with no
 * enforced dimension it is PASS, the composite over no verdict. An
 * advisory dimension is decided and reported with its verdict, but it
 * never enters {@code V_test}, never triggers it and adds nothing to the
 * envelopes. A FAIL or an INCONCLUSIVE names what decided it.
 *
 * @param rateVerdict    {@code V_rate}; empty for a test with no functional criteria
 * @param latencyVerdict {@code V_latency}; empty for a test with no latency constraint
 * @param functionalMode the functional dimension's mode; present exactly
 *                       when {@code rateVerdict} is
 * @param latencyMode    the latency dimension's mode; present exactly
 *                       when {@code latencyVerdict} is
 * @param testVerdict    {@code V_test}, over the enforced dimensions
 * @param triggering     for a FAIL or an INCONCLUSIVE, the enforced criteria
 *                       and latency constraints whose verdict is the
 *                       test's, criteria first
 * @param falseComplianceEnvelope        the union bound on one or more false
 *                       compliance claims: the sum of alpha over the enforced
 *                       compliance decisions (requirements and explicit
 *                       latency ceilings); empty when the test makes none
 * @param falseDegradationSignalEnvelope the union bound on one or more false
 *                       degradation signals: the sum of alpha over the
 *                       enforced regression decisions (baseline-derived
 *                       criteria and latency thresholds); empty when the
 *                       test makes none
 */
public record VerdictComposition(
        Optional<Verdict> rateVerdict,
        Optional<Verdict> latencyVerdict,
        Optional<EnforcementMode> functionalMode,
        Optional<EnforcementMode> latencyMode,
        Verdict testVerdict,
        List<Trigger> triggering,
        OptionalDouble falseComplianceEnvelope,
        OptionalDouble falseDegradationSignalEnvelope) {

    public VerdictComposition {
        Objects.requireNonNull(rateVerdict, "rateVerdict");
        Objects.requireNonNull(latencyVerdict, "latencyVerdict");
        Objects.requireNonNull(functionalMode, "functionalMode");
        Objects.requireNonNull(latencyMode, "latencyMode");
        if (rateVerdict.isPresent() != functionalMode.isPresent()
                || latencyVerdict.isPresent() != latencyMode.isPresent()) {
            throw new IllegalArgumentException("a dimension has a mode exactly when it has a verdict");
        }
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
     * the latency constraints' verdicts, over the dimensions
     * {@code enforcement} leaves enforced.
     *
     * @throws IllegalArgumentException when there is neither a criterion
     *         nor a latency constraint to compose
     */
    public static VerdictComposition compose(
            List<Decided> criteria, List<Decided> latency, AssertionEnforcement enforcement) {
        Objects.requireNonNull(criteria, "criteria");
        Objects.requireNonNull(latency, "latency");
        Objects.requireNonNull(enforcement, "enforcement");
        if (criteria.isEmpty() && latency.isEmpty()) {
            throw new IllegalArgumentException(
                    "a test verdict needs at least one criterion or latency constraint");
        }
        Optional<Verdict> rate = composite(criteria);
        Optional<Verdict> lat = composite(latency);
        Optional<EnforcementMode> functionalMode =
                rate.map(v -> enforcement.mode(AssertionEnforcement.Dimension.FUNCTIONAL));
        Optional<EnforcementMode> latencyMode =
                lat.map(v -> enforcement.mode(AssertionEnforcement.Dimension.LATENCY));
        List<Decided> bindingCriteria = binding(criteria, functionalMode);
        List<Decided> bindingLatency = binding(latency, latencyMode);
        List<Verdict> dimensions = new ArrayList<>(2);
        composite(bindingCriteria).ifPresent(dimensions::add);
        composite(bindingLatency).ifPresent(dimensions::add);
        Verdict test = Verdict.aggregate(dimensions);
        List<Trigger> triggering = new ArrayList<>();
        if (test != Verdict.PASS) {
            for (Decided d : bindingCriteria) {
                if (d.verdict() == test) {
                    triggering.add(new Trigger(Trigger.Kind.CRITERION, d.id()));
                }
            }
            for (Decided d : bindingLatency) {
                if (d.verdict() == test) {
                    triggering.add(new Trigger(Trigger.Kind.LATENCY, d.id()));
                }
            }
        }
        return new VerdictComposition(rate, lat, functionalMode, latencyMode, test, triggering,
                envelope(DecisionRule.Direction.COMPLIANCE, bindingCriteria, bindingLatency),
                envelope(DecisionRule.Direction.REGRESSION, bindingCriteria, bindingLatency));
    }

    /** Whether the functional dimension is present and enforced. */
    public boolean functionalEnforced() {
        return functionalMode.filter(m -> m == EnforcementMode.ENFORCED).isPresent();
    }

    /** Whether the latency dimension is present and enforced. */
    public boolean latencyEnforced() {
        return latencyMode.filter(m -> m == EnforcementMode.ENFORCED).isPresent();
    }

    /** The decisions of a dimension that binds; none of an advisory one. */
    private static List<Decided> binding(List<Decided> decisions, Optional<EnforcementMode> mode) {
        return mode.filter(m -> m == EnforcementMode.ENFORCED).isPresent() ? decisions : List.of();
    }

    private static Optional<Verdict> composite(List<Decided> decisions) {
        return decisions.isEmpty()
                ? Optional.empty()
                : Optional.of(Verdict.aggregate(verdicts(decisions)));
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
