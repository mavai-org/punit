# punit Domain Ontology

This document maps the mavai.org framework family's shared domain
concepts onto punit's Java idioms, packages, and types. It is the
conceptual counterpart to [`DEVELOPER-API.md`](DEVELOPER-API.md) —
together they form punit's spec for "what the framework looks like
to a Java developer." This document answers *what kinds of things
live in this codebase and how they relate*; the API doc answers
*what does it look like to use them*.

Cross-framework concepts (shared with feotest and other ports) are
defined identically across the family; this document is the punit-
specific mapping. Where this document conflicts with the
cross-framework definition, this document is wrong and is
corrected.

---

## How to read

For each domain concept this document carries:

- **concept**: the cross-framework concept name.
- **java type**: the Java identifier in punit (interface, class,
  record, annotation, …).
- **package**: where it lives.
- **role notes**: idiom-level notes — what's a record vs sealed
  interface vs enum vs annotation, what's framework-internal vs
  author-facing, what's overridable vs final.
- **gotchas / drift to fix**: punit-specific points an agent must
  preserve when modifying or generating code.

Cross-framework invariants and policies are not restated here.
Where punit *enforces* one via a specific test or architectural
rule, the enforcement mechanism is named (typically the relevant
ArchUnit-style architecture test).

---

## Subject under test

```yaml
- concept: Service Contract
  java_type: ServiceContract<FT, IT, OT>          # interface (sealed by extension to Contract)
  package: org.mavai.punit.api
  role_notes: |
    Sealed-by-convention: extends Contract<IT, OT>. An author writes
    one `class X implements ServiceContract<F, I, O>` and overrides two
    methods — `invoke` and `criteria()` (returning a `Criteria<O>`
    value), plus whichever metadata methods diverge from defaults
    (`id`, `pacing`, `warmup`, `covariates`, `inputSource`,
    `latency`, …).
    `FT` is the typed factor record; `IT` the per-sample input;
    `OT` the per-sample output value.
  gotchas:
    - `id()` defaults to a kebab-cased simple class name. Override
      when the default would collide or when a stable id is needed
      across renames.
    - The framework caches and reuses one instance per factor
      configuration. Implementations may carry per-configuration
      state but must not mutate it in ways that change observable
      `invoke` behaviour.
    - Factor and Covariate names must be disjoint (cross-framework
      invariant).

- concept: Service Contract ID
  java_type: String (returned by ServiceContract#id())
  package: org.mavai.punit.api
  role_notes: |
    Filename-safe, stable across runs. Path component for emitted
    artefacts (baselines, explorations, optimizations).
  gotchas:
    - Renaming an id without baseline migration breaks every
      committed baseline addressed by that id. Treat as schema
      change.

- concept: Factor
  java_type: typed factor record (author-defined) + Factor / FactorValue / FactorBundle support types
  package: org.mavai.punit.api
  role_notes: |
    The factor type is a Java record the author declares. The
    framework reads its components reflectively for filename
    composition and covariate alignment. `NoFactors` is a
    canonical empty factor type — use it, do not invent your own
    empty record.
  gotchas:
    - Drop the `with_` prefix on builder/fluent factor methods
      (project-wide rule — see cross-framework ontology assistant guidance).
    - **Term to avoid: "factor level".** The canonical mavai term
      is "factor value". "Factor level" survives only in
      `mavai-R/docs/GLOSSARY.md` as a Design-of-Experiments nod.
      Two javadoc surfaces in `Factor.java` and `Config.java` still
      use "factor levels" — sweep when next editing those files.

- concept: Covariate
  java_type: Covariate (interface) + CovariateProfile + per-category implementations
  package: org.mavai.punit.api.covariate
  role_notes: |
    Covariates are first-class members of baseline identity, not
    metadata. Built-in categories: `DayOfWeekCovariate`,
    `RegionCovariate`, `TimeOfDayCovariate`, `TimezoneCovariate`,
    `CustomCovariate`. Authors most often declare covariates by
    overriding `ServiceContract#covariates()` to return a list of these.
  gotchas:
    - When a Service Contract declares Covariates, every artefact path
      that emits or consumes a baseline for that Service Contract MUST
      carry the covariate profile (cross-framework invariant —
      "Covariate-as-identity").
    - `api.covariate.CovariateProfile` is the sole canonical home;
      no other CovariateProfile exists in the codebase.

- concept: Input Source
  java_type: InputSource<IT> + InputSupplier<IT>
  package: org.mavai.punit.api
  role_notes: |
    Backed by an in-memory list, a JSON file fixture
    (`InputSource.fromJsonFile(...)`), or a custom
    `InputSupplier`. The sample loop traverses round-robin.
```

---

## Judgement under test

```yaml
- concept: Service Contract
  java_type: Contract<IT, OT>             # interface
  package: org.mavai.punit.api
  role_notes: |
    The operational layer. Carries `invoke` and `criteria()`
    (returning a `Criteria<O>` value). ServiceContract extends
    Contract; the abstract / concrete split is preserved by
    inheritance.
  gotchas:
    - **Two "service contract" senses live in the codebase.** The
      *subject* concept is the `ServiceContract<F, I, O>`
      interface — the thing under test. The *judgement* concept
      lives in `Contract<I, O>` — the criteria declared via
      `criteria()`, plus the optional `latency()` commitment.
      `ServiceContract` extends `Contract`. In prose, prefer
      "Acceptance Contract" or "criteria" for the judgement
      concept to avoid the overload.

- concept: Criterion clause (postcondition)
  java_type: Decl::satisfies / ::transforming (returned by Criteria.meeting() / Criteria.empirical())
  package: org.mavai.punit.api.criterion
  role_notes: |
    Authors declare clauses by chaining `.satisfies(name, predicate)`
    on a `Decl` returned from a `Criteria.meeting()` or
    `Criteria.empirical()` factory inside the service contract's
    `criteria()` method. Predicates return `Outcome<Void>`:
    `Outcome.ok()` on pass, `Outcome.fail(name, message)` on fail.
    `.transforming(function)` adds a derivation step; subsequent
    `.satisfies(...)` calls evaluate against the transformed value.
  gotchas:
    - Do not move contract judgement out of `.satisfies(...)` into
      `invoke`. Failure detail (clause-named diagnostic) only
      arises when the clause is registered through a criterion
      declaration.
    - Overriding `criteria()` to return a `Criteria<O>` value is
      the canonical way authors declare acceptance criteria.

- concept: Duration Constraint
  java_type: ServiceContract#maxLatency() returning Optional<Duration>
  package: org.mavai.punit.api
  role_notes: |
    Per-sample wall-clock bound. Independent of the criteria
    clauses: a duration violation is recorded even if every
    `.satisfies(...)` clause passes (cross-framework invariant —
    "Service Contract aspect independence").
  gotchas:
    - Do not conflate with PercentileLatency criterion. Duration
      Constraint is per-sample, contractual, fail-fast. Latency
      Assertion is per-test, distributional, percentile-based.

- concept: Expected-Output Match
  java_type: ValueMatcher<OT> + Expectation<OT>
  package: org.mavai.punit.api
  role_notes: |
    Golden-value comparison. Built-in matchers: exact,
    case-insensitive, JSON-structural; custom matchers compose.
    Author-facing API exposes the matcher composition surface and
    the `Expectation` carrier; engine-side dispatch is internal.

- concept: Conformance
  java_type: ServiceContractOutcome<OT> (the per-sample state) + criterion-level evaluation
  package: org.mavai.punit.api (ServiceContractOutcome) + org.mavai.punit.api.spec (criterion path)
  role_notes: |
    `ServiceContractOutcome` carries the per-sample data: timestamped
    wrapper of the `Outcome<OT>`, duration, token charge, clause
    results. The criterion-level conformance pass-rate is
    aggregated by the engine and read by `PassRate`.
  gotchas:
    - `api.ServiceContractOutcome` is the canonical version. A duplicate
      `contract.ServiceContractOutcome` exists in the dead package and is
      scheduled for deletion. Always import from `api`.
```

---

## The act of testing

```yaml
- concept: Sample
  java_type: per-sample tuple (Outcome<OT> + duration + token diff)
              materialised inside Engine; surfaced via SampleSummary
  package: org.mavai.punit.internal.engine + org.mavai.punit.api.spec
  role_notes: |
    Authors do not write Sample — the framework produces it.
    `SampleSummary<OT>` is the aggregated form a criterion sees.
    `SampleClassification` and `Trial` are the fine-grained shapes
    inside `api/spec`.

- concept: Outcome
  java_type: org.mavai.outcome.Outcome<T>     # sealed interface (Ok | Fail)
  package: org.mavai.outcome (external library)
  role_notes: |
    Imported from the `org.mavai:outcome` library; not punit's
    invention. punit also publishes `ServiceContractOutcome<OT>` as the
    per-sample wrapper carrying the `Outcome<OT>` value plus
    cost / timing / clauses. Authors return `Outcome.ok(value)`
    or `Outcome.fail(name, message)` from `invoke`.
  gotchas:
    - **Family invariant — Outcome vs exception discipline.**
      Expected failures travel via Outcome.fail; defects throw.
      The framework does not catch exceptions from invoke; a
      thrown exception aborts the run (defect signal).
    - Do not subclass `Outcome<T>` — sealed by design. Per-project
      additions belong on `ServiceContractOutcome`, not on Outcome.

- concept: Probabilistic Test
  java_type: ProbabilisticTest (api.spec interface) + @ProbabilisticTest annotation
  package: org.mavai.punit.api.spec (spec) + org.mavai.punit.api (annotation)
  role_notes: |
    The annotation `@ProbabilisticTest` is attribute-free. Every
    parameter lives on the spec built inside the method body via
    `PUnit.testing(...)`. The annotation is meta-tagged
    `@Test @Tag("punit")`.
  gotchas:
    - The api.spec `ProbabilisticTest` (the spec type) and the
      api `ProbabilisticTest` (the annotation) share a name and
      different roles. In code, fully qualify when both are in
      scope; in prose, "the test annotation" vs "the test spec".

- concept: Verdict
  java_type: PUnitVerdict (sealed interface) + ProbabilisticTestVerdict (record)
  package: org.mavai.punit.verdict
  role_notes: |
    `PUnitVerdict` is the sealed common surface; the concrete
    record `ProbabilisticTestVerdict` is what every test produces.
    The terminal builder methods on PUnit (`assertPasses`, `run`,
    `build`) translate the Verdict into the test-harness
    convention: PASS → JUnit pass; FAIL → AssertionFailedError;
    INCONCLUSIVE → TestAbortedException (configuration / drift,
    not service degradation).
  gotchas:
    - JUnit always reports pass for punit tests because punit
      owns the verdict. The framework's own Verdict is the
      meaningful artefact (cross-framework ontology — Verdict).
```

---

## Methodology and statistics

```yaml
- concept: Parameter Triangle
  java_type: surfaced via TestBuilder#samples / criterion shapes
  package: org.mavai.punit.runtime (builder) + org.mavai.punit.api.spec (spec)
  role_notes: |
    Three approaches: sample-size-first (samples + criterion;
    framework derives implied claim), confidence-first (criterion
    declares confidence + power + MDE; framework computes
    samples), threshold-first (criterion declares threshold
    explicitly).

- concept: Threshold (pass-rate)
  java_type: Criteria.meeting() / Criteria.empirical() static factories returning Decl
  package: org.mavai.punit.api.criterion (author surface) +
           org.mavai.punit.internal.engine.criteria (PassRate impl)
  role_notes: |
    Two author-facing forms:
    `meeting().passRate(threshold).contractRef(origin, ref)`
    (NORMATIVE, declared) and `empirical().passRate()` (closest-
    match baseline lookup at evaluation time). Both lower to the
    internal `PassRate` criterion under `engine.criteria`, whose
    `evaluate()` consumes `BinomialProportionEstimator` —
    `Criterion` itself is in `api.spec`, but concrete criteria
    that touch statistics live in `engine.criteria`.
  gotchas:
    - **Family invariant — Statistical isolation.** A criterion
      may *call* statistical machinery; it must not contain
      statistical *arithmetic*. PassRate delegates to
      `BinomialProportionEstimator` and `ThresholdDeriver`. If
      ever tempted to inline a Wilson here, stop — the calculation
      belongs in `org.mavai.punit.statistics`.

- concept: Threshold (latency)
  java_type: LatencyCriterion returned by ServiceContract#latency()
  package: org.mavai.punit.api.criterion (author surface) +
           org.mavai.punit.internal.engine.criteria (PercentileLatency impl)
  role_notes: |
    Latency thresholds are declared by overriding
    `LatencyCriterion latency()` on the service contract,
    typically as `meeting().atMost(P95, duration).contractRef(origin, ref)`.
    The contract has either zero or one latency commitment;
    that's captured by the singular return type rather than a
    runtime check. Evaluation lives in `engine.criteria` via the
    `PercentileLatency` criterion.

- concept: Threshold Origin
  java_type: ThresholdOrigin enum
  package: org.mavai.punit.api
  role_notes: |
    SLA / SLO / Policy → NORMATIVE (collectively). EMPIRICAL is
    distinct. Origin × Test Intent decides Feasibility Gate
    behaviour.

- concept: Threshold Provenance
  java_type: fields on ProbabilisticTestVerdict + RunMetadata
  package: org.mavai.punit.verdict
  role_notes: |
    Carried through to verdict XML emission. RP07 makes the
    binding (per cross-framework invariant — "Sample-time provenance
    must accompany the verdict").

- concept: Feasibility Gate
  java_type: VerificationFeasibilityEvaluator + Feasibility (engine criterion adapter)
  package: org.mavai.punit.statistics (evaluator) + org.mavai.punit.internal.engine.criteria (adapter)
  role_notes: |
    The pre-execution gate. Verification + NORMATIVE → reject;
    Smoke + NORMATIVE → run with caveat; any + EMPIRICAL → run.
    SMOKE intent does not produce a warning by design (family
    invariant referenced via memory: `smoke_intent_silent_feasibility`).

- concept: Wilson Score Bound
  java_type: BinomialProportionEstimator (two-sided + one-sided)
  package: org.mavai.punit.statistics
  role_notes: |
    Both bounds live in the estimator. The verdict path uses
    only the one-sided lower bound (cross-framework invariant —
    one-sided Wilson in the verdict path). The two-sided form
    is preserved for SC01 (catalog) + diagnostics.
  gotchas:
    - Verdict XML emits `wilson-lower` only; the legacy
      `ci-lower`/`ci-upper` pair was retired with the 0.7.x cleanup.

- concept: Latency Population
  java_type: LatencyDistribution (statistics) + LatencyPercentileComputer (engine)
  package: org.mavai.punit.statistics + org.mavai.punit.internal.engine
  role_notes: |
    **Family invariant — Latency Population purity.** Only
    successful Samples contribute. The `LatencyPercentileComputer`
    filters at aggregation time; the statistics module operates
    on the filtered sequence.

- concept: Empirical Percentile
  java_type: nearest-rank computation in LatencyDistribution
  package: org.mavai.punit.statistics
  role_notes: |
    Non-parametric, distribution-free. Latency thresholds derive
    from the binomial order-statistic upper confidence bound.
```

---

## Cost, pacing, and budget

```yaml
- concept: Token
  java_type: TokenTracker (interface) + InMemoryTokenTracker (engine impl) + TokenChargeRecorder (annotation-driven static charge)
  package: org.mavai.punit.api (tracker, recorder) + org.mavai.punit.internal.engine (impl)
  role_notes: |
    Generic unit of cost — not LLM-specific (family ambiguous
    term: `Token`). Authors call `tracker.recordTokens(n)` inside
    `invoke` for dynamic charges; static charges declare via
    `@TokenChargeRecorder` or builder.

- concept: Budget
  java_type: ResourceControls + ResourceControlsBuilder + BudgetTracker (engine)
  package: org.mavai.punit.api.spec + org.mavai.punit.internal.engine + org.mavai.punit.internal.engine.budget
  role_notes: |
    Time / token / sample-count flavours. Suite / class / method
    scopes; first exhausted budget triggers termination.
    Exhaustion behaviour configurable: FAIL or EVALUATE_PARTIAL.
  gotchas:
    - feotest token-budget pre-sample check has a known
      double-count bug for static charges (memory:
      `feotest_token_budget_double_count`). Punit's path is sound;
      do not port the flaw if cross-pollinating.

- concept: Pacing
  java_type: Pacing record + per-pacing-mode strategies
  package: org.mavai.punit.api + org.mavai.punit.internal.engine.pacing
  role_notes: |
    Pacing belongs on the Service Contract (`ServiceContract#pacing()`), not on
    the spec — every test of the same service should respect the
    same rate limit. Spec builders deliberately do not expose
    pacing knobs.
```

---

## Experimentation

```yaml
- concept: Experiment
  java_type: Experiment (api.spec interface) + @Experiment annotation
  package: org.mavai.punit.api.spec (spec) + org.mavai.punit.api (annotation)
  role_notes: |
    Three modes via the spec's `Experiment.Kind`: MEASURE,
    EXPLORE, OPTIMIZE. The annotation carries no attributes; the
    builder body decides the mode by entry point
    (`PUnit.measuring`, `PUnit.exploring`, `PUnit.optimizing`).
    The `ArtefactEmissionRegressionTest` enforces that every
    Kind has an emit path (compile-time via exhaustive switch).

- concept: Stepper (Factors)
  java_type: FactorsStepper<FT> (interface) + NextFactor<FT> (sealed return)
  package: org.mavai.punit.api.spec
  role_notes: |
    `next` returns `NextFactor.next(factors)` or
    `NextFactor.stop()`. The pre-0.7 `null`-as-stop pattern is
    retired; do not reintroduce.

- concept: Empirical Baseline
  java_type: BaselineProvider + BaselineLookup (api) + BaselineEmitter (runtime)
  package: org.mavai.punit.api.spec + org.mavai.punit.runtime + org.mavai.punit.internal.engine.baseline
  role_notes: |
    Emitted by MeasureBuilder.run() via BaselineEmitter; loaded
    via BaselineProvider; selected via covariate-aware resolver
    in engine.covariate.

- concept: Footprint
  java_type: footprint hash inside engine.baseline + serialised as the EX09 field
  package: org.mavai.punit.internal.engine.baseline
  role_notes: |
    Internal to the engine; surfaces in baseline YAML. Authors
    do not compute footprints by hand.

- concept: Content Fingerprint
  java_type: contentFingerprint field on baseline YAML (EX10)
  package: org.mavai.punit.internal.engine.baseline
  role_notes: |
    Soft-warning on mismatch (per 2026-05 catalog amendments),
    not hard abort.
```

---

## Test intent and policy

```yaml
- concept: Test Intent
  java_type: TestIntent enum (VERIFICATION, SMOKE)
  package: org.mavai.punit.api
  role_notes: |
    Surfaced on the test builder via `.intent(TestIntent.SMOKE)`.
    Default is VERIFICATION.

- concept: Compliance Testing
  java_type: usage pattern: meeting().passRate(τ).contractRef(NORMATIVE, ref) + Verification intent
  package: pattern, not a type
  role_notes: |
    No dedicated class — emerges from criterion + intent
    composition.

- concept: Conformance Testing
  java_type: usage pattern: empirical().passRate() on the contract's criteria(); PUnit.testing(baselineSupplier) on the test
  package: pattern, not a type
  role_notes: |
    Same — pattern, not a type.
```

---

## Reporting

```yaml
- concept: Verdict XML (RP07)
  java_type: VerdictXmlWriter + VerdictXmlReader
  package: org.mavai.punit.report
  role_notes: |
    `punit-report` module. XSD shipped at
    `punit-report/src/main/resources/.../verdict-1.0.xsd`. Must
    diff clean against the cross-framework canonical XSD shared
    with feotest and mavai.org sentinels / dashboards.
  gotchas:
    - Emits `wilson-lower` only on `<statistics>`; the upper bound
      is not emitted (the verdict path is left-tailed). Aligned to
      the canonical XSD as of the 0.7.x cleanup.

- concept: Verdict Sink
  java_type: VerdictSink (interface, public) + VerdictSinkBus (dispatcher, internal)
  package: org.mavai.punit.verdict (interface) +
           org.mavai.punit.internal.reporting (dispatcher)
  role_notes: |
    ServiceLoader-discovered. With `punit-report` on the
    classpath, the XML sink registers automatically. Without
    it, no default sink — explicit registration required.
```

---

## Sentinel

```yaml
- concept: Sentinel
  java_type: SentinelMain + SentinelExecutor + SentinelOrchestrator
  package: org.mavai.punit.sentinel (in punit-sentinel module)
  role_notes: |
    Zero `org.junit` dependencies. The Sentinel reaches the engine
    through `PUnit` / `runtime`, not through any JUnit surface.
    Enforced by `SentinelArchitectureTest`.
  gotchas:
    - **Family invariant — Sentinel build-time baseline embedding.**
      Every EMPIRICAL-origin test must carry its embedded default
      baseline at sentinel build time. Missing → build failure.
      Normative-origin tests are exempt.
    - **Family invariant — Sentinel emits, never installs.** The
      Sentinel may emit a measurement to its measurement output
      but never overwrites the test input automatically.

- concept: Reliability Specification
  java_type: SentinelConfiguration + EnvironmentMetadata
  package: org.mavai.punit.sentinel
  role_notes: |
    Bundle authored alongside the test suite; consumable by the
    suite via test adapter (SN05) and by the Sentinel via CLI.
```

---

## Punit-only concepts (not at the family level)

These exist because the JVM / JUnit / Java idiom requires them.
They are not in the cross-framework ontology because they have no
cross-language analogue (or different languages express them
differently).

```yaml
- name: Annotation marker
  description: |
    `@ProbabilisticTest` and `@Experiment` are bare markers
    (attribute-free, meta-tagged `@Test @Tag("punit")`). All
    configuration lives in the method body via the typed-builder
    surface.
  rationale: |
    The 0.6 → 0.7 redesign moved configuration off annotations
    onto types so it can carry typed factor records, typed
    initial values, typed expected outputs, and so on. The
    annotation survives as the JUnit hook only.

- name: Builder terminal
  description: |
    Each PUnit-entry-point builder ends in one of: `.assertPasses()`
    (probabilistic-test side), `.run()` (experiment side), or
    `.build()` (pair-pattern supplier — an Experiment value handed
    to `PUnit.testing(baselineSupplier)` at the test side).
  rationale: |
    The terminal selects the test-harness translation. Authors
    rarely call `.build()` directly except in the empirical pair
    pattern; the framework would be content with two terminals
    (assertPasses / run) but the supplier-pattern has earned the
    third.

- name: api / api.criterion / api.spec / engine boundary
  description: |
    `org.mavai.punit.api` carries types an author touches when
    *authoring* (ServiceContract, Contract, Sampling, Factor
    support, annotations, intent, origin, value matchers,
    pacing). `org.mavai.punit.api.criterion`
    carries the types an author calls from `criteria()` and
    `latency()`: the `Criteria` static factory, criterion
    declaration types, `LatencyCriterion`, `CriterionPosture`. `api.spec`
    carries the typed spec surface (Experiment, ProbabilisticTest,
    Criterion, EvaluationContext, FactorsStepper, …). `engine.*`
    is internal — engine.criteria (PassRate, PercentileLatency
    impls), engine.baseline, engine.explore, engine.optimize,
    engine.covariate, engine.emit, engine.budget, engine.pacing,
    engine.spec. Authors should never import from `engine.*`.
  enforcement: |
    `CoreArchitectureTest` (package-level rules in punit-core) +
    `AbstractionLevelArchitectureTest` (cross-cutting abstraction
    discipline). See DEVELOPER-API.md §Architecture-test catalogue.

- name: Empirical pair pattern
  description: |
    Author publishes a `Sampling<F, I, O>` helper used by both a
    measure baseline (`@Experiment` returning `Experiment` via
    `PUnit.measuring(...).build()`) and a probabilistic test
    (`@ProbabilisticTest` calling `PUnit.testing(this::baseline)`).
    The service contract declares the empirical posture
    (`empirical().passRate()`) in `criteria()`; the test passes
    the baseline supplier directly to `PUnit.testing(...)`. Java
    reference semantics enforce that the test and the baseline
    draw from the same sampling population — same service
    contract, same inputs, same factors, same governors.
  rationale: |
    Statistical coherence of "has the service degraded since the
    baseline?" requires the test and baseline to be drawn from
    the same sampling population. By passing the same `Sampling`
    value to both, the language enforces it.
```

---

## Lifecycles in punit terms

```yaml
- concept: probabilistic test execution (Java)
  states: [annotation_dispatched, builder_constructed, spec_built,
           feasibility_checked, sampling, terminated, verdict_built,
           sink_dispatched, junit_translated]
  notes: |
    `@ProbabilisticTest` is a marker annotation meta-annotated with
    `@org.junit.jupiter.api.Test`; Jupiter dispatches the method as
    an ordinary `@Test`. The method body builds and drives the spec
    via `PUnit`. The verdict is dispatched to all registered
    VerdictSinks (XML if punit-report is present, log if Sentinel
    is configured, etc.) and the terminal `assertPasses()` then
    translates the verdict to PASS / AssertionFailedError /
    TestAbortedException.

- concept: empirical baseline (punit)
  states: [in_memory_after_measure, written_to_explorations_outputDir
           (test build), committed_under_src_test_resources_punit_specs,
           loaded_via_BaselineProvider, expired_per_expiresInDays]
  emit_path: org.mavai.punit.runtime.BaselineEmitter
  load_path: org.mavai.punit.api.spec.BaselineProvider via
             ProfileBoundBaselineProvider
```

---

## Punit-specific invariants and enforcement

The cross-framework ontology owns the invariants that hold across every
implementation. punit *enforces* them via specific tests; the table
names the test for each.

```yaml
- cross_framework_invariant: Statistical isolation
  punit_enforcement: |
    `org.mavai.punit.statistics` has no `org.mavai.punit.*`
    imports outside the package. Enforced by
    `CoreArchitectureTest.statisticsModuleMustBeIsolated`. Test
    sources must also keep statistical arithmetic out — see also
    "no Math.sqrt outside statistics" rule in punit's CLAUDE.md.

- cross_framework_invariant: Requirement-code isolation
  punit_enforcement: |
    Codes (CT, EX, LT, PT, RC, RP, SC, SN, TH, UC, XM, DG) MUST NOT
    appear anywhere in punit-core / punit-report / punit-sentinel —
    production OR test source, including `@DisplayName` strings,
    test-class and test-method names, string literals. Enforced by
    `RequirementCodeIsolationTest`.

- invariant: One-sided Wilson in the verdict path
  punit_enforcement: |
    `BinomialProportionEstimator.lowerBound(...)` is the verdict
    path's entry. Two-sided remains for diagnostic uses. The XSD
    emits `wilson-lower` only on `<statistics>`.

- cross_framework_invariant: Latency Population purity
  punit_enforcement: |
    `LatencyPercentileComputer` filters to successful samples
    before delegating to `LatencyDistribution`. A code change to
    latency aggregation must preserve the success-only filter
    end-to-end.

- cross_framework_invariant: Outcome vs exception discipline
  punit_enforcement: |
    The engine does not catch exceptions thrown from
    `Contract#invoke`. Per-test exception policy
    (`ExceptionPolicy` / `RC12 / RC13`) covers *unexpected*
    exceptions only — converting one to a counted failed sample
    is opt-in via the policy, not a default.

- cross_framework_invariant: Sentinel deployability (zero JUnit deps)
  punit_enforcement: |
    `SentinelArchitectureTest` enforces no `org.junit` imports in
    `punit-sentinel`. `RuntimeArchitectureTest` enforces the same
    in `org.mavai.punit.runtime`.

- cross_framework_invariant: Service Contract aspect independence
  punit_enforcement: |
    Postconditions, duration constraint, and expected-output
    match are evaluated independently in the engine. No
    cross-aspect short-circuit.

- cross_framework_invariant: api package must not depend on JUnit extensions
  punit_enforcement: |
    `CoreArchitectureTest.apiPackageMustNotDependOnJUnitExtensions`.
    Annotation meta-tags (`@Test`, `@Tag`) are permitted; engine,
    extension, and platform types are not.
```

---

## Drift to fix (punit-side)

Items below are punit-specific cleanup that this ontology surfaces
but does not itself fix. They are candidates for in-flight or
future cleanup directives.

The **formal, executable specification** of the target package
structure lives in
`punit-core/src/test/java/org/mavai/punit/architecture/PackageStructureArchitectureTest.java`.
That test class encodes each architectural decision below as an
ArchUnit rule against the *post-cleanup* state. Until the cleanup
lands, current violations are captured in `archunit_store/` via
`FreezingArchRule`; each refactor commit shrinks the store.

```yaml
- item: "factor levels" terminology in javadoc
  locations:
    - punit-core/.../api/Factor.java:13
    - punit-core/.../api/Config.java:8
  fix: |
    Replace with "factor values". The canonical mavai term is
    "factor value" (family ambiguous-terms entry "Factor Level").
  scope: editorial; sweep when next editing those files.

- item: model.CovariateProfile vs api.covariate.CovariateProfile
  status: resolved
  fix: |
    `model.CovariateProfile` was deleted; the engine consumers
    (`spec.registry.SpecificationLoader`, `spec.model.ExecutionSpecification`,
    `model.BaselineProvenance`) migrated to the canonical
    `api.covariate.CovariateProfile`. The `Builder` and the
    typed-`CovariateValue` capability were dropped (sole production
    use was String-wrapping; the in-flight `parseCovariateValue`
    output is now coerced to String at the put site via
    `toCanonicalString()`).

- item: Verdict XML attribute names (ci-lower / ci-upper)
  status: resolved
  fix: |
    Resolved in the 0.7.x cleanup window. `<statistics>` now
    emits `wilson-lower` only; the legacy `ci-lower` / `ci-upper`
    pair was retired and the local XSD is diff-clean against the
    canonical cross-framework verdict-XML schema.

- item: Statistical early termination regression
  fix: |
    The 0.6.x sample loop terminated early on impossibility /
    guaranteed success. The spec-engine refactor lost the
    per-sample evaluator and the `disableEarlyTermination()`
    builder method. Verdicts are correct (every test runs to
    its configured sample count); the optimisation is missing.
    Restoration is owed.
```

---

## What this document does not do

- **It does not restate the cross-framework ontology's invariants.** Those
  are owned upstream. This document records *how punit enforces*
  each invariant, not what each invariant says.
- **It does not duplicate the glossary.** Concept definitions live
  in `mavai-R/docs/GLOSSARY.md`. This document maps each
  glossary-defined concept onto a Java type / package / idiom.
- **It does not restate methodology.** Statistical formulae live
  in the Statistical Companion; this document references the
  Companion only via the cross-framework ontology.
- **It does not document the public API surface in detail.** That
  is `DEVELOPER-API.md`'s job. This document maps concepts to
  packages so that an agent reading the API doc has the
  conceptual scaffolding; the API doc carries the signatures and
  the developer-facing prose.
