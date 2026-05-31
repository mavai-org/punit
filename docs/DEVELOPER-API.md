# punit Developer API

The surface a Java developer types when authoring tests, experiments,
sentinel reliability specifications, and consumers against punit.
This document is the operational counterpart to
[`DOMAIN-ONTOLOGY.md`](DOMAIN-ONTOLOGY.md): the ontology says *what
kinds of things live in the codebase*; this document says *what does
it look like to use them*.

This document is not the user guide. The
[`USER-GUIDE.md`](USER-GUIDE.md) is the tutorial; this is the spec.
A new contributor joining punit reads this to learn what the surface
is, what is public, what is internal, what discipline keeps the
surface honest, and what each architecture test enforces. An agent
generating or modifying punit code reads this to know which packages
to touch and which to never touch.

The architectural maxims in this document — particularly the
**statistics isolation rule** and the **api / runtime / engine
boundary** — are not stylistic preferences. They are enforced by
ArchUnit-style tests that fail the build on violation. The tests are
named in §[Architecture-test catalogue](#architecture-test-catalogue);
when modifying punit, satisfying these tests is the minimum bar for
the change being a change at all.

---

## Table of contents

- [Audience and scope](#audience-and-scope)
- [The two authoring annotations](#the-two-authoring-annotations)
- [The PUnit entry point](#the-punit-entry-point)
- [The Sampling primitive](#the-sampling-primitive)
- [Service Contract and Contract](#use-case-and-contract)
- [Declaring acceptance criteria](#declaring-acceptance-criteria)
- [Criterion types and composition](#criterion-types-and-composition)
- [The empirical pair pattern](#the-empirical-pair-pattern)
- [Spec terminals and what each one returns](#spec-terminals-and-what-each-one-returns)
- [Public package contract](#public-package-contract)
- [Architecture-test catalogue](#architecture-test-catalogue)
- [Statistics isolation rule](#statistics-isolation-rule)
- [Sentinel deployability](#sentinel-deployability)
- [Outcome vs exception convention](#outcome-vs-exception-convention)
- [Verdict XML wire format](#verdict-xml-wire-format)
- [Versioning and binary compatibility](#versioning-and-binary-compatibility)

---

## Audience and scope

This document targets:

- **Authors** writing `@ProbabilisticTest` and `@Experiment`
  methods in their own codebases against the published artefact.
- **Sentinel deployers** authoring reliability specifications
  and configuring the sentinel binary.
- **punit contributors** modifying or extending the framework
  itself.
- **Code-generation agents** producing or modifying any of the
  above, who need a stable reference for *what is public, what is
  enforced, and what is internal*.

It does *not* cover:

- The [USER-GUIDE.md](USER-GUIDE.md) tutorial path.
- The Maven / Gradle / IntelliJ configuration (see
  [MAVEN-CONFIGURATION.md](MAVEN-CONFIGURATION.md) and the project
  `README.md`).
- The statistical methodology (see
  [STATISTICAL-COMPANION.md](STATISTICAL-COMPANION.md)).

---

## The two authoring annotations

The author-facing surface is two annotations, both attribute-free.

```java
@Experiment
void shoppingBaseline() {
  PUnit.measuring(shoppingSampling(1000), shoppingFactors())
          .experimentId("baseline-v1")
          .run();
}

@ProbabilisticTest
void shoppingMeetsBaseline() {
    PUnit.testing(this::shoppingBaseline)
            .samples(100)
            .assertPasses();
}
```

Annotated methods are always `void`. `@ProbabilisticTest` methods
end in `.assertPasses()` (which translates the verdict into a
JUnit signal).`@Experiment` methods end in `.run()` (which writes
the baseline / exploration / optimization artefact). The
probabilistic test above declares no criterion directly: the
service contract's `criteria()` method declares the empirical
posture (`empirical().passRate()`), and the test inherits it.

The `this::shoppingBaseline` reference in the test is a separate,
non-annotated helper used as a baseline supplier — see
[The empirical pair pattern](#the-empirical-pair-pattern) for the
full shape.

Each annotation is a JUnit `@Test` meta-tagged `@Tag("punit")`. Every
parameter — sample count, intent, criterion, threshold, baseline
binding — lives in the method body, on the typed builder. The
annotations carry no attributes by design: configuration must be
typed, must compile-check against the service contract, and must be
expressible in the language's normal flow. Annotation attributes are
a strictly weaker channel — strings, integers, class literals — and
historically forced reflection-driven attribute reading the framework
no longer wants.

The annotations live at:

- `org.mavai.punit.api.ProbabilisticTest`
- `org.mavai.punit.api.Experiment`

Their JUnit meta-tags are inherited; they are not authored separately
on each test method.

---

## The PUnit entry point

`org.mavai.punit.runtime.PUnit` is the one entry point an author
imports. Everything else flows from a static call on `PUnit`.

```java
PUnit.testing(sampling, factors)         // probabilistic test
PUnit.testing(sampling)                  // probabilistic test, NoFactors
PUnit.measuring(sampling, factors)       // measure experiment
PUnit.exploring(sampling)                // explore experiment
PUnit.optimizing(sampling)               // optimize experiment
```

Each factory returns a typed builder. The four builder families
(`TestBuilder`, `MeasureBuilder`, `ExploreBuilder`, `OptimizeBuilder`)
all live as static-nested classes on `PUnit`. They wrap the
corresponding spec-builder in `org.mavai.punit.api.spec` and add the
test-harness-aware terminal:

| Builder            | Terminal(s)                                      | What the terminal does                           |
|--------------------|--------------------------------------------------|--------------------------------------------------|
| `TestBuilder`      | `.assertPasses()`, `.build()`                    | Drives the spec; translates Verdict to JUnit.    |
| `MeasureBuilder`   | `.run()`, `.build()`                             | Drives + emits baseline YAML.                    |
| `ExploreBuilder`   | `.run()`, `.build()`                             | Drives + emits per-config exploration YAML.      |
| `OptimizeBuilder`  | `.run()`, `.build()`                             | Drives + emits optimization history YAML.        |

`assertPasses()` translates the resulting Verdict:

- **PASS** → JUnit pass.
- **FAIL** → `org.opentest4j.AssertionFailedError`, carrying the
  verdict's explanation and per-criterion detail.
- **INCONCLUSIVE** → `org.opentest4j.TestAbortedException` —
  configuration / environment problem (no baseline, identity
  mismatch, …), not a service degradation.

`run()` returns normally on success; engine-level *defects*
propagate as runtime exceptions.

`build()` is used in the empirical pair pattern (see
[The empirical pair pattern](#the-empirical-pair-pattern)) to expose
an `Experiment` value that the probabilistic test consumes via
`PUnit.testing(baselineSupplier)`.

The `@ProbabilisticTest`-annotated method always uses `assertPasses()`
or `build()`. The `@Experiment`-annotated method always uses `run()`
or `build()`. JUnit always reports pass for the test method itself
because punit owns the verdict; what the framework computed lives in
the Verdict, not in the JUnit pass/fail signal.

---

## The Sampling primitive

`org.mavai.punit.api.Sampling<FT, IT, OT>` describes *how to produce
samples* — the service contract factory, the input cycle, the sample count,
and the sample-loop governors (budgets, exception policy,
failure-retention cap).

```java
Sampling<F, I, O> sampling = Sampling.of(
        f -> new MyServiceContract(f),       // factory bound to factors
        samples,                     // sample count
        input1, input2, input3);     // round-robin input cycle
```

A `Sampling` does not carry a factor instance. Factors are supplied
at the spec entry point (`testing`, `measuring`, …). This split is
load-bearing: the *same Sampling* is shared between a measure
baseline and the probabilistic test paired against it; the factor
instances at each call site are also the same; together they
guarantee that the test and the baseline draw from the same sampling
population (see [The empirical pair pattern](#the-empirical-pair-pattern)).

Sampling carries the per-test-loop governors:

- `BudgetExhaustionPolicy` — FAIL or EVALUATE_PARTIAL on budget
  exhaustion.
- `ExceptionPolicy` — FAIL_SAMPLE or ABORT_TEST on an unexpected
  thrown exception (this is *unexpected* — see
  [Outcome vs exception convention](#outcome-vs-exception-convention)).
- A failure-retention cap (RC14: how many failure exemplars the
  engine keeps for diagnostic purposes).

---

## Service Contract and Contract

A service contract is one Java class implementing `ServiceContract<FT, IT, OT>`:

```java
public class ShoppingBasketServiceContract
        implements ServiceContract<Factors, Instruction, BasketTranslation> {

    @Override
    public Outcome<BasketTranslation> invoke(Instruction input, TokenTracker tracker) {
        // …call the service, record cost via tracker.recordTokens(n)…
        return Outcome.ok(translation);
        // or Outcome.fail("invalid-action", "actions list was empty");
    }

    @Override
    public Criteria<BasketTranslation> criteria() {
        return empirical().<BasketTranslation>passRate()
                .satisfies("Has actions",
                        t -> t.actions().isEmpty()
                                ? Outcome.fail("empty-actions", "actions list was empty")
                                : Outcome.ok())
                .satisfies("All actions known", ShoppingBasketServiceContract::allKnown);
    }
}
```

`ServiceContract<FT, IT, OT>` extends `Contract<IT, OT>`. The author writes
one `implements` clause and overrides two methods minimum (`invoke`
and `criteria`, plus optional metadata methods).

### What the framework reads from a Service Contract

| Method                                | Purpose                                              | Default                       |
|---------------------------------------|------------------------------------------------------|-------------------------------|
| `invoke(IT, TokenTracker)`            | The service call. Returns Outcome.                   | abstract — author overrides   |
| `criteria()`                          | Declares the contract's verdict-producing criteria.  | default empty — author overrides |
| `latency()`                           | Declares latency-percentile commitment, if any.      | default empty                 |
| `id()`                                | Stable identifier for filenames and logs.            | kebab-cased simple class name |
| `description()`                       | Human-readable description.                          | empty                         |
| `warmup()`                            | Discarded warmup invocations before counted samples. | 0                             |
| `pacing()`                            | Rate / concurrency limits the engine respects.       | unlimited                     |
| `covariates()`                        | Covariates the framework records on every sample.    | empty                         |
| `inputSource()` / `inputSupplier()`   | The input cycle.                                     | none — supplied via Sampling  |
| `maxLatency()`                        | Per-sample wall-clock bound.                         | empty                         |

### Lifecycle and state

The framework constructs **one Service Contract instance per factor
configuration** (via the factory declared in Sampling) and reuses it
across every sample for that configuration. Implementations may carry
internal state — caches, connection handles, long-lived clients — but
must not mutate observable behaviour during a configuration's run.
Pre-existing caches and pools are fine; live reconfiguration in
response to sample outcomes is not.

### What goes in `invoke` vs `criteria`

Keep `invoke` primitive:

- `invoke` does the service call. It returns `Outcome.ok(value)` for
  a successful response, `Outcome.fail(name, message)` for an
  *expected* business-level failure (a contract violation, a
  validation error, a service-returned error code).
- A thrown exception aborts the run — that is the framework's
  treatment of a *defect*, not of a sample that didn't meet
  contract.
- Contract judgement — "the response had this property" or "the
  response did not have this property" — belongs on the criterion
  declaration's `.satisfies(...)` clauses inside `criteria()`.
  That is where each clause gets a name and surfaces clause-named
  diagnostics on failure.
- Service-level errors (the service returned a structured error
  code) belong in `invoke` returning `Outcome.fail`.

This is a recorded discipline — see
[Outcome vs exception convention](#outcome-vs-exception-convention).

---

## Declaring acceptance criteria

A service contract declares what counts as a passing sample by
overriding `Criteria<O> criteria()` and returning a value built up
from the `Criteria.meeting()` (contractual threshold) and
`Criteria.empirical()` (baseline-comparison) static factories in
`org.mavai.punit.api.criterion`.

### Declaring a single criterion

```java
import static org.mavai.punit.api.criterion.Criteria.meeting;
import static org.mavai.punit.api.ThresholdOrigin.SLA;

@Override
public Criteria<Receipt> criteria() {
    return meeting().<Receipt>passRate(0.9999)
            .contractRef(SLA, "Payment Provider SLA v2.3, §4.1")
            .satisfies("Authorisation returned APPROVED",
                    r -> r.approved() ? Outcome.ok() : Outcome.fail("declined", r.reason()));
}
```

The criterion declaration's `.name(...)` is optional when the
contract has only one criterion; missing names default to
`Criteria.DEFAULT_CRITERION_ID`.

### Declaring multiple criteria

```java
import static org.mavai.punit.api.criterion.Criteria.meeting;
import static org.mavai.punit.api.criterion.Criteria.empirical;
import static org.mavai.punit.api.criterion.Criteria.of;

@Override
public Criteria<Receipt> criteria() {
    return of(
        meeting().<Receipt>passRate(0.9999)
            .contractRef(SLA, "Payment Provider SLA v2.3, §4.1")
            .name("payment-completes")
            .satisfies("Authorisation returned APPROVED", ...),
        empirical().<Receipt>passRate()
            .name("structure-valid")
            .satisfies("Receipt is parseable", ...));
}
```

When more than one criterion is bundled, every declaration must
supply a `.name(...)` and the names must be unique within the
bundle. Both rules are enforced by `Criteria.of(...)`.

### Methods on a criterion declaration

- **`.satisfies(name, predicate)`** — leaf clause. Predicate returns
  `Outcome.ok()` on pass or `Outcome.fail(name, message)` on fail.
  The name is the clause's stable identifier in the verdict.
- **`.transforming(function)`** — adds a transformation step;
  subsequent `.satisfies(...)` clauses evaluate against the
  transformed value. Used when one criterion needs a derived view
  of the response that several clauses depend on
  (parse-then-validate).
- **`.contractRef(origin, ref)`** — stamps the threshold's
  provenance and a free-text reference (e.g., an SLA document and
  section number) onto the verdict.
- **`.name(id)`** — required when the contract bundles more than
  one criterion; identifies the criterion in the per-criterion
  verdict rows and in the baseline file.

---

## Criterion types and composition

A criterion is a spec-level claim evaluated against the observed
sample aggregate. The abstract type lives at
`org.mavai.punit.api.spec.Criterion<OT, S extends BaselineStatistics>`;
concrete criteria with statistical machinery live under
`org.mavai.punit.internal.engine.criteria` (so the api package
stays free of statistical dependencies — see
[Statistics isolation rule](#statistics-isolation-rule)).

### Pass-rate criterion factories

Two factory entry points live as static methods on `Criteria`:

```java
import static org.mavai.punit.api.criterion.Criteria.meeting;
import static org.mavai.punit.api.criterion.Criteria.empirical;
import static org.mavai.punit.api.ThresholdOrigin.SLA;

// Contractual — declared threshold, NORMATIVE origin
meeting().<O>passRate(0.95).contractRef(SLA, "...").satisfies("...", ...);

// Empirical — closest-match baseline lookup
empirical().<O>passRate().satisfies("...", ...);
```

- **`meeting().passRate(τ)`** — declared threshold, NORMATIVE origin
  (SLA / SLO / Policy). With VERIFICATION intent the Feasibility
  Gate enforces sample-size adequacy.
- **`empirical().passRate()`** — closest-match baseline lookup;
  threshold derived at evaluation time from the resolved baseline's
  pass rate, via the one-sided Wilson lower bound at the test sample
  size (Statistical Companion §3.4 / §4.3.2). The baseline is
  supplied at the test call site via
  `PUnit.testing(baselineSupplier)` (see
  [The empirical pair pattern](#the-empirical-pair-pattern)).

### Latency criterion

Latency commitments live on the service contract's sibling
`LatencyCriterion latency()` method, not in the criteria bundle:

```java
import static java.time.Duration.ofSeconds;
import static org.mavai.punit.api.PercentileKey.P95;
import static org.mavai.punit.api.criterion.Criteria.meeting;
import static org.mavai.punit.api.ThresholdOrigin.SLA;

@Override
public LatencyCriterion latency() {
    return meeting().atMost(P95, ofSeconds(1))
            .contractRef(SLA, "Acme SLA §4.2");
}
```

The structural 0..1 cardinality of latency is captured by the
singular return type rather than by a runtime check inside the
`criteria()` bundle. A latency criterion auto-injects into every
probabilistic test against the contract.

### Composing on the test builder (auto-inject + explicit additions)

The modern shape is to declare the contract's posture inside
`criteria()` and let the test inherit it:

```java
PUnit.testing(sampling, factors)
        .assertPasses();
```

For overrides or additional criteria, `.criterion(...)` and
`.reportOnly(...)` are still available on the test builder:

```java
PUnit.testing(sampling, factors)
        .reportOnly(empirical().<O>passRate())   // diagnostic-only
        .assertPasses();
```

`.criterion(c)` contributes to the combined verdict; `.reportOnly(c)`
is evaluated and attached but excluded from composition. The
combined verdict is the conjunction over contributing criteria
(every must PASS for the verdict to PASS).

---

## The empirical pair pattern

The framework's structural guarantee that an empirical comparison is
statistically meaningful. The author publishes a `Sampling<F, I, O>`
helper used by both a measure baseline and the probabilistic test
paired against it:

```java
private Sampling<F, I, O> shoppingSampling(int samples) {
    return Sampling.of(f -> new ShoppingBasketServiceContract(f), samples, ...inputs);
}

// @Experiment-annotated method actually runs the measure and writes
// the baseline YAML to disk. Always void; ends in .run().
@Experiment
void measureBaseline() {
    PUnit.measuring(shoppingSampling(1000), shoppingFactors())
            .experimentId("baseline-v1")
            .run();
}

// Plain (non-annotated) helper that builds an Experiment value
// structurally — used by the test as a baseline supplier to
// identify which baseline to resolve. Returns Experiment via .build().
private Experiment baseline() {
    return PUnit.measuring(shoppingSampling(1000), shoppingFactors())
            .experimentId("baseline-v1")
            .build();
}

@ProbabilisticTest
void shoppingMeetsBaseline() {
    PUnit.testing(this::baseline)
            .samples(100)
            .assertPasses();
}
```

The two methods carry the same `Sampling`, the same factors, and
the same `experimentId`. The `@Experiment` method produces the
baseline file; the `baseline()` helper produces an `Experiment`
value whose identity (sampling + factors + id) the framework uses
to look up the matching baseline at test time. Java reference
semantics enforce that the two stay aligned — share the
`Sampling` helper and the factors call site, and divergence
becomes a compile error rather than a silent mismatch.

The service contract's `criteria()` method declares the empirical
posture (`empirical().passRate()`); no `.criterion(...)` call is
needed on the test builder.

Why this matters: an empirical probabilistic test asks *"has the
service's pass rate degraded from the rate the baseline measured?"*
The question is statistically coherent only when the test and
baseline draw from the same sampling population. By passing the same
`Sampling` value into both, Java's reference semantics enforce that
sameness. The pairing is structural, not a brittle prose convention.

---

## Spec terminals and what each one returns

| Builder method               | Returns                                         | Throws                                                                 |
|------------------------------|-------------------------------------------------|------------------------------------------------------------------------|
| `TestBuilder.assertPasses()` | `void`                                          | `AssertionFailedError` on FAIL; `TestAbortedException` on INCONCLUSIVE |
| `TestBuilder.build()`        | `ProbabilisticTest`                             | engine-level defects propagate                                         |
| `MeasureBuilder.run()`       | `void` (artefact written to disk)               | engine-level defects propagate                                         |
| `MeasureBuilder.build()`     | `Experiment` (consumed by `PUnit.testing(baselineSupplier)`) | none                                                                   |
| `ExploreBuilder.run()`       | `void` (artefacts written per configuration)    | engine-level defects propagate                                         |
| `OptimizeBuilder.run()`      | `void` (history file written)                   | engine-level defects propagate                                         |

`assertPasses` is the only path that translates a Verdict into a
JUnit signal; the other terminals leave the Verdict in the Verdict
sink chain.

---

## Public package contract

The packages an author imports and what each one carries. The same
public/internal split is **structurally enforced** as of 0.7.x by
the JPMS `module-info.java` declarations under each module's
`src/main/java/`: the `exports` clauses are the authoritative
public-surface list, and an external **modular** consumer cannot
import a non-exported package — the compiler refuses, and the
runtime throws `IllegalAccessError`. **Unnamed-module** consumers
(plain-classpath builds without a `module-info.java` of their own)
still see all classes on the classpath as a legacy concession;
they are guarded by the `org.mavai.punit.internal.*` namespace
prefix and the ArchUnit regression rules.

| Package                                                                               | Contains                                                                                                                                      | Author may import?                                            |
|---------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------|
| `org.mavai.punit.api`                                                                 | Annotations, ServiceContract, Contract, Sampling, Factor support, ValueMatcher, TestIntent, ThresholdOrigin, Pacing, PacingConfiguration, TokenTracker, …                              | yes                                                           |
| `org.mavai.punit.api.criterion`                                                       | `Criteria` static factory, criterion declaration types, `LatencyCriterion`, `CriterionPosture`, … — what an author calls from `criteria()` and `latency()`                              | yes                                                           |
| `org.mavai.punit.api.spec`                                                            | Spec types (Spec, Experiment, ProbabilisticTest, Criterion, EvaluationContext, FactorsStepper, NextFactor, BaselineProvider, …)               | yes                                                           |
| `org.mavai.punit.api.covariate`                                                       | Covariate (interface) and built-in covariate categories                                                                                       | yes                                                           |
| `org.mavai.punit.runtime`                                                                      | `PUnit` entry point only — emitters live under `internal.runtime`                                                                             | yes — for `PUnit` only                                        |
| `org.mavai.punit.verdict`                                                                      | Verdict types, sinks, RunMetadata, `TokenMode` enum                                                                                            | yes — for sink registration                                   |
| `org.mavai.punit.statistics`                                                                   | Wilson, percentile, threshold derivation, feasibility evaluation                                                                              | yes — but rarely needed; the criteria already wrap statistics |
| `org.mavai.punit.internal.engine.*` (criteria, baseline, explore, optimize, covariate, spec, …) | Engine internals and concrete criterion impls                                                                                                 | **no** — internal                                             |
| `org.mavai.punit.internal.reporting`                                                           | Internal rendering helpers                                                                                                                    | **no** — internal                                             |
| `org.mavai.punit.internal.runtime`                                                             | Emitters (`BaselineEmitter`, `ExploreEmitter`, `OptimizeEmitter`), resolvers, composer — driven by `PUnit`                                    | **no** — internal                                             |
| `org.mavai.punit.internal.util`                                                                | Internal utilities                                                                                                                            | **no** — internal                                             |
| `org.mavai.punit.report.*` (in punit-report)                                          | Verdict XML reader / writer; HTML report                                                                                                      | yes — for sink consumption                                    |
| `org.mavai.punit.sentinel.*` (in punit-sentinel)                                      | Sentinel runtime + CLI                                                                                                                        | author-side: for reliability spec authoring; no JUnit deps    |

---

## Architecture-test catalogue

These tests fail the build on violation. Each protects a specific
invariant from regression.

| Test                                                            | Module         | Enforces                                                                                                                                                                                                             |
|-----------------------------------------------------------------|----------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `CoreArchitectureTest`                                          | punit-core     | Package-level rules in punit-core. No `org.junit` deps in non-api packages.                                                                                                                                          |
| `CoreArchitectureTest.apiPackageMustNotDependOnJUnitExtensions` | punit-core     | The api package may reference JUnit annotation types as meta-annotations only — never extension, engine, or platform types.                                                                                          |
| `CoreArchitectureTest.statisticsModuleMustBeIsolated`           | punit-core     | `org.mavai.punit.statistics` has zero dependencies on other punit packages.                                                                                                                                          |
| `RuntimeArchitectureTest`                                       | punit-core     | `org.mavai.punit.runtime` has zero `org.junit` deps. Sentinel-deployable code reaches PUnit here without a JUnit classpath.                                                                                          |
| `AbstractionLevelArchitectureTest`                              | punit-core     | Abstraction-level discipline across the framework — evaluators / resolvers / deciders must not depend on reporting; renderers must not depend on statistical computation classes.                                   |
| `SentinelArchitectureTest`                                      | punit-sentinel | Zero `org.junit` deps in punit-sentinel.                                                                                                                                                                             |
| `RequirementCodeIsolationTest`                                  | all modules    | Internal feature-tracking codes (CT/EX/LT/PT/RC/RP/SC/SN/TH/UC/XM/DG) MUST NOT appear anywhere in src/main/java or src/test/java — including @DisplayName strings, test-class names, test-method names, string literals. |
| `ArtefactEmissionRegressionTest`                                | punit-core     | Every `Experiment.Kind` must emit at least one artefact when `.run()` succeeds. Exhaustive switch over the enum — adding a new Kind without wiring an emitter is a compile-time fail.                                |
| `PackageStructureArchitectureTest`                              | punit-core     | The **target** package structure punit is moving toward. Each rule documents the step that closes it. Current violations are captured in `archunit_store/` via `FreezingArchRule` so the build stays green during the cleanup; each refactor commit shrinks the store. When every store file is empty, the cleanup arc is complete and the rules become permanent regression guards. See the test class javadoc for the full workflow. |

To run all architecture-style guards:

```bash
./gradlew test --tests "*ArchitectureTest" --tests "*RequirementCodeIsolationTest" --tests "*ArtefactEmissionRegressionTest"
```

To run only the package-structure rules (useful while working through the cleanup arc):

```bash
./gradlew test --tests "*PackageStructureArchitectureTest"
```

To re-baseline a frozen rule after refining its expression:

```bash
./gradlew test --tests "*PackageStructureArchitectureTest" -Darchunit.freeze.refreeze=true
```

---

## Statistics isolation rule

**Statistical calculations live only in `org.mavai.punit.statistics`.
Period.**

This is the architectural maxim that protects punit's statistical
core. The package has no dependencies on any other punit package — a
reader of the framework can validate the statistical core in
isolation, against published formulae (the Statistical Companion),
without tracing through the rest of the codebase. Enforced by
`CoreArchitectureTest.statisticsModuleMustBeIsolated`.

### Consequences for code outside the statistics package

- **Never reimplement** a calculation that already lives in
  `BinomialProportionEstimator`, `LatencyDistribution`, or any other
  member of the statistics package. Reuse the existing class.
- **Never introduce** a new statistical-arithmetic helper outside
  `org.mavai.punit.statistics`. If you find yourself reaching for
  `Math.sqrt`, an inverse-normal-CDF approximation, or
  `MessageDigest`-of-canonical-form in any other package, stop —
  the calculation belongs in the statistics package, with its own
  tests, against the formulation in the Statistical Companion.
- **The api package must not depend on Apache Commons.** When an
  api-side type needs a statistical calculation, the implementation
  belongs in the engine layer, not in the api package. Concrete
  `Criterion` implementations whose `evaluate()` consumes statistics
  live in `org.mavai.punit.internal.engine.criteria` (e.g. `PassRate`)
  — the `Criterion` interface stays in `org.mavai.punit.api.spec`, the
  statistical machinery stays in `org.mavai.punit.statistics`, and
  the criterion bridges the two.

A breach of this rule — typically a duplicated Wilson-score
calculation, a self-contained inverse-normal-CDF approximation, or
similar — must be removed and replaced with a call into the
existing statistics-package class. There is **no exception** for
performance, convenience, or "the architecture would otherwise be
inconvenient": those are signals that the type doing the
calculation belongs in a different package, not that the rule
should bend.

This rule is the *enforcement* (in punit) of the family ontology's
**Statistical isolation** invariant.

---

## Sentinel deployability

`punit-sentinel` is the JUnit-free runtime engine. It evaluates the
same Service Contract as the test suite, but against a live system,
on a schedule, without a JUnit / cargo-test harness.

The architectural consequence is that **Verdict construction, Wilson
statistics, and Verdict XML emission must be core concerns, free of
test-harness dependencies**. The Sentinel reaches the engine through
`PUnit` / `runtime`, not through any JUnit extension. Enforced by:

- `RuntimeArchitectureTest` — zero `org.junit` deps in
  `org.mavai.punit.runtime` and `org.mavai.punit.internal.runtime`.
- `SentinelArchitectureTest` — zero `org.junit` deps in
  `punit-sentinel`.

Two family invariants the Sentinel surfaces specifically:

- **Build-time baseline embedding.** Every EMPIRICAL-origin test
  must carry its embedded default baseline at sentinel build time.
  Sentinel runtime cannot access the source tree at sample time;
  missing a default baseline is a build failure, not a runtime
  warning. Normative-origin tests are exempt (the threshold is
  declared, not derived).
- **Emits, never installs.** Measurement output and test input are
  independently addressable. The Sentinel may emit a baseline
  measurement to its measurement output but never overwrites the
  test input automatically. Promotion of a measurement to a
  committed baseline is operator-owned.

Authoring a reliability specification: see
[`SENTINEL-DEPLOYMENT-GUIDE.md`](SENTINEL-DEPLOYMENT-GUIDE.md).

---

## Outcome vs exception convention

Business-level failures travel as data, not as exceptions. Use
`org.mavai.outcome.Outcome<T>` (from the `org.mavai:outcome`
library): return `Outcome.ok(value)` for success,
`Outcome.fail(name, message)` for an expected business-level failure.
In a service contract this is wrapped as `ServiceContractOutcome<OT>`
whose `value` is an `Outcome<OT>`; authors write
`ServiceContractOutcome.ok(...)` or `ServiceContractOutcome.fail(...)`
indirectly through the Contract `apply` dispatch.

Reserve `throw` for genuine *defects*: programming mistakes
(`NullPointerException`, `IllegalStateException`,
misuse-signalling `IllegalArgumentException`), misconfiguration, and
catastrophe (`OutOfMemoryError`, `StackOverflowError`). A thrown
exception from a `Contract#invoke` aborts the run — that is the
correct response to a bug, not to a sample that happened not to meet
its contract.

The `ExceptionPolicy` (RC12 / RC13) covers genuinely *unexpected*
exceptions. An author who wants the engine to convert an unexpected
exception into a counted failed sample opts in via
`ExceptionPolicy.FAIL_SAMPLE`; the default is `ABORT_TEST`, which
preserves the defect signal.

This convention is the family invariant **Outcome vs exception
discipline**, applied to Java idiomatically. Per-language note: in
feotest the equivalent is idiomatic `Result<T, E>`.

---

## Verdict XML wire format

punit serialises every Verdict to XML using the mavai.org family's
**verdict-XML interchange standard** (cross-language: punit,
feotest, mavai.org sentinels and dashboards all read and write this
format):

- **XSD schema:** `punit-report/src/main/resources/org/mavai/punit/report/verdict-1.0.xsd`.
- **Namespace:** `http://javai.org/verdict/1.0`.
- **Root element:** `<verdict-record>`.

The standard includes pacing, environment metadata, baseline
expiration, and correlation ID. The only punit-specific field not
serialised to XML is `junitPassed` (JUnit always passes in punit
because punit owns the verdict; meaningless outside the JUnit
context).

When modifying the XML format:

- Schema semantics are shared across the mavai.org framework
  family — coordinate cross-framework before changing punit's
  copy in isolation.
- `<statistics>` carries `wilson-lower` only — the one-sided Wilson
  lower bound at the verdict's `confidence-level`. The upper bound
  carries no operational meaning under a left-tailed test and is
  not emitted. Round-trip and schema-validation tests live in
  `punit-report`.

---

## Versioning and binary compatibility

punit is pre-1.0. Breaking API changes are explicit and announced:

- The minor version under 0.x signals breaking
  (`0.6 → 0.7` carried the typed-builder migration).
- Each release carries a `MIGRATION-X.Y-to-X.Z.md` guide for any
  break.
- The migration guide carries a coding-assistant prompt the user
  pastes into Claude Code / Cursor; the prompt walks the user's
  codebase and applies the migration mechanically.

There is no deprecation cycle owed pre-1.0. A 0.x.0 release that
breaks 0.x-1 callers is in policy.

After 1.0:

- Major version bumps signal breaking — and require the same
  migration-guide treatment.
- Module coordinates (`org.mavai:punit`, `org.mavai:punit-core`, …)
  stay constant. Renames are a tool of last resort.

The release process itself is documented in
[`punit/CLAUDE.md`](../CLAUDE.md) under "Release Process".

---

## What this document does not do

- It does not enumerate every public method on every type. The
  Javadoc + IDE auto-complete is authoritative on signatures; this
  document is authoritative on shape, contract, and discipline.
- It does not duplicate the user guide. New users start at
  [`USER-GUIDE.md`](USER-GUIDE.md); this document is for
  contributors and code-generation agents.
- It does not document the methodology. Statistical formulae and
  derivations live in [`STATISTICAL-COMPANION.md`](STATISTICAL-COMPANION.md);
  this document references the companion via the family ontology.
- It does not invent abstractions. Concepts are mapped onto Java
  idioms in punit's ontology ([`DOMAIN-ONTOLOGY.md`](DOMAIN-ONTOLOGY.md));
  this document maps them onto a developer's fingertips.
