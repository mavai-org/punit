package org.mavai.punit.api.spec;

import java.util.Objects;
import java.util.Optional;

import org.mavai.punit.api.LatencyResult;

/**
 * Run-level scalar digest of a typed pipeline execution. Sits on
 * {@link ProbabilisticTestResult} so consumers (verdict XML adapter,
 * HTML report, audit dashboards) have a single typed bundle for the
 * "what happened during the run" data — sample counts, timing, cost,
 * latency, termination reason, baseline traceability.
 *
 * <p>This is a digest of {@link SampleSummary}, not a replacement for it.
 * The summary's per-trial detail (the {@link ServiceContractOutcome} list, the
 * full {@link Trial} history) is still consumed inside {@code Spec.consume}
 * for criterion evaluation; the {@code EngineRunSummary} preserves only
 * the scalars that need to flow downstream after the spec has finished
 * concluding.
 *
 * <p>{@link #plannedSamples()} differs from {@link #samplesExecuted()}
 * when the run terminated early due to budget exhaustion. Together they
 * tell a downstream consumer "we asked for N, we got M" — the difference
 * matters for verdict-XML's {@code <execution planned-samples>} attribute
 * and for any analysis of run completeness.
 *
 * <p>{@link #confidence()} is the statistical confidence level used by
 * empirical criteria (default 0.95). Lifted to the run level so the
 * verdict XML adapter and other consumers don't have to scrape it from
 * a criterion's detail map.
 *
 * <p>{@link #baselineFilename()} carries the matched baseline file's name
 * when an empirical criterion resolved against one — surfaced to the
 * verdict XML's {@code <provenance spec-filename>} attribute for audit
 * traceability. Empty when no empirical criterion ran or no baseline
 * matched.
 *
 * @param plannedSamples     samples requested by the spec (from
 *                           {@link Sampling#samples()}); non-negative
 * @param samplesExecuted    samples actually executed (= successes +
 *                           failures); non-negative; &le; plannedSamples
 * @param successes          count of Ok-outcome samples; non-negative
 * @param failures           count of Fail-outcome samples; non-negative
 * @param elapsedMs          wall-clock time spent sampling, in
 *                           milliseconds; non-negative
 * @param tokensConsumed     sum of per-sample tokens plus the spec's
 *                           static charge for each counted sample;
 *                           non-negative
 * @param failuresDropped    count of failing outcomes whose detail was
 *                           elided due to {@link Sampling}'s
 *                           {@code maxExampleFailures} cap; non-negative
 * @param latencyResult      computed p50/p90/p95/p99 from the run's
 *                           successful samples
 * @param terminationReason  why the sample loop stopped
 * @param confidence         statistical confidence level (1 − α) used
 *                           by empirical criteria; in (0, 1)
 * @param baselineFilename   filename of the matched baseline, when an
 *                           empirical criterion resolved against one;
 *                           {@link Optional#empty()} otherwise
 */
public record EngineRunSummary(
        int plannedSamples,
        int samplesExecuted,
        int successes,
        int failures,
        long elapsedMs,
        long tokensConsumed,
        int failuresDropped,
        LatencyResult latencyResult,
        TerminationReason terminationReason,
        double confidence,
        Optional<String> baselineFilename,
        LatencyResult passingLatencyResult) {

    public EngineRunSummary {
        Objects.requireNonNull(latencyResult, "latencyResult");
        Objects.requireNonNull(terminationReason, "terminationReason");
        Objects.requireNonNull(baselineFilename, "baselineFilename");
        Objects.requireNonNull(passingLatencyResult, "passingLatencyResult");
        if (plannedSamples < 0 || samplesExecuted < 0
                || successes < 0 || failures < 0) {
            throw new IllegalArgumentException("counts must be non-negative");
        }
        if (elapsedMs < 0) {
            throw new IllegalArgumentException("elapsedMs must be non-negative, got " + elapsedMs);
        }
        if (tokensConsumed < 0) {
            throw new IllegalArgumentException("tokensConsumed must be non-negative, got " + tokensConsumed);
        }
        if (failuresDropped < 0) {
            throw new IllegalArgumentException("failuresDropped must be non-negative, got " + failuresDropped);
        }
        if (Double.isNaN(confidence) || confidence <= 0.0 || confidence >= 1.0) {
            throw new IllegalArgumentException(
                    "confidence must be in (0, 1), got " + confidence);
        }
    }

    /**
     * Empty summary for tests and for call sites that don't carry
     * engine-run data — defaults all scalars to zero, latency to
     * {@link LatencyResult#empty()}, termination to
     * {@link TerminationReason#COMPLETED}, confidence to 0.95, and
     * {@link #baselineFilename()} to empty.
     */
    public static EngineRunSummary empty() {
        return new EngineRunSummary(
                0, 0, 0, 0, 0L, 0L, 0,
                LatencyResult.empty(),
                TerminationReason.COMPLETED,
                0.95,
                Optional.empty(),
                LatencyResult.empty());
    }

    /**
     * Backward-compatible 11-arg constructor. Defaults
     * {@link #passingLatencyResult()} to the supplied
     * {@code latencyResult} so callers that haven't yet
     * differentiated passing-only data see the descriptive latency
     * dimension populated as it was before the passing/all-samples
     * split landed. Production code in the engine constructs
     * summaries via the canonical 12-arg constructor with the proper
     * passing-only result.
     */
    public EngineRunSummary(
            int plannedSamples,
            int samplesExecuted,
            int successes,
            int failures,
            long elapsedMs,
            long tokensConsumed,
            int failuresDropped,
            LatencyResult latencyResult,
            TerminationReason terminationReason,
            double confidence,
            Optional<String> baselineFilename) {
        this(plannedSamples, samplesExecuted, successes, failures,
                elapsedMs, tokensConsumed, failuresDropped, latencyResult,
                terminationReason, confidence, baselineFilename,
                latencyResult);
    }
}
