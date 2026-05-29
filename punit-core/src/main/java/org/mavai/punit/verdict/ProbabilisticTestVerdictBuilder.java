package org.mavai.punit.verdict;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.ThresholdOrigin;
import org.mavai.punit.api.spec.EvaluatedCriterion;
import org.mavai.punit.api.spec.FailureCount;
import org.mavai.punit.api.spec.InconclusiveReasons;
import org.mavai.punit.api.spec.Verdict;
import org.mavai.punit.api.ServiceContractAttributes;
import org.mavai.punit.verdict.TokenMode;
import org.mavai.punit.internal.engine.budget.SharedBudgetMonitor;
import org.mavai.punit.api.PacingConfiguration;
import org.mavai.punit.verdict.ExpirationStatus;
import org.mavai.punit.verdict.TerminationReason;
import org.mavai.punit.internal.reporting.RateFormat;
import org.mavai.punit.internal.engine.spec.expiration.ExpirationEvaluator;
import org.mavai.punit.internal.engine.spec.model.ExecutionSpecification;
import org.mavai.punit.statistics.BinomialProportionEstimator;
import org.mavai.punit.statistics.ComplianceEvidenceEvaluator;
import org.mavai.punit.statistics.VerificationFeasibilityEvaluator;
import org.mavai.punit.statistics.transparent.BaselineData;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.*;

/**
 * Constructs {@link ProbabilisticTestVerdict} from the raw sources available after
 * probabilistic test execution completes.
 *
 * <p>This builder bridges the gap between the test execution engine's mutable state
 * (aggregator, budget monitors) and the immutable verdict model. It snapshots mutable
 * state, computes derived values (statistical inference), and organises everything into
 * the nested record structure.
 *
 * <p>The builder does not perform any rendering or formatting.
 */
public class ProbabilisticTestVerdictBuilder {

    private final BinomialProportionEstimator estimator = new BinomialProportionEstimator();

    // ── Envelope ──────────────────────────────────────────────────────────
    private String correlationId;
    private Map<String, String> environmentMetadata = Map.of();

    // ── Identity ──────────────────────────────────────────────────────────
    private String className;
    private String methodName;
    private String serviceContractId;

    // ── Execution ─────────────────────────────────────────────────────────
    private int plannedSamples;
    private int samplesExecuted;
    private int successes;
    private int failures;
    private double minPassRate;
    private double observedPassRate;
    private long elapsedMs;
    private Double appliedMultiplier;
    private TestIntent intent = TestIntent.VERIFICATION;
    private double resolvedConfidence = 0.95;
    private ServiceContractAttributes serviceContractAttributes = ServiceContractAttributes.DEFAULT;

    // ── Dimensions ────────────────────────────────────────────────────────
    private Integer functionalSuccesses;
    private Integer functionalFailures;
    private LatencyInput latencyInput;

    // ── Covariates ────────────────────────────────────────────────────────
    private List<MisalignmentInput> misalignments = List.of();
    private Map<String, String> baselineCovariateProfile = Map.of();
    private Map<String, String> observedCovariateProfile = Map.of();

    // ── Cost ──────────────────────────────────────────────────────────────
    private long methodTokensConsumed;
    private long methodTimeBudgetMs;
    private long methodTokenBudget;
    private TokenMode tokenMode = TokenMode.NONE;
    private SharedBudgetMonitor classBudget;
    private SharedBudgetMonitor suiteBudget;

    // ── Pacing ────────────────────────────────────────────────────────────
    private PacingConfiguration pacingConfig;

    // ── Provenance ────────────────────────────────────────────────────────
    private ThresholdOrigin thresholdOrigin;
    private String contractRef;
    private String specFilename;
    private String baselineSourceLabel;
    private ExecutionSpecification spec;

    // ── Baseline ──────────────────────────────────────────────────────────
    private BaselineData baseline;

    // ── Termination ───────────────────────────────────────────────────────
    private TerminationReason terminationReason = TerminationReason.COMPLETED;
    private String terminationDetails;

    // ── Verdicts ──────────────────────────────────────────────────────────
    private boolean junitPassed;
    // Default to FAIL — the criterion-side default for "no information
    // recorded yet." The adapter sets this from the criterion's actual
    // Verdict; tests that build a verdict directly without a criterion
    // run can override via criterionVerdict(...).
    private Verdict criterionVerdict = Verdict.FAIL;
    // The criterion results carry the inconclusive-reason discriminant
    // (see InconclusiveReasons.DETAIL_KEY). When empty, the reason
    // synthesis falls back to "insufficient evidence" — the catch-all.
    private List<EvaluatedCriterion> criterionResults = List.of();

    // ── Postcondition failure histogram ───────────────────────────────────
    private Map<String, FailureCount> postconditionFailures = Map.of();

    // ── Per-criterion structural decomposition (rows + composite) ─────────
    // Optional. Populated by VerdictAdapter from
    // ProbabilisticTestResult.perCriterionEvaluation() on every normal
    // run; empty for apply-level-failure paths.
    private Optional<PerCriterionStructure> perCriterion = Optional.empty();

    // ── Builder methods ───────────────────────────────────────────────────

    public ProbabilisticTestVerdictBuilder correlationId(String correlationId) {
        this.correlationId = correlationId;
        return this;
    }

    public ProbabilisticTestVerdictBuilder environmentMetadata(Map<String, String> metadata) {
        this.environmentMetadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        return this;
    }

    public ProbabilisticTestVerdictBuilder identity(String className, String methodName, String serviceContractId) {
        this.className = className;
        this.methodName = methodName;
        this.serviceContractId = serviceContractId;
        return this;
    }

    public ProbabilisticTestVerdictBuilder execution(int plannedSamples, int samplesExecuted,
                                                      int successes, int failures,
                                                      double minPassRate, double observedPassRate,
                                                      long elapsedMs) {
        this.plannedSamples = plannedSamples;
        this.samplesExecuted = samplesExecuted;
        this.successes = successes;
        this.failures = failures;
        this.minPassRate = minPassRate;
        this.observedPassRate = observedPassRate;
        this.elapsedMs = elapsedMs;
        return this;
    }

    public ProbabilisticTestVerdictBuilder appliedMultiplier(double multiplier) {
        this.appliedMultiplier = multiplier;
        return this;
    }

    public ProbabilisticTestVerdictBuilder intent(TestIntent intent, double resolvedConfidence) {
        this.intent = intent;
        this.resolvedConfidence = resolvedConfidence;
        return this;
    }

    public ProbabilisticTestVerdictBuilder serviceContractAttributes(ServiceContractAttributes serviceContractAttributes) {
        this.serviceContractAttributes = serviceContractAttributes != null ? serviceContractAttributes : ServiceContractAttributes.DEFAULT;
        return this;
    }

    public ProbabilisticTestVerdictBuilder warmup(int warmup) {
        this.serviceContractAttributes = new ServiceContractAttributes(warmup, this.serviceContractAttributes.maxConcurrent());
        return this;
    }

    public ProbabilisticTestVerdictBuilder functionalDimension(int successes, int failures) {
        this.functionalSuccesses = successes;
        this.functionalFailures = failures;
        return this;
    }

    public ProbabilisticTestVerdictBuilder latencyDimension(LatencyInput input) {
        this.latencyInput = input;
        return this;
    }

    public ProbabilisticTestVerdictBuilder misalignments(List<MisalignmentInput> misalignments) {
        this.misalignments = misalignments != null ? misalignments : List.of();
        return this;
    }

    public ProbabilisticTestVerdictBuilder covariateProfiles(Map<String, String> baselineProfile,
                                                               Map<String, String> observedProfile) {
        this.baselineCovariateProfile = baselineProfile != null ? baselineProfile : Map.of();
        this.observedCovariateProfile = observedProfile != null ? observedProfile : Map.of();
        return this;
    }

    public ProbabilisticTestVerdictBuilder cost(long methodTokensConsumed,
                                                 long methodTimeBudgetMs,
                                                 long methodTokenBudget,
                                                 TokenMode tokenMode) {
        this.methodTokensConsumed = methodTokensConsumed;
        this.methodTimeBudgetMs = methodTimeBudgetMs;
        this.methodTokenBudget = methodTokenBudget;
        this.tokenMode = tokenMode;
        return this;
    }

    public ProbabilisticTestVerdictBuilder sharedBudgets(SharedBudgetMonitor classBudget,
                                                          SharedBudgetMonitor suiteBudget) {
        this.classBudget = classBudget;
        this.suiteBudget = suiteBudget;
        return this;
    }

    public ProbabilisticTestVerdictBuilder pacing(PacingConfiguration pacingConfig) {
        this.pacingConfig = pacingConfig;
        return this;
    }

    public ProbabilisticTestVerdictBuilder provenance(ThresholdOrigin thresholdOrigin,
                                                       String contractRef,
                                                       String specFilename) {
        this.thresholdOrigin = thresholdOrigin;
        this.contractRef = contractRef;
        this.specFilename = specFilename;
        return this;
    }

    public ProbabilisticTestVerdictBuilder provenance(ThresholdOrigin thresholdOrigin,
                                                       String contractRef,
                                                       String specFilename,
                                                       String baselineSourceLabel) {
        this.thresholdOrigin = thresholdOrigin;
        this.contractRef = contractRef;
        this.specFilename = specFilename;
        this.baselineSourceLabel = baselineSourceLabel;
        return this;
    }

    public ProbabilisticTestVerdictBuilder spec(ExecutionSpecification spec) {
        this.spec = spec;
        return this;
    }

    public ProbabilisticTestVerdictBuilder baseline(BaselineData baseline) {
        this.baseline = baseline;
        return this;
    }

    public ProbabilisticTestVerdictBuilder termination(TerminationReason reason, String details) {
        this.terminationReason = reason != null ? reason : TerminationReason.COMPLETED;
        this.terminationDetails = details;
        return this;
    }

    public ProbabilisticTestVerdictBuilder junitPassed(boolean junitPassed) {
        this.junitPassed = junitPassed;
        return this;
    }

    /**
     * Set the criterion-side verdict for this run. The three-state
     * value is preserved through to {@link #derivePUnitVerdict} so an
     * INCONCLUSIVE result from the criterion (no baseline, sample-size
     * violation, identity mismatch) does not silently collapse to FAIL
     * just because the run's covariates happen to be aligned, keeping
     * the verdict consistent with the statistical analysis.
     */
    public ProbabilisticTestVerdictBuilder criterionVerdict(Verdict verdict) {
        this.criterionVerdict = java.util.Objects.requireNonNull(verdict, "verdict");
        return this;
    }

    /**
     * Set the evaluated criterion results for this run. The verdict
     * builder reads each result's {@link CriterionResult#detail() detail map}
     * for the {@link InconclusiveReasons#DETAIL_KEY inconclusiveReason}
     * discriminant when synthesising the verdict reason on an
     * INCONCLUSIVE-aligned-non-budget verdict — the criterion is the
     * only layer that knows which of the four non-covariate non-budget
     * conditions fired (no baseline, inputs mismatch, sample-size
     * violation, statistical insufficiency).
     *
     * <p>When unset, the verdict reason falls back to
     * {@link InconclusiveReasons#INSUFFICIENT_EVIDENCE} — the
     * catch-all.
     */
    public ProbabilisticTestVerdictBuilder criterionResults(List<EvaluatedCriterion> results) {
        this.criterionResults = results == null ? List.of() : List.copyOf(results);
        return this;
    }

    /**
     * Per-postcondition failure histogram, keyed by clause description.
     * Iteration order is preserved (the engine delivers a
     * {@link java.util.LinkedHashMap}); the writer renders clauses in
     * the order the contract declared them.
     *
     * @param postconditionFailures the histogram; {@code null} maps to
     *                              an empty map
     */
    public ProbabilisticTestVerdictBuilder postconditionFailures(
            Map<String, FailureCount> postconditionFailures) {
        this.postconditionFailures = postconditionFailures != null
                ? java.util.Collections.unmodifiableMap(
                        new java.util.LinkedHashMap<>(postconditionFailures))
                : Map.of();
        return this;
    }

    /**
     * The per-criterion methodology-level decomposition: one row per
     * criterion the contract declared, plus the composite verdict.
     * Populated by {@code VerdictAdapter} from
     * {@code ProbabilisticTestResult.perCriterionEvaluation()}.
     * {@code null} leaves the field empty.
     */
    public ProbabilisticTestVerdictBuilder perCriterion(PerCriterionStructure perCriterion) {
        this.perCriterion = Optional.ofNullable(perCriterion);
        return this;
    }

    // ── Build ─────────────────────────────────────────────────────────────

    public ProbabilisticTestVerdict build() {
        String id = correlationId != null ? correlationId : newCorrelationId();
        TestIdentity identity = buildIdentity();
        ExecutionSummary execution = buildExecutionSummary();
        Optional<FunctionalDimension> functional = buildFunctionalDimension();
        Optional<LatencyDimension> latency = buildLatencyDimension();
        CovariateStatus covariates = buildCovariateStatus();
        StatisticalAnalysis statistics = buildStatisticalAnalysis(covariates);
        CostSummary cost = buildCostSummary();
        Optional<PacingSummary> pacing = buildPacingSummary();
        Optional<SpecProvenance> provenance = buildSpecProvenance();
        Termination termination = buildTermination();
        PUnitVerdict punitVerdict = derivePUnitVerdict(covariates);
        String verdictReason = deriveVerdictReason(punitVerdict, covariates);

        return new ProbabilisticTestVerdict(
                id, Instant.now(),
                identity, execution, functional, latency, statistics,
                covariates, cost, pacing, provenance, termination,
                environmentMetadata, junitPassed, punitVerdict, verdictReason,
                postconditionFailures,
                perCriterion
        );
    }

    private static String newCorrelationId() {
        return "v:" + UUID.randomUUID().toString().substring(0, 6);
    }

    // ── Private builders ──────────────────────────────────────────────────

    private TestIdentity buildIdentity() {
        return new TestIdentity(
                className,
                methodName,
                Optional.ofNullable(serviceContractId)
        );
    }

    private ExecutionSummary buildExecutionSummary() {
        return new ExecutionSummary(
                plannedSamples, samplesExecuted, successes, failures,
                minPassRate, observedPassRate, elapsedMs,
                Optional.ofNullable(appliedMultiplier),
                intent, resolvedConfidence, serviceContractAttributes
        );
    }

    private Optional<FunctionalDimension> buildFunctionalDimension() {
        if (functionalSuccesses == null) {
            return Optional.empty();
        }
        int s = functionalSuccesses;
        int f = functionalFailures != null ? functionalFailures : 0;
        double rate = (s + f) > 0 ? (double) s / (s + f) : 0.0;
        return Optional.of(new FunctionalDimension(s, f, rate));
    }

    private Optional<LatencyDimension> buildLatencyDimension() {
        if (latencyInput == null) {
            return Optional.empty();
        }
        LatencyInput li = latencyInput;
        return Optional.of(new LatencyDimension(
                li.successfulSamples(), li.totalSamples(),
                li.skipped(), Optional.ofNullable(li.skipReason()),
                li.p50Ms(), li.p90Ms(), li.p95Ms(), li.p99Ms(), li.maxMs(),
                li.caveats() != null ? li.caveats() : List.of()
        ));
    }

    private CovariateStatus buildCovariateStatus() {
        if (misalignments.isEmpty()) {
            if (baselineCovariateProfile.isEmpty() && observedCovariateProfile.isEmpty()) {
                return CovariateStatus.allAligned();
            }
            return new CovariateStatus(true, List.of(), baselineCovariateProfile, observedCovariateProfile);
        }
        List<Misalignment> converted = misalignments.stream()
                .map(m -> new Misalignment(m.covariateKey(), m.baselineValue(), m.testValue()))
                .toList();
        return new CovariateStatus(false, converted, baselineCovariateProfile, observedCovariateProfile);
    }

    private StatisticalAnalysis buildStatisticalAnalysis(CovariateStatus covariates) {
        double confidenceLevel = resolvedConfidence;

        double standardError = samplesExecuted > 0
                ? estimator.standardError(successes, samplesExecuted)
                : 0.0;

        double wilsonLower = samplesExecuted > 0
                ? estimator.lowerBound(successes, samplesExecuted, confidenceLevel)
                : 0.0;

        Optional<Double> testStatistic = Optional.empty();
        Optional<Double> pValue = Optional.empty();
        if (samplesExecuted > 0) {
            double z = estimator.zTestStatistic(observedPassRate, minPassRate, samplesExecuted);
            testStatistic = Optional.of(z);
            pValue = Optional.of(estimator.oneSidedPValue(z));
        }

        Optional<String> thresholdDerivation = Optional.empty();
        Optional<BaselineSummary> baselineSummary = Optional.empty();
        if (baseline != null && baseline.hasEmpiricalData()) {
            thresholdDerivation = Optional.of(buildThresholdDerivation());
            baselineSummary = Optional.of(new BaselineSummary(
                    baseline.sourceFile(),
                    baseline.generatedAt(),
                    baseline.baselineSamples(),
                    baseline.baselineSuccesses(),
                    baseline.baselineRate(),
                    minPassRate
            ));
        }

        List<String> caveats = buildCaveats(covariates);

        return new StatisticalAnalysis(
                confidenceLevel, standardError, wilsonLower,
                testStatistic, pValue, thresholdDerivation, baselineSummary, caveats
        );
    }

    private String buildThresholdDerivation() {
        if (baseline == null || !baseline.hasEmpiricalData()) {
            return "Inline threshold (no baseline spec)";
        }
        return "Wilson";
    }

    private List<String> buildCaveats(CovariateStatus covariates) {
        List<String> caveats = new ArrayList<>();

        if (!covariates.aligned()) {
            caveats.add("Covariate mismatch: test conditions do not match baseline conditions. "
                    + "Results may not be comparable.");
        }

        String originName = thresholdOrigin != null ? thresholdOrigin.name() : null;
        boolean isSmoke = intent == TestIntent.SMOKE;

        if (!isSmoke && ComplianceEvidenceEvaluator.hasComplianceContext(originName, contractRef)) {
            if (ComplianceEvidenceEvaluator.isUndersized(samplesExecuted, minPassRate)) {
                caveats.add(ComplianceEvidenceEvaluator.SIZING_NOTE);
            }
        }

        if (isSmoke && thresholdOrigin != null && thresholdOrigin.isNormative()) {
            double target = minPassRate;
            if (!Double.isNaN(target) && target > 0.0 && target < 1.0) {
                var result = VerificationFeasibilityEvaluator.evaluate(
                        samplesExecuted, target, resolvedConfidence);
                if (!result.feasible()) {
                    caveats.add(String.format(
                            "Sample not sized for verification (N=%d, need %d for target at %.0f%% confidence).",
                            samplesExecuted, result.minimumSamples(), resolvedConfidence * 100));
                }
            }
        }

        return List.copyOf(caveats);
    }

    private CostSummary buildCostSummary() {
        Optional<BudgetSnapshot> classSnapshot = classBudget != null
                ? Optional.of(snapshotBudget(classBudget))
                : Optional.empty();

        Optional<BudgetSnapshot> suiteSnapshot = suiteBudget != null
                ? Optional.of(snapshotBudget(suiteBudget))
                : Optional.empty();

        return new CostSummary(
                methodTokensConsumed, methodTimeBudgetMs, methodTokenBudget,
                tokenMode, classSnapshot, suiteSnapshot
        );
    }

    private BudgetSnapshot snapshotBudget(SharedBudgetMonitor monitor) {
        return new BudgetSnapshot(
                monitor.getTimeBudgetMs(),
                monitor.getElapsedMs(),
                monitor.getTokenBudget(),
                monitor.getTokensConsumed()
        );
    }

    private Optional<PacingSummary> buildPacingSummary() {
        if (pacingConfig == null || !pacingConfig.hasPacing()) {
            return Optional.empty();
        }
        return Optional.of(new PacingSummary(
                pacingConfig.maxRequestsPerSecond(),
                pacingConfig.maxRequestsPerMinute(),
                pacingConfig.maxRequestsPerHour(),
                pacingConfig.maxConcurrentRequests(),
                pacingConfig.effectiveMinDelayMs(),
                pacingConfig.effectiveConcurrency(),
                pacingConfig.effectiveRps()
        ));
    }

    private Optional<SpecProvenance> buildSpecProvenance() {
        if (thresholdOrigin == null || thresholdOrigin == ThresholdOrigin.UNSPECIFIED) {
            if (contractRef == null || contractRef.isEmpty()) {
                return Optional.empty();
            }
        }
        Optional<ExpirationInfo> expiration = buildExpirationInfo();

        return Optional.of(new SpecProvenance(
                thresholdOrigin != null ? thresholdOrigin.name() : "UNSPECIFIED",
                contractRef,
                specFilename,
                expiration,
                Optional.ofNullable(baselineSourceLabel)
        ));
    }

    private Optional<ExpirationInfo> buildExpirationInfo() {
        if (spec == null) {
            return Optional.empty();
        }
        ExpirationStatus status = ExpirationEvaluator.evaluate(spec);
        Optional<Instant> expiresAt = Optional.empty();
        if (spec.hasExpirationPolicy() && spec.getExpirationPolicy() != null) {
            expiresAt = spec.getExpirationPolicy().expirationTime();
        }
        return Optional.of(new ExpirationInfo(status, expiresAt));
    }

    private Termination buildTermination() {
        return new Termination(terminationReason, Optional.ofNullable(terminationDetails));
    }

    private PUnitVerdict derivePUnitVerdict(CovariateStatus covariates) {
        if (!covariates.aligned()) {
            return PUnitVerdict.INCONCLUSIVE;
        }
        if (terminationReason.isBudgetExhaustion()) {
            return PUnitVerdict.INCONCLUSIVE;
        }
        if (criterionVerdict == Verdict.INCONCLUSIVE) {
            return PUnitVerdict.INCONCLUSIVE;
        }
        return criterionVerdict == Verdict.PASS
                ? PUnitVerdict.PASS
                : PUnitVerdict.FAIL;
    }

    private String deriveVerdictReason(PUnitVerdict punitVerdict, CovariateStatus covariates) {
        if (punitVerdict == PUnitVerdict.INCONCLUSIVE) {
            if (!covariates.aligned()) {
                return InconclusiveReasons.COVARIATE_MISALIGNMENT;
            }
            if (terminationReason.isBudgetExhaustion()) {
                return InconclusiveReasons.BUDGET_EXHAUSTED;
            }
            return inconclusiveReasonFromCriteria()
                    .orElse(InconclusiveReasons.INSUFFICIENT_EVIDENCE);
        }
        if (terminationReason.isBudgetExhaustion()) {
            return InconclusiveReasons.BUDGET_EXHAUSTED;
        }
        String comparator = observedPassRate >= minPassRate ? ">=" : "<";
        return String.format("%s %s %s",
                RateFormat.format(observedPassRate),
                comparator,
                RateFormat.format(minPassRate));
    }

    /**
     * Scan the criterion results for the first INCONCLUSIVE-reason
     * discriminant (per {@link InconclusiveReasons#DETAIL_KEY}). The
     * "first" rule is fine: today's specs carry one criterion, and
     * when multiple are added the first INCONCLUSIVE criterion is the
     * one whose reason most narrowly describes the failure (subsequent
     * criteria run only if earlier ones don't short-circuit).
     */
    private Optional<String> inconclusiveReasonFromCriteria() {
        for (EvaluatedCriterion ec : criterionResults) {
            if (ec.result().verdict() != Verdict.INCONCLUSIVE) {
                continue;
            }
            Object marker = ec.result().detail().get(InconclusiveReasons.DETAIL_KEY);
            if (marker instanceof String s && !s.isBlank()) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    // ── Input records ─────────────────────────────────────────────────────

    /**
     * Primitive-only input for latency dimension data. Avoids coupling to
     * engine-internal types like {@code LatencyAssertionResult}.
     */
    public record LatencyInput(
            int successfulSamples,
            int totalSamples,
            boolean skipped,
            String skipReason,
            long p50Ms,
            long p90Ms,
            long p95Ms,
            long p99Ms,
            long maxMs,
            List<String> caveats
    ) {}

    /**
     * Primitive-only input for a covariate misalignment.
     */
    public record MisalignmentInput(
            String covariateKey,
            String baselineValue,
            String testValue
    ) {}
}
