package org.mavai.punit.api.spec;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Bundled run-bounding values shared by every Stage-3 spec builder.
 *
 * <p>All four spec flavours (Measure, Explore, Optimize,
 * ProbabilisticTest) expose the same knobs for governing
 * <em>how</em> a run executes: how long it can take, how much it
 * can cost, how to react to budget exhaustion, how to react to a
 * thrown defect, and how much failure detail to retain. Rather than
 * duplicate the field declarations and validation across four spec
 * builders, each spec keeps a single {@code ResourceControls}
 * instance and delegates the corresponding
 * {@link Spec} interface accessors to its fields.
 *
 * <p>Evaluation-time thresholds (latency percentiles, functional
 * pass-rate) are <em>not</em> resource controls — they judge the
 * observed data rather than bound the run — and so live on the
 * concrete spec directly, not in this bundle.
 *
 * <p>This record is internal plumbing; authors see only the fluent
 * builder methods. Exposed as package-public so concrete specs can
 * store and consult it without a separate getter per field.
 *
 * @param timeBudget wall-clock budget; {@link Optional#empty()} = no limit
 * @param tokenBudget token budget; {@link OptionalLong#empty()} = no limit
 * @param tokenCharge static per-sample charge; default 0
 * @param budgetPolicy what to do on budget exhaustion; default FAIL
 * @param exceptionPolicy how to treat a thrown exception from
 *                       {@code ServiceContract.apply}; default ABORT_TEST
 * @param maxExampleFailures cap on retained failure detail; default 10
 */
// javai-ref: JVI-9M5SBQZ — do not remove (resolves in javai-orchestrator)
// javai-ref: JVI-N0WK25H — do not remove (resolves in javai-orchestrator)
// javai-ref: JVI-W6A4WRA — do not remove (resolves in javai-orchestrator)
// javai-ref: JVI-T60D7VU — do not remove (resolves in javai-orchestrator)
public record ResourceControls(
        Optional<Duration> timeBudget,
        OptionalLong tokenBudget,
        long tokenCharge,
        BudgetExhaustionPolicy budgetPolicy,
        ExceptionPolicy exceptionPolicy,
        int maxExampleFailures) {

    private static final ResourceControls DEFAULTS = new ResourceControls(
            Optional.empty(),
            OptionalLong.empty(),
            0L,
            BudgetExhaustionPolicy.FAIL,
            ExceptionPolicy.ABORT_TEST,
            10);

    public ResourceControls {
        Objects.requireNonNull(timeBudget, "timeBudget");
        Objects.requireNonNull(tokenBudget, "tokenBudget");
        Objects.requireNonNull(budgetPolicy, "budgetPolicy");
        Objects.requireNonNull(exceptionPolicy, "exceptionPolicy");
        if (timeBudget.isPresent() && (timeBudget.get().isZero() || timeBudget.get().isNegative())) {
            throw new IllegalArgumentException(
                    "timeBudget must be > 0, got " + timeBudget.get());
        }
        if (tokenBudget.isPresent() && tokenBudget.getAsLong() <= 0) {
            throw new IllegalArgumentException(
                    "tokenBudget must be > 0, got " + tokenBudget.getAsLong());
        }
        if (tokenCharge < 0) {
            throw new IllegalArgumentException(
                    "tokenCharge must be non-negative, got " + tokenCharge);
        }
        if (maxExampleFailures < 0) {
            throw new IllegalArgumentException(
                    "maxExampleFailures must be non-negative, got " + maxExampleFailures);
        }
    }

    /** The default-valued bundle — empty budgets, FAIL, ABORT_TEST, 10. */
    public static ResourceControls defaults() {
        return DEFAULTS;
    }
}
