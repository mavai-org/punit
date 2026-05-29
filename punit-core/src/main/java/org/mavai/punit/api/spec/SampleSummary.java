package org.mavai.punit.api.spec;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.mavai.punit.api.LatencyResult;
import org.mavai.punit.api.ServiceContractOutcome;

/**
 * Per-configuration aggregate of observed samples. Produced by the
 * engine, consumed by the spec via {@link Spec#consume(Configuration, SampleSummary)}.
 *
 * <p>{@link #outcomes()} holds the (optionally capped) list of
 * per-sample outcomes; the success / failure counts and token totals
 * are pre-computed at construction so downstream consumers do not
 * re-run lazy postcondition evaluation.
 *
 * <p>{@link #failuresDropped()} records how many failing outcomes
 * were elided from {@code outcomes()} due to a spec's
 * {@code maxExampleFailures} cap — the counts in {@link #failures()}
 * remain accurate.
 *
 * <p>{@link #latencyResult()} is always computed; a spec's latency
 * threshold is enforced separately by the engine.
 *
 * <p>{@link #trials()} exposes the full ordered per-trial history —
 * every trial that contributed to the aggregate counts, including
 * the ones whose detail was elided from {@code outcomes()} by the
 * failure cap. History-sensitive criteria (collision rate, Shewhart
 * runs-rules, autocorrelation, sequence-distribution analysis) read
 * this stream; aggregate-only criteria (Bernoulli pass-rate) ignore
 * it.
 *
 * @param outcomes the per-sample outcomes, possibly with failure
 *                 details elided per {@link #failuresDropped()}
 * @param elapsed wall-clock time spent sampling this configuration
 * @param successes pre-computed count of outcomes whose value is Ok
 * @param failures pre-computed count of outcomes whose value is Fail
 * @param tokensConsumed pre-computed sum of per-sample tokens plus the
 *                      spec's static charge for each counted sample
 * @param failuresDropped count of failing outcomes whose detail was
 *                       dropped because the example-failure cap was
 *                       reached; zero when no cap fired
 * @param latencyResult computed p50/p90/p95/p99 for this configuration
 * @param terminationReason why the sample loop stopped
 * @param trials the full ordered per-trial history (input, outcome,
 *               duration) — same length as {@link #total()}
 * @param <OT> the outcome value type
 */
public record SampleSummary<OT>(
        List<ServiceContractOutcome<?, OT>> outcomes,
        Duration elapsed,
        int successes,
        int failures,
        long tokensConsumed,
        int failuresDropped,
        LatencyResult latencyResult,
        TerminationReason terminationReason,
        List<Trial<?, OT>> trials,
        Map<String, FailureCount> failuresByPostcondition,
        LatencyResult passingLatencyResult,
        List<CriterionSampleCounts> criterionSampleCounts) {

    public SampleSummary {
        Objects.requireNonNull(outcomes, "outcomes");
        Objects.requireNonNull(elapsed, "elapsed");
        Objects.requireNonNull(latencyResult, "latencyResult");
        Objects.requireNonNull(terminationReason, "terminationReason");
        Objects.requireNonNull(trials, "trials");
        Objects.requireNonNull(failuresByPostcondition, "failuresByPostcondition");
        Objects.requireNonNull(passingLatencyResult, "passingLatencyResult");
        Objects.requireNonNull(criterionSampleCounts, "criterionSampleCounts");
        if (successes < 0 || failures < 0) {
            throw new IllegalArgumentException("counts must be non-negative");
        }
        if (tokensConsumed < 0) {
            throw new IllegalArgumentException("tokensConsumed must be non-negative");
        }
        if (failuresDropped < 0) {
            throw new IllegalArgumentException("failuresDropped must be non-negative");
        }
        int expectedOutcomes = successes + failures - failuresDropped;
        if (expectedOutcomes != outcomes.size()) {
            throw new IllegalArgumentException(
                    "successes + failures - failuresDropped (" + successes + " + " + failures
                            + " - " + failuresDropped + ") must equal outcomes count ("
                            + outcomes.size() + ")");
        }
        int total = successes + failures;
        if (!trials.isEmpty() && trials.size() != total) {
            throw new IllegalArgumentException(
                    "trials count (" + trials.size() + ") must equal successes + failures ("
                            + total + ") when trials is non-empty");
        }
        outcomes = List.copyOf(outcomes);
        trials = List.copyOf(trials);
        failuresByPostcondition = Map.copyOf(failuresByPostcondition);
        criterionSampleCounts = List.copyOf(criterionSampleCounts);
    }

    /**
     * Tally successes, failures and tokens from an uncapped outcome
     * list; attach the supplied latency and termination metadata.
     *
     * <p>Convenience for test code and for engine paths that have not
     * yet applied failure capping. Production code in punit-core's
     * engine constructs summaries via the canonical constructor
     * directly with a populated trials list.
     */
    public static <OT> SampleSummary<OT> from(
            List<ServiceContractOutcome<?, OT>> outcomes,
            Duration elapsed,
            LatencyResult latencyResult,
            TerminationReason terminationReason) {
        int s = 0, f = 0;
        long tokens = 0L;
        for (ServiceContractOutcome<?, OT> o : outcomes) {
            if (o.value().isOk()) s++;
            else f++;
            tokens += o.tokens();
        }
        return new SampleSummary<>(outcomes, elapsed, s, f, tokens, 0,
                latencyResult, terminationReason, List.of(),
                Map.of(), LatencyResult.empty(), List.of());
    }

    /**
     * Two-arg factory for callers that don't carry latency or
     * termination data — defaults {@code latencyResult} to
     * {@link LatencyResult#empty()} and {@code terminationReason} to
     * {@link TerminationReason#COMPLETED}.
     */
    public static <OT> SampleSummary<OT> from(List<ServiceContractOutcome<?, OT>> outcomes, Duration elapsed) {
        return from(outcomes, elapsed, LatencyResult.empty(), TerminationReason.COMPLETED);
    }

    public int total() {
        return successes + failures;
    }

    /** Observed pass rate; {@code NaN} when no samples executed. */
    public double passRate() {
        int t = total();
        return t == 0 ? Double.NaN : (double) successes / (double) t;
    }

    /** True when the sample loop was stopped by budget exhaustion. */
    public boolean terminatedEarly() {
        return terminationReason != TerminationReason.COMPLETED;
    }
}
