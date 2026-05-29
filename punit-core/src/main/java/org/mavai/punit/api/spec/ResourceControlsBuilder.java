package org.mavai.punit.api.spec;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Mutable accumulator used by each spec builder to collect the
 * Stage-3 run-bounding knobs before sealing them into an immutable
 * {@link ResourceControls} at {@code build()} time.
 *
 * <p>Not exposed to authors — spec builders delegate to this internally
 * and surface the fluent methods themselves. Keeping the accumulator
 * here removes duplication across the four spec-builder classes.
 */
final class ResourceControlsBuilder {

    private Optional<Duration> timeBudget = Optional.empty();
    private OptionalLong tokenBudget = OptionalLong.empty();
    private long tokenCharge = 0L;
    private BudgetExhaustionPolicy budgetPolicy = BudgetExhaustionPolicy.FAIL;
    private ExceptionPolicy exceptionPolicy = ExceptionPolicy.ABORT_TEST;
    private int maxExampleFailures = 10;

    ResourceControlsBuilder() {}

    void timeBudget(Duration d) {
        Objects.requireNonNull(d, "timeBudget");
        if (d.isZero() || d.isNegative()) {
            throw new IllegalArgumentException(
                    "timeBudget must be > 0, got " + d);
        }
        this.timeBudget = Optional.of(d);
    }

    void tokenBudget(long tokens) {
        if (tokens <= 0) {
            throw new IllegalArgumentException(
                    "tokenBudget must be > 0, got " + tokens);
        }
        this.tokenBudget = OptionalLong.of(tokens);
    }

    void tokenCharge(long tokens) {
        if (tokens < 0) {
            throw new IllegalArgumentException(
                    "tokenCharge must be non-negative, got " + tokens);
        }
        this.tokenCharge = tokens;
    }

    void onBudgetExhausted(BudgetExhaustionPolicy policy) {
        this.budgetPolicy = Objects.requireNonNull(policy, "policy");
    }

    void onException(ExceptionPolicy policy) {
        this.exceptionPolicy = Objects.requireNonNull(policy, "policy");
    }

    void maxExampleFailures(int cap) {
        if (cap < 0) {
            throw new IllegalArgumentException(
                    "maxExampleFailures must be non-negative, got " + cap);
        }
        this.maxExampleFailures = cap;
    }

    ResourceControls build() {
        return new ResourceControls(
                timeBudget,
                tokenBudget,
                tokenCharge,
                budgetPolicy,
                exceptionPolicy,
                maxExampleFailures);
    }
}
