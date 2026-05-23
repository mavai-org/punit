package org.javai.punit.internal.engine;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.javai.outcome.Outcome;
import org.javai.punit.api.LatencyResult;
import org.javai.punit.api.ServiceContract;
import org.javai.punit.api.ServiceContractOutcome;
import org.javai.punit.api.spec.BaselineProvider;
import org.javai.punit.api.spec.Configuration;
import org.javai.punit.api.spec.CriterionSampleCounts;
import org.javai.punit.api.spec.EarlyTerminationContext;
import org.javai.punit.api.spec.EngineResult;
import org.javai.punit.api.spec.ExceptionPolicy;
import org.javai.punit.api.spec.TerminationReason;
import org.javai.punit.api.spec.FailureCount;
import org.javai.punit.api.spec.FailureExemplar;
import org.javai.punit.api.spec.SampleClassification;
import org.javai.punit.api.spec.SampleExecutor;
import org.javai.punit.api.spec.SampleObserver;
import org.javai.punit.api.spec.SampleSummary;
import org.javai.punit.api.spec.Trial;
import org.javai.punit.api.spec.Spec;
import org.javai.punit.api.spec.TypedSpec;

/**
 * The one engine, shared by every spec flavour.
 *
 * <p>The engine iterates
 * {@link Spec#configurations() spec.configurations()}, samples each
 * configuration through a {@link SampleExecutor}, hands the resulting
 * {@link SampleSummary} to
 * {@link Spec#consume(Configuration, SampleSummary) spec.consume(...)},
 * and finally invokes {@link Spec#conclude() spec.conclude()} to
 * obtain the run's {@link EngineResult}.
 *
 * <p>Resource controls declared on the spec (time budget, token
 * budget, static token charge, example-failure cap) are honoured here
 * via a {@link BudgetTracker} that the executor consults before each
 * sample. Latency percentiles are computed for every configuration
 * regardless of whether the spec declared latency thresholds —
 * enforcement is optional, computation is not.
 *
 * <p>No {@code instanceof} / {@code switch} on spec subtype anywhere
 * in this class or its helpers.
 */
public final class Engine {

    private final SampleExecutor executor;
    private final BaselineProvider baselineProvider;

    public Engine() {
        this(new SerialSampleExecutor(), BaselineProvider.EMPTY);
    }

    public Engine(SampleExecutor executor) {
        this(executor, BaselineProvider.EMPTY);
    }

    public Engine(BaselineProvider baselineProvider) {
        this(new SerialSampleExecutor(), baselineProvider);
    }

    public Engine(SampleExecutor executor, BaselineProvider baselineProvider) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.baselineProvider = Objects.requireNonNull(baselineProvider, "baselineProvider");
    }

    public EngineResult run(Spec spec) {
        Objects.requireNonNull(spec, "spec");
        return spec.dispatch(new Spec.Dispatcher<EngineResult>() {
            @Override
            public <FT, IT, OT> EngineResult apply(TypedSpec<FT, IT, OT> typed) {
                return runTyped(typed);
            }
        });
    }

    private <FT, IT, OT> EngineResult runTyped(TypedSpec<FT, IT, OT> spec) {
        // Each configuration starts its input cursor at 0. In an
        // explore / optimize grid, cells must be compared on the same
        // inputs so that the factor change is the only variable; an
        // accumulating cursor across cells confounded the factor with
        // whichever portion of the input list happened to land in
        // each cell. Intra-cell semantics are unchanged: sample i of
        // a cell still uses inputs[i mod inputs.size()].
        Iterator<Configuration<FT, IT, OT>> it = spec.configurations();
        while (it.hasNext()) {
            Configuration<FT, IT, OT> cfg = it.next();
            ServiceContract<FT, IT, OT> uc = spec.serviceContractFactory().apply(cfg.factors());
            // Silent uplift: when the contract carries a
            // confidence-first criterion, the framework computes its
            // PowerAnalysis-required sample count from the baseline
            // and uplifts the configuration's declared count to the
            // maximum. The author's .samples(N) is a floor; the
            // contract's commitment is the source of truth.
            org.javai.punit.internal.engine.baseline.SampleSizeResolver.Resolution resolution =
                    org.javai.punit.internal.engine.baseline.SampleSizeResolver.resolve(
                            uc,
                            org.javai.punit.api.FactorBundle.of(cfg.factors()),
                            baselineProvider,
                            cfg.samples());
            if (resolution.wasUplifted()) {
                System.out.println(String.format(
                        "[PUNIT-UPLIFT] criterion '%s' demands %d samples (PowerAnalysis); "
                                + "uplifting declared count of %d.",
                        resolution.drivenBy().get(),
                        resolution.effective(),
                        resolution.declared()));
            }
            SampleSummary<OT> summary = runConfig(spec, uc, cfg, 0, resolution.effective());
            spec.consume(cfg, summary);
        }
        return spec.conclude(baselineProvider);
    }

    private <FT, IT, OT> SampleSummary<OT> runConfig(
            TypedSpec<FT, IT, OT> spec,
            ServiceContract<FT, IT, OT> serviceContract,
            Configuration<FT, IT, OT> cfg,
            int cycleStart,
            int effectiveSamples) {

        BudgetTracker tracker = new BudgetTracker(
                spec.timeBudget(), spec.tokenBudget(), spec.tokenCharge());
        Aggregator<IT, OT> agg = new Aggregator<>(
                serviceContract,
                cfg.inputs(),
                cycleStart,
                spec.exceptionPolicy(),
                spec.maxExampleFailures(),
                tracker,
                spec.earlyTermination(),
                effectiveSamples);

        long t0 = System.nanoTime();
        executor.runSamples(
                serviceContract,
                cfg.inputs(),
                effectiveSamples,
                cycleStart,
                agg,
                tracker::shouldStopBeforeNextSample);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - t0);
        return agg.toSummary(elapsed);
    }

    /**
     * Aggregates per-sample outcomes. Stamps the per-sample duration
     * onto each outcome, records the token cost on the tracker, and
     * caps retained failure detail at {@code maxExampleFailures}.
     */
    private static final class Aggregator<IT, OT> implements SampleObserver<OT> {

        private final ServiceContract<?, IT, OT> serviceContract;
        private final List<IT> inputs;
        private final int cycleStart;
        private final ExceptionPolicy exceptionPolicy;
        private final int maxExampleFailures;
        private final BudgetTracker tracker;
        private final int plannedSamples;
        private final Optional<EarlyTerminationContext> earlyTermination;
        private final int requiredSuccesses;
        private final int minSamplesForValidity;
        // retained is exposed via the summary; failure detail beyond
        // maxExampleFailures is elided. allForLatency holds durations
        // from every sample so the all-samples latency stats never
        // skew on drop. passingForLatency holds durations from samples
        // that passed (Outcome.Ok at the contract layer per
        // ServiceContractOutcome.value()), feeding the passing-only descriptive
        // percentiles emitted into baseline, exploration, optimize,
        // and verdict artefacts. trials carries the full ordered
        // (input, outcome, duration) history regardless of the failure
        // cap.
        private final List<ServiceContractOutcome<?, OT>> retained = new ArrayList<>();
        private final List<ServiceContractOutcome<?, OT>> allForLatency = new ArrayList<>();
        private final List<ServiceContractOutcome<?, OT>> passingForLatency = new ArrayList<>();
        private final List<Trial<?, OT>> trials = new ArrayList<>();
        private final LinkedHashMap<String, MutableFailureBucket> failuresByPostcondition =
                new LinkedHashMap<>();
        // Per-criterion sample-outcome counts, keyed by criterion id and
        // populated from each sample's CriterionSampleResult stream.
        // LinkedHashMap preserves the criterion declaration order so
        // reporters render criteria in the order the contract declared
        // them.
        private final LinkedHashMap<String, MutableCriterionCounts> criterionCounts =
                new LinkedHashMap<>();
        private static final int EXEMPLAR_CAP_PER_CLAUSE = 3;
        private int successes = 0;
        private int failures = 0;
        private int retainedFailures = 0;
        private long tokensConsumed = 0L;
        private int failuresDropped = 0;

        Aggregator(ServiceContract<?, IT, OT> serviceContract,
                   List<IT> inputs,
                   int cycleStart,
                   ExceptionPolicy exceptionPolicy,
                   int maxExampleFailures,
                   BudgetTracker tracker,
                   Optional<EarlyTerminationContext> earlyTermination,
                   int plannedSamples) {
            this.serviceContract = serviceContract;
            this.inputs = inputs;
            this.cycleStart = cycleStart;
            this.exceptionPolicy = exceptionPolicy;
            this.maxExampleFailures = maxExampleFailures;
            this.tracker = tracker;
            this.plannedSamples = plannedSamples;
            this.earlyTermination = earlyTermination;
            // Cached once: required-successes = ceil(plannedSamples * minPassRate),
            // and the statistical-validity floor below which a guaranteed-success
            // short-circuit must not fire. Both zero when no context is supplied
            // (measure / explore / optimize / empirical / opt-out paths).
            if (earlyTermination.isPresent()) {
                EarlyTerminationContext ctx = earlyTermination.get();
                this.requiredSuccesses =
                        (int) Math.ceil(plannedSamples * ctx.minPassRate());
                this.minSamplesForValidity = ctx.minSamplesForValidity();
            } else {
                this.requiredSuccesses = 0;
                this.minSamplesForValidity = 0;
            }
        }

        @Override
        public void onSample(int index, ServiceContractOutcome<?, OT> outcome, Duration elapsed) {
            // The outcome already carries duration, tokens, postcondition
            // results, and the optional match (set by Contract.apply form 3
            // when the executor is configured for matching). The classifier
            // adds the optional duration violation by reading serviceContract.maxLatency().
            record(index, outcome, classify(outcome));
        }

        @Override
        public void onDefect(int index, Throwable throwable, Duration elapsed) {
            // Errors (OOM, StackOverflow, LinkageError) always propagate
            // — they are not caught regardless of the spec's policy.
            if (throwable instanceof Error e) {
                throw e;
            }
            if (exceptionPolicy == ExceptionPolicy.ABORT_TEST) {
                if (throwable instanceof RuntimeException re) {
                    throw re;
                }
                throw new RuntimeException(throwable);
            }
            // FAIL_SAMPLE: synthesise a failing outcome and continue.
            ServiceContractOutcome<IT, OT> synthetic = new ServiceContractOutcome<>(
                    Outcome.fail("defect", throwable.toString()),
                    serviceContract,
                    List.of(),
                    0L,
                    elapsed);
            record(index, synthetic, classify(synthetic));
        }

        private SampleClassification classify(ServiceContractOutcome<?, OT> outcome) {
            // SampleClassification.classify wants ServiceContractOutcome<I, OT>; the
            // wildcard at this site is safe because all outcomes flowing
            // through this aggregator come from the executor, which only
            // produces ServiceContractOutcome<IT, OT>.
            @SuppressWarnings("unchecked")
            ServiceContractOutcome<IT, OT> typed = (ServiceContractOutcome<IT, OT>) outcome;
            return SampleClassification.classify(serviceContract, typed);
        }

        private void record(int index, ServiceContractOutcome<?, OT> stamped,
                            SampleClassification classification) {
            tracker.recordSampleTokens(classification.tokens());
            allForLatency.add(stamped);
            trials.add(trialFor(index, stamped));
            boolean ok = stamped.value().isOk();
            if (ok) {
                successes++;
                retained.add(stamped);
                passingForLatency.add(stamped);
            } else {
                failures++;
                if (retainedFailures < maxExampleFailures) {
                    retained.add(stamped);
                    retainedFailures++;
                } else {
                    failuresDropped++;
                }
            }
            recordPostconditionFailures(index, classification);
            recordCriterionSampleOutcomes(stamped);
            FailureLineEmitter.emit(index, classification);
            tokensConsumed += classification.tokens() + tracker.tokenCharge();
            checkStatisticalEarlyTermination();
        }

        /**
         * After each recorded sample, check whether the spec's
         * contractual pass-rate threshold is now mathematically
         * unreachable (failure inevitable) or already met and locked
         * in (success guaranteed, subject to the
         * statistical-validity floor). On a hit, signals the
         * {@link BudgetTracker} so the executor halts before the next
         * sample.
         *
         * <p>No-op when no early-termination context was supplied:
         * measure / explore / optimize specs, empirical-mode
         * probabilistic tests, and probabilistic tests whose author
         * called {@code disableEarlyTermination()}.
         *
         * <p>Failure-inevitable wins when both would fire in the
         * same sample — it takes precedence over a (necessarily
         * stale) success-guaranteed reading.
         */
        private void checkStatisticalEarlyTermination() {
            if (earlyTermination.isEmpty()) {
                return;
            }
            int observed = successes + failures;
            int remaining = plannedSamples - observed;
            int maxPossibleSuccesses = successes + remaining;
            if (maxPossibleSuccesses < requiredSuccesses) {
                tracker.recordEarlyTermination(TerminationReason.IMPOSSIBILITY);
                return;
            }
            if (successes >= requiredSuccesses
                    && observed >= minSamplesForValidity
                    && remaining > 0) {
                tracker.recordEarlyTermination(TerminationReason.SUCCESS_GUARANTEED);
            }
        }

        private void recordPostconditionFailures(int index, SampleClassification classification) {
            IT input = inputForIndex(index);
            String inputStr = String.valueOf(input);
            for (var result : classification.postconditionResults()) {
                if (!result.failed()) {
                    continue;
                }
                MutableFailureBucket bucket = failuresByPostcondition.computeIfAbsent(
                        result.description(), k -> new MutableFailureBucket());
                bucket.count++;
                if (bucket.exemplars.size() < EXEMPLAR_CAP_PER_CLAUSE) {
                    String reason = result.failureReason().orElse(result.description());
                    bucket.exemplars.add(new FailureExemplar(inputStr, reason));
                }
            }
        }

        private static final class MutableFailureBucket {
            int count = 0;
            final List<FailureExemplar> exemplars = new ArrayList<>();
        }

        private static final class MutableCriterionCounts {
            int pass = 0;
            int conditionFail = 0;
            int transformFail = 0;
        }

        private void recordCriterionSampleOutcomes(ServiceContractOutcome<?, OT> stamped) {
            // Empty list when the contract had nothing to evaluate (apply-level
            // failure, synthesised defect outcome) or when this sample's
            // outcome was constructed without per-criterion detail (test
            // fixtures using the back-compat constructor).
            for (var entry : stamped.criterionSampleResults()) {
                MutableCriterionCounts counts = criterionCounts.computeIfAbsent(
                        entry.criterionId(), k -> new MutableCriterionCounts());
                switch (entry.outcome()) {
                    case PASS -> counts.pass++;
                    case FAIL -> {
                        // A FAIL carrying a reason is a transform /
                        // no-value failure (the chain never ran); one
                        // carrying postcondition results is a condition
                        // failure.
                        if (entry.reason().isPresent()) {
                            counts.transformFail++;
                        } else {
                            counts.conditionFail++;
                        }
                    }
                }
            }
        }

        private <I> Trial<IT, OT> trialFor(int index, ServiceContractOutcome<I, OT> stamped) {
            // Trial<IT, OT> requires ServiceContractOutcome<IT, OT>; the wildcard
            // carrier in onSample loses that I; we know the executor only
            // ever supplies ServiceContractOutcome<IT, OT> so this is safe.
            @SuppressWarnings("unchecked")
            ServiceContractOutcome<IT, OT> typed = (ServiceContractOutcome<IT, OT>) stamped;
            int inputIndex = cycleIndexFor(index);
            return new Trial<>(inputs.get(inputIndex), typed, stamped.duration(), inputIndex);
        }

        private IT inputForIndex(int index) {
            return inputs.get(cycleIndexFor(index));
        }

        private int cycleIndexFor(int index) {
            return (cycleStart + index) % inputs.size();
        }

        SampleSummary<OT> toSummary(Duration elapsed) {
            LatencyResult latencyResult = LatencyPercentileComputer.computeFrom(allForLatency);
            LatencyResult passingLatencyResult =
                    LatencyPercentileComputer.computeFrom(passingForLatency);
            return new SampleSummary<>(
                    retained,
                    elapsed,
                    successes,
                    failures,
                    tokensConsumed,
                    failuresDropped,
                    latencyResult,
                    tracker.terminationReason(),
                    trials,
                    immutableFailuresByPostcondition(),
                    passingLatencyResult,
                    immutableCriterionSampleCounts());
        }

        private List<CriterionSampleCounts> immutableCriterionSampleCounts() {
            List<CriterionSampleCounts> out = new ArrayList<>(criterionCounts.size());
            for (var e : criterionCounts.entrySet()) {
                MutableCriterionCounts c = e.getValue();
                out.add(new CriterionSampleCounts(
                        e.getKey(), c.pass, c.conditionFail, c.transformFail));
            }
            return List.copyOf(out);
        }

        private Map<String, FailureCount> immutableFailuresByPostcondition() {
            // Preserve insertion order via LinkedHashMap so reporters see
            // postconditions in the order the contract declared them.
            LinkedHashMap<String, FailureCount> out = new LinkedHashMap<>();
            for (var e : failuresByPostcondition.entrySet()) {
                out.put(e.getKey(), new FailureCount(e.getValue().count, e.getValue().exemplars));
            }
            return Map.copyOf(out);
        }
    }
}
