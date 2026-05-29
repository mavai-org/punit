package org.mavai.punit.internal.engine;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;

import org.mavai.punit.api.spec.TerminationReason;

/**
 * Per-configuration tracker that enforces wall-clock and token
 * budgets declared on a spec.
 *
 * <p>Construction records the start instant. The engine consults
 * {@link #shouldStopBeforeNextSample()} before each sample and
 * {@link #recordSampleTokens(long)} after each sample. When the
 * tracker reports it is exhausted, the sample loop halts and the
 * engine attaches the appropriate {@link TerminationReason} to the
 * resulting summary.
 *
 * <p>Budgets are absent when the spec did not declare them; in that
 * case the tracker always reports "do not stop".
 */
final class BudgetTracker {

    private final Optional<Duration> timeBudget;
    private final OptionalLong tokenBudget;
    private final long tokenCharge;
    private final Instant start;
    private long tokenTotal;
    private TerminationReason termination = TerminationReason.COMPLETED;

    BudgetTracker(Optional<Duration> timeBudget, OptionalLong tokenBudget, long tokenCharge) {
        this.timeBudget = timeBudget;
        this.tokenBudget = tokenBudget;
        this.tokenCharge = tokenCharge;
        this.start = Instant.now();
    }

    /**
     * Returns true if the budget is exhausted and the next sample
     * must not start. When this returns true, the tracker records the
     * reason; subsequent calls keep returning true.
     *
     * <p>The static per-sample charge is used as the "next sample
     * will consume at least this many tokens" estimate.
     */
    boolean shouldStopBeforeNextSample() {
        if (termination != TerminationReason.COMPLETED) {
            return true;
        }
        if (timeBudget.isPresent()) {
            Duration elapsed = Duration.between(start, Instant.now());
            if (elapsed.compareTo(timeBudget.get()) >= 0) {
                termination = TerminationReason.TIME_BUDGET;
                return true;
            }
        }
        if (tokenBudget.isPresent()) {
            long projected = tokenTotal + tokenCharge;
            if (projected > tokenBudget.getAsLong()) {
                termination = TerminationReason.TOKEN_BUDGET;
                return true;
            }
        }
        return false;
    }

    /** Records the token cost of a completed sample plus the spec's static charge. */
    void recordSampleTokens(long sampleReportedTokens) {
        tokenTotal += tokenCharge + sampleReportedTokens;
    }

    /**
     * Records a statistical early-termination reason
     * ({@link TerminationReason#IMPOSSIBILITY} or
     * {@link TerminationReason#SUCCESS_GUARANTEED}). Called by the
     * Aggregator after a sample whose outcome makes the verdict
     * mathematically determined.
     *
     * <p>Has no effect if a termination reason has already been
     * recorded — budget-exhaustion reasons that fired earlier in the
     * same loop are not clobbered. Subsequent calls to
     * {@link #shouldStopBeforeNextSample()} will return true.
     */
    void recordEarlyTermination(TerminationReason reason) {
        if (termination == TerminationReason.COMPLETED) {
            termination = reason;
        }
    }

    /** How many tokens the configuration has consumed so far. */
    long tokenTotal() {
        return tokenTotal;
    }

    /** The per-sample static token charge. */
    long tokenCharge() {
        return tokenCharge;
    }

    /**
     * The final termination reason. Defaults to
     * {@link TerminationReason#COMPLETED} until one of the budget
     * checks fires.
     */
    TerminationReason terminationReason() {
        return termination;
    }
}
