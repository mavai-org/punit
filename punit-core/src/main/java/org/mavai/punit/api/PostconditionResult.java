package org.mavai.punit.api;

import java.util.Objects;
import java.util.Optional;

import org.mavai.outcome.Outcome;

/**
 * The result of evaluating one postcondition against a service contract's raw
 * result.
 *
 * @param description human-readable description of what was checked
 * @param outcome the check's outcome — {@link Outcome.Ok} if the
 *                postcondition held, {@link Outcome.Fail} otherwise
 *                (the failure carries the reason)
 */
public record PostconditionResult(String description, Outcome<?> outcome, boolean required) {

    public PostconditionResult {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(outcome, "outcome");
    }

    /** A required check's result — the default. */
    public PostconditionResult(String description, Outcome<?> outcome) {
        this(description, outcome, true);
    }

    public boolean passed() {
        return outcome.isOk();
    }

    public boolean failed() {
        return outcome.isFail();
    }

    /**
     * @return the failure reason, or empty if the postcondition passed
     */
    public Optional<String> failureReason() {
        return switch (outcome) {
            case Outcome.Fail<?> f -> Optional.of(f.failure().message());
            case Outcome.Ok<?> ignored -> Optional.empty();
        };
    }

    /**
     * The failure's symbolic name. For a failed result this is the
     * {@code name} field of the {@link org.mavai.outcome.Outcome.Fail
     * Outcome.Fail} that produced the result — typically a stable
     * identifier the author supplied when constructing
     * {@code Outcome.fail(name, message)} (e.g. {@code "unknown-action"},
     * {@code "empty-actions"}).
     *
     * <p>Aggregators can bucket on the name to distinguish multiple
     * failure modes within a single clause description: one clause
     * called {@code "All actions known"} may emit several distinct
     * named failures across a run.
     *
     * @return the failure name, or empty if the postcondition passed
     */
    public Optional<String> failureName() {
        return switch (outcome) {
            case Outcome.Fail<?> f -> Optional.of(f.failure().id().name());
            case Outcome.Ok<?> ignored -> Optional.empty();
        };
    }

    /**
     * @return {@code "{description}: {reason}"} when failed,
     *         just {@code description} when passed
     */
    public String failureMessage() {
        return failureReason()
                .map(reason -> description + ": " + reason)
                .orElse(description);
    }

    public static PostconditionResult passed(String description) {
        return new PostconditionResult(description, Outcome.ok());
    }

    /** A passing result carrying the check's required/optional standing. */
    public static PostconditionResult passed(String description, boolean required) {
        return new PostconditionResult(description, Outcome.ok(), required);
    }

    /**
     * Construct a failed result with a synthetic failure (name is the
     * description). Used for results that don't originate from an
     * author-supplied {@link Outcome.Fail} — for example, derivation
     * exceptions caught by the framework, or skipped-children entries
     * where the parent derivation failed.
     */
    public static PostconditionResult failed(String description, String reason) {
        Objects.requireNonNull(reason, "reason");
        return new PostconditionResult(description, Outcome.fail(description, reason));
    }

    /** A failed result carrying the check's required/optional standing. */
    public static PostconditionResult failed(String description, String reason, boolean required) {
        Objects.requireNonNull(reason, "reason");
        return new PostconditionResult(description, Outcome.fail(description, reason), required);
    }

    /**
     * Construct a failed result that preserves an author-supplied
     * {@link Outcome.Fail}. Both the failure's name and its message
     * survive into {@link #failureName()} and {@link #failureReason()}
     * unchanged.
     */
    public static PostconditionResult failed(String description, Outcome.Fail<?> failure) {
        Objects.requireNonNull(failure, "failure");
        return new PostconditionResult(description, failure);
    }

    /** A failed result preserving the author's failure and the check's standing. */
    public static PostconditionResult failed(
            String description, Outcome.Fail<?> failure, boolean required) {
        Objects.requireNonNull(failure, "failure");
        return new PostconditionResult(description, failure, required);
    }
}
