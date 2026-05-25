package org.javai.punit.verdict;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.javai.punit.api.TestIntent;
import org.javai.punit.api.spec.FailureCount;
import org.javai.punit.verdict.TokenMode;
import org.javai.punit.verdict.ExpirationStatus;
import org.javai.punit.verdict.TerminationReason;
import org.javai.punit.api.ServiceContractAttributes;

/**
 * The single source of truth for a probabilistic test verdict.
 *
 * <p>This record captures everything needed to render a verdict at any detail level —
 * from a summary table row to a full statistical explanation. It is constructed after
 * test execution completes and before any rendering takes place.
 *
 * <p>All rendering paths (text logs, XML report, HTML report) consume this model.
 * No rendering target has access to information that another lacks.
 */
// javai-ref: JVI-60WEAWK — do not remove (resolves in javai-orchestrator)
public record ProbabilisticTestVerdict(
        String correlationId,
        Instant timestamp,
        TestIdentity identity,
        ExecutionSummary execution,
        Optional<FunctionalDimension> functional,
        Optional<LatencyDimension> latency,
        StatisticalAnalysis statistics,
        CovariateStatus covariates,
        CostSummary cost,
        Optional<PacingSummary> pacing,
        Optional<SpecProvenance> provenance,
        Termination termination,
        Map<String, String> environmentMetadata,
        boolean junitPassed,
        PUnitVerdict punitVerdict,
        String verdictReason,
        Map<String, FailureCount> postconditionFailures,
        Optional<PerCriterionStructure> perCriterion
) {

    public ProbabilisticTestVerdict {
        // Preserve insertion order — clauses appear in the order the
        // contract declared them. Map.copyOf does not guarantee order;
        // unmodifiableMap(LinkedHashMap) does.
        postconditionFailures = postconditionFailures != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(postconditionFailures))
                : Map.of();
        perCriterion = perCriterion != null ? perCriterion : Optional.empty();
    }

    /**
     * Backward-compatible constructor for the 17-component shape that
     * predates the per-criterion structural surface (CR09 / CR01 in
     * the verdict XML). Defaults the per-criterion component to empty.
     */
    public ProbabilisticTestVerdict(
            String correlationId,
            Instant timestamp,
            TestIdentity identity,
            ExecutionSummary execution,
            Optional<FunctionalDimension> functional,
            Optional<LatencyDimension> latency,
            StatisticalAnalysis statistics,
            CovariateStatus covariates,
            CostSummary cost,
            Optional<PacingSummary> pacing,
            Optional<SpecProvenance> provenance,
            Termination termination,
            Map<String, String> environmentMetadata,
            boolean junitPassed,
            PUnitVerdict punitVerdict,
            String verdictReason,
            Map<String, FailureCount> postconditionFailures) {
        this(correlationId, timestamp, identity, execution, functional, latency,
                statistics, covariates, cost, pacing, provenance, termination,
                environmentMetadata, junitPassed, punitVerdict, verdictReason,
                postconditionFailures, Optional.empty());
    }

    /**
     * Backward-compatible constructor for the 16-component shape that
     * predates the per-postcondition failure histogram. Defaults the
     * histogram to an empty map and the per-criterion structure to empty.
     */
    public ProbabilisticTestVerdict(
            String correlationId,
            Instant timestamp,
            TestIdentity identity,
            ExecutionSummary execution,
            Optional<FunctionalDimension> functional,
            Optional<LatencyDimension> latency,
            StatisticalAnalysis statistics,
            CovariateStatus covariates,
            CostSummary cost,
            Optional<PacingSummary> pacing,
            Optional<SpecProvenance> provenance,
            Termination termination,
            Map<String, String> environmentMetadata,
            boolean junitPassed,
            PUnitVerdict punitVerdict,
            String verdictReason) {
        this(correlationId, timestamp, identity, execution, functional, latency,
                statistics, covariates, cost, pacing, provenance, termination,
                environmentMetadata, junitPassed, punitVerdict, verdictReason,
                Map.of(), Optional.empty());
    }

    // ── TestIdentity ──────────────────────────────────────────────────────

    /**
     * Identifies the test that produced this verdict.
     *
     * @param className the test class name
     * @param methodName the test method name
     * @param serviceContractId the service contract identifier, if the test is associated with a service contract
     */
    public record TestIdentity(
            String className,
            String methodName,
            Optional<String> serviceContractId
    ) {
        public TestIdentity {
            if (className == null || className.isBlank()) {
                throw new IllegalArgumentException("className must not be blank");
            }
            if (methodName == null || methodName.isBlank()) {
                throw new IllegalArgumentException("methodName must not be blank");
            }
            serviceContractId = serviceContractId != null ? serviceContractId : Optional.empty();
        }
    }

    // ── ExecutionSummary ──────────────────────────────────────────────────

    /**
     * Summarises how the test was executed.
     *
     * @param plannedSamples the number of samples planned
     * @param samplesExecuted the number of samples actually executed
     * @param successes the number of successful samples
     * @param failures the number of failed samples
     * @param minPassRate the minimum pass rate threshold (p₀)
     * @param observedPassRate the observed pass rate (p̂)
     * @param elapsedMs wall-clock time in milliseconds
     * @param appliedMultiplier sample multiplier, if one was applied
     * @param intent the test intent (VERIFICATION or SMOKE)
     * @param resolvedConfidence the confidence level used for statistical analysis
     * @param serviceContractAttributes service contract attributes (warmup, maxConcurrent, etc.)
     */
    public record ExecutionSummary(
            int plannedSamples,
            int samplesExecuted,
            int successes,
            int failures,
            double minPassRate,
            double observedPassRate,
            long elapsedMs,
            Optional<Double> appliedMultiplier,
            TestIntent intent,
            double resolvedConfidence,
            ServiceContractAttributes serviceContractAttributes
    ) {
        public ExecutionSummary {
            appliedMultiplier = appliedMultiplier.isPresent() ? appliedMultiplier : Optional.empty();
            serviceContractAttributes = serviceContractAttributes != null ? serviceContractAttributes : ServiceContractAttributes.DEFAULT;
        }

        /** Convenience accessor for warmup count. */
        public int warmup() {
            return serviceContractAttributes.warmup();
        }
    }

    // ── FunctionalDimension ───────────────────────────────────────────────

    /**
     * Contract/functional assertion results for the functional dimension.
     *
     * @param successes samples where the functional assertion passed
     * @param failures samples where the functional assertion failed
     * @param passRate functional pass rate
     */
    public record FunctionalDimension(
            int successes,
            int failures,
            double passRate
    ) {}

    // ── LatencyDimension ──────────────────────────────────────────────────

    /**
     * Latency assertion results.
     *
     * @param successfulSamples samples used for latency computation
     * @param totalSamples all samples including failures
     * @param skipped whether latency evaluation was skipped
     * @param skipReason reason for skipping, if applicable
     * @param p50Ms observed p50 latency (-1 if unavailable)
     * @param p90Ms observed p90 latency (-1 if unavailable)
     * @param p95Ms observed p95 latency (-1 if unavailable)
     * @param p99Ms observed p99 latency (-1 if unavailable)
     * @param maxMs observed max latency (-1 if unavailable)
     * @param assertions per-percentile assertion results
     * @param caveats advisory messages
     * @param dimensionSuccesses latency dimension success count from aggregator
     * @param dimensionFailures latency dimension failure count from aggregator
     */
    public record LatencyDimension(
            int successfulSamples,
            int totalSamples,
            boolean skipped,
            Optional<String> skipReason,
            long p50Ms,
            long p90Ms,
            long p95Ms,
            long p99Ms,
            long maxMs,
            List<String> caveats,
            String basis
    ) {
        public LatencyDimension {
            skipReason = skipReason != null ? skipReason : Optional.empty();
            caveats = caveats != null ? List.copyOf(caveats) : List.of();
            basis = basis != null ? basis : "passing-samples";
        }

        /**
         * Backward-compatible constructor that defaults
         * {@link #basis()} to {@code "passing-samples"} (the only
         * currently defined population). Test fixtures and older
         * call sites that haven't yet adopted the canonical field
         * shape can construct via this overload.
         */
        public LatencyDimension(
                int successfulSamples,
                int totalSamples,
                boolean skipped,
                Optional<String> skipReason,
                long p50Ms,
                long p90Ms,
                long p95Ms,
                long p99Ms,
                long maxMs,
                List<String> caveats) {
            this(successfulSamples, totalSamples, skipped, skipReason,
                    p50Ms, p90Ms, p95Ms, p99Ms, maxMs, caveats,
                    "passing-samples");
        }
    }

    // ── StatisticalAnalysis ───────────────────────────────────────────────

    /**
     * The statistical narrative — always computed regardless of display mode.
     *
     * @param confidenceLevel the confidence level used (e.g., 0.95)
     * @param standardError SE = √(p̂(1-p̂)/n)
     * @param wilsonLower Wilson one-sided lower bound on the test
     *                    observation's pass rate at {@code confidenceLevel}.
     *                    The verdict path is one-sided (degradation only);
     *                    the upper bound carries no operational meaning under
     *                    a left-tailed test and is not emitted.
     * @param testStatistic z-test statistic, if computable
     * @param pValue one-sided p-value, if computable
     * @param thresholdDerivation description of how the threshold was derived, if spec-driven
     * @param baseline baseline data summary, if spec-driven
     * @param caveats advisory notes about the statistical analysis
     */
    public record StatisticalAnalysis(
            double confidenceLevel,
            double standardError,
            double wilsonLower,
            Optional<Double> testStatistic,
            Optional<Double> pValue,
            Optional<String> thresholdDerivation,
            Optional<BaselineSummary> baseline,
            List<String> caveats
    ) {
        public StatisticalAnalysis {
            testStatistic = testStatistic != null ? testStatistic : Optional.empty();
            pValue = pValue != null ? pValue : Optional.empty();
            thresholdDerivation = thresholdDerivation != null ? thresholdDerivation : Optional.empty();
            baseline = baseline != null ? baseline : Optional.empty();
            caveats = caveats != null ? List.copyOf(caveats) : List.of();
        }
    }

    /**
     * Baseline data when the test is spec-driven.
     *
     * @param sourceFile the spec filename
     * @param generatedAt when the baseline was generated
     * @param baselineSamples number of samples in the baseline
     * @param baselineSuccesses number of successes in the baseline
     * @param baselineRate baseline pass rate
     * @param derivedThreshold the threshold derived from the baseline
     */
    public record BaselineSummary(
            String sourceFile,
            Instant generatedAt,
            int baselineSamples,
            int baselineSuccesses,
            double baselineRate,
            double derivedThreshold
    ) {}

    // ── CovariateStatus ───────────────────────────────────────────────────

    /**
     * Covariate alignment status between test conditions and baseline conditions.
     *
     * @param aligned true if all covariates match the baseline
     * @param misalignments list of misaligned covariates (empty if aligned)
     * @param baselineProfile all covariates from the baseline spec (empty if no covariates declared)
     * @param observedProfile all covariates observed at test time (empty if no covariates declared)
     */
    public record CovariateStatus(
            boolean aligned,
            List<Misalignment> misalignments,
            Map<String, String> baselineProfile,
            Map<String, String> observedProfile
    ) {
        public CovariateStatus {
            misalignments = misalignments != null ? List.copyOf(misalignments) : List.of();
            baselineProfile = baselineProfile != null
                    ? Map.copyOf(new LinkedHashMap<>(baselineProfile)) : Map.of();
            observedProfile = observedProfile != null
                    ? Map.copyOf(new LinkedHashMap<>(observedProfile)) : Map.of();
        }

        public static CovariateStatus allAligned() {
            return new CovariateStatus(true, List.of(), Map.of(), Map.of());
        }
    }

    /**
     * A single covariate that does not match the baseline.
     *
     * @param covariateKey the covariate name
     * @param baselineValue the value in the baseline
     * @param testValue the value in the current test
     */
    public record Misalignment(
            String covariateKey,
            String baselineValue,
            String testValue
    ) {}

    // ── CostSummary ───────────────────────────────────────────────────────

    /**
     * Token and cost information from test execution.
     *
     * @param methodTokensConsumed tokens consumed at the method level
     * @param methodTimeBudgetMs method-level time budget (0 = unlimited)
     * @param methodTokenBudget method-level token budget (0 = unlimited)
     * @param tokenMode token tracking mode
     * @param classBudget class-level budget snapshot, if a class budget was configured
     * @param suiteBudget suite-level budget snapshot, if a suite budget was configured
     */
    public record CostSummary(
            long methodTokensConsumed,
            long methodTimeBudgetMs,
            long methodTokenBudget,
            TokenMode tokenMode,
            Optional<BudgetSnapshot> classBudget,
            Optional<BudgetSnapshot> suiteBudget
    ) {
        public CostSummary {
            classBudget = classBudget != null ? classBudget : Optional.empty();
            suiteBudget = suiteBudget != null ? suiteBudget : Optional.empty();
        }
    }

    /**
     * A snapshot of a shared budget (class or suite level) at the time the verdict was produced.
     *
     * @param timeBudgetMs configured time budget (0 = unlimited)
     * @param elapsedMs elapsed time at snapshot
     * @param tokenBudget configured token budget (0 = unlimited)
     * @param tokensConsumed tokens consumed at snapshot
     */
    public record BudgetSnapshot(
            long timeBudgetMs,
            long elapsedMs,
            long tokenBudget,
            long tokensConsumed
    ) {}

    // ── PacingSummary ─────────────────────────────────────────────────────

    /**
     * Pacing strategy that was in effect during test execution.
     *
     * @param maxRequestsPerSecond configured max RPS (0 = unlimited)
     * @param maxRequestsPerMinute configured max RPM (0 = unlimited)
     * @param maxRequestsPerHour configured max RPH (0 = unlimited)
     * @param maxConcurrentRequests configured max concurrency (0 or 1 = sequential)
     * @param effectiveMinDelayMs computed minimum delay between samples
     * @param effectiveConcurrency computed effective concurrency
     * @param effectiveRps computed effective requests per second
     */
    public record PacingSummary(
            double maxRequestsPerSecond,
            double maxRequestsPerMinute,
            double maxRequestsPerHour,
            int maxConcurrentRequests,
            long effectiveMinDelayMs,
            int effectiveConcurrency,
            double effectiveRps
    ) {}

    // ── SpecProvenance ────────────────────────────────────────────────────

    /**
     * Where the threshold came from — spec, contract, or inline.
     *
     * @param thresholdOriginName the origin (SLA, SLO, POLICY, EMPIRICAL, UNSPECIFIED)
     * @param contractRef human-readable contract reference
     * @param specFilename the spec filename used for threshold derivation
     * @param expiration expiration status of the spec, if applicable
     * @param baselineSourceLabel describes the baseline source: "(bundled)" for classpath,
     *                            the directory path for env-local, or empty when unknown
     */
    public record SpecProvenance(
            String thresholdOriginName,
            String contractRef,
            String specFilename,
            Optional<ExpirationInfo> expiration,
            Optional<String> baselineSourceLabel
    ) {
        public SpecProvenance {
            expiration = expiration != null ? expiration : Optional.empty();
            baselineSourceLabel = baselineSourceLabel != null ? baselineSourceLabel : Optional.empty();
        }

        /**
         * Backward-compatible constructor without baselineSourceLabel.
         */
        public SpecProvenance(String thresholdOriginName, String contractRef,
                               String specFilename, Optional<ExpirationInfo> expiration) {
            this(thresholdOriginName, contractRef, specFilename, expiration, Optional.empty());
        }
    }

    /**
     * Expiration status of the baseline spec.
     *
     * @param status the evaluated expiration status
     * @param expiresAt the expiration timestamp, if the spec has an expiration policy
     */
    public record ExpirationInfo(
            ExpirationStatus status,
            Optional<Instant> expiresAt
    ) {
        public ExpirationInfo {
            expiresAt = expiresAt != null ? expiresAt : Optional.empty();
        }
    }

    // ── Termination ───────────────────────────────────────────────────────

    /**
     * How test execution ended.
     *
     * @param reason the termination reason
     * @param details additional details about termination
     */
    public record Termination(
            TerminationReason reason,
            Optional<String> details
    ) {
        public Termination {
            details = details != null ? details : Optional.empty();
        }
    }
}
