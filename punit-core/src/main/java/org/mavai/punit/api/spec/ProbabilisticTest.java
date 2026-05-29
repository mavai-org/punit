package org.mavai.punit.api.spec;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.function.Function;

import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.covariate.CovariateAlignment;
import org.mavai.punit.api.covariate.CovariateProfile;
import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.ServiceContract;

/**
 * A probabilistic test: runs a {@link Sampling} bound to a factor
 * bundle and evaluates one or more {@link Criterion} values against
 * the resulting sample summary.
 *
 * <p>Constructed via {@link #testing(Sampling, Object)}. The sample
 * production is governed by the factor-free {@link Sampling}; the
 * second argument is the factor bundle the test runs against. The
 * claims the spec makes about the resulting population are expressed
 * by the criteria registered via {@link Builder#criterion(Criterion)}
 * (REQUIRED) and {@link Builder#reportOnly(Criterion)} (REPORT_ONLY).
 * The combined verdict is {@link Verdict#compose(List)}'d from the
 * REQUIRED subset.
 *
 * <p>The public class carries no type parameters — composition-time
 * type safety lives on the typed {@link Builder}, and the engine
 * recovers the typed view via {@link Spec#dispatch(Dispatcher)}.
 */
public final class ProbabilisticTest implements Spec {

    private final Internal<?, ?, ?> internal;

    private ProbabilisticTest(Internal<?, ?, ?> internal) {
        this.internal = internal;
    }

    /**
     * Entry point — compose a probabilistic test over a
     * {@link Sampling} and the factors it should run against.
     *
     * <p>For an empirical test (criterion built via
     * {@code PassRate.empirical()} or {@code .empiricalFrom(...)}),
     * the {@code Sampling} passed here must be the <em>same value</em>
     * passed to the paired measure's
     * {@link Experiment#measuring(Sampling, Object) Experiment.measuring(...)}.
     * The shared reference is what guarantees the test and the
     * baseline are drawn from the same sampling population — same
     * service contract, same input list, same governors. Without that
     * sameness, the empirical comparison is statistically
     * incoherent.
     *
     * <p>Authoring pattern: extract the {@code Sampling} into a
     * helper method shared between the measure and the test:
     *
     * <pre>{@code
     * private Sampling<F, I, O> sampling(int samples) { ... }
     *
     * @PUnitExperiment Experiment baseline() {
     *     return Experiment.measuring(sampling(1000), factors).build();
     * }
     * @PUnitTest ProbabilisticTest meets() {
     *     return ProbabilisticTest.testing(sampling(100), factors).build();
     * }
     * }</pre>
     *
     * <p>Same {@code factors} at both call sites; same helper
     * supplying the {@code Sampling}; only the sample count and the
     * criterion overlay differ.
     */
    public static <FT, IT, OT> Builder<FT, IT, OT> testing(
            Sampling<FT, IT, OT> sampling, FT factors) {
        return new Builder<>(
                Objects.requireNonNull(sampling, "sampling"),
                Objects.requireNonNull(factors, "factors"));
    }

    /**
     * Inline-sampling form of {@link #testing(Sampling, Object)}.
     * Sampling parameters are supplied through the returned builder.
     *
     * <p>For a probabilistic test with a <strong>contractual</strong>
     * criterion (e.g., {@code PassRate.meeting(threshold, origin)}),
     * the inline form is equivalent to constructing a fresh
     * {@link Sampling} per spec — there is no baseline pairing to
     * preserve, so no integrity guarantee at risk.
     *
     * <p>For a probabilistic test with an <strong>empirical</strong>
     * criterion ({@code PassRate.empirical()} /
     * {@code .empiricalFrom(...)}), the inline form is rejected at
     * {@code .build()} time. An empirical comparison requires the
     * test and the baseline measure to draw from the same sampling
     * population; that integrity guarantee comes from sharing a
     * {@link Sampling} value with the paired measure, which the
     * inline form cannot provide. The diagnostic teaches the
     * helper-extraction pattern.
     */
    public static <FT, IT, OT> InlineBuilder<FT, IT, OT> testing(
            Function<FT, ServiceContract<FT, IT, OT>> serviceContractFactory, FT factors) {
        return new InlineBuilder<>(
                Objects.requireNonNull(serviceContractFactory, "serviceContractFactory"),
                Objects.requireNonNull(factors, "factors"));
    }

    /** Sample count for this test, taken from its sampling. */
    public int samples() { return internal.sampling.samples(); }

    /**
     * The test's declared intent. {@link TestIntent#VERIFICATION} (default)
     * claims evidential status; {@link TestIntent#SMOKE} is a
     * sentinel-grade lightweight check.
     */
    public TestIntent intent() { return internal.intent; }

    @Override
    public <R> R dispatch(Dispatcher<R> dispatcher) {
        return doDispatch(internal, dispatcher);
    }

    private static <FT, IT, OT, R> R doDispatch(Internal<FT, IT, OT> typed, Dispatcher<R> d) {
        return d.apply(typed);
    }

    // ── Internal delegate (engine-facing) ───────────────────────────

    private static final class Internal<FT, IT, OT> implements TypedSpec<FT, IT, OT> {

        private final Sampling<FT, IT, OT> sampling;
        private final FT factors;
        private final List<Registered<OT>> registered;
        private final TestIntent intent;
        private final boolean earlyTerminationDisabled;

        private SampleSummary<OT> summary;

        private Internal(Builder<FT, IT, OT> b) {
            this.sampling = b.sampling;
            this.factors = b.factors;
            this.registered = List.copyOf(b.registered);
            this.intent = b.intent;
            this.earlyTerminationDisabled = b.earlyTerminationDisabled;
        }

        @Override public Function<FT, ServiceContract<FT, IT, OT>> serviceContractFactory() {
            return sampling.serviceContractFactory();
        }

        @Override public Iterator<Configuration<FT, IT, OT>> configurations() {
            return List.of(new Configuration<FT, IT, OT>(
                    factors, sampling.inputs(), sampling.samples())).iterator();
        }

        @Override public void consume(Configuration<FT, IT, OT> config, SampleSummary<OT> summary) {
            this.summary = summary;
        }

        @Override public EngineResult conclude(BaselineProvider provider) {
            Objects.requireNonNull(provider, "provider");
            SampleSummary<OT> s = summary != null
                    ? summary
                    : SampleSummary.from(List.of(), Duration.ZERO);
            FactorBundle factorBundle = FactorBundle.of(factors);
            org.mavai.punit.api.ServiceContract<FT, IT, OT> contract =
                    sampling.serviceContractFactory().apply(factors);
            String serviceContractId = contract.id();
            // Per-methodology-criterion postures, keyed by criterion id —
            // threaded through the EvaluationContext so PassRate (and
            // future K-aware evaluators) can read each criterion's
            // commitment without re-resolving the contract.
            java.util.Map<String, org.mavai.punit.api.criterion.CriterionPosture> criterionPostures =
                    new java.util.LinkedHashMap<>();
            for (org.mavai.punit.api.criterion.Criterion<OT> c : contract.effectiveCriteria()) {
                criterionPostures.put(c.id(), c.posture());
            }
            String testInputsIdentity = sampling.inputsIdentity();
            // Looked up once and reused across criteria — the identity is a
            // property of the baseline file, not of any individual criterion.
            Optional<String> baselineInputsIdentity = anyEmpiricalCriterion()
                    ? provider.baselineInputsIdentityFor(serviceContractId, factorBundle)
                    : Optional.empty();

            List<EvaluatedCriterion> evaluated = new ArrayList<>(registered.size());
            // De-duplicated misalignment notes — selection runs once per
            // empirical criterion against the same file, so each criterion
            // accumulates the same notes; recording per-criterion would
            // produce N copies of every reason. The notes describe the
            // baseline file's misalignment, not any one criterion's.
            java.util.LinkedHashSet<String> warnings = new java.util.LinkedHashSet<>();
            // The matched baseline's covariate profile. All empirical
            // criteria that resolve a baseline for the same
            // (serviceContractId, factorsFingerprint) tuple resolve the same
            // file, so the first non-empty profile we see is the one
            // for the run. Stamped onto the result so verdict
            // renderers / HTML emitters can compare against the
            // observed profile (post-stamped at the JUnit boundary).
            CovariateProfile baselineProfile =
                    CovariateProfile.empty();
            // Same logic as baselineProfile: every empirical criterion
            // that resolves for the same (serviceContractId, factorsFingerprint)
            // tuple sees the same baseline file, so first-non-empty wins.
            Optional<String> baselineFilename = Optional.empty();
            // True when any resolved baseline has passed its validity
            // window. Drives the fail-on-expired policy below; the
            // human caveat is already in `warnings` via lookup notes.
            boolean anyBaselineExpired = false;
            for (Registered<OT> entry : registered) {
                BaselineLookupCapture capture = new BaselineLookupCapture();
                CriterionResult result = evaluate(
                        entry.criterion(), s, factorBundle, serviceContractId,
                        testInputsIdentity, baselineInputsIdentity, criterionPostures,
                        provider, warnings, capture);
                evaluated.add(new EvaluatedCriterion(result, entry.role()));
                if (baselineProfile.isEmpty() && !capture.profile.isEmpty()) {
                    baselineProfile = capture.profile;
                }
                if (baselineFilename.isEmpty() && capture.sourceFile.isPresent()) {
                    baselineFilename = capture.sourceFile;
                }
                anyBaselineExpired |= capture.expired;
            }

            EngineRunSummary engineSummary = buildEngineSummary(s, evaluated, baselineFilename);
            PerCriterionEvaluation perCriterionEvaluation =
                    PerCriterionVerdicts.derive(evaluated, s.criterionSampleCounts());
            // Substitute the functional verdict-component's verdict with
            // the per-criterion composite, then continue to use
            // Verdict.compose for cross-dimension aggregation. This keeps
            // latency-dimension criteria (PercentileLatency etc.) in
            // their existing role — they remain spec-layer EvaluatedCriterion
            // entries and contribute to the final verdict under the
            // existing INCONCLUSIVE-first composition rule — while the
            // functional dimension's verdict is now driven by the
            // methodology-level per-criterion composite under the
            // FAIL-dominant aggregation rule (§1.4.6).
            //
            // The substitution is a no-op for K=1 contracts (per-criterion
            // composite over one verdict equals that verdict; step-3's
            // K=1 isomorphism test pins this), preserving byte-identical
            // behaviour. For K>1 contracts the hiding-result case flips:
            // a single failing criterion now drives the functional
            // verdict to FAIL.
            //
            // Empty per-criterion evaluation (apply-level-failure runs
            // where Contract.evaluateClauses never fired) leaves the
            // unsubstituted compose result intact.
            List<EvaluatedCriterion> compositeAdjusted = perCriterionEvaluation.perCriterionVerdicts().isEmpty()
                    ? evaluated
                    : substituteFunctionalVerdict(evaluated, perCriterionEvaluation.compositeVerdict());
            Verdict authoritative = Verdict.compose(compositeAdjusted);
            // Fail-on-expired policy: an expired baseline always carries
            // a caveat (already in `warnings` via lookup notes); under
            // the FAIL policy it additionally forces the verdict to FAIL,
            // dominating the statistical determination — a stale baseline
            // makes the empirical comparison untrustworthy, so a PASS
            // against it must not stand. Default (WARN) leaves the
            // statistical verdict intact.
            if (anyBaselineExpired
                    && ExpiredBaselinePolicy.fromEnvironment() == ExpiredBaselinePolicy.FAIL) {
                warnings.add("expiration policy=FAIL: verdict failed because the resolved "
                        + "baseline has expired (see the baseline-expiry caveat above) — "
                        + "re-run the MEASURE experiment to refresh it");
                authoritative = Verdict.FAIL;
            }
            // contractRef now lives on the criterion's posture. Surface
            // the first non-empty ref at the result level (the legacy
            // top-level field) — multi-criterion contracts whose
            // criteria cite different clauses are still uncommon enough
            // that "first wins" is a safe default. The per-criterion
            // refs travel alongside on each EvaluatedCriterion's result
            // detail map.
            Optional<String> contractRefFromContract = criterionPostures.values().stream()
                    .map(org.mavai.punit.api.criterion.CriterionPosture::contractRef)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst();
            return new ProbabilisticTestResult(
                    authoritative, factorBundle, evaluated, intent,
                    List.copyOf(warnings),
                    CovariateAlignment.compute(
                            CovariateProfile.empty(),
                            baselineProfile),
                    contractRefFromContract,
                    s.failuresByPostcondition(),
                    engineSummary,
                    perCriterionEvaluation);
        }

        /**
         * Returns a copy of {@code evaluated} in which the
         * {@code bernoulli-pass-rate} entry's verdict is replaced by
         * the supplied composite verdict. Other entries (latency,
         * future dimensions) pass through unchanged. When no
         * bernoulli-pass-rate entry is present (a spec without a
         * functional criterion — currently unreachable but defensively
         * supported), the list is returned unchanged.
         */
        private static List<EvaluatedCriterion> substituteFunctionalVerdict(
                List<EvaluatedCriterion> evaluated, Verdict composite) {
            List<EvaluatedCriterion> out = new java.util.ArrayList<>(evaluated.size());
            for (EvaluatedCriterion ec : evaluated) {
                if ("bernoulli-pass-rate".equals(ec.result().criterionName())) {
                    CriterionResult substituted = new CriterionResult(
                            ec.result().criterionName(),
                            composite,
                            ec.result().explanation(),
                            ec.result().detail());
                    out.add(new EvaluatedCriterion(substituted, ec.role()));
                } else {
                    out.add(ec);
                }
            }
            return out;
        }

        /**
         * Build the engine-run summary from the captured
         * {@link SampleSummary}, the evaluated criteria, and the
         * matched-baseline source file. Confidence is lifted from the
         * first criterion whose detail map carries a "confidence" entry
         * (PassRate); falls back to 0.95.
         */
        private EngineRunSummary buildEngineSummary(
                SampleSummary<OT> s,
                List<EvaluatedCriterion> evaluated,
                Optional<String> baselineFilename) {
            double confidence = scanConfidence(evaluated);
            return new EngineRunSummary(
                    sampling.samples(),
                    s.total(),
                    s.successes(),
                    s.failures(),
                    s.elapsed().toMillis(),
                    s.tokensConsumed(),
                    s.failuresDropped(),
                    s.latencyResult(),
                    s.terminationReason(),
                    confidence,
                    baselineFilename,
                    s.passingLatencyResult());
        }

        private static double scanConfidence(List<EvaluatedCriterion> evaluated) {
            for (EvaluatedCriterion ec : evaluated) {
                Object v = ec.result().detail().get("confidence");
                if (v instanceof Number n) {
                    return n.doubleValue();
                }
            }
            return 0.95;
        }

        /**
         * Side channel for {@link #evaluate} to surface the matched
         * baseline's covariate profile back to {@link #conclude}.
         * Kept off {@link CriterionResult} because the matched-baseline
         * profile is a lookup-side concern, not a criterion-semantic
         * concern — every empirical criterion that resolves a
         * baseline for the same {@code (serviceContractId, factorsFingerprint)}
         * tuple sees the same profile, so the data belongs to the
         * run, not to any one criterion.
         */
        private static final class BaselineLookupCapture {
            CovariateProfile profile =
                    CovariateProfile.empty();
            Optional<String> sourceFile = Optional.empty();
            boolean expired = false;
        }

        private boolean anyEmpiricalCriterion() {
            for (Registered<OT> entry : registered) {
                if (entry.criterion().isEmpirical()) {
                    return true;
                }
            }
            return false;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private CriterionResult evaluate(
                Criterion<OT, ?> criterion,
                SampleSummary<OT> s,
                FactorBundle factorBundle,
                String serviceContractId,
                String testInputsIdentity,
                Optional<String> baselineInputsIdentity,
                java.util.Map<String, org.mavai.punit.api.criterion.CriterionPosture> criterionPostures,
                BaselineProvider provider,
                java.util.Set<String> warnings,
                BaselineLookupCapture capture) {
            TestIntent testIntent = this.intent;
            Criterion raw = criterion;
            Optional<? extends BaselineStatistics> resolved;
            if (criterion.isEmpirical()) {
                BaselineLookup<? extends BaselineStatistics> lookup = provider.baselineLookup(
                        serviceContractId, factorBundle, criterion.name(), criterion.statisticsType());
                resolved = lookup.selected();
                warnings.addAll(lookup.notes());
                capture.profile = lookup.baselineProfile();
                capture.sourceFile = lookup.sourceFile();
                capture.expired = lookup.expired();
            } else {
                resolved = Optional.empty();
            }
            EvaluationContext ctx = new EvaluationContext<OT, BaselineStatistics>() {
                @Override public SampleSummary<OT> summary() { return s; }
                @Override public Optional<BaselineStatistics> baseline() {
                    return (Optional<BaselineStatistics>) resolved;
                }
                @Override public FactorBundle factors() { return factorBundle; }
                @Override public String testInputsIdentity() { return testInputsIdentity; }
                @Override public Optional<String> baselineInputsIdentity() {
                    return baselineInputsIdentity;
                }
                @Override
                public java.util.Map<String, org.mavai.punit.api.criterion.CriterionPosture> criterionPostures() {
                    return criterionPostures;
                }
                @Override public TestIntent intent() { return testIntent; }
            };
            return (CriterionResult) raw.evaluate(ctx);
        }

        @Override public Optional<Duration> timeBudget() { return sampling.timeBudget(); }
        @Override public OptionalLong tokenBudget() { return sampling.tokenBudget(); }
        @Override public long tokenCharge() { return sampling.tokenCharge(); }
        @Override public BudgetExhaustionPolicy budgetPolicy() { return sampling.budgetPolicy(); }
        @Override public ExceptionPolicy exceptionPolicy() { return sampling.exceptionPolicy(); }
        @Override public int maxExampleFailures() { return sampling.maxExampleFailures(); }

        @Override
        public Optional<EarlyTerminationContext> earlyTermination() {
            if (earlyTerminationDisabled) {
                return Optional.empty();
            }
            for (Registered<OT> entry : registered) {
                if (entry.role() != CriterionRole.REQUIRED) {
                    continue;
                }
                OptionalDouble threshold = entry.criterion().earlyTerminationPassRate();
                if (threshold.isPresent()) {
                    double rate = threshold.getAsDouble();
                    int floor = new org.mavai.punit.statistics.BinomialProportionEstimator()
                            .minSamplesForNormalApproximation(rate);
                    return Optional.of(new EarlyTerminationContext(rate, floor));
                }
            }
            return Optional.empty();
        }
    }

    // ── Builder ──────────────────────────────────────────────────────

    // javai-ref: JVI-QRV7SYX — do not remove (resolves in javai-orchestrator)
    public static final class Builder<FT, IT, OT> {

        private final Sampling<FT, IT, OT> sampling;
        private final FT factors;
        private final List<Registered<OT>> registered = new ArrayList<>();
        private TestIntent intent = TestIntent.VERIFICATION;
        private boolean earlyTerminationDisabled = false;

        private Builder(Sampling<FT, IT, OT> sampling, FT factors) {
            this.sampling = sampling;
            this.factors = factors;
        }

        /** Register a criterion that contributes to the combined verdict. */
        public Builder<FT, IT, OT> criterion(Criterion<OT, ?> criterion) {
            Objects.requireNonNull(criterion, "criterion");
            registered.add(new Registered<>(criterion, CriterionRole.REQUIRED));
            return this;
        }

        /** Register a criterion whose result is attached but excluded from composition. */
        public Builder<FT, IT, OT> reportOnly(Criterion<OT, ?> criterion) {
            Objects.requireNonNull(criterion, "criterion");
            registered.add(new Registered<>(criterion, CriterionRole.REPORT_ONLY));
            return this;
        }

        /**
         * Declares the test's intent. Defaults to
         * {@link TestIntent#VERIFICATION}. Authors of sentinel-grade
         * smoke checks against external providers opt into
         * {@link TestIntent#SMOKE} explicitly.
         */
        public Builder<FT, IT, OT> intent(TestIntent intent) {
            this.intent = Objects.requireNonNull(intent, "intent");
            return this;
        }

        /**
         * Run every declared sample regardless of statistical
         * determination. Disables the framework's mid-run short-circuit
         * so the sample loop continues even after the verdict is
         * mathematically determined (threshold unreachable or
         * guaranteed).
         *
         * <p>Use when the full sample count is wanted for downstream
         * data (a follow-on baseline emission, a complete latency
         * distribution, exhaustive failure exemplars) and the
         * cost-saving short-circuit is not the right behaviour. The
         * resulting summary's {@code terminationReason} is
         * {@code COMPLETED} — no spurious {@code IMPOSSIBILITY} /
         * {@code SUCCESS_GUARANTEED} annotation.
         *
         * <p>Unrelated to the optimize loop's
         * {@code OptimizeBuilder.disableEarlyTermination()}, which
         * controls a heuristic no-improvement window on a different
         * builder.
         */
        public Builder<FT, IT, OT> disableEarlyTermination() {
            this.earlyTerminationDisabled = true;
            return this;
        }

        public ProbabilisticTest build() {
            autoInjectFromContract(sampling, factors, registered);
            return new ProbabilisticTest(new Internal<>(this));
        }
    }

    private record Registered<OT>(Criterion<OT, ?> criterion, CriterionRole role) {}

    /**
     * Populate {@code registered} from the contract's criteria postures,
     * additively per posture. The contract's acceptance posture is the
     * source of truth; an explicit test-builder {@code .criterion(...)}
     * suppresses derivation only for criteria of the same class as the
     * derived one, so a non-PassRate test-builder criterion (e.g.
     * {@code PercentileLatency}) does not block PassRate auto-injection
     * from a contract that declared a {@code .meeting(...)} or
     * {@code .empirical()} posture.
     *
     * <p>Derivation is delegated to the {@link SpecCriterionDeriver}
     * SPI — the implementation in {@code internal.engine.criteria}
     * maps postures to {@code PassRate} variants. The SPI indirection
     * is what keeps {@code api.spec} free of {@code internal} imports.
     */
    private static <FT, IT, OT> void autoInjectFromContract(
            Sampling<FT, IT, OT> sampling, FT factors, List<Registered<OT>> registered) {
        ServiceContract<FT, IT, OT> probe = sampling.serviceContractFactory().apply(factors);
        SpecCriterionDeriver deriver = SpecCriterionDeriver.lookup();
        for (org.mavai.punit.api.criterion.Criterion<OT> c : probe.effectiveCriteria()) {
            deriver.<OT>derive(c.posture()).ifPresent(sc -> {
                if (!alreadyRegistered(registered, sc.getClass())) {
                    registered.add(new Registered<>(sc, CriterionRole.REQUIRED));
                }
            });
        }
    }

    private static <OT> boolean alreadyRegistered(
            List<Registered<OT>> registered, Class<?> derivedType) {
        for (Registered<OT> r : registered) {
            if (derivedType.isInstance(r.criterion())) {
                return true;
            }
        }
        return false;
    }

    // ── Inline-sampling builder ─────────────────────────────────────

    /**
     * Inline-form builder. Accumulates sampling-level state alongside
     * the test-overlay state; synthesises a {@link Sampling} at
     * {@link #build()} time. Rejects empirical criteria at build time
     * with a diagnostic teaching the helper-extraction pattern.
     */
    // javai-ref: JVI-QRV7SYX — do not remove (resolves in javai-orchestrator)
    public static final class InlineBuilder<FT, IT, OT> {

        private final Function<FT, ServiceContract<FT, IT, OT>> serviceContractFactory;
        private final FT factors;
        private List<IT> inputs;
        private int samples = 1000;
        private Optional<Duration> timeBudget = Optional.empty();
        private OptionalLong tokenBudget = OptionalLong.empty();
        private long tokenCharge = 0L;
        private BudgetExhaustionPolicy budgetPolicy = BudgetExhaustionPolicy.FAIL;
        private ExceptionPolicy exceptionPolicy = ExceptionPolicy.ABORT_TEST;
        private int maxExampleFailures = 10;
        private final List<Registered<OT>> registered = new ArrayList<>();
        private TestIntent intent = TestIntent.VERIFICATION;
        private boolean earlyTerminationDisabled = false;

        private InlineBuilder(Function<FT, ServiceContract<FT, IT, OT>> serviceContractFactory, FT factors) {
            this.serviceContractFactory = serviceContractFactory;
            this.factors = factors;
        }

        public InlineBuilder<FT, IT, OT> inputs(List<IT> inputs) {
            Objects.requireNonNull(inputs, "inputs");
            if (inputs.isEmpty()) {
                throw new IllegalArgumentException("inputs must be non-empty");
            }
            this.inputs = List.copyOf(inputs);
            return this;
        }

        @SafeVarargs
        public final InlineBuilder<FT, IT, OT> inputs(IT... inputs) {
            return inputs(List.of(Objects.requireNonNull(inputs, "inputs")));
        }

        public InlineBuilder<FT, IT, OT> samples(int samples) {
            if (samples < 1) {
                throw new IllegalArgumentException("samples must be >= 1, got " + samples);
            }
            this.samples = samples;
            return this;
        }

        public InlineBuilder<FT, IT, OT> timeBudget(Duration budget) {
            this.timeBudget = Optional.of(Objects.requireNonNull(budget, "timeBudget"));
            return this;
        }

        public InlineBuilder<FT, IT, OT> tokenBudget(long tokens) {
            this.tokenBudget = OptionalLong.of(tokens);
            return this;
        }

        public InlineBuilder<FT, IT, OT> tokenCharge(long tokens) {
            this.tokenCharge = tokens;
            return this;
        }

        public InlineBuilder<FT, IT, OT> onBudgetExhausted(BudgetExhaustionPolicy policy) {
            this.budgetPolicy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        public InlineBuilder<FT, IT, OT> onException(ExceptionPolicy policy) {
            this.exceptionPolicy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        public InlineBuilder<FT, IT, OT> maxExampleFailures(int cap) {
            this.maxExampleFailures = cap;
            return this;
        }

        public InlineBuilder<FT, IT, OT> criterion(Criterion<OT, ?> criterion) {
            Objects.requireNonNull(criterion, "criterion");
            registered.add(new Registered<>(criterion, CriterionRole.REQUIRED));
            return this;
        }

        public InlineBuilder<FT, IT, OT> reportOnly(Criterion<OT, ?> criterion) {
            Objects.requireNonNull(criterion, "criterion");
            registered.add(new Registered<>(criterion, CriterionRole.REPORT_ONLY));
            return this;
        }

        public InlineBuilder<FT, IT, OT> intent(TestIntent intent) {
            this.intent = Objects.requireNonNull(intent, "intent");
            return this;
        }

        /**
         * Run every declared sample regardless of statistical
         * determination. Mirrors {@link Builder#disableEarlyTermination()}
         * on the inline builder.
         */
        public InlineBuilder<FT, IT, OT> disableEarlyTermination() {
            this.earlyTerminationDisabled = true;
            return this;
        }

        public ProbabilisticTest build() {
            // Empirical-criterion guard. The integrity guarantee for an
            // empirical comparison comes from sharing a Sampling value
            // with the paired measure; the inline form constructs a
            // fresh Sampling per spec and cannot provide that guarantee.
            for (Registered<OT> r : registered) {
                if (r.criterion().isEmpirical()) {
                    throw new IllegalStateException(
                            "An empirical criterion (" + r.criterion().name() + ") requires "
                                    + "the probabilistic test to share a Sampling with its "
                                    + "baseline measure. The inline ProbabilisticTest.testing("
                                    + "serviceContractFactory, factors) form cannot guarantee that "
                                    + "structural pairing.\n\n"
                                    + "Extract the sampling as a helper and use the "
                                    + "Sampling-bound entry point at both call sites:\n"
                                    + "    private Sampling<F, I, O> sampling(int samples) {\n"
                                    + "        return Sampling.of(serviceContractFactory, samples, inputs);\n"
                                    + "    }\n"
                                    + "    @PUnitExperiment Experiment baseline() {\n"
                                    + "        return Experiment.measuring(sampling(1000), factors).build();\n"
                                    + "    }\n"
                                    + "    @PUnitTest ProbabilisticTest meets() {\n"
                                    + "        return ProbabilisticTest.testing(sampling(100), factors)\n"
                                    + "                .criterion(PassRate.empirical())\n"
                                    + "                .build();\n"
                                    + "    }");
                }
            }
            if (inputs == null) {
                throw new IllegalStateException(
                        "inputs is required — call .inputs(...) before .build()");
            }
            Sampling.Builder<FT, IT, OT> sb = Sampling.<FT, IT, OT>builder()
                    .serviceContractFactory(serviceContractFactory)
                    .inputs(inputs)
                    .samples(samples);
            timeBudget.ifPresent(sb::timeBudget);
            tokenBudget.ifPresent(sb::tokenBudget);
            if (tokenCharge > 0L) {
                sb.tokenCharge(tokenCharge);
            }
            sb.onBudgetExhausted(budgetPolicy);
            sb.onException(exceptionPolicy);
            sb.maxExampleFailures(maxExampleFailures);
            Sampling<FT, IT, OT> sampling = sb.build();

            Builder<FT, IT, OT> delegate = new Builder<>(sampling, factors);
            delegate.intent(intent);
            if (earlyTerminationDisabled) {
                delegate.disableEarlyTermination();
            }
            for (Registered<OT> r : registered) {
                if (r.role() == CriterionRole.REQUIRED) {
                    delegate.criterion(r.criterion());
                } else {
                    delegate.reportOnly(r.criterion());
                }
            }
            return delegate.build();
        }
    }
}
