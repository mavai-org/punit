# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Documentation

- Brought the optimize comparison report's description in the user guide
  (Part 11) in line with the 0.9.3 run-order layout — iterations listed in
  run order with a **Rank** column, not a score-sorted leaderboard.

## [0.9.3] - 2026-06-30

### Changed

- **Optimization comparison report lists iterations in run order.** The
  iteration table — previously an "Iteration leaderboard" sorted by score —
  now lists iterations chronologically by iteration index, carrying the
  score rank in a **Rank** column rather than by row order, so the run
  reads as the sequence it actually ran in. The chosen-best iteration is
  highlighted (row tint, green accent, and badge) instead of relying on row
  position, and a low-but-valid score is no longer visually conflated with
  an error — only abnormal termination is flagged. The per-criterion
  comparison columns follow the same run order. Near-equal scores still
  share a rank (read as a repeated rank value in the table); the `≈`
  too-close-to-call marker now appears only in the overview's leading
  cluster, not on the table rows.

## [0.9.2] - 2026-06-30

> **Highlights:** two new **experiment comparison reports** — a
> browser-friendly, self-contained HTML page that ranks the candidates of
> an EXPLORE or OPTIMIZE experiment so you can see which performed best
> without reading the raw YAML.

### Added

- **Exploration comparison report (`explorationReport` task).** Renders a
  single self-contained HTML page comparing the variants of an EXPLORE
  experiment — overall and per criterion. Per service: a ranked
  leaderboard (overall pass rate, then latency, then cost), a per-criterion
  matrix comparing the variants check-by-check, and a latency-distribution
  strip per variant. Variants whose ordering rests on a margin within 5%
  are flagged *too close to call* — a presentational marker, not a
  significance test. Reads the exploration YAML under
  `build/punit/explorations/<service>/` (the `explorationsDir`) and writes
  `build/reports/punit-explorations/html/index.html`.

- **Optimization comparison report (`optimizationReport` task).** Renders a
  single self-contained HTML page summarising an OPTIMIZE experiment's
  iterations, ranked by the scorer (objective-aware: `MAXIMIZE` /
  `MINIMIZE`). Per run: names the service and objective, a score-ranked
  iteration leaderboard with each row expanding to reveal the factor bundle
  that produced it, a per-criterion matrix, and a score trajectory across
  the run, with the chosen best iteration highlighted. Iterations within 5%
  of each other on score are flagged *too close to call*. Reads the
  optimization YAML under `build/punit/optimizations/<service>/` (the
  `optimizationsDir`) and writes
  `build/reports/punit-optimizations/html/index.html`.

  Both reports share the test report's styling and, like it, embed all CSS
  and make no external requests — they open directly from disk with no
  server. Run them after the experiment that produces the data
  (`./gradlew exp -Prun=…`); with no experiments present, the report states
  that none were found rather than failing the build.

## [0.9.1] - 2026-06-08

### Changed

- **Verdict-XML namespace `http://javai.org/verdict/1.0` →
  `http://mavai.org/verdict/1.0` (breaking interchange change).** The
  verdict-XML wire namespace moves off `javai.org` to complete the family
  rename. The writer emits, and the reader accepts, only the `mavai.org`
  namespace — documents in the old namespace are no longer read. Only the
  namespace host changes; the schema shape and all schema versions
  (1.0 / 1.1 / 1.2) are unchanged. Consumers parsing the verdict XML by
  namespace must update. (Released as a patch version by project decision
  despite the breaking nature.)

## [0.9.0] - 2026-05-29

### Changed

- **Coordinate / package / module / plugin-id move to `org.mavai`
  (breaking).** punit has moved off the `org.javai` namespace:
  - Maven coordinates: `org.javai:punit{,-core,-report,-sentinel}` →
    `org.mavai:punit{,-core,-report,-sentinel}`.
  - Java packages: `org.javai.punit.*` → `org.mavai.punit.*`.
  - JPMS modules: `org.javai.punit.{core,report,sentinel}` →
    `org.mavai.punit.{core,report,sentinel}`. Modular consumers update
    their `requires` directives accordingly.
  - Gradle plugin id: `org.javai.punit` → `org.mavai.punit`
    (`plugins { id("org.mavai.punit") }`).
  - The `outcome` dependency follows its own move to
    `org.mavai:outcome`.

  No API surface change — every type, annotation, and method keeps its
  simple name; only the namespace differs. Final `org.javai:punit*`
  releases carry a Maven relocation POM that auto-redirects with a
  deprecation warning; the Gradle Plugin Portal cannot redirect, so a
  final `org.javai.punit` plugin release points at the new id. The
  verdict-XML wire namespace (`http://javai.org/verdict/1.0`) is
  unchanged — it is a cross-framework interchange identifier, not a
  Maven coordinate.

## [0.8.1] - 2026-05-21

> **Highlights:** **baseline expiration** — measured baselines can now
> carry a validity window that surfaces as a verdict caveat, with a
> configurable fail-on-expired policy — and the probabilistic-test
> **inline contract** (`Contract.inline()`), which authors a
> single-artefact contractual test with no separate `ServiceContract`
> class. Also adds expected-value matching and renames "zero tolerance"
> to "zero failures".

### Added

- **Inline contract authoring surface (`Contract.inline()`).** Declares
  a contractual probabilistic test in one method — the service call
  (`invoking` / `returning`), a single criterion (`passRate` /
  `zeroFailures`, refined with `satisfies` / `where` / `contractRef` /
  `latencyAtMost`), and a contract-first `sampling(n, inputs)` terminal —
  with no named `ServiceContract` class. Restricted by design to
  contractual criteria (no empirical path — the absence is enforced at
  compile time) and to a single criterion. Empirical baselines,
  covariates, configurable factors, reuse, and multiple
  independently-thresholded criteria remain the province of a named
  `ServiceContract`, which the inline body lifts into verbatim; `build()`
  yields a `ServiceContract` for the factored explore / optimize path.
  The user-guide quick start now opens with this form.
- **Baseline expiration.** A measured baseline can declare a validity
  window via `MeasureBuilder.expiresInDays(n)`, persisted into the
  `punit-baseline-3` schema. An expired (or soon-to-expire) baseline
  surfaces as a verdict caveat on the typed path
  (`WarningLevel.APPROACHING` / `EXPIRED`), and a configurable policy can
  escalate an expired baseline to a failing verdict.
- **Expected-value matching.** Inputs implementing `Expected<O>` can be
  matched against produced outputs via `ValueMatcher` and the
  `.matchedBy(...)` / `.matchedByEquality()` criterion shape.

### Changed

- **Terminology: "zero tolerance" → "zero failures".** The
  any-failure-fails criterion and its surrounding prose and identifiers
  now use "zero failures" consistently (`zeroFailures()`).
- Removed redundant attribution-licensing references left over from the
  Apache 2.0 relicense.

## [0.8.0] - 2026-05-20

> **🎯 Stable release.** Graduates the 0.7.0-alpha series straight
> to 0.8.0. Highlights since 0.7.0-alpha6: Apache 2.0 relicense
> with DCO contributor model; MEASURE baseline schema bump
> `punit-baseline-2` → `punit-baseline-3` (per-methodology-
> criterion shape); HTML report per-criterion breakdown;
> back-compat constructor cleanup on `ProbabilisticTestResult`
> and `SampleSummary`; verdict-XML 1.1 schema; retirement of
> the legacy `PostconditionBuilder` authoring path in favour of
> the value-form `Criteria.meeting()` / `Criteria.empirical()`
> factory shape. See section headings below for details.

### Changed (license)

- **Relicensed from Attribution Required License (ARL-1.0) to
  Apache License, Version 2.0.** All source, build metadata
  (`punit-core`, `punit-sentinel`, `punit-report`, umbrella POM),
  and documentation now reference Apache 2.0. The `LICENSE` and
  `NOTICE` files at the repository root carry the canonical text.
  Versions of PUnit published prior to this change remain
  available under their original ARL-1.0 terms; the relicense
  applies from this release forward.
- **Contributions now governed by the Developer Certificate of
  Origin (DCO).** The DCO 1.1 text is committed verbatim as
  `dco.txt`; `CONTRIBUTING.md` documents the `git commit -s`
  sign-off requirement. A GitHub Actions workflow
  (`.github/workflows/dco.yml`) blocks unsigned commits on pull
  requests. No separate contributor agreement is required —
  Apache 2.0 §5 (inbound = outbound) combined with the per-commit
  DCO sign-off carries the legal weight.

### Changed (MEASURE baseline schema — per-methodology-criterion shape)

- **Baseline YAML schema bumps from `punit-baseline-2` to
  `punit-baseline-3`.** The `statistics.bernoulli-pass-rate`
  block now nests a `criteria:` sub-map keyed by methodology
  criterion id; each entry carries its own `observedPassRate`
  and `sampleCount`. K=1 contracts (the common case, via the
  legacy `postconditions(ContractBuilder)` path) produce a
  single-entry map keyed by the auto-derived
  `DefaultCriterion.id()`. K&gt;1 contracts (those declaring
  `criteria(CriteriaBuilder)` explicitly) produce one entry
  per methodology criterion in contract declaration order. The
  top-level `latency:` block is unchanged — latency stays at the
  run level, not partitioned by criterion.

- **`BaselineReader` rejects `punit-baseline-2` files with a
  re-MEASURE migration message.** No silent read-time adapter=.
  Files on disk in the older shape must be regenerated i.e. .
  experiments must be re-run.

- **`PerCriterionPassRateStatistics` is a new `BaselineStatistics`
  variant** wrapping `Map<String, PassRateStatistics>`. It is
  what the baseline file's `bernoulli-pass-rate` block
  deserialises to. The `BaselineResolver` unwraps it for callers
  that request `PassRateStatistics.class` (the empirical
  `PassRate` evaluator): single-entry maps yield their lone
  entry; multi-entry maps fast-fail with an
  `IllegalStateException` naming the criteria — multi-criterion
  empirical evaluation awaits the evaluator-inference machinery
  (deferred to a later directive).

- **`BaselineEmitter` composes the per-criterion structure from
  the engine's `SampleSummary.criterionSampleCounts()`**, one
  entry per methodology criterion the engine recorded. No new
  arithmetic — the per-criterion pass / total counts come
  straight from the engine's already-recorded data.

### Added (HTML report — per-criterion breakdown)

- **HTML report renders the methodology-level per-criterion
  decomposition** at the deepest expansion level of each test row.
  When the verdict carries a `<per-criterion>` structure (verdict
  XML 1.2, or in-memory results that produced one), the new
  `Per-criterion breakdown` `<details>` block lists one block per
  criterion, headed by the criterion's identifier. Each block
  shows the three-valued verdict (PASS / FAIL / INCONCLUSIVE) with
  the existing verdict-CSS colouring, the per-outcome sample
  counts (pass / fail / inconclusive / total), the observed
  marginal pass-rate, and the threshold the row was judged
  against. `NaN` observed-rates and thresholds render as a dash.
  The block is omitted entirely for runs that produced no
  per-criterion structure (legacy 1.0 XML, apply-level-failure
  runs).
- **Composite verdict is not duplicated as a separate line.** The
  level-1 verdict column already carries the composite under the
  step-4 cutover (the composite *is* the test's verdict); the new
  block surfaces the rows the composite was composed from.
- **Latency is not added as a per-criterion row.** It remains
  surfaced once, in the dedicated p50 / p95 / p99 columns at
  level 1 and the Statistical Analysis block at level 3.

### Removed (`ProbabilisticTestResult` back-compat constructors)

- **`ProbabilisticTestResult` 5-arg, 6-arg, 7-arg, 8-arg, and 9-arg
  back-compat constructors** withdrawn. Introduced over the rollouts
  that added `CovariateAlignment`, `contractRef`,
  `failuresByPostcondition`, `EngineRunSummary`, and
  `PerCriterionEvaluation` respectively, they have served their
  migration purpose; every caller now passes the canonical 10-arg
  form. The `withCovariates(...)` and `withContractRef(...)`
  copy-on-write methods are unchanged.

### Removed (`SampleSummary` back-compat constructors)

- **`SampleSummary` 9-arg, 10-arg, and 11-arg back-compat
  constructors** withdrawn. Introduced over the step-3 / step-4 /
  step-5 rollout, they have served their migration purpose; every
  caller now passes the full canonical 12-arg form (outcomes,
  elapsed, successes, failures, tokensConsumed, failuresDropped,
  latencyResult, terminationReason, trials, failuresByPostcondition,
  passingLatencyResult, criterionSampleCounts). A follow-on directive
  will revisit the shape of `SampleSummary` itself — the canonical
  arity is the right thing to sweep first, then the shape.

### Removed (legacy-aggregate deprecation close-out)

- **`ProbabilisticTestResult.legacyAggregateVerdict`** —
  the transitional `Optional<Verdict>` field introduced at the
  step-4 cutover for one release as an audit-trail aid for K>1
  contracts whose JUnit outcome changed. The one-release window has
  closed; the field is gone. `ProbabilisticTest.conclude(...)` no
  longer computes the pre-cutover legacy aggregate.

- **`ProbabilisticTestVerdict.legacyAggregateVerdict`** and the
  matching builder setter — same field at the persistence layer, also
  gone.

- **`<legacy-aggregate>` XML element** — the transitional one-release
  child of `<per-criterion>` is withdrawn. The schema bumps from
  `verdict-1.1.xsd` to `verdict-1.2.xsd`; the new schema's
  `PerCriterionType` carries `<criterion>*` and `<composite>` only.
  Both `verdict-1.0.xsd` and `verdict-1.1.xsd` are retained for one
  release each as references. The `VerdictXmlReader` remains
  permissive — a stray `<legacy-aggregate>` element from a 1.1
  emitter still in service is silently ignored at parse, not
  rejected.

- **Transparent-stats audit-trail callout** — the per-criterion block
  no longer emits the "pre-cutover aggregate verdict was X; the
  composite is now authoritative" callout. The block renders the
  per-criterion table and the composite line only.

### Added (verdict-XML 1.1 schema)

- **`verdict-1.1.xsd`** — additive schema bump under the existing
  `http://javai.org/verdict/1.0` namespace. Adds an optional
  `<per-criterion>` bundle under `<verdict-record>` carrying the
  methodology-level decomposition:

  - `<criterion id="…" verdict="PASS|FAIL|INCONCLUSIVE"
       pass="…" fail="…" inconclusive="…" total="…"
       observed-rate="…" threshold="…"/>` — one element per
    criterion the contract declared, in declaration order.
    `observed-rate` and `threshold` are optional attributes omitted
    when the in-memory value is `NaN`.
  - `<composite value="…"/>` — the composite verdict over the rows
    under the FAIL-dominant rule (§1.4.6).
  - `<legacy-aggregate value="…"/>` — transitional one-release
    audit-trail element mirroring `ProbabilisticTestResult.legacyAggregateVerdict()`.
    Present only when the in-memory `Optional` is populated. A
    follow-on directive removes both this element and the in-memory
    field.

  Emitter sets the root `<verdict-record version>` attribute to
  `"1.1"` when 1.1 content is populated, `"1.0"` otherwise.
  `verdict-1.0.xsd` is retained for one release as a reference;
  consumers strictly validating against 1.0 will reject 1.1
  `<per-criterion>` content (the additive-evolution break).

- **`ProbabilisticTestVerdict`** gains
  `Optional<PerCriterionStructure> perCriterion` and
  `Optional<Verdict> legacyAggregateVerdict` components; two
  back-compat constructors (17-arg, 16-arg) layered to preserve
  existing call sites.

- **`VerdictXmlReader`** parses both 1.0 and 1.1 messages
  permissively — the new elements are read when present and absent
  Optionals are preserved otherwise.

### Cross-framework coordination

- Feotest's verdict reader and emitter follow in their own
  directive. The shared schema artefact is the coordination point;
  feotest's update lands when feotest's step-4-equivalent is on
  the runway.

### Changed (behavioural)

- **Composite verdict is now the contract's overall verdict authority.**
  Step 4 of the multi-criterion rollout. `ProbabilisticTestResult.verdict()`
  is now driven by the methodology-level per-criterion composite under
  the FAIL-dominant rule (companion §1.4.6) rather than the legacy
  spec-layer flat-aggregation over the functional verdict-component
  alone. Cross-dimension aggregation (functional + latency) continues
  to flow through `Verdict.compose` — the per-criterion composite
  substitutes the functional verdict-component's verdict, then
  compose handles cross-dimension under the existing INCONCLUSIVE-first
  rule.

  **K=1 contracts:** byte-identical. The per-criterion composite
  over one verdict equals that verdict, so the substitution is a
  no-op.

  **K>1 contracts:** the *hiding result* case — one criterion
  genuinely failing, others passing, flat-aggregation passing — now
  reports FAIL at the harness boundary. Authors of K>1 contracts
  whose JUnit outcome changed should review.

  **Empty per-criterion evaluation** (apply-level-failure runs where
  `Contract.evaluateClauses` never fired): falls back to the legacy
  flat-aggregation result.

### Added

- **`ProbabilisticTestResult.legacyAggregateVerdict`** —
  `Optional<Verdict>` component carrying the pre-cutover
  `Verdict.compose(evaluated)` value. Transitional artefact carried
  for one release so authors of affected K>1 contracts can see what
  the verdict was pre-step-4. A follow-on directive removes it.

- **Transparent-stats audit-trail callout.** When the pre-cutover
  legacy aggregate differs from the composite, the per-criterion
  block in the transparent-stats output emits a line noting the
  pre-cutover verdict and the new authoritative composite. Replaces
  step 3's evidence-gathering callout.

### Deferred to follow-on directive

- **Verdict-XML schema bump to `verdict-1.1.xsd`** — explicit
  per-criterion table and composite element. The existing
  `<verdict>` element correctly carries the composite as the
  authoritative value because `ProbabilisticTestResult.verdict()`
  returns the composite. The XSD bump adds explanatory surfaces
  (per-criterion breakdown, explicit composite element) and is the
  subject of a separate directive after we gather experience from
  this cutover. Cross-framework coordination (feotest reader) lands
  with that directive.

- **Conformance-fixture consumption** from javai-R v0.8.0 remains
  pending per a separate directive.

## [0.7.0-alpha6] - 2026-05-12

> **🧪 Experimental release.** Breaking: renames the authoring-surface
> interface `UseCase` → `ServiceContract` and its companion types.
> See *Changed (breaking)* below for the migration recipe. Wire
> format unchanged.

### Changed (breaking)

- **`UseCase` → `ServiceContract`.** The authoring-surface
  interface renames from `org.javai.punit.api.UseCase<F, I, O>`
  to `org.javai.punit.api.ServiceContract<F, I, O>`. The
  abstract/concrete split with `Contract<I, O>` is preserved:
  `ServiceContract` extends `Contract` exactly as `UseCase` did.
  Companion types follow:
  - `UseCaseOutcome<O>` → `ServiceContractOutcome<O>`
  - `UseCaseAttributes` → `ServiceContractAttributes`

  Builder / factory identifier renames pair with the type
  rename: `useCaseFactory(...)` → `serviceContractFactory(...)`,
  `resolveUseCaseId` → `resolveServiceContractId`, parameter and
  field names `useCase` → `serviceContract` across the API.

  **Migration recipe** (find-and-replace, IDE refactor
  recommended):
  - `UseCase`, `UseCaseOutcome`, `UseCaseAttributes` →
    `ServiceContract`, `ServiceContractOutcome`,
    `ServiceContractAttributes`
  - `useCase`, `useCaseFactory`, `useCaseAttributes` →
    `serviceContract`, `serviceContractFactory`,
    `serviceContractAttributes`

  **Wire format unchanged.** The RP07 verdict XML attribute
  remains `use-case-id`; the YAML baseline / spec field remains
  `useCaseId`. Existing baselines load against the new release
  without regeneration; existing verdict XML validates against
  the unchanged RP07 schema. The Java↔wire asymmetry is
  deliberate — wire-format regeneration is reserved for a
  later, separate release.

  Rationale: "use case" was overloaded across the field (UML /
  Jacobson sense, regulatory deployment-scenario sense, generic
  product-discussion sense) and told a punit author nothing
  about the artefact's purpose in probabilistic-testing context.
  "Service contract" names the thing it is: the specification
  of correct behaviour the framework measures against.

### Documentation

- **`-alpha` exit criterion closed.** Both gaps the original
  0.7.0-alpha CHANGELOG declared as the qualifier's exit criteria
  are now in:
  - *Statistical early termination* (PT09 / PT10) landed via #150
    in 0.7.0-alpha5's window. Failure-inevitable and
    success-guaranteed short-circuits fire from
    `Engine.Aggregator`; `disableEarlyTermination()` on
    `ProbabilisticTest.Builder` covers the opt-out cases.
  - *Wider feasibility-gate audit* — the headline concern,
    "configurations whose implied confidence falls below 80% are
    not yet rejected across all intents," was already closed in
    `Feasibility.check` (the soundness-floor branch aborts before
    the intent-specific path, so VERIFICATION and SMOKE trip the
    same abort). `SoundnessFloorTest` is the end-to-end audit. The
    alpha4 / alpha5 release notes carried the gap text by inertia;
    code-side it had been resolved in the alpha2 feasibility-gate
    arc.
- **Test-class doc:** `PreflightInvariantsTest` class-level
  javadoc previously called the soundness floor "intentionally
  absent — recorded as a gap." That note no longer matched the
  code path; rewritten to point at `SoundnessFloorTest` as the
  dedicated audit and `Feasibility.check`'s cross-intent abort as
  the enforcement seam.

The implication: the original `-alpha` exit criteria are in. The
qualifier itself is **held**, not dropped — the breaking-change
train is not yet complete. The `UseCase` → `ServiceContract`
rename (see the *Changed (breaking)* entry above) lands in this
release; further breaking-shaped work (optimize-experiment API
DX, verdict-catalogue rebuild) still needs to be triaged as
additive or breaking. The qualifier drops when the
breaking-change train is genuinely complete, not on the
mechanical closure of the original criteria — trust in a
stability signal is built slowly and lost in one move; spending
the qualifier to absorb anticipated breaks is the cheaper path.

## [0.7.0-alpha5] - 2026-05-11

> **🧪 Experimental release.** Adopts `org.javai:outcome` 0.3.0,
> which now ships a real `module-info.class`. The build-time
> `extra-java-module-info` shim that 0.7.0-alpha4 used to wrap
> outcome as an automatic module is gone, and so is the
> `org.gradlex.extra-java-module-info` Gradle plugin from
> `punit-core`. Modular consumers of the published artifact pick up
> outcome 0.3.0 transitively and `requires org.javai.outcome`
> resolves to a real named module — no consumer-side shim needed.
>
> No source changes. No API changes.

### Changed
- **`org.javai:outcome` dependency bumped from 0.2.0 to 0.3.0**
  in both `build.gradle.kts` (root) and `punit-core/build.gradle.kts`.
  The bump is the headline reason for this release; classpath
  consumers see nothing new, but the modular-consumer story is now
  honest end-to-end.

### Removed
- **`extra-java-module-info` shim and plugin.** `punit-core` no
  longer applies `org.gradlex.extra-java-module-info` and no longer
  carries the `extraJavaModuleInfo { automaticModule(...) }` block.
  With outcome 0.3.0 providing its own descriptor, the shim's exit
  condition (noted as a `TODO` since alpha4) is met.

## [0.7.0-alpha4] - 2026-05-11

> **🧪 Experimental release.** The 0.7.x public-surface
> consolidation arc: JPMS `module-info.java` on every published
> library module, the `punit-junit5` bundler artifact retired, four
> verdict-side types promoted out of `internal.*` to their natural
> public packages (breaking FQN change), and the orphan declarative
> `@Latency` surface removed in favour of the `PercentileLatency`
> criterion. Conformance coverage extended on the verdict-XML and
> latency oracles.
>
> **Exit criterion for `-alpha`.** The two gaps documented in
> 0.7.0-alpha — statistical early termination (PT09/PT10) and the
> wider feasibility-gate audit (sub-80% implied confidence
> rejection across all intents) — remain the contract for shipping
> 0.7.0 without a qualifier. `-alpha` stays on until both close.

### Added (JPMS module declarations)

Three of punit's four library modules now ship a
`module-info.java` declaring exports, requires, and ServiceLoader
provides/uses. `module-info.class` is the published artifact-level
expression of the public-surface promise the
`org.javai.punit.internal.*` namespace started.

- **`punit-core`** declares module `org.javai.punit.core`. Exports
  every public package (`api`, `api.spec`, `api.covariate`,
  `api.match`, `runtime`, `verdict`, `statistics`,
  `statistics.transparent`). Targeted exports
  (`exports … to <module>`) grant sibling modules the minimum
  internal-package surface they need —
  `internal.engine.emit` to `punit-report`, `internal.reporting`
  to `punit-report` and `punit-sentinel`. `requires static
  org.junit.jupiter.api` keeps JUnit a compile-time-only
  dependency.
- **`punit-report`** declares module `org.javai.punit.report`.
  `provides org.javai.punit.verdict.VerdictSink with
  org.javai.punit.report.VerdictXmlSink` is the JPMS expression of
  the existing `META-INF/services/` ServiceLoader registration.
- **`punit-sentinel`** declares module
  `org.javai.punit.sentinel`. Explicitly omits `requires` for any
  JUnit module — the compiler now enforces the JUnit-free
  invariant as a property of the module declaration, complementing
  `SentinelArchitectureTest`.
- **`punit-junit5`** has been retired as a published artifact —
  see the *Removed* section below.

### Removed (`org.javai:punit-junit5` Maven coordinate)

`org.javai:punit-junit5` was a dependency-bundler with no
production code. The framework's user-facing annotations
(`@ProbabilisticTest`, `@Experiment`) live in `punit-core/api`;
they are meta-annotated with `@org.junit.jupiter.api.Test` (via
`requires static`) and carry no `@ExtendWith`, so a probabilistic
test reaches the framework by calling
`PUnit.testing(useCase).assertPasses()` inside the test body —
nothing in the JUnit lifecycle needs a punit-supplied extension.
The bundler module added nothing beyond a curated `api` group of
JUnit Jupiter + jackson + log4j that consumers can declare
directly, and its sole content was integration tests and test
subjects (now absorbed into `punit-core`'s test source).

**Migration.** Consumers previously declaring:

```kotlin
testImplementation("org.javai:punit-junit5:<version>")
```

replace it with the direct deps the bundler used to pull in. The
minimal set for an author who writes `@ProbabilisticTest` against
`PUnit.testing(...)` and wants verdict XML emission is:

```kotlin
implementation("org.javai:punit-core:<version>")
testImplementation("org.javai:punit-report:<version>")  // XML VerdictSink
testImplementation("org.junit.jupiter:junit-jupiter")    // JUnit Jupiter
```

Add `jackson-databind` / `jackson-dataformat-csv` only if the
test sources use `@InputSource` for JSON/CSV inputs; add
`log4j-core` (or another SLF4J backend) only if punit's runtime
logging needs to land somewhere.

### Consumer impact

- **Modular consumers** (own `module-info.java`, depend on punit
  via modulepath) — the `exports` clauses are now the
  authoritative public surface. Attempting to import a non-exported
  `internal.*` package fails to compile and at runtime throws
  `IllegalAccessError`. Existing modular consumers may need to
  add `requires org.javai.punit.core` (or
  `org.javai.punit.report` / `org.javai.punit.sentinel`) to their
  own `module-info.java`.
- **Unnamed-module consumers** (no `module-info.java`, classpath
  build) — no source changes required. JPMS export visibility is
  not gated on the classpath path; these consumers continue to see
  every class on punit's JARs and remain guarded by the
  `internal.*` namespace prefix and the ArchUnit rules.

### Build infrastructure

- `punit-core` applies `org.gradlex.extra-java-module-info` 1.13
  to wrap `org.javai:outcome` as an automatic module. Every other
  transitive dependency is already JPMS-aware (real `module-info`
  in jackson / snakeyaml / log4j / JUnit / opentest4j;
  `Automatic-Module-Name` in commons-statistics).

### Changed (public-surface promotion — breaking FQN change)

Authoring-time and verdict-shape types that previously lived under
`internal.*` packages have been promoted to their natural public
locations. The motivating principle: a type that appears in a
public method signature on a public class must itself be in a
public package, so a JPMS-modularised consumer can compile against
the verdict API without reaching into framework internals.

- `org.javai.punit.internal.engine.budget.CostBudgetMonitor.TokenMode`
  (enum) → `org.javai.punit.verdict.TokenMode` (top-level). The
  enum is a verdict-side label naming how token cost was measured
  (NONE / STATIC / DYNAMIC); it is a peer to `ThresholdOrigin` and
  `Verdict`, not a sub-concept of the budget monitor.
- `org.javai.punit.internal.engine.pacing.PacingConfiguration`
  (record) → `org.javai.punit.api.PacingConfiguration` (top-level,
  alongside the existing `Pacing` builder type). The record carries
  authoring-time pacing constraints + computed execution plan; it
  is consumed by the builder API and read off the verdict, both
  user-facing.

### Changed (package layout — internal relocations)

Renderers and adapters that had drifted into public packages have
been relocated to their natural internal homes. No external API
change (all are non-public classes); only their FQNs move.

- `org.javai.punit.verdict.VerdictTextRenderer` →
  `org.javai.punit.internal.reporting.VerdictTextRenderer`
- `org.javai.punit.verdict.VerdictAdapter` →
  `org.javai.punit.internal.runtime.VerdictAdapter`
- `org.javai.punit.verdict.VerdictSinkBus` →
  `org.javai.punit.internal.reporting.VerdictSinkBus`

The `VerdictSink` interface itself stays public at
`org.javai.punit.verdict.VerdictSink`; only the dispatcher moved.

### Migration

Consumers that referenced any of the four promoted types by FQN:

- `org.javai.punit.internal.engine.budget.CostBudgetMonitor.TokenMode`
  → `org.javai.punit.verdict.TokenMode`
- `org.javai.punit.internal.engine.pacing.PacingConfiguration`
  → `org.javai.punit.api.PacingConfiguration`
- The three renderer/adapter relocations affect only consumers that
  were already reaching into the framework's internal-shaped types —
  not a supported pattern, no migration documented.

### Removed (declarative latency surface — breaking)

The orphan `@Latency` annotation (declared `@Target({})`, no
processor) and the never-populated `assertions` /
`dimensionSuccesses` / `dimensionFailures` fields on
`LatencyDimension` are gone. Latency is descriptive in the verdict
record; gating is expressed via the `PercentileLatency` criterion
on the typed builder.

- **Wire format.** `<latency>` always emits zero violations and no
  `<evaluations>` block. Readers tolerate legacy `evaluations`
  attributes but do not surface them.
- **Migration.** Consumers that referenced `org.javai.punit.api.Latency`
  by FQN — none in practice, since the annotation had no valid target
  — replace it with a `PercentileLatency` criterion on the builder.

### Added (conformance coverage)

- **`VerdictXmlConformanceTest`** asserts that the emitted verdict
  XML round-trips against the canonical RP07 fixture.
- **`MultiDimensionVerdictIntegrationTest`** exercises the
  multi-dimension verdict path end-to-end.
- **`LatencyConformanceTest`** extended with bootstrap-comparison
  cases against the javai-R oracle.

## [0.7.0-alpha3] - 2026-05-10

> **🧪 Experimental release.** The 0.7.x structural-cleanup arc:
> dead-code excision, package-drift resolution, the
> wire-format alignment with the canonical RP07 schema, and the
> public/internal split made visible at every import statement
> via the new `org.javai.punit.internal.*` namespace. Four merged
> PRs (#140, #141, #142, #143), 25 commits, net ~−4,400 lines.
> Mechanical migration: most consumer changes are find-and-replace
> on import statements. No new framework behaviour; no oracle
> changes.

### Removed (breaking)

- **The `org.javai.punit.contract.*` parallel stack is gone.**
  The package was a stillborn second authoring surface (14
  production classes, 13 test classes) that had been deprecated
  but never excised. Replaced where authors actually used it by
  the new `org.javai.punit.api.match` subpackage carrying
  `ValueMatcher`, `MatchResult`, `StringMatcher`, `JsonMatcher`.
  If you were reaching into `contract.*`, the typed authoring
  surface (`Sampling.matching(...)`, `UseCase.postconditions(...)`)
  is the supported path; `api.match` covers the value-matcher
  use case directly.

### Changed (package layout — breaking FQN change)

- The public/internal split is now structural. Every framework-internal
  package moved under `org.javai.punit.internal.*`:
  - `org.javai.punit.engine.*` → `org.javai.punit.internal.engine.*`
    (criteria, baseline, explore, optimize, covariate, spec, budget,
    pacing, emit, …).
  - `org.javai.punit.reporting` → `org.javai.punit.internal.reporting`.
  - `org.javai.punit.util` → `org.javai.punit.internal.util`.
  - The non-`PUnit` residents of `runtime/` (the emitters
    `BaselineEmitter`, `ExploreEmitter`, `OptimizeEmitter`; the
    resolvers `BaselineProviderResolver`, `TestIdentityResolver`; the
    composer `EmpiricalTestComposer`) moved to
    `org.javai.punit.internal.runtime.*`. `PUnit` itself stays public
    at `org.javai.punit.runtime.PUnit`.
- The public packages — `api`, `api.spec`, `api.covariate`, `api.match`,
  `runtime` (for `PUnit`), `verdict`, `statistics`,
  `statistics.transparent` — keep their FQNs.
- Migration for code that imported any of the moved packages: prepend
  `internal.` to the package path. The migration is mechanical and a
  find-and-replace pattern resolves every affected import:
  - `org.javai.punit.engine` → `org.javai.punit.internal.engine`
  - `org.javai.punit.reporting` → `org.javai.punit.internal.reporting`
  - `org.javai.punit.util` → `org.javai.punit.internal.util`

### Changed (verdict XML wire format — breaking)

- `<statistics>` now carries `wilson-lower` (the one-sided Wilson
  lower bound at the verdict's `confidence-level`). The legacy
  `ci-lower` / `ci-upper` attribute pair has been retired.
  Consumers reading verdict XML must update their parsers; the
  reader rejects documents missing `wilson-lower` with a clear
  diagnostic.
- `ProbabilisticTestVerdict.StatisticalAnalysis.ciLower` /
  `ciUpper` record components renamed to `wilsonLower` (single
  one-sided lower bound). The two-sided
  `BinomialProportionEstimator.estimate(...)` is unchanged and
  still validated against the javai-R `wilson_ci` fixture; only
  the verdict path moves to one-sided.
- Verdict text rendering replaces the "CI lower bound:" label with
  "Wilson lower bound:" and drops the prose framing the value as a
  confidence interval.
- The local `verdict-1.0.xsd` is now diff-clean against the
  canonical RP07 verdict-XML schema.

### Changed (package drift — breaking FQN change)

A coordinated sweep ahead of the internal-namespace move resolved
every long-standing package-drift case. Six steps, mostly mechanical
relocations; consumers that imported any of these specific FQNs need
to update.

- `org.javai.punit.power.PowerAnalysis` folded into
  `org.javai.punit.engine.baseline` (then relocated to
  `internal.engine.baseline` under the namespace move). The class is
  a baseline-resolution bridge; co-locating with its primary
  collaborators removed an awkward single-class package.
- The `org.javai.punit.model.*` catch-all is dispersed:
  - `CovariateProfile` consolidated on the typed
    `api.covariate.CovariateProfile` (the duplicate is gone).
  - Covariate-shaped types moved to `api.covariate.*`.
  - `UseCaseAttributes` moved to `api.*`.
  - Verdict-lifecycle types moved to `verdict.*`.
  - The `model/` directory is deleted.
- `org.javai.punit.controls.budget/pacing` collapsed into
  `org.javai.punit.engine.budget/pacing` (then under
  `internal.engine.*`); `controls/` is deleted.
- The top-level `org.javai.punit.spec.{registry,expiration,model,criteria}`
  runtime tree relocated under `engine.spec.*` (then under
  `internal.engine.spec.*`); `api.spec.*` is the public typed-spec
  surface and is unchanged.
- `org.javai.punit.engine.output` folded into
  `org.javai.punit.engine.emit` (then under
  `internal.engine.emit`); `engine/output/` is deleted.

### Added

- `org.javai.punit.api.match` subpackage with `ValueMatcher`,
  `MatchResult`, `StringMatcher`, `JsonMatcher`. Wired through
  `Sampling.matching(...)` for instance conformance.
- `PackageStructureArchitectureTest` carrying the cleanup-arc rules
  with `FreezingArchRule` violation stores under `archunit_store/`.
  Each rule's frozen exception list shrinks as cleanup commits land;
  the rules become permanent regression guards when the stores
  reach empty.
- `RuntimeArchitectureTest.internalRuntimeMustNotImportJUnit` —
  parallel JUnit-free rule for `internal.runtime`.

### Internal

- ArchUnit rule refresh: `CoreArchitectureTest.statisticsModuleMustBeIsolated`
  and `utilPackageMustBeSelfContained` updated to the post-namespace
  layout. `corePackagesMustNotDependOnJUnitExtensions` renamed to
  `statisticsPackageMustNotDependOnJUnitExtensions` (the rule body
  always covered only the statistics package; the old name was stale).
- Six static methods promoted from package-private to `public` to
  cross the new package boundary out of `runtime/` into
  `internal.runtime/`: `BaselineEmitter.emit`,
  `BaselineProviderResolver.resolveDir`/`resolve`,
  `EmpiricalTestComposer.compose`, `TestIdentityResolver.resolve`,
  the `*Emitter.emit` methods. All inside `internal.*` and so still
  off-limits to authors via `publicMustNotImportInternal`.

### Known follow-up

- The `publicMustNotImportInternal` ArchUnit rule's frozen store is
  not empty at release time (116 entries across three categories):
  `runtime.PUnit` driving internals by design; `verdict/*` types
  embedding `internal.engine.budget` / `internal.engine.pacing`
  configuration state for serialisation (package drift, candidate
  for follow-up); `api.covariate.CovariateDeclaration` reaching into
  `internal.util.HashUtils` (small util reach). The rule is a live
  regression guard via `freeze()`; the residue tracks the remaining
  cross-boundary cleanup. Resolving the verdict-embedding drift is
  the prerequisite for adopting JPMS modularisation.

## [0.7.0-alpha2] - 2026-05-10

> **🧪 Experimental release.** Patch over 0.7.0-alpha. Closes the
> wider feasibility-gate audit listed as a "Known gap" in the
> 0.7.0-alpha entry; corrects the empirical-threshold-derivation
> methodology against the javai-R oracle; tightens cost / UX on
> empirical runs that have no resolvable baseline; and removes
> internal tracking codes from public source. No breaking API
> changes vs 0.7.0-alpha.

### Fixed
- **Empirical threshold derivation now applies Wilson at the test
  sample size**, per Statistical Companion §3.4 / §4.3.2. The 0.7.0-alpha
  path applied Wilson at the baseline sample size and compared the
  test's Wilson lower bound against that; this could yield a
  threshold that the test's own confidence machinery couldn't
  underwrite. The fix matches javai-R's reference implementation;
  the conformance fixtures pass tolerance 1e-6.
- **PowerAnalysis.sampleSize now resolves covariate-stamped
  baselines.** A use case declaring covariates could not use the
  confidence-first authoring pattern — `PowerAnalysis.sampleSize`
  threw `IllegalStateException("no baseline found …")` even when a
  matching covariate-stamped baseline existed and was findable by
  the test pipeline's own resolver. Fixed by threading the use
  case's covariate profile + declarations through to the four-arg
  `BaselineResolver` overload.
- **PowerAnalysis admits perfect baselines.** A two-sided
  precondition rejected baselines whose recorded `observedPassRate`
  sat within `mde` of 1.0 — including the common case of a
  `rate = 1.0` baseline. Relaxed to one-sided so the threshold
  path's existing perfect-baseline two-step is reachable from the
  power path too.
- **INCONCLUSIVE verdicts emit a `[PUNIT-INCONCLUSIVE]` line to
  stderr** before the `TestAbortedException` is thrown. Earlier the
  IDE per-test detail pane showed the explanation but the build
  console saw only the four ⊘ icons; a developer watching the build
  log had no account of why a test was skipped.
- **Empirical runs with no resolvable baseline short-circuit
  pre-flight.** Earlier the framework drove the spec through the
  engine — taking *all configured samples* — before the criterion's
  `evaluate` returned INCONCLUSIVE. Each sample is an LLM API call
  in the typical use case; the framework now skips sampling when the
  verdict is structurally guaranteed to be INCONCLUSIVE-no-baseline.

### Added — feasibility-gate audit closes
- **Pre-flight feasibility now applies to contractual criteria too.**
  0.7.0-alpha's `Feasibility.check` silently early-returned for any
  criterion where `!isEmpirical()`; a contractual test with a 99.99%
  SLA at n=50 produced a verdict instead of aborting. Both
  contractual and empirical paths now go through the same gate.
- **Soundness floor enforced regardless of intent.** A configured
  confidence below `StatisticalDefaults.SOUNDNESS_FLOOR_CONFIDENCE`
  (= 0.80) aborts pre-flight in both VERIFICATION and SMOKE intents.
  This was listed as a known gap in 0.7.0-alpha; closes the wider
  feasibility-gate audit.
- **End-to-end audit tests** in `punit-junit5` cover each pre-flight
  invariant via TestKit, with abort-before-sampling verified by a
  counter probe on the use case. The original regression slipped
  past existing unit tests because the gate was wired only at the
  evaluator level — these guard against the same shape of regression
  recurring.

### Changed
- **Orchestrator-internal requirement codes** (the project family's
  internal feature-tracking shorthand) removed from public source —
  javadoc, inline comments, `@DisplayName` strings, assertion
  messages. These tracking codes are interpretable only via an
  internal catalog and were noise to an open-source reader. An
  ArchUnit-style regression guard scans both production and test
  source to keep them out.

### Known gaps still in this experimental release
- **Statistical early termination** (early termination on
  failure-inevitable / success-guaranteed) is still not wired. The
  supporting model is in place but the per-sample evaluator and the
  builder method that opts out have not yet been re-added. Tracked
  for restoration in a subsequent release.

## [0.7.0-alpha] - 2026-05-07

> **🧪 Experimental release.** The first cut of the 0.7.0 typed-builder API. Core authoring surface and statistical engine are ready for evaluation; some optimisations are deferred — see **Known gaps** below. v0.x means breaking changes are still possible: pin to this exact version if you depend on its surface today, and check the issue tracker before upgrading. Feedback at https://github.com/javai-org/punit/issues.

> **⚠️ Breaking changes** — punit 0.7.0 replaces the annotation-driven authoring style of 0.6.x with a typed, builder-based one. See [MIGRATION-0.6-to-0.7.md](docs/MIGRATION-0.6-to-0.7.md) — it carries a coding-assistant prompt that walks your codebase and applies the migration, plus per-change discussion and FAQ. There is no deprecation cycle and no 0.6.x-compatible shim. Maven coordinates are unchanged.

### Changed (breaking)
- **Annotation attributes move to a typed builder.** Configuration that used to live on `@ProbabilisticTest(...)`, `@MeasureExperiment(...)`, `@ExploreExperiment(...)`, `@OptimizeExperiment(...)` is now expressed via builder calls on `PUnit.testing(...)` / `.measuring(...)` / `.exploring(...)` / `.optimizing(...)` invoked from the method body. The annotations survive as bare markers with no attributes. See [MIGRATION §2.1](docs/MIGRATION-0.6-to-0.7.md#21-parameterised-annotation--unparameterised-annotation--builder).
- **Three experiment annotations consolidated to one.** `@MeasureExperiment`, `@ExploreExperiment`, `@OptimizeExperiment` → single `@Experiment` marker; experiment kind is selected by the body's `PUnit.*` factory call. See [MIGRATION §2.1](docs/MIGRATION-0.6-to-0.7.md#21-parameterised-annotation--unparameterised-annotation--builder).
- **`@UseCase` annotation replaced by the typed `UseCase<FT, I, O>` interface.** A use case is now a class implementing the interface; metadata (`description`, `warmup`, `pacing`, `covariates`, …) becomes method overrides; factor variation is expressed via a factor record passed at construction. See [MIGRATION §2.2](docs/MIGRATION-0.6-to-0.7.md#22-use-cases).
- **Covariate redesign.** `@Covariate`, `@CovariateSource`, `@DayGroup`, `@RegionGroup` annotations are gone. Covariates are now values from the sealed hierarchy under `org.javai.punit.api.covariate` (`DayOfWeekCovariate`, `RegionCovariate`, `TimeOfDayCovariate`, `TimezoneCovariate`, `Covariate.custom(...)`), declared on `UseCase.covariates()` and resolved on `UseCase.customCovariateResolvers()` for project-defined covariates. See [MIGRATION §2.3](docs/MIGRATION-0.6-to-0.7.md#23-covariates).
- **`org.javai.punit.contract` package gone.** `Postcondition`, `PostconditionCheck`, `PostconditionEvaluator`, `PostconditionResult`, `UseCaseOutcome` relocated to `org.javai.punit.api`; `ServiceContract` replaced by the typed `Contract<I, O>`, typically folded into the `UseCase` rather than kept as a separate type. See [MIGRATION §2.4](docs/MIGRATION-0.6-to-0.7.md#24-imports-moved-from-contract-to-api).
- **MEASURE baseline shape — aggregate signal only.** Baseline YAML no longer carries the per-sample `resultProjection:` block. The probabilistic test consumes only pass count, sample total, footprint, fingerprint, derived threshold, and the per-clause failure histogram; the per-sample shape is now carried by EXPLORE / OPTIMIZE outputs only. Baselines produced by 0.6.x are not consumed by 0.7.x — regenerate. File size becomes constant in sample count; the EX10 integrity fingerprint covers only content the resolver reads.
- **Failed contract evaluations emit to `System.err` during MEASURE and probabilistic-test runs.** One `[PUNIT-FAIL]` line per failed clause, written synchronously as the engine processes each sample. The line carries the sample index, the failure kind (`apply` / `match` / `postcondition` / `defect`), the clause name, and a single-line reason. Apply-level failures, match mismatches, postcondition failures, and defects all use the same line shape and channel. Stderr is not intercepted by the progress bridge, so failure detail and the `[PUNIT-PROGRESS]` counter on stdout coexist without interleaving.

### Removed (breaking)
- **`@Sentinel` annotation.** The `punit-sentinel` module scans typed `UseCase` implementations directly; no marker required.
- **`@FactorSetter`, `@FactorGetter`, `@FactorAnnotations`.** Reflection-based factor accessors retire entirely. Factors are typed records consumed at construction. (Deprecated in 0.6.0.)
- **`UseCaseProvider.registerAutoWired(...)`** and `UseCaseFactory.registerAutoWired(...)`. Use `registerWithFactors(...)`. (Deprecated in 0.6.0.)
- **Parameter-level annotations no longer wired by the framework**: `@Factor`, `@FactorSource`, `@Input`, `@InputSource`, `@Config`, `@ConfigSource`, `@ControlFactor`, `@DayGroup`, `@RegionGroup`, `@Latency`, `@ExperimentDesign`, `@ExperimentGoal`. Their `@interface` definitions linger in `api/` for binary compatibility, but the engine reads none of them. Use the typed authoring surface.
- **Types removed from `api/`**: `AdaptiveFactor`, `UseCaseContext`, `ProbabilisticTestBudget`. Subsumed by the typed pipeline (custom `FactorsStepper` for adaptive factors; covariate metadata + `TokenTracker` for context; builder-level budget calls + `BudgetExhaustionPolicy` for budget).
- **`OptimizeExperiment.initialControlFactorValue`** (inline string attribute, could not express typed initial values).
- **`OptimizeExperiment.initialControlFactorSource`** (renamed to **`initialFactor`** with identical semantics — a static no-arg method on the experiment class returning the typed initial value).
- **`@FactorGetter` fallback path** in `OptimizeStrategy`. Initial factor values come solely from the `initialFactor` method.
- **`ExploreExperiment.expiresInDays`**. Baseline expiration is a property of MEASURE-produced baseline specs only.

### Added
- **Typed-builder entry points** in `org.javai.punit.runtime.PUnit`: `testing(...)`, `measuring(...)`, `exploring(...)`, `optimizing(...)`.
- **Typed pipeline types in `org.javai.punit.api`**: `Contract<I, O>`, `ContractBuilder<O>`, `Sampling`, `MatchResult`, `ValueMatcher`, `Expectation`, `InputSupplier`, `FactorBundle`, `FactorValue`, `LatencyResult`, `LatencySpec`, plus value records (`BooleanValue`, `DecimalValue`, `DurationValue`, `EnumValue`, `InstantValue`, `IntegralValue`, `StringValue`, `UriValue`).
- **`org.javai.punit.api.spec` subpackage** with spec / verdict / runtime-result types: `Spec`, `TypedSpec`, `Verdict`, `EngineResult`, `EngineRunSummary`, `ProbabilisticTestResult`, `Trial`, `SampleClassification`, `SampleSummary`, `SampleObserver`, `SampleExecutor`, `FactorsStepper`, `NextFactor` (sealed `Continue` / `Stop`), `Scorer`, `ResourceControls` / `ResourceControlsBuilder`, `Criterion`, `EvaluatedCriterion`, `BaselineProvider`, `BudgetExhaustionPolicy`, `FailureCount`, `FailureExemplar`, `LatencyStatistics`, `PassRateStatistics`, `NoStatistics`, `PercentileLatency`, `PercentileKey`, `TerminationReason`, `DurationViolation`, `ExperimentResult`.
- **`org.javai.punit.api.covariate` subpackage** with the sealed `Covariate` hierarchy.
- **`OptimizeBuilder.disableEarlyTermination()`** — opts a single optimize run out of statistical early termination.
- **Postcondition failure histograms** on `SampleSummary`, `ProbabilisticTestResult`, verdict text, verdict XML (`<postcondition-failures>`), and the HTML report. Failures broken down by named clause with bounded exemplars.
- **`EngineRunSummary` on `ProbabilisticTestResult`** — run-level scalars (planned/executed samples, elapsed, tokens, latency, termination, confidence, matched baseline filename).
- **Covariate-aware best-match baseline selection.** Baseline filenames carry a covariate hash; the resolver picks the best-aligned baseline.
- **Pre-flight feasibility gate.** A VERIFICATION-intent test whose configured (samples, threshold, confidence) tuple cannot underwrite a verification claim aborts pre-flight with an `INFEASIBLE VERIFICATION` diagnostic — no samples execute, no misleading PASS / FAIL is produced. Applies to both contractual (`SLA` / `SLO` / `POLICY`) and empirical thresholds. SMOKE-intent runs are silent: the developer has explicitly opted into the sizing gap.
- **RP07 XML verdict on every test.** Each `@ProbabilisticTest` produces a `{className}.{methodName}.xml` file under `build/reports/punit/xml/` (configurable via `punit.report.dir` / `PUNIT_REPORT_DIR`, suppressible with `punit.report.enabled=false`). The HTML report (`./gradlew punitReport`) consumes these files. See Part 11 of the user guide for the field reference.
- **`BaselineLookup.sourceFile`.** Matched baseline filename threaded from `BaselineResolver` through `Spec.conclude` to the verdict XML's `<provenance spec-filename>` attribute.

### Known gaps in this experimental release

These are the rough edges that justify the `-alpha` qualifier. Each is tracked for restoration in a subsequent release.

- **Statistical early termination is not yet wired.** The 0.6.x sample loop stopped early when the outcome became impossible (failure inevitable) or guaranteed (success guaranteed); the spec-engine refactor preserved the supporting model (the `TerminationReason` enum, scenario fixtures, rendering paths) but the per-sample evaluator and the `disableEarlyTermination()` builder method on the probabilistic-test surface have not yet been re-added. Verdicts are correct — every test runs to its configured sample count and produces the right answer — but configurations that could have terminated after a few samples now run all of them.
- **Wider feasibility-gate audit.** The pre-flight gate (see *Added* above) is wired correctly for the failure shape that motivated this release, but the catalog defines a family of related invariants — soundness floor (implied confidence < 80%, cross-intent), parameter-bounds validation, configuration-coherence checks across the threshold-first / sample-size-first / confidence-first approaches. Some of these remain unchecked. Configurations whose implied confidence falls below 80% are not yet rejected across all intents.

## [0.6.0] - 2026-04-16

### Changed
- **Annotations consolidation (breaking).** All user-facing PUnit annotations — `@ProbabilisticTest`, `@MeasureExperiment`, `@ExploreExperiment`, `@OptimizeExperiment` — now live in `punit-core`. Previously, the three experiment annotations lived in `punit-junit5` because they carried `@ExtendWith(ExperimentExtension.class)`; this meta-annotation is now removed. JUnit wiring is handled entirely via ServiceLoader auto-registration (already in place). Users of the `org.javai.punit` Gradle plugin see no change. Maven users and plain-Gradle users without the plugin must set `junit.jupiter.extensions.autodetection.enabled=true` in `junit-platform.properties` — see `docs/MAVEN-CONFIGURATION.md`. The fully-qualified annotation names (`org.javai.punit.api.MeasureExperiment` etc.) are unchanged; only the publishing module differs. Sentinel specs in modules that depend on `punit-core` (and not `punit-junit5`) now compile without the reflection-based workaround that shipped in 0.5.x.
- **Immutable use case pattern.** `@FactorSetter`, `@FactorGetter`, and `UseCaseProvider.registerAutoWired` are deprecated and scheduled for removal. Mutable factor patterns violate the i.i.d. assumption required for valid statistical inference. Use `UseCaseProvider.registerWithFactors` to configure factors at construction time. See the Immutable Use Cases section in the user guide.
- **Statistics rework for contract-linked latency.** Latencies are now formally categorised by whether the associated service contract passed or failed. This addresses a prior weakness where latency statistics could be computed over a population that mixed successful and failed invocations, distorting percentile estimates. The change is of course synchronised with the Javai oracle project [javai-R](https://github.com/javai-org/javai-R).
- **Experiment output locations split by semantics.** MEASURE specs (inputs to subsequent `@ProbabilisticTest` runs) still default to `src/test/resources/punit/specs/` and are intended to be committed. EXPLORE and OPTIMIZE outputs (human-review artefacts only) now default to `build/punit/explorations/` and `build/punit/optimizations/` respectively. Downstream projects that were relying on the old default locations for EXPLORE/OPTIMIZE outputs must set `punit.explorations.outputDir` / `punit.optimizations.outputDir` explicitly to restore the prior behaviour. See the Experiment Output Directories section in the user guide.

### Added
- `@ConfigSource` and `@InputSource` can now be combined on the same EXPLORE experiment method, enabling exploration across both configuration and input axes simultaneously.
- Additional validation for `@Input` annotation targets, catching misconfiguration at setup time rather than during sample execution.
- Conformance test coverage extended to match the javai-R statistical oracle for additional edge cases.

### Removed
- **`zjsonpatch` dependency.** PUnit no longer depends on `zjsonpatch`. Downstream consumers that relied on the transitive presence of this library must declare it directly.

### Deprecated
- `@FactorSetter`, `@FactorGetter`, `UseCaseProvider.registerAutoWired` — see **Changed** above.

## [0.5.2] - 2026-03-22

### Added
- **Latency distribution always visible** — the HTML report now shows p50, p95, and p99 latency values for every test with successful samples, even when no `@Latency` thresholds are configured. Previously, tests without explicit latency thresholds showed dashes in these columns.
- **Per-cell latency colour coding** — latency cells in the HTML report are colour-coded per percentile: green when an asserted threshold is met, red when exceeded, and muted grey when no threshold is configured (observational only).
- **`punitVerify` Gradle task** — a post-execution verification task (wired into `check`) that reads verdict XMLs and fails the build only when a probabilistic test's statistical verdict is FAIL. Individual sample failures during test execution no longer break the build; they are reported as JUnit "aborted" (skipped) rather than "failed", deferring the pass/fail decision to the aggregate verdict.

### Changed
- Framework console output now routes to stdout/stderr instead of Log4j, ensuring visibility in all build environments without Log4j configuration.

## [0.5.1] - 2026-03-21

### Fixed
- Improved accuracy of baseline threshold derivation for high-reliability use cases. The confidence interval used to compute `minPassRate` in MEASURE experiment specs now uses the Wilson score method consistently, matching the statistical engine used elsewhere in PUnit. Previously generated specs with very high observed pass rates (near 100%) may produce slightly different `minPassRate` values when re-measured. Re-running affected MEASURE experiments is recommended.

## [0.5.0] - 2026-03-13

### Added
- **HTML test report** — standalone HTML report summarising all probabilistic test verdicts from a test run. Generated via `./gradlew punitReport` from XML verdict files produced during test execution. The report groups results by use case, provides expandable per-test detail (verdict summary, statistical analysis, baseline provenance), latency percentiles, and operator guidance for inconclusive verdicts caused by covariate misalignment.
- Statistical assumptions and limitations disclosure in the HTML report header (collapsed by default)
- `punit-report` module containing XML verdict serialisation, HTML report generation, and report configuration
- `punitReport` Gradle task registered automatically by the PUnit plugin

## [0.4.1] - 2026-03-10

### Changed
- Bumped `org.javai:outcome` transitive dependency from 0.1.0 to 0.2.0 — see [outcome 0.2.0 changelog](https://github.com/javai-org/outcome/releases/tag/v0.2.0) for breaking changes to `Failure` record fields (`exception`, `retryAfter`, `correlationId` now return `Optional`)

## [0.4.0] - 2026-03-10

### Added
- **PUnit Sentinel** — a JUnit-free execution engine for running probabilistic tests and experiments in deployed environments (staging, production). The Sentinel runs the same `@ProbabilisticTest` and `@MeasureExperiment` methods defined for development-time testing, producing environment-specific baselines and dispatching verdicts to observability infrastructure.
- `@Sentinel` annotation for marking reliability specification classes consumed by both JUnit and the Sentinel engine
- `SentinelRunner` API for programmatic test and experiment execution with use-case-level filtering
- `SentinelMain` CLI with commands (`test`, `exp`), options (`--list`, `--useCase <id>`, `--verbose`, `--help`), and structured exit codes
- `createSentinel` Gradle task (via the `org.javai.punit` plugin) that builds an executable fat JAR containing all `@Sentinel` classes, their dependencies, and the Sentinel runtime
- `WebhookVerdictSink` for dispatching verdicts as JSON to HTTP endpoints with configurable URL, timeout, and headers
- Environment metadata tagging on verdicts (`PUNIT_ENVIRONMENT`, `PUNIT_INSTANCE_ID`)
- Three-module decomposition: `punit-core` (JUnit-free engine), `punit-junit5` (JUnit 5 extensions), `punit-sentinel` (Sentinel runtime)
- `UseCaseFactory` in `punit-core` for JUnit-free use case registration, independent of `UseCaseProvider`
- `SpecRepository` interface with `LayeredSpecRepository` for environment-local-first spec resolution
- Dimension-scoped assertion API: `UseCaseOutcome.assertContract()`, `assertLatency()`, `assertAll()`
- Per-dimension baselines and spec resolution (functional and latency specs as separate files)
- ArchUnit-enforced module boundaries: `punit-core` and `punit-sentinel` have zero `org.junit` dependencies

### Changed
- Reliability specifications now use the spec-first model: `@Sentinel` classes in production source sets, consumed by both JUnit (via inheritance) and the Sentinel engine (directly)
- Explicit `@Latency` attribute on `@ProbabilisticTest` overrides baseline-derived thresholds without causing a misconfiguration error

## [0.3.1] - 2026-03-02

### Fixed
- Verdict header contradicting verdict body when using spec-derived thresholds — `BernoulliTrialsConfig` was not synced with `TestConfiguration` after baseline `minPassRate` derivation, causing the verdict summary to use stale values

## [0.3.0] - 2026-03-02

### Added
- Latency assertions via `@Latency` annotation with per-percentile thresholds (p50, p90, p95, p99)
- Automatic latency threshold derivation from baseline specs using confidence-interval upper bounds
- Advisory latency mode (default): breaches warn in output but do not fail the test
- Latency enforcement mode (`-Dpunit.latency.enforce=true`) for CI environments with consistent hardware
- Latency feasibility gate for VERIFICATION intent — rejects undersized sample configurations before execution
- Indicative marker for latency results when sample size is below the statistical minimum
- Latency data captured in MEASURE and EXPLORE experiment specs (percentiles, mean, standard deviation, sample count)
- Latency analysis section in transparent statistics output

### Changed
- Improved EXPLORE experiment output format with diff-anchor lines

## [0.2.0] - 2026-02-15

### Added
- Release lifecycle Gradle tasks (`release`, `tagRelease`)
- Centralised version in `gradle.properties`
- `CHANGELOG.md` with validation gate in the release task
- Test intent system: `VERIFICATION` (evidential) and `SMOKE` (sentinel) modes
- Feasibility gate using Wilson score lower bound for verification tests
- Diff-anchor lines in explore experiment output
- Infeasibility fail-fast for infeasible thresholds

## [0.1.0] - 2025-12-15

Initial release of PUnit — a JUnit 5 extension framework for probabilistic
unit testing of non-deterministic systems.

### Added
- `@ProbabilisticTest` annotation with configurable samples, minPassRate, and confidence
- `@MeasureExperiment` and `@ExploreExperiment` for baseline measurement and exploration
- `@OptimizeExperiment` for feedback-driven parameter optimization
- Spec-based workflow: measure baseline, generate YAML spec, derive thresholds statistically
- Early termination (impossibility and success-guaranteed detection)
- Time and token cost budgets (method, class, and suite scopes)
- Covariate-aware baseline selection with environmental, temporal, and operational categories
- Design by Contract support via `ServiceContract` and `UseCaseOutcome`
- `@InputSource` for parameterized test inputs (JSON, CSV)
- Instance conformance checking (JSON schema matching)
- Pacing support to avoid rate limits from LLM/API providers
- Statistical engine with Wilson score confidence intervals and threshold derivation
- Verbose statistical explanation output
- Gradle plugin (`org.javai.punit`) for test/experiment task configuration

[Unreleased]: https://github.com/javai-org/punit/compare/v0.7.0-alpha5...HEAD
[0.7.0-alpha5]: https://github.com/javai-org/punit/compare/v0.7.0-alpha4...v0.7.0-alpha5
[0.7.0-alpha4]: https://github.com/javai-org/punit/compare/v0.7.0-alpha3...v0.7.0-alpha4
[0.7.0-alpha3]: https://github.com/javai-org/punit/compare/v0.7.0-alpha2...v0.7.0-alpha3
[0.7.0-alpha2]: https://github.com/javai-org/punit/compare/v0.7.0-alpha...v0.7.0-alpha2
[0.7.0-alpha]: https://github.com/javai-org/punit/compare/v0.6.0...v0.7.0-alpha
[0.6.0]: https://github.com/javai-org/punit/compare/v0.5.2...v0.6.0
[0.5.2]: https://github.com/javai-org/punit/compare/v0.5.1...v0.5.2
[0.5.1]: https://github.com/javai-org/punit/compare/v0.5.0...v0.5.1
[0.5.0]: https://github.com/javai-org/punit/compare/v0.4.1...v0.5.0
[0.4.1]: https://github.com/javai-org/punit/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/javai-org/punit/compare/v0.3.1...v0.4.0
[0.3.1]: https://github.com/javai-org/punit/compare/v0.3.0...v0.3.1
[0.3.0]: https://github.com/javai-org/punit/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/javai-org/punit/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/javai-org/punit/releases/tag/v0.1.0
