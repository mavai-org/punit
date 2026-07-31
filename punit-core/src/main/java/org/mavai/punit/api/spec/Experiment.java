package org.mavai.punit.api.spec;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Function;

import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.api.spec.FactorsStepper.IterationResult;

/**
 * The unified spec for the three experiment kinds — measure, explore,
 * and optimize. The kind is chosen at the static factory:
 *
 * <ul>
 *   <li>{@link #measuring(Sampling, Object)} — one configuration,
 *       produces a baseline.</li>
 *   <li>{@link #exploring(Sampling)} — many configurations, produces
 *       a grid.</li>
 *   <li>{@link #optimizing(Sampling)} — iterative search, produces an
 *       optimization history.</li>
 * </ul>
 *
 * <p>The engine treats all three uniformly via the
 * {@link Spec#dispatch(Spec.Dispatcher)} protocol; the kind-specific
 * configuration generation, summary aggregation, and result
 * production live behind a typed internal delegate per kind.
 *
 * <p>The public class carries no type parameters — composition-time
 * type safety lives on the typed builder, which the static factories
 * return. The triplet of static factories on this single class
 * replaces the historical
 * {@code MeasureSpec} / {@code ExploreSpec} / {@code OptimizeSpec}
 * classes; the experiment family is a single sealed permit on
 * {@link Spec}, mirroring the binary
 * {@code ExperimentResult} / {@code ProbabilisticTestResult} on the
 * result side.
 */
public final class Experiment implements Spec {

    /** Which experiment shape this value represents. */
    public enum Kind { MEASURE, EXPLORE, OPTIMIZE }

    private final Kind kind;
    private final Internal<?, ?, ?> internal;

    private Experiment(Kind kind, Internal<?, ?, ?> internal) {
        this.kind = kind;
        this.internal = internal;
    }

    // ── Static factories ─────────────────────────────────────────────

    /**
     * Compose a measure experiment over a {@link Sampling} and the
     * factors the measure runs against. The same {@link Sampling}
     * value can be passed to {@link ProbabilisticTest#testing} to
     * pair the measure with an empirical test — Java's reference
     * semantics enforce that both operate on the same sampling
     * population, the structural guarantee the empirical comparison
     * depends on.
     */
    public static <FT, IT, OT> MeasureBuilder<FT, IT, OT> measuring(
            Sampling<FT, IT, OT> sampling, FT factors) {
        return new MeasureBuilder<>(
                Objects.requireNonNull(sampling, "sampling"),
                Objects.requireNonNull(factors, "factors"));
    }

    /**
     * Compose an explore experiment over a {@link Sampling}; the
     * grid of factors instances is supplied through the returned
     * builder.
     */
    public static <FT, IT, OT> ExploreBuilder<FT, IT, OT> exploring(
            Sampling<FT, IT, OT> sampling) {
        return new ExploreBuilder<>(Objects.requireNonNull(sampling, "sampling"));
    }

    /**
     * Compose an optimize experiment over a {@link Sampling}; the
     * initial factors, stepper, and scorer/direction are supplied
     * through the returned builder.
     */
    public static <FT, IT, OT> OptimizeBuilder<FT, IT, OT> optimizing(
            Sampling<FT, IT, OT> sampling) {
        return new OptimizeBuilder<>(Objects.requireNonNull(sampling, "sampling"));
    }

    // ── Inline-sampling entry points (one-off authoring) ────────────
    //
    // Each inline form accepts a use-case factory in place of a Sampling.
    // The returned builder accumulates sampling-level state (inputs,
    // samples, governors) alongside the spec-overlay state, synthesising
    // a Sampling at .build() time.
    //
    // Inline form is for one-off authoring (no reuse). The integrity
    // guarantee that comes from sharing a Sampling value across a
    // measure / probabilistic-test pair is unavailable in the inline
    // form — measures, explores, and optimizes are unaffected (no
    // pairing); empirical probabilistic tests are rejected at .build()
    // with a diagnostic teaching the helper-extraction pattern.

    /**
     * Inline-sampling form of {@link #measuring(Sampling, Object)}.
     * Sampling parameters are supplied through the returned builder.
     */
    public static <FT, IT, OT> InlineMeasureBuilder<FT, IT, OT> measuring(
            Function<FT, ServiceContract<FT, IT, OT>> serviceContractFactory, FT factors) {
        return new InlineMeasureBuilder<>(
                Objects.requireNonNull(serviceContractFactory, "serviceContractFactory"),
                Objects.requireNonNull(factors, "factors"));
    }

    /** Inline-sampling form of {@link #exploring(Sampling)}. */
    public static <FT, IT, OT> InlineExploreBuilder<FT, IT, OT> exploring(
            Function<FT, ServiceContract<FT, IT, OT>> serviceContractFactory) {
        return new InlineExploreBuilder<>(
                Objects.requireNonNull(serviceContractFactory, "serviceContractFactory"));
    }

    /** Inline-sampling form of {@link #optimizing(Sampling)}. */
    public static <FT, IT, OT> InlineOptimizeBuilder<FT, IT, OT> optimizing(
            Function<FT, ServiceContract<FT, IT, OT>> serviceContractFactory) {
        return new InlineOptimizeBuilder<>(
                Objects.requireNonNull(serviceContractFactory, "serviceContractFactory"));
    }

    // ── Public scalar accessors ─────────────────────────────────────

    public Kind kind() { return kind; }
    public String experimentId() { return internal.experimentId(); }
    public int samples() { return internal.samples(); }

    /**
     * The baseline validity window in days, when the measure declared
     * one via {@code .expiresInDays(n)}. Empty for explore and
     * optimize, and for measures that declared no expiration (or
     * declared {@code 0}). Consumed by the baseline emitter so the
     * window is persisted into the baseline spec.
     */
    public OptionalInt expiresInDays() {
        if (internal instanceof MeasureInternal<?, ?, ?> m) {
            return m.expiresInDays.map(OptionalInt::of).orElseGet(OptionalInt::empty);
        }
        return OptionalInt.empty();
    }

    /**
     * Diagnostic accessor populated only after a measure experiment
     * has run. Empty for explore and optimize, and for measures that
     * have not yet been executed.
     */
    public Optional<SampleSummary<?>> lastSummary() {
        if (internal instanceof MeasureInternal<?, ?, ?> m) {
            return Optional.ofNullable(m.lastSummary.orElse(null));
        }
        return Optional.empty();
    }

    /**
     * Diagnostic accessor populated only by optimize experiments.
     * Empty for measure and explore.
     */
    public List<IterationResult<?>> history() {
        if (internal instanceof OptimizeInternal<?, ?, ?> o) {
            return List.copyOf(o.history);
        }
        return List.of();
    }

    /**
     * Diagnostic accessor returning the per-iteration
     * {@link SampleSummary} parallel to {@link #history()}, in the
     * same execution order. Empty for measure and explore. Consumed
     * by the OPTIMIZE artefact emitter when it needs per-sample
     * trial detail (the {@code resultProjection:} block of the
     * optimize-experiment output).
     */
    public List<SampleSummary<?>> iterationSummaries() {
        if (internal instanceof OptimizeInternal<?, ?, ?> o) {
            return List.copyOf(o.iterationSummaries);
        }
        return List.of();
    }

    /**
     * Diagnostic accessor populated only after an explore experiment
     * has run. Yields one entry per {@code FT} in the grid, in
     * iteration order. Empty for measure and optimize, and for
     * explores that have not yet been executed. Consumed by the
     * EXPLORE artefact emitter.
     */
    public List<PerConfigSummary<?, ?>> perConfigSummaries() {
        if (internal instanceof ExploreInternal<?, ?, ?> e) {
            return List.copyOf(e.perConfigSummariesList());
        }
        return List.of();
    }

    /**
     * The explored grid point the sweep was built around, when the
     * experiment declared one. Empty for a grid that is a plain product
     * of factor values — such a grid has no base, and the artefacts
     * state none. Consumed by the EXPLORE artefact emitter.
     */
    public Optional<Object> baseConfiguration() {
        if (internal instanceof ExploreInternal<?, ?, ?> e) {
            return Optional.ofNullable(e.baseConfiguration);
        }
        return Optional.empty();
    }

    /**
     * Diagnostic accessor populated only after an optimize experiment
     * has run. Returns the best-scoring iteration per the
     * optimisation's declared direction (maximise / minimise).
     * Empty for measure and explore, and for optimizes that have
     * not yet been executed. Consumed by the OPTIMIZE artefact
     * emitter.
     */
    public Optional<IterationResult<?>> bestOptimizeIteration() {
        if (internal instanceof OptimizeInternal<?, ?, ?> o) {
            return Optional.ofNullable(o.bestSoFar());
        }
        return Optional.empty();
    }

    /**
     * Diagnostic accessor populated for optimize experiments only.
     * Returns the optimisation direction declared on the builder —
     * {@code "MAXIMIZE"} or {@code "MINIMIZE"}. Empty for measure
     * and explore.
     */
    public Optional<String> optimizeObjective() {
        if (internal instanceof OptimizeInternal<?, ?, ?> o) {
            return Optional.of(o.maximizing ? "MAXIMIZE" : "MINIMIZE");
        }
        return Optional.empty();
    }

    /**
     * Diagnostic accessor populated for optimize experiments only.
     * Returns the declared scorer's stable name, when the scorer
     * carries one (see {@link Scorer#name()}). Empty for measure and
     * explore, and for ad-hoc lambda scorers. Consumed by the
     * OPTIMIZE artefact emitter for the additive {@code scorer}
     * field.
     */
    public Optional<String> optimizeScorerName() {
        if (internal instanceof OptimizeInternal<?, ?, ?> o) {
            return o.scorer.name();
        }
        return Optional.empty();
    }

    /**
     * Diagnostic accessor populated for optimize experiments only,
     * after the run has completed. Reports why the iteration loop
     * stopped: {@code MAX_ITERATIONS}, {@code NO_IMPROVEMENT}, or
     * {@code STEPPER_STOP}. Empty for measure and explore, and for
     * optimizes that have not yet been executed.
     */
    public Optional<String> optimizeTerminationReason() {
        if (internal instanceof OptimizeInternal<?, ?, ?> o) {
            return Optional.ofNullable(o.terminationReason);
        }
        return Optional.empty();
    }

    // ── Spec dispatch ────────────────────────────────────────────────

    @Override
    public <R> R dispatch(Dispatcher<R> dispatcher) {
        return doDispatch(internal, dispatcher);
    }

    private static <FT, IT, OT, R> R doDispatch(Internal<FT, IT, OT> typed, Dispatcher<R> d) {
        return d.apply(typed);
    }

    // ── Common typed-internal base ───────────────────────────────────

    private abstract static class Internal<FT, IT, OT> implements TypedSpec<FT, IT, OT> {

        final Sampling<FT, IT, OT> sampling;
        final String experimentId;

        Internal(Sampling<FT, IT, OT> sampling, String experimentId) {
            this.sampling = sampling;
            this.experimentId = experimentId;
        }

        String experimentId() { return experimentId; }
        int samples() { return sampling.samples(); }

        @Override public Function<FT, ServiceContract<FT, IT, OT>> serviceContractFactory() {
            return sampling.serviceContractFactory();
        }

        @Override public Optional<Duration> timeBudget() { return sampling.timeBudget(); }
        @Override public OptionalLong tokenBudget() { return sampling.tokenBudget(); }
        @Override public long tokenCharge() { return sampling.tokenCharge(); }
        @Override public BudgetExhaustionPolicy budgetPolicy() { return sampling.budgetPolicy(); }
        @Override public ExceptionPolicy exceptionPolicy() { return sampling.exceptionPolicy(); }
        @Override public int maxExampleFailures() { return sampling.maxExampleFailures(); }
    }

    // ── Measure ──────────────────────────────────────────────────────

    private static final class MeasureInternal<FT, IT, OT> extends Internal<FT, IT, OT> {

        private final FT factors;
        private final Optional<Integer> expiresInDays;

        private Optional<SampleSummary<OT>> lastSummary = Optional.empty();

        private MeasureInternal(MeasureBuilder<FT, IT, OT> b) {
            super(b.sampling, b.experimentId != null ? b.experimentId : defaultId("measure"));
            this.factors = b.factors;
            this.expiresInDays = Optional.ofNullable(b.expiresInDays);
        }

        @Override public Iterator<Configuration<FT, IT, OT>> configurations() {
            return List.of(new Configuration<FT, IT, OT>(
                    factors, sampling.inputs(), sampling.samples())).iterator();
        }

        @Override public void consume(Configuration<FT, IT, OT> config, SampleSummary<OT> summary) {
            this.lastSummary = Optional.of(summary);
        }

        @Override public EngineResult conclude(BaselineProvider provider) {
            FactorBundle bundle = FactorBundle.of(factors);
            Path path = defaultBaselinePath(experimentId, bundle);
            String message = "measure baseline (stage 2 placeholder — serialisation lands in stage 4); "
                    + "samples=" + lastSummary.map(SampleSummary::total).orElse(0)
                    + ", passRate=" + lastSummary.map(s -> String.format("%.3f", s.passRate())).orElse("n/a");
            return new ExperimentResult(message, path);
        }
    }

    // mavai-ref: JVI-315MNJX — do not remove (resolves in mavai-orchestrator)
    public static final class MeasureBuilder<FT, IT, OT> {

        private final Sampling<FT, IT, OT> sampling;
        private final FT factors;
        private String experimentId;
        private Integer expiresInDays;

        private MeasureBuilder(Sampling<FT, IT, OT> sampling, FT factors) {
            this.sampling = sampling;
            this.factors = factors;
        }

        public MeasureBuilder<FT, IT, OT> experimentId(String id) {
            this.experimentId = Objects.requireNonNull(id, "experimentId");
            return this;
        }

        /** Baseline validity window. {@code 0} means no expiration. */
        public MeasureBuilder<FT, IT, OT> expiresInDays(int days) {
            if (days < 0) {
                throw new IllegalArgumentException("expiresInDays must be non-negative, got " + days);
            }
            this.expiresInDays = days;
            return this;
        }

        public Experiment build() {
            return new Experiment(Kind.MEASURE, new MeasureInternal<>(this));
        }
    }

    // ── Explore ──────────────────────────────────────────────────────

    private static final class ExploreInternal<FT, IT, OT> extends Internal<FT, IT, OT> {

        private final List<FT> grid;
        private final FT baseConfiguration;
        private final Map<FT, SampleSummary<OT>> perConfig = new LinkedHashMap<>();
        private final Map<FT, Integer> perConfigSamplesPlanned = new LinkedHashMap<>();

        private ExploreInternal(ExploreBuilder<FT, IT, OT> b) {
            super(b.sampling, b.experimentId != null ? b.experimentId : defaultId("explore"));
            this.grid = b.grid;
            this.baseConfiguration = b.baseConfiguration;
        }

        @Override public Iterator<Configuration<FT, IT, OT>> configurations() {
            List<Configuration<FT, IT, OT>> configs = new ArrayList<>(grid.size());
            for (FT ft : grid) {
                configs.add(Configuration.of(ft, sampling.inputs(), sampling.samples()));
            }
            return configs.iterator();
        }

        @Override public void consume(Configuration<FT, IT, OT> config, SampleSummary<OT> summary) {
            perConfig.put(config.factors(), summary);
            perConfigSamplesPlanned.put(config.factors(), config.samples());
        }

        List<PerConfigSummary<FT, OT>> perConfigSummariesList() {
            return perConfig.entrySet().stream()
                    .map(entry -> new PerConfigSummary<>(
                            entry.getKey(),
                            entry.getValue(),
                            perConfigSamplesPlanned.getOrDefault(entry.getKey(), 0)))
                    .toList();
        }

        @Override public EngineResult conclude(BaselineProvider provider) {
            Path dir = Paths.get("explorations", experimentId);
            StringBuilder message = new StringBuilder("explore artefact (stage 2 placeholder); configurations=")
                    .append(perConfig.size());
            renderPerConfigHistograms(message);
            return new ExperimentResult(message.toString(), dir);
        }

        /**
         * Renders the per-configuration postcondition-failure histograms
         * as a "diff-style" appendix to the explore artefact message —
         * one block per grid point, so readers see at a glance which
         * configurations tripped which clauses. Configurations with no
         * postcondition failures emit a one-line marker so the column
         * structure stays visible.
         *
         * <p>This is a placeholder rendering until the typed-pipeline
         * gains a real explore-diff output writer; once that lands, the
         * same per-config data feeds into a structured artefact instead
         * of an inline message string.
         */
        private void renderPerConfigHistograms(StringBuilder out) {
            boolean anyHistogram = false;
            for (var summary : perConfig.values()) {
                if (!summary.failuresByPostcondition().isEmpty()) {
                    anyHistogram = true;
                    break;
                }
            }
            if (!anyHistogram) {
                return;
            }
            out.append('\n').append("Failure breakdown by configuration:");
            for (var entry : perConfig.entrySet()) {
                out.append('\n').append("  config=").append(entry.getKey()).append(':');
                var hist = entry.getValue().failuresByPostcondition();
                if (hist.isEmpty()) {
                    out.append(" (no postcondition failures)");
                    continue;
                }
                var ordered = hist.entrySet().stream()
                        .sorted((a, b) -> Integer.compare(b.getValue().count(), a.getValue().count()))
                        .toList();
                for (var clause : ordered) {
                    out.append('\n').append("    ").append(clause.getKey())
                       .append(": ").append(clause.getValue().count()).append(" failure")
                       .append(clause.getValue().count() == 1 ? "" : "s");
                }
            }
        }
    }

    // mavai-ref: JVI-HGF78G* — do not remove (resolves in mavai-orchestrator)
    public static final class ExploreBuilder<FT, IT, OT> {

        private final Sampling<FT, IT, OT> sampling;
        private List<FT> grid;
        private String experimentId;
        private FT baseConfiguration;

        private ExploreBuilder(Sampling<FT, IT, OT> sampling) {
            this.sampling = sampling;
        }

        /**
         * The grid of factors instances to explore — one configuration
         * per element. Required, non-empty.
         */
        public ExploreBuilder<FT, IT, OT> grid(List<FT> grid) {
            Objects.requireNonNull(grid, "grid");
            if (grid.isEmpty()) {
                throw new IllegalArgumentException("grid must be non-empty");
            }
            this.grid = List.copyOf(grid);
            return this;
        }

        @SafeVarargs
        public final ExploreBuilder<FT, IT, OT> grid(FT... grid) {
            Objects.requireNonNull(grid, "grid");
            return grid(List.of(grid));
        }

        public ExploreBuilder<FT, IT, OT> experimentId(String id) {
            this.experimentId = Objects.requireNonNull(id, "experimentId");
            return this;
        }

        /**
         * Names the grid element the sweep was built around — the base
         * configuration whose factor values the other grid points vary.
         * Optional: a grid that is a plain product of factor values has
         * no base, and states none. Where there is one, stating it is
         * what lets a consumer present the comparison as deviations
         * from a known original instead of guessing an anchor, which a
         * balanced grid makes impossible.
         *
         * @param factors the base configuration, which must be a grid element.
         */
        public ExploreBuilder<FT, IT, OT> baseConfiguration(FT factors) {
            this.baseConfiguration = Objects.requireNonNull(factors, "baseConfiguration");
            return this;
        }

        public Experiment build() {
            if (grid == null) {
                throw new IllegalStateException("grid is required — call .grid(...)");
            }
            if (baseConfiguration != null && !grid.contains(baseConfiguration)) {
                throw new IllegalArgumentException(
                        "the base configuration is not a grid element — it names the grid "
                                + "point the sweep was built around, so it must be explored too");
            }
            return new Experiment(Kind.EXPLORE, new ExploreInternal<>(this));
        }
    }

    // ── Optimize ─────────────────────────────────────────────────────

    private static final class OptimizeInternal<FT, IT, OT> extends Internal<FT, IT, OT> {

        private final FT initialFactors;
        private final FactorsStepper<FT> stepper;
        private final Scorer scorer;
        private final boolean maximizing;
        private final int maxIterations;
        private final int noImprovementWindow;

        private final List<IterationResult<FT>> history = new ArrayList<>();
        private final List<SampleSummary<OT>> iterationSummaries = new ArrayList<>();
        String terminationReason;

        private OptimizeInternal(OptimizeBuilder<FT, IT, OT> b) {
            super(b.sampling, b.experimentId != null ? b.experimentId : defaultId("optimize"));
            this.initialFactors = b.initialFactors;
            this.stepper = b.stepper;
            this.scorer = b.scorer;
            this.maximizing = b.maximizing;
            this.maxIterations = b.maxIterations;
            this.noImprovementWindow = b.noImprovementWindow;
        }

        @Override public Iterator<Configuration<FT, IT, OT>> configurations() {
            return new AdaptiveIterator();
        }

        @Override public void consume(Configuration<FT, IT, OT> config, SampleSummary<OT> summary) {
            double score = scorer.score(summary);
            history.add(new IterationResult<>(
                    config.factors(),
                    score,
                    summary.failuresByPostcondition(),
                    flattenExemplars(summary.failuresByPostcondition()),
                    summary.successes(),
                    summary.failures(),
                    summary.total()));
            iterationSummaries.add(summary);
        }

        private static List<FailureExemplar> flattenExemplars(
                Map<String, FailureCount> byClause) {
            List<FailureExemplar> all = new ArrayList<>();
            for (FailureCount bucket : byClause.values()) {
                all.addAll(bucket.exemplars());
            }
            return List.copyOf(all);
        }

        @Override public EngineResult conclude(BaselineProvider provider) {
            IterationResult<FT> best = bestSoFar();
            Path dir = Paths.get("optimizations", experimentId);
            String message = "optimize artefact (stage 2 placeholder); iterations="
                    + history.size()
                    + (best == null ? "" : ", bestScore=" + String.format("%.3f", best.score()));
            return new ExperimentResult(message, dir);
        }

        private IterationResult<FT> bestSoFar() {
            IterationResult<FT> best = null;
            for (IterationResult<FT> r : history) {
                if (best == null || isBetter(r.score(), best.score())) {
                    best = r;
                }
            }
            return best;
        }

        private boolean isBetter(double a, double b) {
            return maximizing ? a > b : a < b;
        }

        private final class AdaptiveIterator implements Iterator<Configuration<FT, IT, OT>> {

            private FT next = initialFactors;
            private int issued = 0;
            private int iterationsSinceImprovement = 0;
            private double bestScoreSeen = Double.NaN;

            @Override public boolean hasNext() {
                refresh();
                return next != null;
            }

            @Override public Configuration<FT, IT, OT> next() {
                refresh();
                if (next == null) {
                    throw new NoSuchElementException();
                }
                FT factors = next;
                next = null;
                issued++;
                return Configuration.of(factors, sampling.inputs(), sampling.samples());
            }

            private void refresh() {
                if (next != null) {
                    return;
                }
                if (issued >= maxIterations) {
                    if (terminationReason == null) {
                        terminationReason = "MAX_ITERATIONS";
                    }
                    return;
                }
                if (!history.isEmpty()) {
                    IterationResult<FT> last = history.get(history.size() - 1);
                    if (Double.isNaN(bestScoreSeen)
                            || isBetter(last.score(), bestScoreSeen)) {
                        bestScoreSeen = last.score();
                        iterationsSinceImprovement = 0;
                    } else {
                        iterationsSinceImprovement++;
                    }
                    if (iterationsSinceImprovement >= noImprovementWindow) {
                        if (terminationReason == null) {
                            terminationReason = "NO_IMPROVEMENT";
                        }
                        return;
                    }
                    FT current = last.factors();
                    NextFactor<FT> candidate = stepper.next(current,
                            Collections.unmodifiableList(history));
                    switch (candidate) {
                        case NextFactor.Continue<FT> c -> next = c.factor();
                        case NextFactor.Stop<FT> s -> {
                            if (terminationReason == null) {
                                terminationReason = "STEPPER_STOP";
                            }
                            /* leave next null; loop ends */
                        }
                    }
                }
            }
        }
    }

    // mavai-ref: JVI-PS5XC2C — do not remove (resolves in mavai-orchestrator)
    public static final class OptimizeBuilder<FT, IT, OT> {

        private final Sampling<FT, IT, OT> sampling;
        private FT initialFactors;
        private FactorsStepper<FT> stepper;
        private Scorer scorer;
        private boolean maximizing;
        private boolean directionSet;
        private int maxIterations = 20;
        private int noImprovementWindow = 5;
        private String experimentId;

        private OptimizeBuilder(Sampling<FT, IT, OT> sampling) {
            this.sampling = sampling;
        }

        public OptimizeBuilder<FT, IT, OT> initialFactors(FT factors) {
            this.initialFactors = Objects.requireNonNull(factors, "initialFactors");
            return this;
        }

        public OptimizeBuilder<FT, IT, OT> stepper(FactorsStepper<FT> s) {
            this.stepper = Objects.requireNonNull(s, "stepper");
            return this;
        }

        /**
         * Sets the scorer and declares the optimisation direction as
         * "maximise the score." Mutually exclusive with
         * {@link #minimize(Scorer)} — last one called wins. One of the
         * two is required.
         */
        public OptimizeBuilder<FT, IT, OT> maximize(Scorer scorer) {
            this.scorer = Objects.requireNonNull(scorer, "scorer");
            this.maximizing = true;
            this.directionSet = true;
            return this;
        }

        /**
         * Sets the scorer and declares the optimisation direction as
         * "minimise the score." Mutually exclusive with
         * {@link #maximize(Scorer)} — last one called wins. One of the
         * two is required.
         */
        public OptimizeBuilder<FT, IT, OT> minimize(Scorer scorer) {
            this.scorer = Objects.requireNonNull(scorer, "scorer");
            this.maximizing = false;
            this.directionSet = true;
            return this;
        }

        public OptimizeBuilder<FT, IT, OT> maxIterations(int n) {
            if (n < 1) {
                throw new IllegalArgumentException("maxIterations must be >= 1");
            }
            this.maxIterations = n;
            return this;
        }

        public OptimizeBuilder<FT, IT, OT> noImprovementWindow(int n) {
            if (n < 1) {
                throw new IllegalArgumentException("noImprovementWindow must be >= 1");
            }
            this.noImprovementWindow = n;
            return this;
        }

        /**
         * Run the full {@code maxIterations} regardless of score
         * progress. Disables the optimize loop's no-improvement-window
         * heuristic so the run continues even when consecutive
         * iterations fail to improve the score.
         *
         * <p>Use when the iteration count itself is the iteration plan
         * (a fixed numeric sweep, a stepper that exhausts a finite set
         * of values) and the heuristic is not the right stopping
         * signal.
         *
         * <p>Unrelated to the statistical early-termination of
         * probabilistic tests, which short-circuits a sample loop
         * once the verdict is mathematically determined. That
         * mechanism lives on a different builder and is not affected
         * by this method.
         */
        public OptimizeBuilder<FT, IT, OT> disableEarlyTermination() {
            this.noImprovementWindow = Integer.MAX_VALUE;
            return this;
        }

        public OptimizeBuilder<FT, IT, OT> experimentId(String id) {
            this.experimentId = Objects.requireNonNull(id, "experimentId");
            return this;
        }

        public Experiment build() {
            if (initialFactors == null) {
                throw new IllegalStateException("initialFactors is required");
            }
            if (stepper == null) {
                throw new IllegalStateException("stepper is required");
            }
            if (!directionSet) {
                throw new IllegalStateException(
                        "scorer is required — call .maximize(...) or .minimize(...)");
            }
            return new Experiment(Kind.OPTIMIZE, new OptimizeInternal<>(this));
        }
    }

    // ── Inline builders ─────────────────────────────────────────────

    /**
     * Carries the sampling-level state (service contract factory, inputs,
     * samples, governors) shared by all inline experiment builders.
     * Subclasses add the kind-specific overlay state.
     */
    private static class InlineSamplingState<FT, IT, OT> {

        final Function<FT, ServiceContract<FT, IT, OT>> serviceContractFactory;
        List<IT> inputs;
        int samples = 1000;
        Optional<Duration> timeBudget = Optional.empty();
        OptionalLong tokenBudget = OptionalLong.empty();
        long tokenCharge = 0L;
        BudgetExhaustionPolicy budgetPolicy = BudgetExhaustionPolicy.FAIL;
        ExceptionPolicy exceptionPolicy = ExceptionPolicy.ABORT_TEST;
        int maxExampleFailures = 10;

        InlineSamplingState(Function<FT, ServiceContract<FT, IT, OT>> serviceContractFactory) {
            this.serviceContractFactory = serviceContractFactory;
        }

        Sampling<FT, IT, OT> toSampling() {
            if (inputs == null) {
                throw new IllegalStateException(
                        "inputs is required — call .inputs(...) before .build()");
            }
            Sampling.Builder<FT, IT, OT> b = Sampling.<FT, IT, OT>builder()
                    .serviceContractFactory(serviceContractFactory)
                    .inputs(inputs)
                    .samples(samples);
            timeBudget.ifPresent(b::timeBudget);
            tokenBudget.ifPresent(b::tokenBudget);
            if (tokenCharge > 0L) {
                b.tokenCharge(tokenCharge);
            }
            b.onBudgetExhausted(budgetPolicy);
            b.onException(exceptionPolicy);
            b.maxExampleFailures(maxExampleFailures);
            return b.build();
        }
    }

    // mavai-ref: JVI-SVDWG0G — do not remove (resolves in mavai-orchestrator)
    public static final class InlineMeasureBuilder<FT, IT, OT> {

        private final InlineSamplingState<FT, IT, OT> sampling;
        private final FT factors;
        private String experimentId;
        private Integer expiresInDays;

        private InlineMeasureBuilder(Function<FT, ServiceContract<FT, IT, OT>> serviceContractFactory, FT factors) {
            this.sampling = new InlineSamplingState<>(serviceContractFactory);
            this.factors = factors;
        }

        // Sampling-level setters
        public InlineMeasureBuilder<FT, IT, OT> inputs(List<IT> inputs) {
            Objects.requireNonNull(inputs, "inputs");
            if (inputs.isEmpty()) {
                throw new IllegalArgumentException("inputs must be non-empty");
            }
            sampling.inputs = List.copyOf(inputs);
            return this;
        }

        @SafeVarargs
        public final InlineMeasureBuilder<FT, IT, OT> inputs(IT... inputs) {
            return inputs(List.of(Objects.requireNonNull(inputs, "inputs")));
        }

        public InlineMeasureBuilder<FT, IT, OT> samples(int samples) {
            if (samples < 1) {
                throw new IllegalArgumentException("samples must be >= 1, got " + samples);
            }
            sampling.samples = samples;
            return this;
        }

        public InlineMeasureBuilder<FT, IT, OT> timeBudget(Duration budget) {
            Objects.requireNonNull(budget, "timeBudget");
            sampling.timeBudget = Optional.of(budget);
            return this;
        }

        public InlineMeasureBuilder<FT, IT, OT> tokenBudget(long tokens) {
            sampling.tokenBudget = OptionalLong.of(tokens);
            return this;
        }

        public InlineMeasureBuilder<FT, IT, OT> tokenCharge(long tokens) {
            sampling.tokenCharge = tokens;
            return this;
        }

        public InlineMeasureBuilder<FT, IT, OT> onBudgetExhausted(BudgetExhaustionPolicy policy) {
            sampling.budgetPolicy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        public InlineMeasureBuilder<FT, IT, OT> onException(ExceptionPolicy policy) {
            sampling.exceptionPolicy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        public InlineMeasureBuilder<FT, IT, OT> maxExampleFailures(int cap) {
            sampling.maxExampleFailures = cap;
            return this;
        }

        // Measure-overlay setters
        public InlineMeasureBuilder<FT, IT, OT> experimentId(String id) {
            this.experimentId = Objects.requireNonNull(id, "experimentId");
            return this;
        }

        public InlineMeasureBuilder<FT, IT, OT> expiresInDays(int days) {
            if (days < 0) {
                throw new IllegalArgumentException("expiresInDays must be non-negative, got " + days);
            }
            this.expiresInDays = days;
            return this;
        }

        public Experiment build() {
            MeasureBuilder<FT, IT, OT> delegate = new MeasureBuilder<>(
                    sampling.toSampling(), factors);
            if (experimentId != null) {
                delegate.experimentId(experimentId);
            }
            if (expiresInDays != null) {
                delegate.expiresInDays(expiresInDays);
            }
            return delegate.build();
        }
    }

    // mavai-ref: JVI-SVDWG0G — do not remove (resolves in mavai-orchestrator)
    public static final class InlineExploreBuilder<FT, IT, OT> {

        private final InlineSamplingState<FT, IT, OT> sampling;
        private List<FT> grid;
        private String experimentId;

        private InlineExploreBuilder(Function<FT, ServiceContract<FT, IT, OT>> serviceContractFactory) {
            this.sampling = new InlineSamplingState<>(serviceContractFactory);
        }

        public InlineExploreBuilder<FT, IT, OT> inputs(List<IT> inputs) {
            Objects.requireNonNull(inputs, "inputs");
            if (inputs.isEmpty()) {
                throw new IllegalArgumentException("inputs must be non-empty");
            }
            sampling.inputs = List.copyOf(inputs);
            return this;
        }

        @SafeVarargs
        public final InlineExploreBuilder<FT, IT, OT> inputs(IT... inputs) {
            return inputs(List.of(Objects.requireNonNull(inputs, "inputs")));
        }

        public InlineExploreBuilder<FT, IT, OT> samples(int samples) {
            if (samples < 1) {
                throw new IllegalArgumentException("samples must be >= 1, got " + samples);
            }
            sampling.samples = samples;
            return this;
        }

        public InlineExploreBuilder<FT, IT, OT> timeBudget(Duration budget) {
            sampling.timeBudget = Optional.of(Objects.requireNonNull(budget, "timeBudget"));
            return this;
        }

        public InlineExploreBuilder<FT, IT, OT> tokenBudget(long tokens) {
            sampling.tokenBudget = OptionalLong.of(tokens);
            return this;
        }

        public InlineExploreBuilder<FT, IT, OT> tokenCharge(long tokens) {
            sampling.tokenCharge = tokens;
            return this;
        }

        public InlineExploreBuilder<FT, IT, OT> onBudgetExhausted(BudgetExhaustionPolicy policy) {
            sampling.budgetPolicy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        public InlineExploreBuilder<FT, IT, OT> onException(ExceptionPolicy policy) {
            sampling.exceptionPolicy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        public InlineExploreBuilder<FT, IT, OT> maxExampleFailures(int cap) {
            sampling.maxExampleFailures = cap;
            return this;
        }

        public InlineExploreBuilder<FT, IT, OT> grid(List<FT> grid) {
            Objects.requireNonNull(grid, "grid");
            if (grid.isEmpty()) {
                throw new IllegalArgumentException("grid must be non-empty");
            }
            this.grid = List.copyOf(grid);
            return this;
        }

        @SafeVarargs
        public final InlineExploreBuilder<FT, IT, OT> grid(FT... grid) {
            return grid(List.of(Objects.requireNonNull(grid, "grid")));
        }

        public InlineExploreBuilder<FT, IT, OT> experimentId(String id) {
            this.experimentId = Objects.requireNonNull(id, "experimentId");
            return this;
        }

        public Experiment build() {
            if (grid == null) {
                throw new IllegalStateException("grid is required — call .grid(...)");
            }
            ExploreBuilder<FT, IT, OT> delegate = new ExploreBuilder<>(sampling.toSampling())
                    .grid(grid);
            if (experimentId != null) {
                delegate.experimentId(experimentId);
            }
            return delegate.build();
        }
    }

    // mavai-ref: JVI-SVDWG0G — do not remove (resolves in mavai-orchestrator)
    public static final class InlineOptimizeBuilder<FT, IT, OT> {

        private final InlineSamplingState<FT, IT, OT> sampling;
        private FT initialFactors;
        private FactorsStepper<FT> stepper;
        private Scorer scorer;
        private boolean maximizing;
        private boolean directionSet;
        private int maxIterations = 20;
        private int noImprovementWindow = 5;
        private String experimentId;

        private InlineOptimizeBuilder(Function<FT, ServiceContract<FT, IT, OT>> serviceContractFactory) {
            this.sampling = new InlineSamplingState<>(serviceContractFactory);
        }

        public InlineOptimizeBuilder<FT, IT, OT> inputs(List<IT> inputs) {
            Objects.requireNonNull(inputs, "inputs");
            if (inputs.isEmpty()) {
                throw new IllegalArgumentException("inputs must be non-empty");
            }
            sampling.inputs = List.copyOf(inputs);
            return this;
        }

        @SafeVarargs
        public final InlineOptimizeBuilder<FT, IT, OT> inputs(IT... inputs) {
            return inputs(List.of(Objects.requireNonNull(inputs, "inputs")));
        }

        public InlineOptimizeBuilder<FT, IT, OT> samples(int samples) {
            if (samples < 1) {
                throw new IllegalArgumentException("samples must be >= 1, got " + samples);
            }
            sampling.samples = samples;
            return this;
        }

        public InlineOptimizeBuilder<FT, IT, OT> timeBudget(Duration budget) {
            sampling.timeBudget = Optional.of(Objects.requireNonNull(budget, "timeBudget"));
            return this;
        }

        public InlineOptimizeBuilder<FT, IT, OT> tokenBudget(long tokens) {
            sampling.tokenBudget = OptionalLong.of(tokens);
            return this;
        }

        public InlineOptimizeBuilder<FT, IT, OT> tokenCharge(long tokens) {
            sampling.tokenCharge = tokens;
            return this;
        }

        public InlineOptimizeBuilder<FT, IT, OT> onBudgetExhausted(BudgetExhaustionPolicy policy) {
            sampling.budgetPolicy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        public InlineOptimizeBuilder<FT, IT, OT> onException(ExceptionPolicy policy) {
            sampling.exceptionPolicy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        public InlineOptimizeBuilder<FT, IT, OT> maxExampleFailures(int cap) {
            sampling.maxExampleFailures = cap;
            return this;
        }

        public InlineOptimizeBuilder<FT, IT, OT> initialFactors(FT factors) {
            this.initialFactors = Objects.requireNonNull(factors, "initialFactors");
            return this;
        }

        public InlineOptimizeBuilder<FT, IT, OT> stepper(FactorsStepper<FT> s) {
            this.stepper = Objects.requireNonNull(s, "stepper");
            return this;
        }

        public InlineOptimizeBuilder<FT, IT, OT> maximize(Scorer scorer) {
            this.scorer = Objects.requireNonNull(scorer, "scorer");
            this.maximizing = true;
            this.directionSet = true;
            return this;
        }

        public InlineOptimizeBuilder<FT, IT, OT> minimize(Scorer scorer) {
            this.scorer = Objects.requireNonNull(scorer, "scorer");
            this.maximizing = false;
            this.directionSet = true;
            return this;
        }

        public InlineOptimizeBuilder<FT, IT, OT> maxIterations(int n) {
            if (n < 1) {
                throw new IllegalArgumentException("maxIterations must be >= 1");
            }
            this.maxIterations = n;
            return this;
        }

        public InlineOptimizeBuilder<FT, IT, OT> noImprovementWindow(int n) {
            if (n < 1) {
                throw new IllegalArgumentException("noImprovementWindow must be >= 1");
            }
            this.noImprovementWindow = n;
            return this;
        }

        /** See {@link OptimizeBuilder#disableEarlyTermination()}. */
        public InlineOptimizeBuilder<FT, IT, OT> disableEarlyTermination() {
            this.noImprovementWindow = Integer.MAX_VALUE;
            return this;
        }

        public InlineOptimizeBuilder<FT, IT, OT> experimentId(String id) {
            this.experimentId = Objects.requireNonNull(id, "experimentId");
            return this;
        }

        public Experiment build() {
            if (initialFactors == null) {
                throw new IllegalStateException("initialFactors is required");
            }
            if (stepper == null) {
                throw new IllegalStateException("stepper is required");
            }
            if (!directionSet) {
                throw new IllegalStateException(
                        "scorer is required — call .maximize(...) or .minimize(...)");
            }
            OptimizeBuilder<FT, IT, OT> delegate = new OptimizeBuilder<>(sampling.toSampling())
                    .initialFactors(initialFactors)
                    .stepper(stepper)
                    .maxIterations(maxIterations)
                    .noImprovementWindow(noImprovementWindow);
            if (maximizing) {
                delegate.maximize(scorer);
            } else {
                delegate.minimize(scorer);
            }
            if (experimentId != null) {
                delegate.experimentId(experimentId);
            }
            return delegate.build();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private static String defaultId(String kind) {
        return kind + "-" + Instant.now().toEpochMilli();
    }

    static Path defaultBaselinePath(String experimentId, FactorBundle bundle) {
        String filename = experimentId;
        if (!bundle.isEmpty()) {
            filename = filename + "-" + bundle.bundleHash();
        }
        return Paths.get("specs", filename + ".yaml");
    }
}
