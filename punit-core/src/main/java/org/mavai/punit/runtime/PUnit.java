package org.mavai.punit.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.covariate.CovariateAlignment;
import org.mavai.punit.api.spec.Spec;
import org.mavai.punit.api.spec.TypedSpec;
import org.mavai.punit.internal.runtime.BaselineEmitter;
import org.mavai.punit.internal.runtime.BaselineProviderResolver;
import org.mavai.punit.internal.runtime.EmpiricalTestComposer;
import org.mavai.punit.internal.runtime.ExploreEmitter;
import org.mavai.punit.internal.runtime.OptimizeEmitter;
import org.mavai.punit.internal.runtime.TestIdentityResolver;
import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.NoFactors;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.api.spec.BaselineLookup;
import org.mavai.punit.api.spec.BaselineProvider;
import org.mavai.punit.api.spec.BaselineStatistics;
import org.mavai.punit.api.spec.Criterion;
import org.mavai.punit.api.spec.CriterionResult;
import org.mavai.punit.api.spec.CriterionRole;
import org.mavai.punit.api.spec.EmpiricalChecks;
import org.mavai.punit.api.spec.EngineResult;
import org.mavai.punit.api.spec.EngineRunSummary;
import org.mavai.punit.api.spec.EvaluatedCriterion;
import org.mavai.punit.api.spec.Experiment;
import org.mavai.punit.api.spec.FactorsStepper;
import org.mavai.punit.api.spec.FailureCount;
import org.mavai.punit.api.spec.FailureExemplar;
import org.mavai.punit.api.spec.MeasuredBaseline;
import org.mavai.punit.api.spec.NormativeJudgement;
import org.mavai.punit.api.spec.PerCriterionEvaluation;
import org.mavai.punit.api.spec.ProbabilisticTest;
import org.mavai.punit.api.spec.ProbabilisticTestResult;
import org.mavai.punit.api.spec.Scorer;
import org.mavai.punit.api.spec.UnsupportableJudgementException;
import org.mavai.punit.api.spec.Verdict;
import org.mavai.punit.api.covariate.Covariate;
import org.mavai.punit.api.covariate.CovariateProfile;
import org.mavai.punit.internal.engine.Engine;
import org.mavai.punit.internal.engine.baseline.ProfileBoundBaselineProvider;
import org.mavai.punit.internal.engine.covariate.CovariateResolver;
import org.mavai.punit.internal.engine.criteria.Feasibility;
import org.mavai.punit.internal.engine.criteria.PassRate;
import org.mavai.punit.internal.reporting.TransparentStatsRenderer;
import org.mavai.punit.statistics.NormativeJudgementEvaluator;
import org.mavai.punit.statistics.transparent.TransparentStatsConfig;
import org.mavai.punit.verdict.ProbabilisticTestVerdict;
import org.mavai.punit.verdict.RunMetadata;
import org.mavai.punit.internal.runtime.VerdictAdapter;
import org.mavai.punit.verdict.VerdictSink;
import org.mavai.punit.internal.reporting.VerdictSinkBus;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.TestAbortedException;

/**
 * The user-facing entry point for the typed-compositional API under
 * JUnit. Authors call {@link #testing}, {@link #measuring},
 * {@link #exploring}, and {@link #optimizing} from within their
 * {@link org.mavai.punit.api.ProbabilisticTest @ProbabilisticTest}
 * and {@link org.mavai.punit.api.Experiment @Experiment} methods.
 *
 * <p>Each factory returns a builder that wraps the corresponding
 * {@link Experiment}- or
 * {@link ProbabilisticTest}-builder
 * in {@code api package}, adding a terminal {@link MeasureBuilder#run}
 * (for experiments) or {@link TestBuilder#assertPasses} (for
 * probabilistic tests). Each terminal drives the spec through
 * {@link Engine} and translates the outcome:
 *
 * <ul>
 *   <li>Experiments produce artefacts (baseline files, exploration
 *       grids, optimization histories). {@code .run()} returns
 *       normally on success; engine-level defects propagate as
 *       runtime exceptions.</li>
 *   <li>Probabilistic tests produce verdicts. {@code .assertPasses()}
 *       returns normally on {@link Verdict#PASS}, throws
 *       {@link AssertionFailedError} on {@link Verdict#FAIL}, and
 *       throws {@link TestAbortedException} on
 *       {@link Verdict#INCONCLUSIVE} (configuration / environment
 *       drift, not service degradation).</li>
 * </ul>
 *
 * <p>A {@code .build()} terminal is also available on every builder
 * — used for the
 * {@link PassRate#empiricalFrom
 * empiricalFrom(supplier)} pattern, where a method returning a built
 * {@link Experiment} value supplies the baseline a probabilistic
 * test compares against.
 */
public final class PUnit {

    private PUnit() { }

    /**
     * A declarative run in progress: the surface {@link PUnit#declared()}
     * returns. The contract file carries the claim — criteria, thresholds,
     * provenance, intent; this builder carries only the invocation's
     * concerns: the budget and the bindings override.
 *
     * <p>Implemented by the punit-decl module and discovered via
     * {@link java.util.ServiceLoader}; punit-core defines the seam and
     * carries no reader machinery.
     */
    public interface Declared {

        /**
         * The run's sample budget. Resolution order for a declarative run:
         * the per-contract system property
         * {@code -Dpunit.samples.<contract-name>=N}, then this call, then
         * the derived minimum the declared bars require.
         */
        Declared samples(int samples);

        /**
         * The bindings class to resolve service names against — the
         * compile-checked override of the conventional {@code MavaiBindings}
         * class in the test's package.
         */
        Declared bindings(Class<?> bindingsClass);

        /**
         * An explicit service-definition file, overriding discovery
         * (the conventional {@code mavai-services.yaml} beside the
         * contract files, then the project root).
         */
        Declared services(java.nio.file.Path servicesFile);

        /**
         * The target power for a risk-driven run (default 0.80) — the
         * {@code --power} flag's equivalent; property channel
         * {@code -Dpunit.power.<contract-name>=P}. One sizing source per
         * invocation: contradicts {@link #samples(int)}.
         */
        Declared atPower(double power);

        /**
         * Overrides the sole empirical criterion's risk claim — the bare
         * {@code --tolerate} flag's equivalent; legal only when exactly one
         * empirical criterion exists. Property channel
         * {@code -Dpunit.tolerate.<contract-name>=RATE}. One sizing source
         * per invocation: contradicts {@link #samples(int)}.
         */
        Declared tolerating(double rate);

        /**
         * Overrides a named criterion's risk claim — the
         * {@code --tolerate CRITERION=RATE} form; the criterion must exist
         * and be empirical. Property channel
         * {@code -Dpunit.tolerate.<contract-name>.<criterion>=RATE}.
         */
        Declared tolerating(String criterion, double rate);

        /**
         * Runs the declared contract as a probabilistic test and translates
         * the verdict through punit's standard opentest4j mapping: returns
         * normally on PASS, throws {@link org.opentest4j.AssertionFailedError}
         * on FAIL. Declarative-layer refusals (a malformed file, an
         * unresolvable name, an infeasible design) travel on the defect
         * channel as {@link IllegalStateException} — a test error, never a
         * FAIL — and always before any sample runs.
         */
        void assertPasses();

        /**
         * Runs the declared contract as a measure experiment: every
         * criterion recorded, the standard baseline artefact always
         * persisted, no verdict rendered — recording is the
         * experiment's main act. A measurement's budget is an
         * experimental-design decision: an explicit sample count is
         * required (1,000 is baseline-grade; a smaller deliberate
         * budget is legitimate — an empirical bar derived from a
         * smaller baseline widens honestly).
         */
        void run();

        /**
         * The measure experiment with the developer's opt-in assertion:
         * identical recording and persistence-before-assertion, then
         * asserts each declared bar was met.
         */
        void assertMeets();
    }

    /**
     * The service-provider seam between {@link PUnit#declared()} and the
     * declarative reader. punit-decl provides the implementation via
     * {@link java.util.ServiceLoader} (the {@code VerdictSink} precedent);
     * punit-core only defines the seam, so a project that never uses
     * declarative authoring carries none of the reader's machinery.
     */
    public interface DeclarativeFrontEnd {

        /**
         * Opens a declarative run for the calling test method.
         *
         * @param caller the test class whose resource package holds the
         *     contract files
         * @param methodName the calling method's name — kebab-cased into
         *     the contract name unless {@code explicitName} overrides
         * @param explicitName the explicit contract name, or {@code null}
         *     to resolve from the method name
         */
        Declared open(Class<?> caller, String methodName, String explicitName);
    }

    // ── Declarative entry ───────────────────────────────────────────

    /**
     * Opens a declarative run: the contract is resolved by its declared
     * {@code contract:} name — the calling method's name, kebab-cased —
     * against the contract files in the calling test class's resource
     * package. Requires the punit-decl module on the runtime classpath;
     * discovered via {@link java.util.ServiceLoader}.
     */
    public static Declared declared() {
        return declared(null);
    }

    /**
     * Opens a declarative run for an explicitly named contract —
     * the override for when the method name and the {@code contract:}
     * key deliberately differ.
     */
    public static Declared declared(String contractName) {
        StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        StackWalker.StackFrame frame = walker.walk(frames -> frames
                .filter(f -> !f.getClassName().equals(PUnit.class.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "PUnit.declared() could not identify its calling method")));
        DeclarativeFrontEnd frontEnd = ServiceLoader.load(DeclarativeFrontEnd.class)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "PUnit.declared() requires the punit-decl module on the classpath — "
                                + "add the org.mavai:punit-decl dependency"));
        return frontEnd.open(frame.getDeclaringClass(), frame.getMethodName(), contractName);
    }

    // ── Sampling-bound factories ────────────────────────────────────

    /** Compose a contractual probabilistic test against a shared sampling. */
    // mavai-ref: JVI-SVDWG0G — do not remove (resolves in mavai-orchestrator)
    public static <FT, IT, OT> TestBuilder<FT, IT, OT> testing(
            Sampling<FT, IT, OT> sampling, FT factors) {
        return new TestBuilder<>(
                ProbabilisticTest.testing(sampling, factors),
                sampling, factors);
    }

    /**
     * Factor-less form of {@link #testing(Sampling, Object)} — for
     * service contracts whose behaviour does not depend on factor values.
     * Equivalent to {@code testing(sampling, NoFactors.INSTANCE)}.
     * The sampling's {@code FT} must be {@link NoFactors}.
     */
    public static <IT, OT> TestBuilder<NoFactors, IT, OT> testing(
            Sampling<NoFactors, IT, OT> sampling) {
        return testing(sampling, NoFactors.INSTANCE);
    }

    /**
     * Compose an empirical probabilistic test that derives its sampling
     * and factors from a baseline {@link Experiment} supplier. The
     * author specifies only the (typically smaller) sample count and
     * the criterion; identity, factors, and inputs follow from the
     * baseline.
     */
    public static EmpiricalTestBuilder testing(Supplier<Experiment> baselineSupplier) {
        return new EmpiricalTestBuilder(Objects.requireNonNull(baselineSupplier, "baselineSupplier"));
    }

    /** Compose a measure experiment over a sampling and bound factors. */
    public static <FT, IT, OT> MeasureBuilder<FT, IT, OT> measuring(
            Sampling<FT, IT, OT> sampling, FT factors) {
        return new MeasureBuilder<>(Experiment.measuring(sampling, factors));
    }

    /**
     * Factor-less form of {@link #measuring(Sampling, Object)} — for
     * service contracts whose behaviour does not depend on factor values.
     * Equivalent to {@code measuring(sampling, NoFactors.INSTANCE)}.
     * The sampling's {@code FT} must be {@link NoFactors}.
     */
    public static <IT, OT> MeasureBuilder<NoFactors, IT, OT> measuring(
            Sampling<NoFactors, IT, OT> sampling) {
        return measuring(sampling, NoFactors.INSTANCE);
    }

    /** Compose an explore experiment over a sampling; supply the grid via the builder. */
    public static <FT, IT, OT> ExploreBuilder<FT, IT, OT> exploring(
            Sampling<FT, IT, OT> sampling) {
        return new ExploreBuilder<>(Experiment.exploring(sampling));
    }

    /**
     * Compose an optimize experiment over a sampling; supply initial
     * factors, the stepper, and the scorer/direction via the builder.
     */
    public static <FT, IT, OT> OptimizeBuilder<FT, IT, OT> optimizing(
            Sampling<FT, IT, OT> sampling) {
        return new OptimizeBuilder<>(Experiment.optimizing(sampling));
    }

    // ── Internal: drive an experiment / test ────────────────────────

    private static EngineResult drive(Spec spec) {
        BaselineProvider provider = profileBoundProvider(spec);
        return new Engine(provider).run(spec);
    }

    /**
     * Drive a MEASURE experiment, persist its baseline artefact, and
     * render its normative judgements (when the contract declares
     * normative criteria). Returns the emission summary so a gating
     * terminal can assert on the judgements — strictly after the
     * artefact is on disk.
     */
    private static MeasuredBaseline driveAndEmit(Experiment experiment) {
        drive(experiment);
        MeasuredBaseline emission =
                BaselineEmitter.emit(experiment, BaselineProviderResolver.resolveDir());
        if (!emission.judgementRendering().isEmpty()) {
            System.out.println(emission.judgementRendering());
        }
        return emission;
    }

    private static void driveAndEmitExplore(Experiment experiment) {
        drive(experiment);
        ExploreEmitter.emit(experiment,
                org.mavai.punit.internal.engine.explore.ExplorationsResolver.resolveDir());
    }

    private static void driveAndEmitOptimize(Experiment experiment) {
        drive(experiment);
        OptimizeEmitter.emit(experiment,
                org.mavai.punit.internal.engine.optimize.OptimizationsResolver.resolveDir());
    }

    /**
     * Generic-binding helper for the baseline-existence probe. The
     * caller has a {@code Criterion<?, ?>} (statistics type wildcarded);
     * this method binds {@code S} via the criterion's
     * {@link Criterion#statisticsType() statisticsType} so the typed
     * {@link BaselineProvider#baselineLookup} call compiles.
     *
     * <p>{@code baselineLookup} (not {@code baselineFor}) is used so
     * the lookup's rejection notes flow through to the synthesised
     * result's warnings. {@link #translate} reads those notes to
     * distinguish "no candidates existed" (legitimate INCONCLUSIVE
     * → JUnit-aborted) from "candidates existed but all rejected"
     * (misconfiguration → JUnit-failed).
     */
    private static <S extends BaselineStatistics> BaselineLookup<S> probeBaseline(
            BaselineProvider provider,
            String serviceContractId,
            FactorBundle bundle,
            Criterion<?, S> criterion,
            CovariateProfile profile,
            List<Covariate> declarations) {
        return provider.baselineLookup(
                serviceContractId, bundle, criterion.name(),
                criterion.statisticsType(), profile, declarations);
    }

    /**
     * Wraps {@link BaselineProviderResolver}'s provider in a
     * profile-bound decorator carrying the run's covariate profile,
     * so the engine and pre-flight feasibility see covariate-aware
     * baseline lookups. {@link ProfileBoundBaselineProvider#bind}
     * returns the wrapped delegate unchanged when the service contract
     * declared no covariates, so non-covariate tests pay no cost
     * here.
     */
    private static BaselineProvider profileBoundProvider(
            Spec spec) {
        BaselineProvider provider = BaselineProviderResolver.resolve();
        return spec.dispatch(new Spec.Dispatcher<>() {
            @Override
            public <FT, IT, OT> BaselineProvider apply(
                    TypedSpec<FT, IT, OT> typed) {
                var configs = typed.configurations();
                if (!configs.hasNext()) {
                    return provider;
                }
                FT factors = configs.next().factors();
                ServiceContract<FT, IT, OT> serviceContract = typed.serviceContractFactory().apply(factors);
                List<Covariate> declarations = serviceContract.covariates();
                if (declarations.isEmpty()) {
                    return provider;
                }
                CovariateProfile profile = CovariateResolver.defaults()
                        .resolve(declarations, serviceContract.customCovariateResolvers());
                return ProfileBoundBaselineProvider.bind(provider, profile, declarations);
            }
        });
    }

    /**
     * Adapts the typed result to a {@link ProbabilisticTestVerdict} and
     * dispatches it through {@link VerdictSinkBus}. Identity comes
     * from {@link TestIdentityResolver} (stack walk for the nearest
     * {@link org.mavai.punit.api.ProbabilisticTest @ProbabilisticTest}
     * frame); falls back to {@code (serviceContractId, serviceContractId)} when the
     * resolver finds no annotated frame (e.g. hand-driven tests).
     *
     * <p>Installs the default sink — discovered via {@code ServiceLoader}
     * over {@link VerdictSink} — on first call. With {@code punit-report}
     * on the classpath this resolves to its XML sink; with no provider
     * present (e.g. a sentinel binary that ships no reporter), no
     * default sink is installed and verdicts dispatch only to whatever
     * sinks the caller has registered explicitly. Idempotent; tests
     * can override via {@link VerdictSinkBus#replaceAll}.
     */
    private static void emitVerdict(ProbabilisticTestResult result, String fallbackServiceContractId) {
        ServiceLoader.load(VerdictSink.class)
                .findFirst()
                .ifPresent(sink -> VerdictSinkBus.installDefaultSink(() -> sink));
        RunMetadata meta = TestIdentityResolver.resolve()
                .orElseGet(() -> RunMetadata.of(
                        fallbackServiceContractId, fallbackServiceContractId, fallbackServiceContractId));
        ProbabilisticTestVerdict verdict = VerdictAdapter.adapt(result, meta);
        VerdictSinkBus.dispatch(verdict);
    }

    static void translate(ProbabilisticTestResult result, String serviceContractId) {
        Verdict verdict = result.verdict();
        if (verdict == Verdict.PASS) {
            return;
        }
        String message = formatMessage(result);
        if (verdict == Verdict.INCONCLUSIVE) {
            // INCONCLUSIVE diagnostics are echoed to stderr regardless
            // of which JUnit-side throwable the trichotomy below
            // selects — the abort exception's message reaches the IDE
            // pane but not necessarily the build console (Gradle's
            // default text reporter only surfaces the throwable's
            // message for FAIL-style outcomes, leaving aborts silent).
            // Mirrors the PASS-with-warnings stderr emission so the
            // operator's view of an INCONCLUSIVE run is symmetric with
            // PASS and FAIL.
            emitInconclusiveDiagnostics(result, serviceContractId, message);
            // The covariate-alignment contract has three INCONCLUSIVE
            // sub-cases that we resolve here — see the project's
            // covariate-misalignment trichotomy:
            //
            //   1. No candidates considered (no baseline files yet)
            //      — legitimate skip; the workflow is measure first,
            //      then test. JUnit-aborted (TestAbortedException).
            //   2. Candidates considered but all rejected (CONFIGURATION
            //      mismatch on every one, or zero-overlap)
            //      — misconfiguration: the user is asking for a
            //      reference that doesn't exist for this configuration.
            //      JUnit-failed (AssertionFailedError).
            //   3. Candidate matched (possibly partial fallback)
            //      — verdict is whatever the criterion produced; not
            //      this branch.
            //
            // Detection: rejection notes on the result distinguish (2)
            // from (1). BaselineSelector emits "rejected {filename} — …"
            // for each candidate it couldn't select; their presence
            // means candidates existed.
            if (candidatesWereRejected(result)) {
                throw new AssertionFailedError(message);
            }
            throw new TestAbortedException(message);
        }
        throw new AssertionFailedError(message);
    }

    private static void emitInconclusiveDiagnostics(
            ProbabilisticTestResult result, String serviceContractId, String message) {
        String header = serviceContractId == null || serviceContractId.isBlank()
                ? "[PUNIT-INCONCLUSIVE]"
                : "[PUNIT-INCONCLUSIVE] " + serviceContractId;
        System.err.println(header);
        System.err.println(message);
    }

    private static boolean candidatesWereRejected(ProbabilisticTestResult result) {
        for (String warning : result.warnings()) {
            if (warning.startsWith("rejected ")) {
                return true;
            }
        }
        return false;
    }

    // Package-private for direct unit testing; not part of the public API.
    static String formatMessage(ProbabilisticTestResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(result.verdict());
        List<EvaluatedCriterion> evaluated = result.criterionResults();
        if (!evaluated.isEmpty()) {
            sb.append('\n');
            for (EvaluatedCriterion entry : evaluated) {
                CriterionResult cr = entry.result();
                sb.append("  [").append(entry.role()).append("] ")
                        .append(cr.criterionName()).append(" → ")
                        .append(cr.verdict()).append(": ")
                        .append(cr.explanation()).append('\n');
            }
        }
        // Misalignment / fallback notes from baseline selection — appear
        // here so an INCONCLUSIVE verdict explains *why* the baseline
        // didn't match (CONFIGURATION mismatch, partial match, fallback).
        if (!result.warnings().isEmpty()) {
            sb.append('\n');
            for (String warning : result.warnings()) {
                sb.append("  ! ").append(warning).append('\n');
            }
        }
        // Covariate alignment — the conditions under which the test
        // ran, paired with the matched baseline's covariates. Useful
        // diagnostic context on FAIL / INCONCLUSIVE: the author
        // should immediately see which environment was being tested
        // and how it differed from the baseline (when at all).
        var alignment = result.covariates();
        if (!alignment.observed().isEmpty() || !alignment.baseline().isEmpty()) {
            sb.append('\n');
            if (!alignment.observed().isEmpty()) {
                sb.append("  Observed covariates: ")
                        .append(formatProfile(alignment.observed())).append('\n');
            }
            if (!alignment.baseline().isEmpty()) {
                sb.append("  Baseline covariates: ")
                        .append(formatProfile(alignment.baseline())).append('\n');
            }
            if (!alignment.mismatches().isEmpty()) {
                sb.append("  Misaligned: ");
                boolean first = true;
                for (var m : alignment.mismatches()) {
                    if (!first) sb.append(", ");
                    sb.append(m.covariateKey())
                            .append(" (observed=")
                            .append(m.observed() == null ? "<absent>" : m.observed())
                            .append(", baseline=")
                            .append(m.baseline() == null ? "<absent>" : m.baseline())
                            .append(')');
                    first = false;
                }
                sb.append('\n');
            }
        }
        // Per-postcondition failure breakdown — when the contract has
        // clauses and any of them tripped, surface the histogram so
        // the developer sees which clause failed, how many times, and
        // a small bounded set of input/reason exemplars. This is the
        // diagnostic that turns "the test failed" into "this specific
        // clause failed because the LLM produced X for input Y."
        if (!result.failuresByPostcondition().isEmpty()) {
            sb.append('\n').append("  Postcondition failures:").append('\n');
            // Sort clauses by descending count so the most-common
            // failure mode appears first.
            var ordered = result.failuresByPostcondition().entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue().count(), a.getValue().count()))
                    .toList();
            for (var entry : ordered) {
                FailureCount bucket = entry.getValue();
                sb.append("    - \"").append(entry.getKey()).append("\" → ")
                        .append(bucket.count()).append(" failures").append('\n');
                int shown = 0;
                for (FailureExemplar ex : bucket.exemplars()) {
                    if (shown == 2) break;   // cap shown exemplars at 2 per clause
                    sb.append("        e.g. \"").append(ex.input()).append("\" → ")
                            .append(ex.reason()).append('\n');
                    shown++;
                }
            }
        }
        // Author-supplied audit pointer to the document the threshold
        // derives from — an SLA paragraph, an SLO contract, an
        // internal policy reference. Emitted on every verdict so
        // anyone reading the output can trace the threshold back to
        // its source.
        result.contractRef().ifPresent(ref ->
                sb.append('\n').append("  Contract: ").append(ref).append('\n'));
        return sb.toString().trim();
    }

    private static String formatProfile(CovariateProfile profile) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (var e : profile.values().entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.toString();
    }

    // ── Wrapper builders ────────────────────────────────────────────

    /** Wrapper around {@link Experiment.MeasureBuilder} adding {@link #run}. */
    // mavai-ref: JVI-315MNJX — do not remove (resolves in mavai-orchestrator)
    public static final class MeasureBuilder<FT, IT, OT> {
        private final Experiment.MeasureBuilder<FT, IT, OT> delegate;

        MeasureBuilder(Experiment.MeasureBuilder<FT, IT, OT> delegate) {
            this.delegate = delegate;
        }

        public MeasureBuilder<FT, IT, OT> experimentId(String id) {
            delegate.experimentId(id);
            return this;
        }

        public MeasureBuilder<FT, IT, OT> expiresInDays(int days) {
            delegate.expiresInDays(days);
            return this;
        }

        public Experiment build() {
            return delegate.build();
        }

        /**
         * The neutral terminal. Runs the measure, persists the
         * baseline artefact (normative-judgement markers included),
         * and renders any normative judgements prominently in the
         * experiment's output — but never fails on a failed
         * judgement. A measure characterises; whether a stipulation
         * in force at measure time was cleared is reported, not
         * enforced. Use {@link #assertMeets()} to make the
         * stipulations binding.
         */
        public void run() {
            // Measure runs and emits a baseline file when a baseline directory
            // is configured (system property or project convention). The
            // emission is silent when no directory resolves — the run itself
            // succeeded; the artefact just has nowhere to land.
            driveAndEmit(build());
        }

        /**
         * The gating terminal — normative judgement at experiment
         * time, made binding. Mutually exclusive alternative to
         * {@link #run()}: same run, same persistence, then asserts.
         * Mirrors {@link TestBuilder#assertPasses()}'s fused
         * run-and-assert convention — asserting terminals imply
         * running.
         *
         * <p>Judgement-to-outcome mapping (the same opentest4j
         * mapping {@code assertPasses()} uses):
         *
         * <ul>
         *   <li>every normative criterion met — returns normally;</li>
         *   <li>any normative criterion failed — throws
         *       {@link AssertionFailedError};</li>
         *   <li>any judgement unsupportable at this sample size —
         *       throws {@link UnsupportableJudgementException} (a
         *       {@link TestAbortedException}, so the harness aborts
         *       rather than fails), stating the feasible minimum
         *       sample count.</li>
         * </ul>
         *
         * <p>Persistence strictly precedes assertion: the baseline
         * artefact is on disk before any throw — a failed stipulation
         * never costs the baseline. A failed judgement states the
         * run's relation to the stipulation, not a claim about the
         * service's validity; opting into this terminal is what makes
         * that relation binding for the host harness.
         *
         * @throws IllegalStateException when the contract declares no
         *         normative criteria — there is nothing to assert;
         *         use {@link #run()}. Thrown before any sampling.
         */
        public void assertMeets() {
            Experiment experiment = build();
            requireNormativeCriteria(experiment);
            MeasuredBaseline emission = driveAndEmit(experiment);
            translateJudgements(emission);
        }

        /**
         * Configuration-defect gate for {@link #assertMeets()}: a
         * contract with no normative (stipulated pass-rate) criteria
         * has nothing to assert. Checked before sampling so the
         * defect surfaces without spending the run's samples.
         */
        private static void requireNormativeCriteria(Experiment experiment) {
            boolean anyNormative = experiment.dispatch(new Spec.Dispatcher<Boolean>() {
                @Override
                public <F, I, O> Boolean apply(TypedSpec<F, I, O> typed) {
                    var configs = typed.configurations();
                    if (!configs.hasNext()) {
                        return false;
                    }
                    F factors = configs.next().factors();
                    ServiceContract<F, I, O> serviceContract =
                            typed.serviceContractFactory().apply(factors);
                    for (org.mavai.punit.api.criterion.Criterion<O> criterion
                            : serviceContract.effectiveCriteria()) {
                        if (criterion.posture().kind()
                                == org.mavai.punit.api.criterion.CriterionPosture
                                        .Kind.STATISTICAL_CONTRACTUAL) {
                            return true;
                        }
                    }
                    return false;
                }
            });
            if (!anyNormative) {
                throw new IllegalStateException("""
                        assertMeets() requires at least one normative criterion — \
                        a stipulated pass rate declared via meeting().passRate(...). \
                        This contract declares none, so there is nothing to \
                        assert; use run() to characterise without gating.""");
            }
        }

        /**
         * Translate the run's normative judgements into the host
         * harness's outcome. Failed dominates unsupportable: when both
         * are present the run is a failure, and the message carries
         * the unsupportable criteria as context.
         */
        private static void translateJudgements(MeasuredBaseline emission) {
            List<NormativeJudgement> failed = new ArrayList<>();
            List<NormativeJudgement> unsupportable = new ArrayList<>();
            for (NormativeJudgement judgement : emission.normativeJudgements()) {
                switch (judgement.judgement().state()) {
                    case FAILED -> failed.add(judgement);
                    case UNSUPPORTABLE -> unsupportable.add(judgement);
                    case MET -> { /* nothing to translate */ }
                }
            }
            if (!failed.isEmpty()) {
                throw new AssertionFailedError(
                        judgementMessage(emission, failed, unsupportable));
            }
            if (!unsupportable.isEmpty()) {
                throw new UnsupportableJudgementException(
                        judgementMessage(emission, failed, unsupportable));
            }
        }

        private static String judgementMessage(
                MeasuredBaseline emission,
                List<NormativeJudgement> failed,
                List<NormativeJudgement> unsupportable) {
            StringBuilder sb = new StringBuilder(String.format(
                    "%s: %d samples",
                    emission.serviceContractId(), emission.sampleCount()));
            failed.forEach(judgement -> sb.append(failedLine(judgement)));
            unsupportable.forEach(judgement ->
                    sb.append(unsupportableLine(judgement, emission.sampleCount())));
            sb.append(String.format(
                    "%n  baseline persisted before this assertion: %s",
                    emission.filename()));
            return sb.toString();
        }

        private static String failedLine(NormativeJudgement judgement) {
            NormativeJudgementEvaluator.Judgement j = judgement.judgement();
            return String.format(
                    "%n  criterion \"%s\" did not clear its stipulated %s%s: "
                            + "observed %s, lower bound %s at confidence %s",
                    judgement.criterionId(), j.stipulatedThreshold(),
                    stipulationSource(judgement),
                    j.observedRate(), j.lowerBound(), j.confidence());
        }

        private static String unsupportableLine(
                NormativeJudgement judgement, int sampleCount) {
            NormativeJudgementEvaluator.Judgement j = judgement.judgement();
            return String.format(
                    "%n  criterion \"%s\" is unsupportable at %d samples: "
                            + "judging the stipulated %s at confidence %s "
                            + "requires at least %d samples",
                    judgement.criterionId(), sampleCount,
                    j.stipulatedThreshold(), j.confidence(),
                    j.feasibleMinimumSamples());
        }

        private static String stipulationSource(NormativeJudgement judgement) {
            return judgement.stipulationRef()
                    .map(ref -> " (" + ref + ")")
                    .orElse("");
        }
    }

    /** Wrapper around {@link Experiment.ExploreBuilder} adding {@link #run}. */
    // mavai-ref: JVI-HGF78G* — do not remove (resolves in mavai-orchestrator)
    public static final class ExploreBuilder<FT, IT, OT> {
        private final Experiment.ExploreBuilder<FT, IT, OT> delegate;

        ExploreBuilder(Experiment.ExploreBuilder<FT, IT, OT> delegate) {
            this.delegate = delegate;
        }

        public ExploreBuilder<FT, IT, OT> grid(List<FT> grid) {
            delegate.grid(grid);
            return this;
        }

        @SafeVarargs
        public final ExploreBuilder<FT, IT, OT> grid(FT... grid) {
            delegate.grid(grid);
            return this;
        }

        public ExploreBuilder<FT, IT, OT> experimentId(String id) {
            delegate.experimentId(id);
            return this;
        }

        public Experiment build() {
            return delegate.build();
        }

        public void run() {
            driveAndEmitExplore(build());
        }
    }

    /** Wrapper around {@link Experiment.OptimizeBuilder} adding {@link #run}. */
    // mavai-ref: JVI-PS5XC2C — do not remove (resolves in mavai-orchestrator)
    public static final class OptimizeBuilder<FT, IT, OT> {
        private final Experiment.OptimizeBuilder<FT, IT, OT> delegate;

        OptimizeBuilder(Experiment.OptimizeBuilder<FT, IT, OT> delegate) {
            this.delegate = delegate;
        }

        public OptimizeBuilder<FT, IT, OT> initialFactors(FT factors) {
            delegate.initialFactors(factors);
            return this;
        }

        public OptimizeBuilder<FT, IT, OT> stepper(FactorsStepper<FT> s) {
            delegate.stepper(s);
            return this;
        }

        public OptimizeBuilder<FT, IT, OT> maximize(Scorer scorer) {
            delegate.maximize(scorer);
            return this;
        }

        public OptimizeBuilder<FT, IT, OT> minimize(Scorer scorer) {
            delegate.minimize(scorer);
            return this;
        }

        public OptimizeBuilder<FT, IT, OT> maxIterations(int n) {
            delegate.maxIterations(n);
            return this;
        }

        public OptimizeBuilder<FT, IT, OT> noImprovementWindow(int n) {
            delegate.noImprovementWindow(n);
            return this;
        }

        /** See {@link Experiment.OptimizeBuilder#disableEarlyTermination()}. */
        public OptimizeBuilder<FT, IT, OT> disableEarlyTermination() {
            delegate.disableEarlyTermination();
            return this;
        }

        public OptimizeBuilder<FT, IT, OT> experimentId(String id) {
            delegate.experimentId(id);
            return this;
        }

        public Experiment build() {
            return delegate.build();
        }

        public void run() {
            driveAndEmitOptimize(build());
        }
    }

    /** Wrapper around {@link ProbabilisticTest.Builder} adding {@link #assertPasses}. */
    public static final class TestBuilder<FT, IT, OT> {
        private final ProbabilisticTest.Builder<FT, IT, OT> delegate;
        private final Sampling<FT, IT, OT> sampling;
        private final FT factors;
        private final List<Criterion<OT, ?>> requiredCriteria = new ArrayList<>();
        private TestIntent intent = TestIntent.VERIFICATION;
        private Boolean transparentStatsOverride;

        TestBuilder(ProbabilisticTest.Builder<FT, IT, OT> delegate,
                    Sampling<FT, IT, OT> sampling, FT factors) {
            this.delegate = delegate;
            this.sampling = sampling;
            this.factors = factors;
        }

        public TestBuilder<FT, IT, OT> criterion(Criterion<OT, ?> criterion) {
            delegate.criterion(criterion);
            requiredCriteria.add(criterion);
            return this;
        }

        public TestBuilder<FT, IT, OT> reportOnly(Criterion<OT, ?> criterion) {
            delegate.reportOnly(criterion);
            // REPORT_ONLY criteria don't gate the verdict — feasibility doesn't
            // apply, so we don't capture them here.
            return this;
        }

        public TestBuilder<FT, IT, OT> intent(TestIntent intent) {
            this.intent = Objects.requireNonNull(intent, "intent");
            delegate.intent(intent);
            return this;
        }

        /**
         * Enables verbose statistical reporting on every verdict —
         * including PASS — with the framework's hypothesis-test
         * framing, observed-data section, threshold provenance, and
         * inference reasoning. Output goes to stderr alongside the
         * JUnit assertion message; visible in IDE test consoles,
         * surefire reports, and CI logs.
         *
         * <p>Resolution precedence (highest first):
         * <ol>
         *   <li>This builder method.</li>
         *   <li>System property {@code punit.stats.transparent}.</li>
         *   <li>Environment variable {@code PUNIT_STATS_TRANSPARENT}.</li>
         *   <li>Default: off.</li>
         * </ol>
         *
         * <p>Service contract: audit / compliance documentation where the
         * statistical reasoning behind a passing verdict has to be
         * shown, not just inferred from the absence of a failure.
         */
        public TestBuilder<FT, IT, OT> transparentStats() {
            this.transparentStatsOverride = Boolean.TRUE;
            return this;
        }

        /** Explicit-boolean variant of {@link #transparentStats()}. */
        public TestBuilder<FT, IT, OT> transparentStats(boolean enabled) {
            this.transparentStatsOverride = enabled;
            return this;
        }

        public ProbabilisticTest build() {
            return delegate.build();
        }

        public void assertPasses() {
            ProbabilisticTest spec = build();
            ServiceContract<FT, IT, OT> serviceContract = sampling.serviceContractFactory().apply(factors);
            // When the author registered no explicit .criterion(...) the
            // framework auto-injects from the contract's posture during
            // build(). The pre-flight gates below iterate
            // requiredCriteria; populate it from the contract's
            // auto-derived list so the gates fire even with no
            // test-builder calls.
            if (requiredCriteria.isEmpty()) {
                org.mavai.punit.api.spec.SpecCriterionDeriver deriver =
                        org.mavai.punit.api.spec.SpecCriterionDeriver.lookup();
                for (org.mavai.punit.api.criterion.Criterion<OT> c : serviceContract.effectiveCriteria()) {
                    deriver.<OT>derive(c.posture()).ifPresent(requiredCriteria::add);
                }
            }
            String serviceContractId = serviceContract.id();
            CovariateProfile observed = resolveCovariates(serviceContract);
            BaselineProvider provider = boundProvider(serviceContract, observed);

            // Existence probe runs before sampling: when any required
            // empirical criterion has no resolvable baseline the verdict
            // is structurally guaranteed to be INCONCLUSIVE-no-baseline,
            // and sampling cannot influence the outcome — every sample
            // would just be charged to the user's account before the
            // criterion's evaluate() discovered the missing baseline.
            // Short-circuit through the same emitVerdict / translate
            // pipeline the post-sampling path uses; the operator-visible
            // diagnostic is byte-equivalent modulo samplesExecuted = 0.
            Optional<ProbabilisticTestResult> shortCircuit =
                    baselineExistencePreflight(serviceContract, observed, provider);
            if (shortCircuit.isPresent()) {
                ProbabilisticTestResult synthesised = shortCircuit.get();
                maybeRenderTransparentStats(synthesised);
                emitVerdict(synthesised, serviceContractId);
                translate(synthesised, serviceContractId);
                return;
            }

            List<String> warnings = preflightFeasibility(serviceContract, provider);
            EngineResult result = drive(spec);
            if (!(result instanceof ProbabilisticTestResult typed)) {
                throw new IllegalStateException(
                        "Engine produced unexpected result type: " + result.getClass().getName());
            }
            warnings.forEach(System.err::println);
            // Stamp the run's observed covariate profile onto the result
            // alongside the baseline profile conclude collected. The
            // structured CovariateAlignment flows downstream — to the
            // verbose renderer here, and to HTML / XML / JSON sinks.
            ProbabilisticTestResult stamped = typed.withCovariates(
                    CovariateAlignment.compute(
                            observed, typed.covariates().baseline()));
            maybeRenderTransparentStats(stamped);
            emitVerdict(stamped, serviceContractId);
            translate(stamped, serviceContractId);
        }

        private void maybeRenderTransparentStats(ProbabilisticTestResult result) {
            if (!TransparentStatsConfig.resolve(transparentStatsOverride).enabled()) {
                return;
            }
            String testIdentity = sampling.serviceContractFactory().apply(factors).id();
            System.err.println(TransparentStatsRenderer.render(testIdentity, result));
        }

        private CovariateProfile resolveCovariates(ServiceContract<FT, IT, OT> serviceContract) {
            List<Covariate> declarations = serviceContract.covariates();
            if (declarations.isEmpty()) {
                return CovariateProfile.empty();
            }
            return CovariateResolver.defaults()
                    .resolve(declarations, serviceContract.customCovariateResolvers());
        }

        private BaselineProvider boundProvider(
                ServiceContract<FT, IT, OT> serviceContract, CovariateProfile observed) {
            BaselineProvider raw = BaselineProviderResolver.resolve();
            return ProfileBoundBaselineProvider.bind(
                    raw, observed, serviceContract.covariates());
        }

        /**
         * Probes baseline existence per required empirical criterion.
         * Returns a synthesised INCONCLUSIVE-no-baseline result when
         * any required empirical criterion is missing its baseline;
         * empty when sampling can proceed.
         *
         * <p>Lookup notes (rejected candidates, partial-match
         * announcements) are pulled from each probed criterion and
         * propagated into the synthesised result's
         * {@link ProbabilisticTestResult#warnings()} list — that's
         * what {@link #translate} reads to distinguish the
         * "candidates existed but all rejected" misconfiguration FAIL
         * from the "no candidates at all" legitimate-skip ABORT.
         */
        private Optional<ProbabilisticTestResult> baselineExistencePreflight(
                ServiceContract<FT, IT, OT> serviceContract,
                CovariateProfile observed,
                BaselineProvider provider) {
            String serviceContractId = serviceContract.id();
            FactorBundle bundle = FactorBundle.of(factors);
            List<Covariate> declarations = serviceContract.covariates();
            List<EvaluatedCriterion> noBaselineResults = new ArrayList<>();
            List<String> notes = new ArrayList<>();
            for (Criterion<OT, ?> c : requiredCriteria) {
                if (!c.isEmpirical()) {
                    continue;
                }
                BaselineLookup<?> lookup = probeBaseline(
                        provider, serviceContractId, bundle, c, observed, declarations);
                if (lookup.selected().isPresent()) {
                    continue;
                }
                CriterionResult r = EmpiricalChecks.noBaseline(c.name(), c.empiricalDetail());
                noBaselineResults.add(new EvaluatedCriterion(r, CriterionRole.REQUIRED));
                notes.addAll(lookup.notes());
            }
            if (noBaselineResults.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(synthesiseNoBaselineResult(
                    noBaselineResults, notes, observed, serviceContract));
        }

        private ProbabilisticTestResult synthesiseNoBaselineResult(
                List<EvaluatedCriterion> noBaselineResults,
                List<String> warnings,
                CovariateProfile observed,
                ServiceContract<FT, IT, OT> serviceContract) {
            Optional<String> contractRefOpt = serviceContract.effectiveCriteria().stream()
                    .map(org.mavai.punit.api.criterion.Criterion::posture)
                    .map(org.mavai.punit.api.criterion.CriterionPosture::contractRef)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst();
            return new ProbabilisticTestResult(
                    Verdict.compose(noBaselineResults),
                    FactorBundle.of(factors),
                    noBaselineResults,
                    intent,
                    warnings,
                    CovariateAlignment.compute(observed, CovariateProfile.empty()),
                    contractRefOpt,
                    Map.of(),
                    EngineRunSummary.empty(),
                    PerCriterionEvaluation.empty());
        }

        private List<String> preflightFeasibility(
                ServiceContract<FT, IT, OT> serviceContract, BaselineProvider provider) {
            String serviceContractId = serviceContract.id();
            FactorBundle bundle = FactorBundle.of(factors);
            List<String> warnings = new ArrayList<>();
            for (Criterion<OT, ?> c : requiredCriteria) {
                warnings.addAll(Feasibility.check(
                        sampling.samples(), c, serviceContractId, bundle, intent, provider));
            }
            return warnings;
        }
    }

    /**
     * Builder for the empirical probabilistic-test entry point —
     * {@link PUnit#testing(Supplier)}. Pulls sampling and factors
     * from the baseline supplier; the author specifies only the
     * sample count and criterion.
     */
    public static final class EmpiricalTestBuilder {
        private final Supplier<Experiment> baselineSupplier;
        private Integer samples;
        private Criterion<?, ?> criterion;
        private TestIntent intent = TestIntent.VERIFICATION;
        private Boolean transparentStatsOverride;

        EmpiricalTestBuilder(Supplier<Experiment> baselineSupplier) {
            this.baselineSupplier = baselineSupplier;
        }

        public EmpiricalTestBuilder samples(int n) {
            if (n < 1) {
                throw new IllegalArgumentException("samples must be >= 1, got " + n);
            }
            this.samples = n;
            return this;
        }

        public EmpiricalTestBuilder criterion(Criterion<?, ?> criterion) {
            Objects.requireNonNull(criterion, "criterion");
            if (!criterion.isEmpirical()) {
                throw new IllegalArgumentException(
                        "PUnit.testing(supplier) only accepts empirical criteria; "
                                + "got contractual " + criterion.name()
                                + ". Use PUnit.testing(sampling, factors) for contractual tests.");
            }
            this.criterion = criterion;
            return this;
        }

        public EmpiricalTestBuilder intent(TestIntent intent) {
            this.intent = Objects.requireNonNull(intent, "intent");
            return this;
        }

        /** See {@link TestBuilder#transparentStats()}. */
        public EmpiricalTestBuilder transparentStats() {
            this.transparentStatsOverride = Boolean.TRUE;
            return this;
        }

        /** See {@link TestBuilder#transparentStats(boolean)}. */
        public EmpiricalTestBuilder transparentStats(boolean enabled) {
            this.transparentStatsOverride = enabled;
            return this;
        }

        public ProbabilisticTest build() {
            if (samples == null) {
                throw new IllegalStateException(
                        "samples is required — call .samples(n) before .build() / .assertPasses()");
            }
            Experiment baseline = Objects.requireNonNull(
                    baselineSupplier.get(),
                    "baseline supplier returned null");
            // Criterion is optional: when omitted, the test-spec builder
            // auto-injects from the contract's posture (any empirical
            // posture maps to PassRate.empirical).
            return EmpiricalTestComposer.compose(baseline, samples, criterion, intent);
        }

        public void assertPasses() {
            ProbabilisticTest spec = build();
            SpecContext ctx = resolveSpecContext(spec);

            // Existence probe runs before sampling: see TestBuilder.assertPasses
            // for the rationale.
            Optional<ProbabilisticTestResult> shortCircuit =
                    baselineExistencePreflight(ctx);
            if (shortCircuit.isPresent()) {
                ProbabilisticTestResult synthesised = shortCircuit.get();
                maybeRenderTransparentStats(ctx, synthesised);
                emitVerdict(synthesised, ctx.serviceContractId);
                translate(synthesised, ctx.serviceContractId);
                return;
            }

            List<String> warnings = preflightFeasibility(ctx);
            EngineResult result = drive(spec);
            if (!(result instanceof ProbabilisticTestResult typed)) {
                throw new IllegalStateException(
                        "Engine produced unexpected result type: " + result.getClass().getName());
            }
            warnings.forEach(System.err::println);
            ProbabilisticTestResult stamped = typed.withCovariates(
                    CovariateAlignment.compute(
                            ctx.profile, typed.covariates().baseline()));
            maybeRenderTransparentStats(ctx, stamped);
            emitVerdict(stamped, ctx.serviceContractId);
            translate(stamped, ctx.serviceContractId);
        }

        /**
         * Captures the per-run state every preflight step needs:
         * serviceContractId, factor bundle, configured samples, resolved
         * covariate profile, declarations, and a profile-bound
         * baseline provider.
         */
        private static final class SpecContext {
            final String serviceContractId;
            final FactorBundle bundle;
            final int samples;
            final CovariateProfile profile;
            final List<Covariate> declarations;
            final BaselineProvider provider;
            final FactorBundle resultFactors;
            final Optional<String> contractRef;

            SpecContext(String serviceContractId, FactorBundle bundle, int samples,
                    CovariateProfile profile, List<Covariate> declarations,
                    BaselineProvider provider, FactorBundle resultFactors,
                    Optional<String> contractRef) {
                this.serviceContractId = serviceContractId;
                this.bundle = bundle;
                this.samples = samples;
                this.profile = profile;
                this.declarations = declarations;
                this.provider = provider;
                this.resultFactors = resultFactors;
                this.contractRef = contractRef;
            }
        }

        private SpecContext resolveSpecContext(ProbabilisticTest spec) {
            BaselineProvider raw = BaselineProviderResolver.resolve();
            return spec.dispatch(new Spec.Dispatcher<SpecContext>() {
                @Override
                public <FT, IT, OT> SpecContext apply(TypedSpec<FT, IT, OT> typed) {
                    var cfg = typed.configurations().next();
                    FT factors = cfg.factors();
                    ServiceContract<FT, IT, OT> serviceContract = typed.serviceContractFactory().apply(factors);
                    FactorBundle bundle = FactorBundle.of(factors);
                    List<Covariate> declarations = serviceContract.covariates();
                    CovariateProfile profile = declarations.isEmpty()
                            ? CovariateProfile.empty()
                            : CovariateResolver.defaults()
                                    .resolve(declarations, serviceContract.customCovariateResolvers());
                    BaselineProvider provider = ProfileBoundBaselineProvider.bind(
                            raw, profile, declarations);
                    Optional<String> contractRef = serviceContract.effectiveCriteria().stream()
                            .map(org.mavai.punit.api.criterion.Criterion::posture)
                            .map(org.mavai.punit.api.criterion.CriterionPosture::contractRef)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .findFirst();
                    return new SpecContext(
                            serviceContract.id(), bundle, cfg.samples(),
                            profile, declarations, provider, bundle, contractRef);
                }
            });
        }

        @SuppressWarnings("unchecked")
        private Optional<ProbabilisticTestResult> baselineExistencePreflight(
                SpecContext ctx) {
            // The preflight short-circuit is an optimisation: when the
            // baseline is missing, skip sampling because the verdict is
            // structurally INCONCLUSIVE. When the author omitted
            // .criterion(...) (auto-injection path), skip the preflight
            // — the engine's evaluation will produce INCONCLUSIVE
            // naturally if the baseline is missing.
            if (criterion == null) {
                return Optional.empty();
            }
            Criterion<Object, ?> c = (Criterion<Object, ?>) criterion;
            BaselineLookup<?> lookup = probeBaseline(
                    ctx.provider, ctx.serviceContractId, ctx.bundle,
                    c, ctx.profile, ctx.declarations);
            if (lookup.selected().isPresent()) {
                return Optional.empty();
            }
            CriterionResult r = EmpiricalChecks.noBaseline(c.name(), c.empiricalDetail());
            EvaluatedCriterion evaluated = new EvaluatedCriterion(r, CriterionRole.REQUIRED);
            return Optional.of(synthesiseNoBaselineResult(
                    ctx, List.of(evaluated), lookup.notes()));
        }

        private ProbabilisticTestResult synthesiseNoBaselineResult(
                SpecContext ctx,
                List<EvaluatedCriterion> noBaselineResults,
                List<String> warnings) {
            Optional<String> contractRefOpt = ctx.contractRef;
            return new ProbabilisticTestResult(
                    Verdict.compose(noBaselineResults),
                    ctx.resultFactors,
                    noBaselineResults,
                    intent,
                    warnings,
                    CovariateAlignment.compute(ctx.profile, CovariateProfile.empty()),
                    contractRefOpt,
                    Map.of(),
                    EngineRunSummary.empty(),
                    PerCriterionEvaluation.empty());
        }

        @SuppressWarnings("unchecked")
        private List<String> preflightFeasibility(SpecContext ctx) {
            if (criterion == null) {
                // Auto-injection path: feasibility is checked at the
                // spec/engine layer over the auto-derived criteria.
                return new ArrayList<>();
            }
            return new ArrayList<>(Feasibility.check(
                    ctx.samples,
                    (Criterion<Object, ?>) criterion,
                    ctx.serviceContractId, ctx.bundle, intent, ctx.provider));
        }

        private void maybeRenderTransparentStats(
                SpecContext ctx, ProbabilisticTestResult result) {
            if (!TransparentStatsConfig.resolve(transparentStatsOverride).enabled()) {
                return;
            }
            System.err.println(TransparentStatsRenderer.render(ctx.serviceContractId, result));
        }
    }
}
