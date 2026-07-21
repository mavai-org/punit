# PUnit User Guide

*Probabilistic testing for systems characterised by uncertainty.*

---

## Table of Contents

- [Introduction](#introduction)
  - [Why probabilistic testing](#why-probabilistic-testing)
  - [What PUnit is](#what-punit-is)
  - [Quick start](#quick-start)
- [Part 1: The Service Contract — the shared correctness target](#part-1-the-service-contract--the-shared-correctness-target)
- [Part 2: The lifecycle](#part-2-the-lifecycle)
- [Part 3: Testing](#part-3-testing)
- [Part 4: Measuring](#part-4-measuring)
- [Part 5: Exploring](#part-5-exploring)
- [Part 6: Optimizing](#part-6-optimizing)
- [Part 7: Latency](#part-7-latency)
- [Part 8: Resource controls](#part-8-resource-controls)
- [Part 9: Covariates](#part-9-covariates)
- [Part 10: Sentinels — production-time execution](#part-10-sentinels--production-time-execution)
- [Part 11: Reports](#part-11-reports)
- [Part 12: Statistics — what is actually computed](#part-12-statistics--what-is-actually-computed)
- [Appendix A: Configuration](#appendix-a-configuration)
- [Appendix B: Glossary](#appendix-b-glossary)

---

## Introduction

### Why probabilistic testing

Traditional unit testing rests on a binary premise: call a function,
assert the result, pass or fail. This works brilliantly for deterministic
systems, where the same input always produces the same output. An
entire class of modern systems does not behave this way:

- **LLM integrations** — outputs vary with temperature, prompt phrasing,
  or simply from call to call.
- **ML model inference** — predictions occasionally fall below their
  confidence threshold.
- **Distributed systems** — network conditions, timing, and contention
  introduce variability that no caller controls.
- **Randomised algorithms** — by design, outputs differ across executions.

Test such a system the traditional way and the test will sometimes fail.
What does that failure teach you? Nothing useful: only that the system
under test is non-deterministic, which you already knew. The fundamental
question is not *"does it work?"* but **"how often does it work, and is
that often enough?"**

PUnit answers the second question. Every test is a sample from a
distribution; every verdict reflects what the distribution looks like,
not what one particular invocation happened to do.

Stochastic behaviour shows up along two independent dimensions:

1. **Functional** — whether the system produces a correct result. An
   LLM might return valid JSON 93% of the time; a classifier might
   achieve 97% accuracy. Correctness is a random variable.
2. **Temporal** — how long the system takes, even on successful calls.
   A 200ms-mean service occasionally takes 2 seconds. Latency is a
   distribution, not a point.

PUnit handles both with statistical machinery sized to the question,
and combines the two with logical AND: a test passes only if *both*
the functional and the temporal claim are satisfied.

### What PUnit is

PUnit is a JUnit 5 extension framework for **probabilistic testing**.
It runs your test many times, records what happened, and decides PASS
or FAIL against a statistical threshold rather than a single-execution
assertion.

PUnit is *not* a replacement for traditional unit testing. Deterministic
code should still be tested deterministically. PUnit is for the
stochastic component of your stack: the LLM call, the classifier, the
distributed-systems hop, the rate-limited dependency.

It provides:

1. **Probabilistic tests** — run a service contract `n` times, count successes,
   evaluate against a contractual threshold or against a previously
   recorded baseline.
2. **Latency assertions** — evaluate observed percentile latencies
   (p50, p90, p95, p99) against contractual or baseline-derived
   thresholds, with a non-parametric construction.
3. **Experiments** — measure a baseline, explore configurations, or
   optimize toward a target. The same service contract definition powers
   all four.
4. **Statistical rigour** — Wilson-score confidence intervals,
   binomial order-statistic upper bounds for latency thresholds,
   power analysis for sample sizing, qualified verdicts.

The mathematical foundations live in the
[Statistical Companion Document](https://r.mavai.org/statistical-companion.pdf);
this guide stays at the engineering level, with pointers into the
companion where a reader wants the proof.

### Quick start

**Gradle setup:**

```kotlin
// build.gradle.kts
plugins {
    id("org.mavai.punit") version "0.9.3"
}

dependencies {
    testImplementation("org.mavai:punit-core:0.9.3")
}
```

The plugin auto-registers the `experiment` and `exp` tasks for running
experiments, configures the standard `test` task to exclude
experiment-tagged methods, and supports `-Prun=` shorthand for
filtering.

**Three static imports** give an author the criterion-authoring
vocabulary. The rest of the examples in this guide assume them:

```java
import static org.mavai.punit.api.criterion.Criteria.*;   // meeting, empirical, of
import static org.mavai.punit.api.ThresholdOrigin.*;       // SLA, SLO, POLICY, UNSPECIFIED
import static org.mavai.punit.api.PercentileKey.*;         // P50, P90, P95, P99
```

**The smallest test.** A probabilistic test can be a single method.
Declare the service call and the bar it must clear *inline*, then say how
many times to sample it:

```java
@ProbabilisticTest
void apiReturnsJson() {
    LlmClient llm = LlmClient.resolve();
    PUnit.testing(
            Contract.<String, String>inline()
                    .invoking(prompt -> {
                        ChatResponse r = llm.chat(prompt);
                        return r.content() == null
                                ? Outcome.fail("empty", "LLM returned no content")
                                : Outcome.ok(r.content());
                    })
                    .passRate(0.95)
                    .satisfies("parses as JSON", s -> isValidJson(s)
                            ? Outcome.ok()
                            : Outcome.fail("not-json", "did not parse: " + truncate(s)))
                    .sampling(100, PROMPTS))
            .assertPasses();
}
```

That is a complete probabilistic test. It runs the call 100 times over
`PROMPTS`, counts how many parse as JSON, and applies the Wilson-95%
lower bound to the observed rate; it passes if the bound clears 0.95.
One artefact, no separate class, none of the "contract" vocabulary yet —
just **the call, the bar, and the sample size**. `Contract.inline()`
reads top-to-bottom: the service call (`invoking`), the target
(`passRate`), the per-sample check (`satisfies`), then `sampling(...)`.

**Factoring the contract out.** The inline form fits a service held to a
fixed, *normative* target with a **single** criterion (which may carry
several postconditions, ANDed together). The moment you want to **reuse**
that definition across several tests, sweep it in an experiment, derive
its threshold from a **measured baseline** instead of asserting one, or
declare **several independently-thresholded criteria** (each with its own
pass rate and verdict), you lift the same body into a named
`ServiceContract`. The service call and the criteria transfer almost
verbatim — the criteria simply gains the `meeting()` opener and the class
gains an `id()`:

```java
public final class JsonResponseServiceContract
        implements ServiceContract<NoFactors, String, String> {

    private final LlmClient llm = LlmClient.resolve();

    @Override
    public Outcome<String> invoke(String prompt, TokenTracker tracker) {
        ChatResponse r = llm.chat(prompt);
        tracker.recordTokens(r.totalTokens());
        return r.content() == null
                ? Outcome.fail("empty", "LLM returned no content")
                : Outcome.ok(r.content());
    }

    @Override
    public Criteria<String> criteria() {
        return meeting().passRate(0.95)
                .contractRef(SLA, "Acme JSON API SLA v1 §3.2")
                .satisfies("Parses as JSON", s ->
                        isValidJson(s)
                                ? Outcome.ok()
                                : Outcome.fail("not-json", "did not parse: " + truncate(s)));
    }
}
```

`NoFactors` is the empty factor record punit provides (`org.mavai.punit.api.NoFactors`) for service contracts with no varying configuration factors. `meeting()` opens a contractual chain. `.passRate(0.95)` declares the
criterion is a pass-rate target at 0.95. `.contractRef(SLA, "...")`
names the source category (SLA) and the document reference together.
`.satisfies(...)` adds the per-sample postcondition.

**A test** asserts that the service contract meets its declared
criteria:

```java
public class JsonResponseTest {

    private static final List<String> PROMPTS = List.of(
            "Return {\"hello\": \"world\"} as JSON",
            "Return [1, 2, 3] as JSON",
            "Return {\"answer\": 42} as JSON");

    @ProbabilisticTest
    void apiMeetsContract() {
        PUnit.testing(Sampling.of(f -> new JsonResponseServiceContract(),
                        100, PROMPTS))
                .assertPasses();
    }
}
```

This test runs the service contract 100 times, counts how many return valid
JSON, and applies the Wilson-95% lower bound to the observed pass
rate. It passes if the bound clears the 0.95 threshold. The
`contractRef` declared on the contract surfaces in the verdict for
audit traceability. This factor-less `testing(sampling)` form is for
service contracts whose behaviour does not vary with configuration
factors; when factors are in play, pass the factor instance explicitly
via `testing(sampling, factors)`.

That is the whole pattern. The rest of this guide unpacks it.

---

## Part 1: The Service Contract — the shared correctness target

Beyond the single inline test shown in the quick start, the shared unit
every test, experiment, and sentinel is built on is one class: the
`ServiceContract`. It is the single shared definition of the
service-under-test that every probabilistic test, every experiment, and
every sentinel run consults — and the artefact the inline form graduates
into the moment a definition needs to be reused, swept in an experiment,
or compared against a measured baseline. A baseline measured against
`ShoppingBasketServiceContract` and a regression test running against
`ShoppingBasketServiceContract` cannot drift onto different definitions of
"shopping basket".

A `ServiceContract<F, I, O>` declares three type parameters:

- `F` — the **factor** record. Configuration the author has chosen to
  vary (LLM model, temperature, prompt). Tests pass a concrete `F`
  at the call site; experiments either fix one (`measuring`) or sweep
  several (`exploring`, `optimizing`).
- `I` — the **input** type. The per-sample payload the service contract
  consumes.
- `O` — the **output** type. The successful-result type the contract
  evaluates against.

`ServiceContract` extends `Contract<I, O>`, which carries the two methods the
author always overrides:

```java
public interface ServiceContract<F, I, O> extends Contract<I, O> {
    // metadata: id(), description(), pacing(), warmup(),
    // covariates(), customCovariateResolvers()
}

public interface Contract<I, O> {
    Outcome<O> invoke(I input, TokenTracker tracker);
    Criteria<O> criteria();
    LatencyCriterion latency();            // optional sibling
    // framework-implemented: apply(...) — three overloads
}
```

The split is structural, not cosmetic. `Contract` is the per-sample
operational layer; `ServiceContract` is the per-run metadata layer. Author
cost stays at zero — one `implements ServiceContract<...>`, two required
methods to override (`invoke` and `criteria`), plus an `id()` for
non-trivial implementations and an optional `latency()` sibling when
the contract declares a temporal SLA.

### `invoke` — the service call

`invoke` does the operational work and returns an `Outcome<O>`.
**Outcome is data, not exceptions:**

- `Outcome.ok(value)` — success. The value is the service contract's output.
- `Outcome.fail(name, message)` — anticipated failure: a contract
  violation, a service-side error code, an empty response.

A *thrown exception* is a defect — a programming mistake, a
misconfiguration, or a catastrophe. The framework treats it as such:
the run aborts, the developer investigates. Use `Outcome.fail(...)`
for failures the author anticipated; reserve `throw` for failures the
author did *not* anticipate.

The `TokenTracker` is the cost channel. Service contracts that consume tokens
(LLM calls, paid APIs) report consumption via
`tracker.recordTokens(n)` during the call. The framework rolls these
up into per-sample and per-run cost totals; the budget machinery in
[Part 8](#part-8-resource-controls) consults them.

### `criteria` — the acceptance contract

`criteria()` returns a `Criteria<O>` declaring what the framework
evaluates against every successful sample's value. Single-criterion
contracts return a chain directly; multi-criterion contracts compose
via `Criteria.of(...)`. Each criterion carries one or more
`.satisfies(...)` clauses — a description and a check that returns
an `Outcome`:

```java
@Override
public Criteria<BasketTranslation> criteria() {
    return empirical().<BasketTranslation>passRate()
            .satisfies("Has actions", t ->
                    t.actions().isEmpty()
                            ? Outcome.fail("empty", "actions list was empty")
                            : Outcome.ok())
            .satisfies("All actions known", ShoppingBasketServiceContract::allKnown)
            .satisfies("Quantities non-negative", ShoppingBasketServiceContract::quantitiesValid);
}
```

The framework evaluates each clause per sample and surfaces the
per-clause results in the verdict, the report, and the optimize /
explore feedback path. A clause's failure does not throw — it is
recorded as data, ranked into a histogram by description, and
exemplified with the tripping inputs.

For a derivation — transform first, then evaluate clauses against
the derived type — chain `.transforming(...)`:

```java
return empirical().<BasketTranslation>passRate()
        .transforming(translation -> resolveAgainstCatalog(translation.actions()))
        .satisfies("Every action mapped", r -> r.unmapped().isEmpty()
                ? Outcome.ok()
                : Outcome.fail("unmapped", "missing: " + r.unmapped()))
        .satisfies("No duplicate SKUs", ...);
```

When the derivation fails, the criterion's clauses report as
`skipped`. When it succeeds, the clauses run against the derived
value.

A service contract with no acceptance criteria — smoke-test scaffolding,
throwaway fixtures — explicitly declares the choice:

```java
@Override
public Criteria<O> criteria() {
    return Criteria.empty();
}
```

This is intentional. A service contract without acceptance criteria is one whose
author has not yet decided what counts as success; making the choice
visible is part of building the service contract, not an afterthought.

### Identity, description, and metadata

Every service contract has a stable `id()` — the kebab-case form of the simple
class name by default (`ShoppingBasketServiceContract` → `shopping-basket`).
The id anchors baseline filenames, verdict reports, and covariate
fingerprints. Override when the default would collide or when the
class name does not read well as a filename.

Other metadata methods all default sensibly; override only when you
need a non-default value:

| Method                          | What it controls                                           |
|---------------------------------|------------------------------------------------------------|
| `description()`                 | Human-readable description for reports.                    |
| `warmup()`                      | Discarded sample count before counting begins.             |
| `pacing()`                      | Rate / concurrency limits the engine must respect.         |
| `covariates()`                  | Environmental factors the service contract is sensitive to.        |
| `customCovariateResolvers()`    | Resolvers for custom covariates declared in `covariates()`.|
| `latency()`                     | Optional sibling: contractual or empirical latency commitment ([Part 7](#part-7-latency)). |

[Part 9](#part-9-covariates) covers covariates in depth.
[Part 8](#part-8-resource-controls) covers warmup and pacing.

### A worked example

```java
public final class ShoppingBasketServiceContract
        implements ServiceContract<LlmTuning, String, BasketTranslation> {

    public record LlmTuning(String model, double temperature, String systemPrompt) {
        public static final LlmTuning DEFAULT =
                new LlmTuning("gpt-4o-mini", 0.3, DEFAULT_SYSTEM_PROMPT);
    }

    private final ChatLlm llm;
    private final LlmTuning tuning;

    public ShoppingBasketServiceContract(ChatLlm llm, LlmTuning tuning) {
        this.llm = llm;
        this.tuning = tuning;
    }

    @Override public String id() { return "shopping-basket"; }

    @Override
    public List<Covariate> covariates() {
        return List.of(
                Covariate.custom("llm_model",   CovariateCategory.CONFIGURATION),
                Covariate.custom("temperature", CovariateCategory.CONFIGURATION));
    }

    @Override
    public Map<String, Supplier<String>> customCovariateResolvers() {
        return Map.of(
                "llm_model",   () -> tuning.model(),
                "temperature", () -> Double.toString(tuning.temperature()));
    }

    @Override
    public Outcome<BasketTranslation> invoke(String instruction, TokenTracker tracker) {
        ChatResponse response;
        try {
            response = llm.chat(tuning.systemPrompt(), instruction,
                    tuning.model(), tuning.temperature());
        } catch (LlmException e) {
            return Outcome.fail("llm-error", e.getMessage());
        }
        tracker.recordTokens(response.totalTokens());
        return ShoppingActionValidator.validate(response);
    }

    @Override
    public Criteria<BasketTranslation> criteria() {
        return empirical().<BasketTranslation>passRate()
                .satisfies("Has actions", t ->
                        t.actions().isEmpty()
                                ? Outcome.fail("empty", "actions list was empty")
                                : Outcome.ok())
                .satisfies("All actions valid", ShoppingBasketServiceContract::actionsValid);
    }

    public static Sampling<LlmTuning, String, BasketTranslation> sampling(
            List<String> instructions, int samples) {
        return Sampling.of(
                tuning -> new ShoppingBasketServiceContract(ChatLlmProvider.resolve(), tuning),
                samples, instructions);
    }
}
```

That is one class — the unit of correctness. Tests, experiments, and
sentinels reference this class; they cannot diverge from it.

---

## Part 2: The lifecycle

PUnit organises a stochastic system's reliability story into four
phases, each with a distinct purpose. They form a natural progression
when an empirical baseline is the reference; they collapse to just
phase 4 when a contractual SLA is.

```
┌──────────────────────────────────────────────────────────────┐
│                                                              │
│   EXPLORE  →  OPTIMIZE  →  MEASURE  →  TEST                  │
│                                                              │
│   discover    refine      record      verify                 │
│   what is     what works   what is     it stays              │
│   possible    best         observed    that way              │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

Each phase has a fluent entry point:

```java
PUnit.exploring(sampling).grid(...).run();         // EXPLORE
PUnit.optimizing(sampling).initialFactors(...)
        .stepper(...).maximize(...).run();         // OPTIMIZE
PUnit.measuring(sampling, factors).run();          // MEASURE
PUnit.testing(sampling, factors).assertPasses();   // TEST
```

A typical project uses MEASURE and TEST routinely (CI), invokes
EXPLORE early in development to choose a configuration, and reaches
for OPTIMIZE when a configuration has many continuous knobs and a
clear scorer. Contractual SLA tests skip MEASURE entirely — the
threshold comes from the contract, not from data.

The rest of the guide takes each phase in turn, then layers on
latency, resource controls, covariates, and the production-time
sentinel runner.

---

## Part 3: Testing

`PUnit.testing(sampling, factors)` composes a probabilistic test that
runs the bound service contract `samples` times and evaluates the
**criteria** the contract declares against the result.

```java
@ProbabilisticTest
void apiMeetsSla() {
    PUnit.testing(ShoppingBasketServiceContract.sampling(INSTRUCTIONS, 50),
                  LlmTuning.DEFAULT)
            .assertPasses();
}
```

The `@ProbabilisticTest` annotation is a parameter-free JUnit hook —
it just tells JUnit Jupiter that this method is a punit test and
should be discovered. The criteria evaluated against each sample
are declared once on the service contract; the test site simply
runs the contract and asserts.

### Threshold sources

A criterion's threshold comes from one of two places, selected by the
factory that opens its declaration on the contract:

- **Contractual** (`meeting().passRate(...)`, `meeting().atMost(...)`)
  — a fixed value declared by an SLA, SLO, or policy. No baseline is
  consulted; PUnit passes the run iff its own Wilson-95% lower bound
  clears the declared threshold — the sample must provide
  confidence-grade evidence for the commitment, not merely a point
  estimate that grazes it.
  ```java
  return meeting().passRate(0.99).contractRef(SLA, "Acme SLA v3 §2.1")
          .satisfies(...);
  ```
- **Empirical** (`empirical().passRate()`, `empirical().atMost(...)`)
  — derived at runtime from a recorded baseline file. PUnit derives
  the threshold as the Wilson-95% lower bound of the baseline rate at
  the test's sample size, and passes iff the raw observed success
  count meets the derivation's integer cutoff `c = ⌈n·p*⌉`.
  ```java
  return empirical().<O>passRate().satisfies(...);
  ```

Empirical criteria require a `MEASURE` step to have produced a
baseline; see [Part 4](#part-4-measuring). Use empirical criteria for
*regression* testing (has the system degraded?), contractual criteria
for *compliance* testing (does the system meet its mandate?).

> **Antipattern: pinning a contractual threshold to a baseline's
> observed rate.** Reading a baseline file and pasting its rate
> into `meeting().passRate(0.935).contractRef(EMPIRICAL, ...)` looks
> like the empirical pattern but isn't. The contractual path is
> deterministic; sampling variance puts the next run's observed
> rate below 0.935 about half the time even when nothing has
> changed. Result: a coin-flip false-fail rate. Use
> `empirical().passRate()` — it resolves the baseline at runtime,
> applies the Wilson lower bound at the configured confidence,
> and gives the test the statistical buffer the hardcoded approach
> lacks.

### Test intent

Two intents shape the framework's tolerance for marginal sample sizes:

- **`TestIntent.VERIFICATION`** (default) — an evidential claim. PUnit
  requires the sample size to be large enough to verify the threshold
  at 95% confidence. If too small, PUnit *rejects the configuration*
  before any samples run, with a diagnostic that points at the
  feasibility gate.
- **`TestIntent.SMOKE`** — a lightweight early-warning check. PUnit
  warns about an undersized sample but proceeds. The verdict is
  labelled SMOKE, making clear it is not a full verification.

```java
PUnit.testing(sampling, factors)
        .intent(TestIntent.SMOKE)
        .assertPasses();
```

### Combining criteria

A contract can declare multiple criteria by composing them with
`Criteria.of(...)`. Each is independently evaluated; the test passes
only if every required criterion passes. The functional criteria
live on the contract's `criteria()`; an optional `latency()` sibling
expresses the temporal commitment exactly once:

```java
@Override
public Criteria<Translation> criteria() {
    return Criteria.of(
            empirical().<Translation>passRate()
                    .satisfies("Parses", Translation::isWellFormed)
                    .name("well-formed"),
            empirical().<Translation>passRate()
                    .satisfies("All actions valid", Translation::actionsValid)
                    .name("actions-valid"));
}

@Override
public LatencyCriterion latency() {
    return meeting().atMost(P95, ofMillis(500))
            .atMost(P99, ofMillis(1000))
            .contractRef(SLA, "Acme API SLA v3 §2.4");
}
```

A test site that wants extra diagnostic comparisons — recorded
elsewhere but not gated — can attach them with `.reportOnly(...)`:

```java
PUnit.testing(sampling, factors)
        .reportOnly(empirical().<Translation>passRate())
        .assertPasses();
```

The functional and latency criteria on the contract gate the verdict;
the `reportOnly` clause compares against the empirical baseline for
diagnostic purposes without affecting pass/fail.

### Instance conformance — comparing produced values against expected outputs

Postconditions on the service contract (Part 1) describe what *any*
acceptable output looks like — a basket translation is a parseable JSON
object, every action is valid for its context, every quantity is a
positive integer. Some tests need a sharper question: for *this*
input, did the service produce *that* specific output? That is
**instance conformance** — per-sample comparison against an expected
value indexed alongside the inputs.

Instance conformance is opt-in. When configured, the engine pairs each
input with the expected value at the same index and runs a
`ValueMatcher<O>` against the service's produced value. The match
outcome contributes to the sample's pass/fail in exactly the same way
a failed postcondition does — pass-rate criteria see one pass-or-fail
verdict per sample, regardless of whether the failure came from a
postcondition or a mismatched expectation.

The default matcher is `ValueMatcher.equality()` — strict `equals`.
Authors supply a custom `ValueMatcher` when equality is too brittle:
case-insensitive string equality, structural JSON equivalence,
numeric tolerance, normalised transcript comparison, and so on.

**Where to configure it.** Two call shapes are supported, and they
mean different things.

**Shape A — on the `Sampling` fixture.** The matcher is a property
of the fixture itself: every consumer of this `Sampling` (one test,
multiple tests against different factors, the paired measure run, a
future explore or optimize run) sees the same comparison strategy:

```java
Sampling<Provider, AudioSample, TranscriptionResult> sampling = Sampling
        .<Provider, AudioSample, TranscriptionResult>builder()
        .serviceContractFactory(SpeechToTextServiceContract::new)
        .inputs(audioSamples)
        .samples(audioSamples.size())
        .matching(expectedTranscripts, normalisedTranscript)  // ← lives on the fixture
        .build();

PUnit.testing(sampling, Provider.DEEPGRAM)
        .assertPasses();
```

(The pass-rate criterion is declared on `SpeechToTextServiceContract`
itself — typically `meeting().passRate(0.5).contractRef(SLO, "...")`.)

This is the **recommended default for production suites**. It
eliminates test/measure drift by construction — the matcher lives in
exactly one place, so an empirical baseline collected with the same
`Sampling` is checked against the same comparison strategy the test
uses.

**Shape B — on the spec builder.** The matcher is a property of the
spec, not the fixture. Two situations call for this:

```java
PUnit.testing(sampling, Provider.DEEPGRAM)
        .expectedOutputs(expectedTranscripts)        // ← lives on the spec
        .matcher(normalisedTranscript)
        .assertPasses();
```

Use shape B when:

- **Inline sampling form.** There is no separate `Sampling` value to
  carry the matcher because the `Sampling` is being assembled on the
  spec builder itself:
  ```java
  PUnit.testing(factors)
          .serviceContractFactory(MyServiceContract::new)
          .inputs(inputs)
          .samples(50)
          .expectedOutputs(expected)
          .matcher(normalisedTranscript)
          .assertPasses();
  ```
- **Per-spec override.** The same fixture is consumed by multiple
  specs with different comparison strategies — strict in one test,
  lenient in another. Spec-builder values always override values
  carried on `Sampling`.

When both shape A and shape B are used on the same call, **shape B
wins**: the spec builder's `expectedOutputs(...)` and `matcher(...)`
override what the fixture carries.

### The verdict

`assertPasses()` translates the engine's verdict into a JUnit outcome:

- **PASS** — returns normally.
- **FAIL** — throws `AssertionFailedError`. The service contract degraded or
  the SLA was breached.
- **INCONCLUSIVE** — throws `TestAbortedException` (skipped) when
  the configuration cannot be evaluated (no baseline yet, baseline
  has been rejected as misaligned). FAIL is the right outcome only
  when the data shows degradation; INCONCLUSIVE is used when the
  framework cannot draw any conclusion at all.

A typical FAIL message:

```
FAIL
  [REQUIRED] pass-rate → FAIL: observed=0.7800 (Wilson-95% lower=0.6640)
                              vs threshold=0.9500 (origin=SLA) over 50 samples

  Postcondition failures:
    - "All actions valid" → 7 failures
        e.g. "Add 2 apples and remove eggs" → unknown action 'remove'
        e.g. "Clear basket" → context mismatch

  Contract: Acme API SLA v3 §2.1
```

The verdict carries everything a developer needs to triage: the
criterion that failed, the observed rate, the Wilson bound, the
threshold and its provenance, the most-frequent postcondition
failures with two example inputs each, and the contract reference.

### Early termination

When the verdict is mathematically determined mid-run, PUnit stops
sampling. This is **default-on** for any `ProbabilisticTest` whose
contract declares a contractual pass-rate criterion
(`meeting().passRate(...)`) — the threshold is known up front, so
after each sample the framework can check whether the verdict is
already decided:

- **Failure inevitable** — even if every remaining sample passes,
  the observed rate cannot reach the threshold. The run stops and
  the verdict is FAIL.
- **Success guaranteed** — enough samples have already passed that
  the threshold holds regardless of remaining outcomes, *and* the
  run has cleared a statistical-validity floor (the minimum sample
  count for the normal approximation to be meaningful at the
  threshold). The run stops and the verdict is PASS.

The verdict matches what would have come back from running every
declared sample — early termination is lossless. The
`terminationReason` on the verdict surfaces `IMPOSSIBILITY` or
`SUCCESS_GUARANTEED` so reports can distinguish a short-circuited
run from one that completed naturally.

Empirical-mode tests (`empirical().passRate()`) resolve their
threshold from a baseline at evaluate time, so the up-front check
has nothing to compare against; those runs continue through every
declared sample. Measure / explore / optimize specs likewise run to
completion — they have no threshold to short-circuit on.

To opt a contractual test out of early termination (e.g. when the
full sample count is wanted for a follow-on baseline emission, a
complete latency distribution, or exhaustive failure exemplars):

```java
PUnit.testing(sampling, factors)
        .disableEarlyTermination()
        .assertPasses();
```

The verdict is unchanged — it depends only on the final pass
count. The `terminationReason` becomes `COMPLETED`.

This builder method is unrelated to the optimize loop's
`OptimizeBuilder.disableEarlyTermination()`, which controls a
different mechanism — a heuristic no-improvement window on the
optimize side, not the verdict-driven short-circuit here.

### Transparent statistics

For audit or compliance contexts, enable verbose statistical output:

```java
PUnit.testing(sampling, factors)
        .transparentStats()
        .assertPasses();
```

Or via system property (`-Dpunit.stats.transparent=true`) or env var
(`PUNIT_STATS_TRANSPARENT=true`). Output appears on stderr alongside
the JUnit message:

```
═ STATISTICAL ANALYSIS FOR: shopping-basket ═════════════════════ PUnit ═

  HYPOTHESIS TEST
    H₀ (null):             True success rate π ≤ 0.8500
    H₁ (alternative):      True success rate π > 0.8500
    Test type:             One-sided binomial proportion test

  OBSERVED DATA
    Sample size (n):       100
    Successes (k):         87
    Observed rate (p̂):     0.8700

  STATISTICAL INFERENCE
    Confidence interval:   Wilson 95% [0.788, 0.929]
    Lower bound:           0.788 ≥ 0.8500? No

  VERDICT
    Result:                PASS (lower bound clears threshold)

═════════════════════════════════════════════════════════════════════════
```

Use this when the statistical reasoning behind a passing verdict has
to be *shown*, not just inferred from the absence of a failure.

---

## Part 4: Measuring

A MEASURE experiment runs a service contract at high statistical power
(typically 1000+ samples) and writes a **baseline file** — the empirical
record of how the service contract behaved under a specified configuration.

```java
@Experiment
void shoppingBaseline() {
    PUnit.measuring(ShoppingBasketServiceContract.sampling(INSTRUCTIONS, 1000),
                    LlmTuning.DEFAULT)
            .run();
}
```

Like `@ProbabilisticTest`, `@Experiment` is a parameter-free JUnit hook
— configuration lives on the fluent builder.

Run experiments via the `experiment` (or `exp`) Gradle task:

```bash
./gradlew exp -Prun=ShoppingBasketBaseline
./gradlew exp -Prun=ShoppingBasketBaseline.shoppingBaseline
```

The `exp` task is configured by the punit Gradle plugin to pick up
only `@Experiment`-tagged methods. Regular `./gradlew test` skips
them.

### What gets written

A successful MEASURE writes a single YAML file to the configured
**baseline directory**. The file records:

- The service contract id and the experiment method name (identity).
- A fingerprint of the factors record (so the test side can match).
- The fingerprint of the inputs population (so a test cannot pair
  with a baseline that observed different inputs).
- The covariate profile resolved at measurement time.
- The recorded statistics — observed pass rate, sample count, and
  the full sorted vector of successful-sample latencies.
- A capture timestamp.

By default the file lives under `src/test/resources/punit/baselines/`,
a filesystem path relative to the process's working directory (not a
classpath resource lookup). Override via the `-Dpunit.baseline.dir`
system property or the `PUNIT_BASELINE_DIR` environment variable
(checked in that order — see Appendix A), or by setting the property
in the project's `gradle.properties` for a Gradle-driven run.

### Normative judgement at experiment time

When the measured contract declares normative criteria (`meeting().passRate(...)`), the measure experiment judges each one against its stipulated threshold using the run's own samples: the criterion is **met** when the Wilson one-sided lower confidence bound of its observed rate — at the run's sample count, at the criterion's confidence — clears the stipulation, **failed** when it does not, and **unsupportable** when the run's sample count cannot support the stipulated threshold at that confidence even with a perfect observation (the output states the feasible minimum sample count). Empirical criteria are never judged at experiment time — their bar does not exist until a baseline supplies it.

The judgement is rendered in the experiment's console output alongside the measured characterisation, and recorded per criterion in the baseline file as an additive `normativeJudgement` marker (state, stipulated threshold, confidence) inside the criterion's statistics row. Baseline resolution and threshold derivation ignore the marker entirely — it is a durable record for later readers of the file. The judgement states a relation to the stipulation, nothing more: a failed judgement at measure time can be entirely expected — an aspirational bar measured mid-development, a fresh configuration characterised before tuning.

`run()` never fails on a failed judgement. To make the stipulations binding, finish the builder with `assertMeets()` instead of `run()` — the two are mutually exclusive terminals:

```java
@Experiment
void measurePaymentGatewayGated() {
    PUnit.measuring(PaymentGatewayServiceContract.sampling(1000)).assertMeets();
}
```

`assertMeets()` performs the same run and the same baseline persistence — the artefact is on disk before any throw — then translates the judgements with the same opentest4j mapping `assertPasses()` uses: a failed judgement throws `AssertionFailedError`, an unsupportable one throws `UnsupportableJudgementException` — a `TestAbortedException` subtype, so the harness aborts rather than fails — stating the feasible minimum. Calling it on a contract with no normative criteria is a configuration defect (`IllegalStateException`), detected before any sampling.

### Asymmetric sampling

The standard pattern measures with high statistical power and tests
with lower:

```java
private static final int BASELINE_SAMPLES     = 1000;
private static final int VERIFICATION_SAMPLES = 50;

@Experiment
void baseline() {
    PUnit.measuring(ShoppingBasketServiceContract.sampling(INSTRUCTIONS, BASELINE_SAMPLES),
                    LlmTuning.DEFAULT)
            .run();
}

@ProbabilisticTest
void shouldNotRegress() {
    PUnit.testing(ShoppingBasketServiceContract.sampling(INSTRUCTIONS, VERIFICATION_SAMPLES),
                  LlmTuning.DEFAULT)
            .assertPasses();
}
```

(The empirical pass-rate criterion lives on
`ShoppingBasketServiceContract.criteria()` as
`empirical().<BasketTranslation>passRate().satisfies(...)`. The
verification test simply runs the contract and asserts.)

The asymmetry is intentional and pedagogic. The baseline is captured
once — paying the cost of high precision so the recorded rate is a
tight estimate of the true rate. The verification test then runs
cheaply and frequently against that baseline. Equal sample counts on
both sides would flatten this distinction and burn budget that
calibration deserves more than routine verification does.

### Baseline expiration

Baselines reflect the system at the moment they were captured. As the
system evolves the baseline drifts out of sync with reality. PUnit
stamps every baseline with an expiration date (default: 90 days) and
warns when the test side resolves an expired baseline:

```java
PUnit.measuring(sampling, factors)
        .expiresInDays(30)   // override default
        .run();
```

Expired baselines do not fail tests — that would be too noisy. They
emit a stderr warning so the team sees that the baseline needs
refreshing.

### Empirical-supplier form

When the same builder produces both the baseline-running experiment
and the test that consumes it, the supplier form removes the
duplication:

```java
public class ShoppingBasketRoundTrip {

    private Experiment baseline() {
        return PUnit.measuring(ShoppingBasketServiceContract.sampling(INSTRUCTIONS, 1000),
                               LlmTuning.DEFAULT)
                .build();
    }

    @Experiment
    void runBaseline() {
        PUnit.measuring(ShoppingBasketServiceContract.sampling(INSTRUCTIONS, 1000),
                        LlmTuning.DEFAULT)
                .run();
    }

    @ProbabilisticTest
    void shouldNotRegress() {
        PUnit.testing(this::baseline)
                .samples(50)
                .assertPasses();
    }
}
```

The test side specifies only the (typically smaller) sample count;
the criteria, identity, factors, and inputs all follow from the
baseline's contract.

---

## Part 5: Exploring

An EXPLORE experiment runs a service contract across a grid of factor values,
reports per-configuration statistics, and writes one row per grid
point.

```java
@Experiment
void compareModels() {
    PUnit.exploring(ShoppingBasketServiceContract.sampling(INSTRUCTIONS, 200))
            .grid(
                    new LlmTuning("gpt-4o-mini",       0.3, DEFAULT_PROMPT),
                    new LlmTuning("gpt-4o",            0.3, DEFAULT_PROMPT),
                    new LlmTuning("claude-3-5-sonnet", 0.3, DEFAULT_PROMPT))
            .run();
}
```

Output is an exploration grid file: one row per configuration with
observed pass rate, latency percentiles, postcondition failure
histogram, and exemplars. The diff format makes per-postcondition
comparison across configurations the easy thing:

```
configuration               pass-rate   p50    p95    p99    "Has actions"   "All actions valid"
gpt-4o-mini @ 0.3            0.91       180ms  680ms  1240ms     0/200            18/200
gpt-4o @ 0.3                 0.96        220ms  720ms  1190ms     0/200             8/200
claude-3-5-sonnet @ 0.3      0.94       290ms  840ms  1320ms     0/200            12/200
```

Use EXPLORE when you need to *choose* a configuration. The output
gives the trade-off — pass rate, latency, dominant failure modes —
side-by-side. Multi-factor grids are a Cartesian product:

```java
.grid(
    new LlmTuning("gpt-4o-mini", 0.0, DEFAULT_PROMPT),
    new LlmTuning("gpt-4o-mini", 0.3, DEFAULT_PROMPT),
    new LlmTuning("gpt-4o-mini", 0.7, DEFAULT_PROMPT),
    new LlmTuning("gpt-4o",      0.0, DEFAULT_PROMPT),
    new LlmTuning("gpt-4o",      0.3, DEFAULT_PROMPT),
    new LlmTuning("gpt-4o",      0.7, DEFAULT_PROMPT))
```

Build the configurations programmatically when the grid is large; the
builder accepts `List<F>` as well as varargs.

---

## Part 6: Optimizing

An OPTIMIZE experiment iteratively explores a continuous factor space
to maximise (or minimise) a scorer over the service contract's results.

```java
@Experiment
void optimizeTemperature() {
    PUnit.optimizing(ShoppingBasketServiceContract.sampling(INSTRUCTIONS, 100))
            .initialFactors(new LlmTuning("gpt-4o", 0.0, DEFAULT_PROMPT))
            .stepper((current, history) ->
                    current.temperature() >= 1.0
                            ? null
                            : current.temperature(current.temperature() + 0.1))
            .maximize(summary ->
                    summary.passRate() - 0.05 * summary.p95LatencyMs() / 1000.0)
            .maxIterations(15)
            .noImprovementWindow(3)
            .run();
}
```

Three things distinguish OPTIMIZE from EXPLORE:

1. **Stepper** — a function that produces the *next* factor from the
   current one and the iteration history. Returning `null` terminates
   the search early.
2. **Scorer** — a function that turns a per-iteration `SampleSummary`
   into a number. The framework compares scores across iterations and
   tracks the best so far.
3. **Termination** — `maxIterations` caps the run; `noImprovementWindow`
   stops early if the scorer has not improved for N iterations.

OPTIMIZE writes an optimization history file — one row per iteration
with factor values, the scorer output, the per-postcondition failure
histogram, and exemplars. The dominant-failure histogram is what
makes the meta-prompt pattern work: an LLM-driven prompt-tuning loop
can read the previous iteration's most-common failure and propose a
prompt tweak addressing it.

Use OPTIMIZE when:

- You have a continuous (or large discrete) factor space.
- You have a clear scorer that captures what you care about.
- Trying every point on a grid is wasteful or impossible.

Use EXPLORE otherwise.

---

## Part 7: Latency

Pass rate is one half of a stochastic service's contract. Latency is
the other. PUnit treats them as independent quality dimensions and
combines them with logical AND: a test passes only if *both* claims
are satisfied.

### The problem with averages

Service latency distributions are typically:

- **Right-skewed** — a long tail caused by cache misses, GC pauses,
  retries, cold starts.
- **Multimodal** — distinct modes for fast (cached) and slow (database
  / remote API) paths.
- **Heavy-tailed** — outliers orders of magnitude above the median.

A 200ms-mean service with 50ms standard deviation could have a p99 of
350ms (near-normal) or 2000ms (heavy-tailed). Summary statistics
cannot distinguish the two. PUnit deliberately avoids parametric
fits to latency; instead, it works with the **empirical
distribution** directly — the sorted vector of observed latencies.

### What PUnit measures

For every successful sample (functional `Outcome.ok`), PUnit records
the wall-clock duration. Failed samples produce execution times that
are not comparable with successful ones (a fast validation rejection
and a slow timeout both reflect *error paths*, not the latency of
successful operation), so latency is conditioned on `X = 1` —
successful samples only. This is the **tripartite-contract
decomposition** the [statistical companion §12.2.1](https://r.mavai.org/statistical-companion.pdf)
formalises: correctness, availability, and latency-given-success are
three orthogonal sub-contracts evaluated independently.

PUnit reports four percentiles as standard: p50, p90, p95, p99. The
estimator is **nearest-rank** — for a percentile `p` and a sorted
sample of size `n_s`, the estimate is the `⌈p · n_s⌉`-th order
statistic. Integer-millisecond by construction; no interpolation.

Minimum sample sizes for non-degenerate percentile estimates:

| Percentile | Minimum successful samples |
|------------|----------------------------|
| p50        | 5                          |
| p90        | 10                         |
| p95        | 20                         |
| p99        | 100                        |

Below these, the percentile collapses to the maximum and the
framework will not report a number — it raises a feasibility error
under VERIFICATION intent and marks the result as **indicative**
under SMOKE intent.

### Asserting latency: contractual thresholds

For SLA-style targets, declare the contract's temporal commitment on
the `latency()` sibling, chaining one `.atMost(percentile, duration)`
per percentile asserted:

```java
@Override
public LatencyCriterion latency() {
    return meeting().atMost(P95, ofMillis(500))
            .atMost(P99, ofMillis(1000))
            .contractRef(SLA, "Acme SLA v3 §2.4");
}
```

The test site simply runs the contract:

```java
PUnit.testing(sampling, factors).assertPasses();
```

A constraint passes when `Q(p_j) ≤ τ_j` for every declared percentile;
the overall latency assertion passes when every constraint passes.
Declare only the percentiles you care about — others are not asserted.
Explicitly supplied durations must be monotonically non-decreasing
across `P50 → P90 → P95 → P99`; the framework rejects misconfigured
contracts up front.

### Asserting latency: baseline-derived thresholds

When the threshold should track the service contract's measured behaviour,
PUnit derives it from a recorded baseline using the **binomial
order-statistic upper confidence bound** on the baseline quantile:

```
τ_j = t_(k_j)   where   k_j = qbinom(1 − α, n_s, p_j) + 1
```

clamped to `[⌈p_j · n_s⌉, n_s]`. `t_(k)` is the `k`-th order statistic
of the baseline's sorted successful-sample latencies; the threshold
is therefore an *observed baseline latency*, in integer milliseconds,
by construction.

Three properties matter:

- **Exact and distribution-free** for i.i.d. samples from any
  continuous latency distribution. The rank of the population
  quantile follows `Bin(n_s, p_j)` regardless of the underlying
  density; no normal approximation, no density estimate, no
  second moment.
- **Symmetric with the pass-rate side** — Wilson-score lower bound
  for pass rate; binomial order-statistic upper bound for latency.
  Both are non-parametric finite-sample constructions.
- **Integer-millisecond** — the threshold is an observed value,
  aligning with how SLA targets are written and compared.

[Statistical companion §12.4](https://r.mavai.org/statistical-companion.pdf)
develops the construction with proofs; the implementation lives in
`org.mavai.punit.statistics.LatencyThresholdDeriver`.

### Advisory vs enforced

Latency profiles are environment-dependent. A baseline recorded on CI
hardware may legitimately differ from a developer-laptop run, even
when the system has not regressed. PUnit therefore offers two
enforcement modes:

| Mode      | Breach behaviour              | Default | When to use                                              |
|-----------|-------------------------------|---------|----------------------------------------------------------|
| Advisory  | Warning in output; test passes| Yes     | Mixed-hardware environments; latency is informational.   |
| Enforced  | Test fails                    | No      | Controlled environments (dedicated CI, staging); SLA gating. |

Advisory is the default because failing tests on environmental
differences erodes trust in the framework. Switch to enforced when
hardware consistency is controlled and latency is a first-class SLA
dimension.

---

## Part 8: Resource controls

PUnit gives the service contract author and the test author direct levers
over time, tokens, pacing, and exception handling.

### Budgets

Time and token budgets cap the resource cost of a single sampling
run. Specify them on the `Sampling`:

```java
Sampling.<F, I, O>builder()
        .serviceContractFactory(factory)
        .inputs(INPUTS)
        .samples(1000)
        .timeBudget(Duration.ofMinutes(2))
        .tokenBudget(50_000)
        .tokenCharge(100)            // static per-sample charge
        .onBudgetExhausted(BudgetExhaustionPolicy.PASS_INCOMPLETE)
        .build();
```

`tokenCharge` is a static per-sample projection used for pre-sample
budget enforcement; the service contract's own `tracker.recordTokens(...)`
calls add to the post-sample running total. `BudgetExhaustionPolicy`
selects the response when a budget runs out:

| Policy             | Behaviour                                              |
|--------------------|--------------------------------------------------------|
| `FAIL` (default)   | Mark the run as terminated early; verdict is FAIL.     |
| `PASS_INCOMPLETE`  | Mark the run as terminated early; pass on what samples did run. |

### Pacing

Some services impose rate or concurrency limits. Declare them on the
service contract (not on the test — every test of the same service should
respect the same limits):

```java
@Override
public Pacing pacing() {
    return Pacing.builder()
            .maxRequestsPerSecond(10.0)
            .minMillisPerSample(50L)
            .maxConcurrent(3)
            .build();
}
```

Pacing composes most-restrictive-wins: if `maxRequestsPerSecond` of
10 implies a 100ms gap and `minMillisPerSample` is 250, the engine
waits 250ms.

### Exception handling

A thrown exception from `invoke` is treated as a defect by default
— the run aborts. To run a noisy service contract where some samples
genuinely throw and the test wants to count those as failures:

```java
Sampling.<F, I, O>builder()
        .onException(ExceptionPolicy.FAIL_SAMPLE)
        ...
```

| Policy                  | Behaviour                                                  |
|-------------------------|------------------------------------------------------------|
| `ABORT_TEST` (default)  | Rethrow; the engine aborts. Defect-stays-a-defect.         |
| `FAIL_SAMPLE`           | Catch, count as a failed sample, continue.                 |

Use `ABORT_TEST` for any service contract where a thrown exception genuinely
reflects a bug; use `FAIL_SAMPLE` for a service contract whose exceptions
are part of its expected (probabilistic) behaviour.

### Warmup

Some services have cold-start behaviour — the first few invocations
are unrepresentative. Discard them:

```java
@Override
public int warmup() { return 3; }
```

Warmup samples are invoked but not counted; their latencies are not
recorded; their results do not contribute to pass rate. They do
consume budget.

### Per-sample latency bound

Service contracts can declare a hard per-sample latency bound:

```java
@Override
public Optional<Duration> maxLatency() {
    return Optional.of(Duration.ofSeconds(5));
}
```

The engine records a duration violation for any sample that exceeds
the bound — the sample's acceptance criteria still evaluate, the
violation is an additional facet, not a short-circuit. Most use
cases do *not* set this. Aggregate latency claims (the 95th
percentile under N ms) belong on the contract's `latency()` sibling
via `meeting().atMost(P95, ofMillis(500))`. The two statements have
two distinct homes.

---

## Part 9: Covariates

A covariate is an environmental factor that the developer does not
control but that influences the service contract's behaviour: the time of
day, the deployment region, the model version, the day of week.
Declaring covariates makes their effect *visible* — and it makes
baseline matching honest.

### Declaring covariates

Built-in covariates: `time-of-day`, `day-of-week`, `region`,
`timezone`. Use them as-is:

```java
@Override
public List<Covariate> covariates() {
    return List.of(Covariate.dayOfWeek());
}
```

Declare custom covariates with a category from
`CovariateCategory` — `CONFIGURATION`, `OPERATIONAL`, `TEMPORAL`,
`EXTERNAL_DEPENDENCY`, `INFRASTRUCTURE`, or `DATA_STATE` — and
supply a resolver:

```java
@Override
public List<Covariate> covariates() {
    return List.of(
            Covariate.custom("llm_model",   CovariateCategory.CONFIGURATION),
            Covariate.custom("temperature", CovariateCategory.CONFIGURATION),
            Covariate.custom("region",      CovariateCategory.OPERATIONAL));
}

@Override
public Map<String, Supplier<String>> customCovariateResolvers() {
    return Map.of(
            "llm_model",   () -> tuning.model(),
            "temperature", () -> Double.toString(tuning.temperature()),
            "region",      () -> System.getenv("AWS_REGION"));
}
```

Resolvers are called once per run, before any samples execute. They
must be deterministic for a single run.

### Categories

The category tells the framework how strictly to match across baseline
and test. One category is **hard-gated**; the rest are **soft-matched**
(mismatch surfaces as a warning on the verdict but does not block the
comparison).

- **`CONFIGURATION`** *(hard-gated)* — a knob the developer set
  (model, prompt, temperature). PUnit hard-gates here: a baseline
  measured under `model=gpt-4o` cannot match a test running under
  `model=gpt-4-turbo`. The verdict comes back INCONCLUSIVE with a
  misalignment note. The hard-gate protects against a class of silent
  bugs where a baseline measured under one configuration is used as
  the reference for a test under a different one.
- **`OPERATIONAL`** *(soft-matched)* — an environmental factor the
  developer does not directly control at the call site (region,
  timezone, deployment slot).
- **`TEMPORAL`** *(soft-matched)* — cyclical / time-bound factors
  affecting behaviour (day of week, time of day).
- **`EXTERNAL_DEPENDENCY`** *(soft-matched)* — third-party services
  whose own behaviour may drift between measure and test (upstream
  API version, vendor model revision).
- **`INFRASTRUCTURE`** *(soft-matched)* — execution-environment
  characteristics (cloud provider, instance type).
- **`DATA_STATE`** *(soft-matched)* — observable state of inputs to
  the system under test (cache state, index version, training-data
  snapshot, catalog size).

The built-in covariate factories (`Covariate.dayOfWeek()`,
`Covariate.region()`, `Covariate.timeOfDay()`,
`Covariate.timezone()`) carry their categories internally. Pick the
category that matches the *cause* of the variation, not the field
name: a model-version covariate is `CONFIGURATION` if the test author
sets it, `EXTERNAL_DEPENDENCY` if a vendor controls when it changes.

### Baseline matching

When the contract declares an `empirical().passRate()` criterion, the baseline resolver:

1. Looks up baselines for the service contract id.
2. Filters by exact factor-record match.
3. Filters by exact CONFIGURATION covariate match.
4. Picks the candidate whose OPERATIONAL covariates best align with
   the run's, surfacing any mismatch as a warning.
5. Returns INCONCLUSIVE if step 2 or 3 leaves no candidates, with a
   note indicating *why* (which CONFIGURATION axis didn't match).

The verdict text always shows both the observed and the matched
baseline's covariates, so a developer reading a FAIL or INCONCLUSIVE
can immediately see the conditions under which the test ran.

---

## Part 10: Sentinels — production-time execution

Parts 1–9 cover PUnit as a development-time framework integrated with
JUnit 5. The **Sentinel** is PUnit's runtime for *deployed*
environments — a lightweight test runner with no JUnit dependency,
designed for production-time reliability checks.

If running "tests" in production feels uncomfortable, examine why. The
discomfort comes from a deterministic-software intuition: once a test
passes in CI, the feature is safe. For stochastic features that
intuition does not hold. An LLM that passed at 95% in CI may run at
80% under production load, against production-shaped inputs, on a
different LLM provider's backend. The Sentinel is for catching that
divergence before users do.

### The shape of a sentinel

A sentinel is just a class with one or more `@ProbabilisticTest` or
`@Experiment` methods. **No class-level marker is required.** The
PUnit Gradle plugin discovers sentinels by scanning compiled classes
for the same method-level annotations JUnit uses:

```java
public class PaymentGatewaySentinel {

    private static final List<Charge> CHARGES = List.of(
            new Charge("tok_visa",       1500),
            new Charge("tok_mastercard", 4200),
            new Charge("tok_amex",       12_000));

    @ProbabilisticTest
    void paymentMeetsContractualSla() {
        PUnit.testing(PaymentGatewayServiceContract.sampling(CHARGES, 50),
                      Tier.DEFAULT)
                .assertPasses();
    }
}
```

(`PaymentGatewayServiceContract.criteria()` declares the SLA
pass-rate criterion as
`meeting().passRate(0.99).contractRef(SLA, "Acme Payment SLA v3.2 §4.1").satisfies(...)`;
the sentinel's test body simply runs and asserts.)

The same class is picked up by JUnit during normal `./gradlew test`
runs (because `@ProbabilisticTest` is meta-annotated `@Test`). One
class, two consumers — JUnit at development time, the Sentinel
binary at runtime.

### Building the Sentinel binary

The Gradle plugin provides the `createSentinel` task:

```bash
./gradlew createSentinel
# produces build/libs/<project>-sentinel.jar
```

The task scans the compiled test classpath for any class declaring at
least one `@ProbabilisticTest` or `@Experiment` method, packages all
of those plus their transitive dependencies plus the punit-core
runtime into a self-contained executable JAR, and writes the FQNs
into `META-INF/punit/sentinel-classes`.

The plugin requires at least one sentinel class to exist or it will
fail with a diagnostic. There is no way to declare "this class is *not*
a sentinel" because the question is moot — a class without
`@ProbabilisticTest` or `@Experiment` methods isn't a sentinel.

### Running the Sentinel

```bash
java -jar build/libs/myapp-sentinel.jar test
```

Subcommands:

- `test` — run every `@ProbabilisticTest` method.
- `experiment` — run every `@Experiment` method.
- `--filter '<pattern>'` — restrict by method name pattern.

The Sentinel binary returns:

- Exit 0 on PASS.
- Exit 1 on FAIL (any test verdict was FAIL).
- Exit 2 on INCONCLUSIVE (any test was INCONCLUSIVE).
- Exit 3 on engine-level error.

Use these in container health checks, scheduled jobs, or CI pipeline
steps. Verdict XML and (optionally) HTML reports are emitted to a
configured directory in the same shape as the development-time runs;
[Part 11](#part-11-reports) covers the report format.

### Baseline file location

The Sentinel's `experiment` subcommand can run `@Experiment` methods
generally, including MEASURE experiments — a deployed sentinel can
establish its own baseline in its own environment, exactly as a
development-time MEASURE run does. The `test` subcommand's
`@ProbabilisticTest` methods resolve baselines the same way.

Both directions go through the identical resolution mechanism covered
in [Part 4](#part-4-measuring) and Appendix A:
the `-Dpunit.baseline.dir` system property, then the `PUNIT_BASELINE_DIR`
environment variable, then the fixed convention path
`src/test/resources/punit/baselines`. There is no Sentinel-specific
configuration surface beyond these two — `SentinelConfiguration` and the
Sentinel CLI do not expose a baseline-directory-specific flag or builder
method of their own.

The convention-path fallback assumes a Gradle test-source-tree layout
that generally does not exist inside a deployed sentinel JAR or
container, so a real deployment should set one of the two overrides
explicitly rather than relying on it. Because `-D` flags require control
of the JVM launch command, the environment variable is often the more
natural fit for container-orchestrated deployments (Kubernetes, most
container schedulers) where environment variables are the standard
configuration channel.

A sentinel that both measures its own baseline (`exp`) and tests against
it (`test`) must point both invocations at the same directory — nothing
hands the freshly-written baseline from one run to the other
automatically. A sentinel that only tests (measuring elsewhere, e.g. in
CI, and shipping the resulting baseline file(s) as part of the deployment
artifact) must ensure the shipped baseline directory is what
`punit.baseline.dir` / `PUNIT_BASELINE_DIR` resolves to at runtime.

See also `docs/SENTINEL-DEPLOYMENT-GUIDE.md`'s "Establish Baselines"
and "Verify Against Baselines" sections for the full deployment
walkthrough.

### Production-only configuration

Some sentinel runs need values not appropriate for development —
production credentials, real LLM provider keys, the real payment
gateway. Pass them at deploy time via system properties or environment
variables; the service contract constructor reads from the environment as
usual. The framework imposes no opinion on how secrets reach the use
case.

### The triage signal

A sentinel verdict is a *triage signal*, not a failure mode. A FAIL in
production rarely means "page someone immediately"; more often it
means "the empirical pass rate has slipped below the configured
threshold and merits investigation." The verdict's contract reference,
postcondition histogram, and covariate alignment are what give a
human enough context to decide whether to act.

---

## Part 11: Reports

PUnit emits structured reports from every run, suitable for both
human reading and machine consumption.

### HTML report

For local development and CI artifacts, PUnit can generate an HTML
report aggregating every verdict from the most recent run:

```bash
./gradlew test punitReport
```

Output: `build/reports/punit/html/index.html`. The report shows, per test:

- The verdict (PASS / FAIL / INCONCLUSIVE) and the contract reference.
- The criteria evaluated and their individual results.
- The per-postcondition failure histogram with exemplars.
- The latency profile (percentiles plus the full distribution as a
  histogram).
- The covariate alignment between the run and the matched baseline.
- The transparent-statistics breakdown if it was enabled.

### Experiment comparison reports

Beyond the per-test report above, PUnit renders browser-friendly
**comparison reports** for EXPLORE and OPTIMIZE experiments — a single
self-contained HTML page that ranks the candidates so you can see at a
glance which performed best, without reading the raw YAML. Both share the
test report's styling and, like it, embed all CSS and make no external
requests, so they open directly from disk with no server.

Run a comparison report after the experiment that produces its data
(`./gradlew exp -Prun=YourExperiment`). If no experiments have been run,
the report states that none were found rather than failing the build.

**Exploration comparison.** Compares the variants of an EXPLORE
experiment (one factor combination — model, temperature, system prompt, …
— per variant), overall and per criterion:

```bash
./gradlew explorationReport
```

Reads the exploration YAML under `build/punit/explorations/<service>/`
(the `explorationsDir`) and writes
`build/reports/punit-explorations/html/index.html`. Per service it shows a
ranked leaderboard (overall pass rate, then latency, then cost), a
per-criterion matrix comparing the variants check-by-check, and a
latency-distribution strip per variant. Variants whose ranking rests on a
margin within 5% are flagged **too close to call** (a presentational
marker, not a significance test).

**Optimization comparison.** Summarises one OPTIMIZE experiment's
iterations, ranked by the scorer:

```bash
./gradlew optimizationReport
```

Reads the optimization YAML under `build/punit/optimizations/<service>/`
(the `optimizationsDir`) and writes
`build/reports/punit-optimizations/html/index.html`. Per run it names the
service and the optimisation objective (`MAXIMIZE` / `MINIMIZE`), then lists
the iterations in run order — each row expanding to reveal the factor bundle
that produced it — carrying the objective-aware score rank in a **Rank**
column rather than by row order, so the run reads as the sequence it
actually ran in. The chosen best iteration is highlighted. A per-criterion
matrix and a score trajectory across the run follow. Iterations within 5% of
each other on score share a rank and are flagged **too close to call**.

Both tasks reuse the `punit { }` extension's existing output-directory
properties (`explorationsDir`, `optimizationsDir`); no extra configuration
is required for the defaults.

### Verdict XML (RP07)

Every probabilistic test verdict serialises to an XML file conforming
to the **RP07 mavai verdict interchange standard**:

- Namespace: `http://mavai.org/verdict/1.0`
- Root: `<verdict-record>`
- Schema: `verdict-1.0.xsd` (bundled in `punit-report`).

The schema covers identity, verdict, criterion results, sample
counts, latency percentiles, baseline expiration, environment
metadata, contract reference, and correlation id. The verdict XML is
the one format that flows between punit, feotest, and mavai.org —
sentinels and dashboards consume it without caring which framework
produced it.

Configuration: set the output directory via system property
(`-Dpunit.report.dir=...`) or the `punit { }` Gradle extension.

---

## Part 12: Statistics — what is actually computed

This section is a brief tour of what PUnit computes and where it
lives. The comprehensive treatment is the
[Statistical Companion Document](https://r.mavai.org/statistical-companion.pdf);
the implementation lives in the ArchUnit-isolated
`org.mavai.punit.statistics` package, which has no dependencies on
any other punit package so the statistical core can be audited
against published formulae in isolation.

### Pass rate: Wilson score lower bound

A service contract's `n_test` invocations are modelled as Bernoulli trials
under a working approximation of independence and stationarity. The
total number of successes is binomial, and the sample proportion
`p̂ = k / n` is an unbiased estimator of the true success
probability `p`.

For a one-sided `(1-α)` confidence claim, PUnit applies the **Wilson
score lower bound**:

```
              p̂ + z²/(2n)  − z · √( p̂(1−p̂)/n  + z²/(4n²) )
p_lower  =  ────────────────────────────────────────────────
                          1 + z²/n
```

Wilson is used everywhere — small samples, extreme proportions, the
boundary case `p̂ = 1`. There is no method-switching: the same
formula handles every case correctly. Conformance against the
R-generated reference data (`mavai-R/inst/cases/wilson_*.json`) is
verified on every build.

For empirical thresholds: the test passes iff the run's Wilson lower
bound clears the recorded baseline rate. For contractual thresholds:
the test passes iff `p̂ ≥ threshold` directly — no margin, since the
threshold is given, not estimated.

### Latency: binomial order-statistic upper bound

For latency thresholds derived from a baseline, PUnit uses the
**exact binomial order-statistic upper confidence bound** on the
baseline quantile:

```
τ_j = t_(k_j)   where   k_j = qbinom(1 − α, n_s, p_j) + 1
```

clamped to `[⌈p_j · n_s⌉, n_s]`. `t_(k)` is the `k`-th order
statistic of the baseline's sorted successful-sample latencies; the
threshold is an observed baseline latency, in integer milliseconds,
by construction.

This is exact and distribution-free for i.i.d. samples from any
continuous latency distribution — the rank of the population
quantile follows `Bin(n_s, p_j)` regardless of the underlying density.
No density estimate, no normal approximation, no second moment.

It is the non-parametric counterpart of the Wilson lower bound used
on the pass-rate side, restoring the statistical symmetry between
the two halves of the contract.

### Power and sample sizing

For sample-size planning under the empirical (baseline-driven)
pathway, PUnit uses normal-asymptotic approximations (epistemic
status: planning approximation, not theorem). To detect a shift
from `p_0` to `p_1` at significance `α` and power `1-β`:

```
            ⎛ z_α · √(p₀(1-p₀))  +  z_β · √(p₁(1-p₁)) ⎞²
  n   =    ⎜ ─────────────────────────────────────── ⎟
            ⎝                  p₀ - p₁                 ⎠
```

This computation is **internal**. Authors do not call it directly;
no `power(...)` or `minDetectableEffect(...)` setter is exposed on
the authoring builders. The framework applies the formula on the
empirical pathway when sizing a derived baseline against a
configured confidence — the result feeds the feasibility check
below. Power calculations are *budgeting* aids — adequate for "is
this test worth running?" — not exact calibration claims. Companion
§5 develops the distinction.

### Feasibility gates

Before a probabilistic test runs, the framework checks two
invariants. The check fires for `PassRate` criteria (contractual
and empirical alike); `PercentileLatency` is skipped pending its
own feasibility model.

- **Soundness floor (cross-intent).** A configured confidence
  below the framework's floor (currently **80%**) aborts the run
  with `IllegalStateException`, **regardless of intent**. A test
  that cannot make a claim at the floor's confidence level cannot
  underwrite a verdict, and SMOKE intent does not buy past that.
- **Sample-size adequacy (intent-gated).** Given the resolved
  target rate (contractual threshold or baseline-derived rate)
  and the configured confidence, is the declared sample size
  large enough to underwrite a verification claim?
  - Under **VERIFICATION** intent (the default), the run aborts
    with `IllegalStateException` before any samples execute.
  - Under **SMOKE** intent the gate is silent — the developer
    has explicitly declared "I know this is undersized; treat it
    as a sentinel." No warning, no abort. (The soundness floor
    above is the one exception.)

The diagnostic on abort names the configuration's shortfall and
the smallest sample count that would underwrite the claim, so the
author can either bump samples or rethink the threshold.

### Further reading

Mathematical foundations:
[Statistical Companion Document](https://r.mavai.org/statistical-companion.pdf).

Cross-language conformance: every mavai framework (punit, feotest,
baseltest) reproduces the R-generated reference data within stated
tolerances. The conformance machinery is documented in the
`mavai-R` project README.

---

## Appendix A: Configuration

PUnit resolves configuration in this order (highest priority first):

1. Builder method on the fluent API (`.transparentStats()`, `.intent(...)`).
2. JVM system property (`-Dpunit.*`).
3. Environment variable (`PUNIT_*`).
4. Framework default.

| Setting                 | System property              | Env var                     | Default                                            |
|-------------------------|------------------------------|-----------------------------|----------------------------------------------------|
| Baseline directory      | `punit.baseline.dir`         | `PUNIT_BASELINE_DIR`        | `src/test/resources/punit/baselines/` (filesystem, relative to CWD) |
| Report directory        | `punit.report.dir`           | `PUNIT_REPORT_DIR`          | `build/reports/punit/xml/` (verdict XML; the HTML report is written beside it under `build/reports/punit/html/`) |
| Transparent stats       | `punit.stats.transparent`    | `PUNIT_STATS_TRANSPARENT`   | `false`                                            |
| Confidence level        | `punit.confidence`           | `PUNIT_CONFIDENCE`          | `0.95`                                             |
| Latency enforcement     | `punit.latency.enforcement`  | `PUNIT_LATENCY_ENFORCEMENT` | `advisory`                                         |
| Default samples         | `punit.samples`              | `PUNIT_SAMPLES`             | builder-supplied; no global default                |

Gradle plugin configuration in the `punit { }` extension block
mirrors the same settings. See the plugin module's README for the
extension surface.

### Per-sample progress counter

PUnit emits a `completed/total` counter to standard output after
each sample completes. For a 100-sample MEASURE the counter ticks
through `  1/100`, `  2/100`, `  3/100`, ..., `100/100` — width-
padded so the rendered length stays constant — giving live
feedback that the JVM is making progress rather than blocked.

The display rendering depends on whether you run via Gradle or via
another launcher:

**Under Gradle (`./gradlew test`, `./gradlew exp`)** — the
punit-gradle-plugin installs a test-output bridge that intercepts
the per-sample emissions, strips an internal protocol marker, and
relays the counter to the build's terminal as a single
`\r`-prefixed line that updates in place. So you see one ticking
counter, not a vertical scroll of every count. Real test stdout
that *isn't* progress is passed through unmodified — the bridge
restores the visibility the dropped `STANDARD_OUT` decoration
would have provided.

**Outside Gradle** — IntelliJ's direct-JVM test runner, Maven
Surefire, plain `java` invocation — the marker prefix is visible
in the raw output (`[PUNIT-PROGRESS]  1/100`, one line per
sample), and the counter scrolls. The bridge that strips the
marker is plugin-side, so other launchers don't get the in-place
nicety; they do still get a live progress signal.

The feature is deliberately lean: one counter emission per sample,
no spinner, no ETA, no rate, no end-of-run summary, and no
pass/fail glyph. The verdict record covers post-run pass/fail in
full; threshold-aware live colouring would require statistics-core
plumbing into the executor that breaches the package-isolation
rule, so it is out of scope for this surface.

---

## Appendix B: Glossary

**Baseline.** A YAML file recording a service contract's measured behaviour
under a specific factor configuration: pass rate, sample count,
sorted latency vector, covariate profile, capture timestamp.
Produced by a MEASURE experiment; consumed by tests that use
empirical criteria.

**Contract.** Two distinct senses, both intentional:
1. The `Contract<I, O>` interface — the per-sample operational layer
   of a service contract (`invoke`, `criteria`, optional `latency`).
2. The human-language reliability target an SLA / SLO / policy
   document defines. Pointed at via `.contractRef(origin, "...")`.

**Covariate.** An environmental factor declared by the service contract that
influences behaviour but is not part of the factor record. Resolved
once per run; participates in baseline matching.

**Criterion.** A statistical test the framework runs against a sample
summary. Pass-rate and latency are the built-in kinds. Authored via
`meeting()` (contractual) or `empirical()` (baseline-derived) followed
by a kind-selector (`.passRate(rate)`, `.zeroFailures()`,
`.atMost(percentile, duration)`).

**Early termination.** A probabilistic test whose contract declares a
contractual `meeting().passRate(...)` criterion short-circuits when
the verdict is mathematically determined — failure inevitable
(`IMPOSSIBILITY`) or success guaranteed (`SUCCESS_GUARANTEED`,
subject to a statistical-validity floor). Default-on; the verdict
matches what would have come back from running every declared
sample. Opt out per-test with `.disableEarlyTermination()`.
Empirical-mode tests run every declared sample.

**Factor.** A configuration knob the developer chooses to vary —
LLM model, temperature, prompt. Bound at the test/experiment call
site as a record of type `F`.

**`Outcome<T>`.** A sealed type with `Ok<T>` and `Fail<T>` variants.
The `org.mavai:outcome` library's data type for expected failure;
distinct from thrown exceptions, which signal defects.

**Postcondition.** One named acceptance clause on a criterion.
Authored via `.satisfies(name, check)` on a criterion chain;
optionally preceded by `.transforming(fn)` to derive a value before
the clauses evaluate. Evaluated per sample; failures are recorded as
data and surface in the verdict's failure histogram.

**Sampling.** A factor-free description of a sampling run: the use
case factory, the inputs population, the sample count, budgets,
pacing, exception policy. Constructed via `Sampling.builder()` or
`Sampling.of(...)`. Reused across MEASURE, TEST, EXPLORE, and
OPTIMIZE.

**Sentinel.** A class containing one or more `@ProbabilisticTest` or
`@Experiment` methods, runnable both under JUnit (development) and
under the Sentinel binary produced by `./gradlew createSentinel`
(production). No class-level annotation is required.

**Spec.** A typed declarative record of a data-generating process —
either an `Experiment` (MEASURE / EXPLORE / OPTIMIZE) or a
`ProbabilisticTest`. Produced by the fluent builders and dispatched
through the engine.

**TestIntent.** `VERIFICATION` (default) requires the configuration
to be statistically adequate and rejects undersized runs.
`SMOKE` warns but proceeds; verdicts carry the SMOKE qualifier.

**ThresholdOrigin.** The provenance label on a contractual threshold
(`SLA`, `SLO`, `EMPIRICAL`, `POLICY`, ...). Recorded on the verdict
for audit traceability.

**TokenTracker.** The cost channel passed to `invoke`. Service contracts
report token consumption via `tracker.recordTokens(n)`; the
framework rolls these up for the budget machinery and per-run
totals.

**Service Contract.** The `ServiceContract<F, I, O>` implementation — the single
shared definition of the service-under-test that all tests,
experiments, and sentinels reference.

**Verdict.** `PASS`, `FAIL`, or `INCONCLUSIVE`. INCONCLUSIVE is
reserved for "the framework cannot draw a conclusion" (no baseline
yet, baseline rejected as misaligned), distinct from FAIL ("the
data shows degradation").

**Wilson lower bound.** The one-sided lower confidence bound on a
proportion, used universally by PUnit for pass-rate inference.
Proper coverage at every sample size and proportion, including the
boundary `p̂ = 1`.

---

*Last reviewed: 2026-05-03. The mathematical foundations are
maintained separately as the
[Statistical Companion Document](https://r.mavai.org/statistical-companion.pdf);
this guide stays at the engineering level.*
