# Operational Flow: From Requirement to Certainty

This document describes the end-to-end workflow for using **PUnit** to test systems characterized by uncertainty—systems that don't always produce the same output for the same input.

Licensed under the Apache License, Version 2.0.

---

## Overview

When a system behaves with inherent uncertainty (LLMs, distributed systems, randomized algorithms), traditional pass/fail testing breaks down. A test might pass today and fail tomorrow—not because the system changed, but because of its nature.

PUnit provides a disciplined workflow that:

1. **Expresses** requirements as statistical thresholds (from normative origins like SLAs or empirical data)
2. **Executes** multiple samples to gather evidence
3. **Evaluates** results with proper statistical context and declared **intent**
4. **Reports** qualified verdicts (categorized as VERIFICATION or SMOKE)

---

## The Two Testing Paradigms

PUnit supports two complementary approaches to defining thresholds:

### SLA-Driven Testing (Compliance vs. Smoke)

The threshold comes from a **normative origin**—a contract, policy, or SLO. 

However, we must distinguish between **compliance** and a **smoke test**. A true compliance test requires a sample size large enough to support an evidential claim (VERIFICATION). For highly reliable services like payment gateways, this sample size can be very large. 

If you want an early-warning check without the cost of a full compliance audit, declare your intent as `SMOKE`.

The service contract declares the contractual posture in its
`criteria()` method; the test sets the intent:

```java
// On the service contract
@Override
public Criteria<String> criteria() {
    return meeting().<String>passRate(0.999)
            .contractRef(SLA, "Customer API SLA §3.1")
            .satisfies("Output is valid JSON",
                    output -> JsonValidator.isValid(output)
                            ? Outcome.ok()
                            : Outcome.fail("invalid-json", "validator rejected output"));
}

// On the test
@ProbabilisticTest
void apiMeetsSla() {
    PUnit.testing(JsonGenerationServiceContract.sampling(PROMPTS, 100), Tuning.DEFAULT)
            .intent(TestIntent.SMOKE)               // smoke, not full compliance audit
            .assertPasses();
}
```

**Verification Enforcement:** If you use `.intent(TestIntent.VERIFICATION)` (the default) with a normative threshold origin like `SLA`, PUnit will reject the test configuration if the sample size is too low to provide a statistically sound result.

**Workflow:** Requirement → Declare Intent → Test → Verify

### Spec-Driven Testing

The threshold is **derived from empirical data** gathered through experiments:

```
┌────────────────────────────────────────────────────────────────────────────────────┐
│ Service Contract ──▶ EXPLORE ──▶ OPTIMIZE ──▶ MEASURE ──▶ Baseline ──▶ Test        │
│                     (compare)     (tune)     (large N)    (commit) (with baseline) │
│                                                                                    │
└────────────────────────────────────────────────────────────────────────────────────┘
```

**Workflow:** Explore → Optimize → Measure → Commit Spec → Test

Both paradigms use the same `@ProbabilisticTest` annotation. The difference is where `minPassRate` comes from.

---

## The Three Operational Approaches

Regardless of which paradigm you use, you must decide **how to parameterize** your test.

> **Where the threshold comes from, and how the comparison is decided.** The pass-rate criterion has two modes, declared via `Criteria.meeting()` or `Criteria.empirical()` on the service contract's `criteria()`:
>
> - **Empirical** (`empirical().passRate()`) — the threshold is the observed pass rate read at runtime from the matched baseline spec (produced by a prior MEASURE experiment). The verdict applies a one-sided **Wilson score lower bound** to the run's observed rate at the configured confidence (default 0.95) and passes iff that lower bound clears the baseline rate. Approaches 1 and 2 below use this mode.
> - **Contractual** (`meeting().passRate(threshold).contractRef(origin, ref)`) — the threshold is an externally-fixed number declared in code (an SLA, SLO, or policy figure). The verdict is a **deterministic** `observed >= threshold` comparison. No Wilson margin: an SLA is an external commitment to a specific number, not a statistical claim against a baseline. Approach 3 uses this mode.
>
> What the three approaches differ in is which knob the author fixes first.

### Approach 1: Sample-Size-First (Budget-Driven)

*"My budget allows 100 samples per run. Run them against the recorded baseline and let the framework decide."*

The contract declares `empirical().passRate()` in `criteria()`; the test passes the baseline supplier:

```java
@ProbabilisticTest
void sampleSizeFirst() {
    PUnit.testing(this::baseline)
            .samples(100)
            .assertPasses();
}
```

**What happens:**
- PUnit runs the chosen samples.
- The contract's `empirical().passRate()` criterion resolves the matched baseline spec at runtime; its observed rate becomes the threshold for this run.
- The verdict applies the Wilson lower bound at the default confidence (0.95) to *this* run's observed rate, and passes iff that lower bound clears the baseline.
- With small N the Wilson margin is wide, so passing requires the observed rate to be clearly above the baseline.

**Trade-off:** You accept whatever statistical power 100 samples affords. Detects large regressions confidently; less sensitive to small ones.

**Best for:** Continuous monitoring, CI pipelines, rate-limited APIs.

### Approach 2: Confidence-First (Power-Driven)

*"I need to be able to detect a 5-percentage-point regression with 80% probability at 95% confidence. How many samples?"*

The contract again declares the empirical posture in `criteria()`; the test computes the required sample size via `PowerAnalysis`:

```java
@ProbabilisticTest
void confidenceFirst() {
    int n = PowerAnalysis.sampleSize(this::baseline, 0.05, 0.80);

    PUnit.testing(this::baseline)
            .samples(n)
            .assertPasses();
}
```

The confidence target (default 0.95) is set on the contract's
`empirical().passRate().atConfidence(...)` posture if the default
needs overriding.

**What happens:**
- `PowerAnalysis.sampleSize(baseline, mde, power)` derives the required N from the baseline rate, the minimum detectable effect (MDE), and the target power.
- PUnit runs that many samples (typically larger than budget-driven runs).
- Verdict still applies the Wilson lower bound at the target confidence — but N is sized so a real regression of size MDE has the configured probability of failing the bound.

**Trade-off:** Sample size is determined by statistics, not budget. Tight MDEs and high power require many samples.

**Best for:** Safety-critical systems, compliance audits, pre-release assurance.

### Approach 3: Threshold-First (Externally-Dictated)

*"The threshold is fixed by an SLA, SLO, or policy. Verify against it."*

The contract declares `meeting().passRate(0.90).contractRef(SLA, "...")` in `criteria()`; the test is bare:

```java
@ProbabilisticTest
void thresholdFirst() {
    PUnit.testing(MyServiceContract.sampling(INPUTS, 100), MyFactors.DEFAULT)
            .assertPasses();
}
```

**What happens:**
- The threshold and its provenance (`SLA`, `SLO`, or `POLICY`) are declared in code; no baseline is involved.
- PUnit runs the chosen samples and applies a **deterministic** `observed >= threshold` comparison — no Wilson margin.
- The threshold's provenance and the optional `contractRef` are recorded on the verdict for audit traceability.

**Trade-off:** No statistical buffer. With a small N relative to a strict threshold, declare `.intent(TestIntent.SMOKE)` to mark the run as a sentinel rather than a verification claim; otherwise PUnit's pre-flight feasibility gate may reject the configuration as undersized for verification.

**Best for:** SLA-style verification of services with externally-committed reliability targets.

> **Antipattern: pinning a contractual threshold to a baseline's observed rate.** Reading a baseline file by eye and pasting its observed rate into `meeting().passRate(0.935).contractRef(EMPIRICAL, ...)` looks like the empirical-pair pattern but isn't. The contractual path is deterministic — `observed >= 0.935` — and natural sampling variance puts the next run's observed rate below 0.935 roughly half the time even when the SUT is performing exactly at baseline. Result: ~50% false-fail rate. The proper baseline-comparison path is `empirical().passRate()`, which resolves the baseline at runtime, applies the Wilson lower bound at the configured confidence, and gives the test the statistical buffer that the hardcoded contractual approach is missing.

### Choosing Your Approach

| If Your Priority Is...               | Use...            | You're Saying...                                                               |
|--------------------------------------|-------------------|--------------------------------------------------------------------------------|
| Controlling costs (CI time, API)     | Sample-Size-First | "We can afford N samples against the baseline. Verdict at default confidence." |
| Minimizing risk (safety, compliance) | Confidence-First  | "We need to detect this regression at this power. How many samples?"           |
| Honouring a contractual target       | Threshold-First   | "The SLA says 0.99. Pass iff observed beats it."                               |

> **Working example:** See [`ShoppingBasketThresholdApproachesTest`](https://github.com/mavai-org/punitexamples/blob/main/src/test/java/org/mavai/punit/examples/probabilistictests/ShoppingBasketThresholdApproachesTest.java) in punitexamples for a complete demonstration of all three approaches.

---

## The Fundamental Trade-Off

You cannot simultaneously have:
- Low testing cost (few samples)
- High confidence (low false positive rate)
- Tight threshold (detect small degradations)

This is not a limitation of PUnit—it's a fundamental property of statistical inference.

| If You Fix...     | And You Fix...   | Then Statistics Determines... |
|-------------------|------------------|-------------------------------|
| Sample size       | Threshold        | Confidence level              |
| Sample size       | Confidence       | How tight threshold can be    |
| Confidence        | Threshold        | Required sample size          |

PUnit makes these trade-offs **explicit and computable** rather than leaving them implicit.

---

## The Spec-Driven Workflow in Detail

### Stage 1: Define a Service Contract

PUnit recognizes that **experiments and tests must refer to the same objects**.

In traditional testing, we articulate correctness through a series of test assertions. This works for deterministic systems where we expect 100% success. However, for systems with inherent uncertainty, a test assertion that aborts on failure is of zero use when we want to collect data about the service's behavior. We need to know *how often* it fails, not just that it *did* fail.

We therefore define a **Service Contract** and its associated **Service Contract**. The Service Contract is the shared expression of correctness:
- **Experiments** use it as a source of correctness data (to measure behavior).
- **Probabilistic Tests** use it as a correctness enforcer (to verify performance against a threshold).

A Service Contract is a reusable class that invokes your production code and declares an acceptance contract:

```java
public final class JsonGenerationServiceContract
        implements ServiceContract<Tuning, String, String> {

    @Override
    public Outcome<String> invoke(String prompt, TokenTracker tracker) {
        LlmResponse response = llmClient.complete(prompt);
        tracker.recordTokens(response.getTokensUsed());
        return Outcome.ok(response.getContent());
    }

    @Override
    public Criteria<String> criteria() {
        return empirical().<String>passRate()
                .satisfies("Output is valid JSON",
                        output -> JsonValidator.isValid(output)
                                ? Outcome.ok()
                                : Outcome.fail("invalid-json", "validator rejected output"));
    }

    @Override
    public String id() { return "json-generation"; }
}
```

The service contract is **reused** across experiments AND tests. You define it once.

### Stage 2: Run a MEASURE Experiment

Run the service contract many times (typically 1000+) to gather empirical data:

```java
@Experiment
void measureBaseline() {
    PUnit.measuring(JsonGenerationServiceContract.sampling(PROMPTS, 1000), Tuning.DEFAULT)
            .run();
}
```

```bash
./gradlew measure --tests "JsonGenerationExperiment"
```

### Stage 3: Commit the Baseline

The experiment writes a baseline file to
`src/test/resources/punit/baselines/`. The filename is
`<useCaseId>.<methodName>-<factorsFingerprint>.yaml`, so a re-measure
under a different factor configuration produces a sibling file rather
than overwriting:

```yaml
schemaVersion: punit-baseline-3
useCaseId: json-generation
methodName: measureBaseline
factorsFingerprint: a1b2c3d4
inputsIdentity: sha256:7d3a8c1e9b2f
sampleCount: 1000
generatedAt: '2026-01-12T10:30:00Z'
statistics:
  bernoulli-pass-rate:
    criteria:
      output-valid-json:
        observedPassRate: 0.935
        sampleCount: 1000
latency:
  basis: passing-samples
  contributingSamples: 935
  totalSamples: 1000
  p50Ms: 240
  p90Ms: 480
  p95Ms: 760
  p99Ms: 1180
contentFingerprint: 7d3a8c1e9b2f4a5b6c7d8e9f0a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d
```

A few things to notice:

- **`statistics.bernoulli-pass-rate.criteria.<id>`** — pass-rate is
  always recorded per methodology criterion, one entry per criterion
  the service contract declared (here, the single criterion from
  Stage 1 surfaces as `output-valid-json`). When the contract
  bundles multiple criteria every one gets its own row; a
  probabilistic test that picks a per-criterion threshold sees them
  via the same structure.
- **`latency:` is a top-level block, not a criterion.** Percentiles are
  emitted from passing samples only (`basis: passing-samples`); each
  percentile is omitted if too few samples back it (the per-percentile
  minima are documented in the latency section of the design notes).
- **`contentFingerprint:` is the last line** — a SHA-256 over the body
  that precedes it. The reader recomputes the digest on load; any
  hand-edit raises an integrity warning on the next test run.
- **`inputsIdentity`** pins the baseline to the exact input list it was
  measured on; **`factorsFingerprint`** pins it to the factor
  configuration. The runtime resolver uses both when selecting a
  baseline at test time, so a baseline only applies to a test whose
  inputs and factors hash the same way.

**Review and commit:**

```bash
git add src/test/resources/punit/baselines/
git commit -m "Add baseline for JSON generation (93.5% @ N=1000)"
```

The approval step IS the commit. If your organization uses pull requests, that's where review happens.

### Stage 4: Create a Probabilistic Test

The test references the baseline. The threshold is derived at runtime:

```java
@ProbabilisticTest
void jsonGenerationMeetsBaseline() {
    PUnit.testing(JsonGenerationServiceContract.sampling(PROMPTS, 100), Tuning.DEFAULT)
            .assertPasses();
}
```

The service contract declared `empirical().passRate()` in
`criteria()` at Stage 1; the test simply runs against the contract
and the criterion auto-injects.

**What happens at runtime:**
1. Resolve the baseline by `useCaseId`, `factorsFingerprint`, and
   `inputsIdentity` (here: `json-generation.measureBaseline-a1b2c3d4.yaml`)
2. Verify `contentFingerprint`; raise a warning on the verdict if the
   body has been edited since the measure produced it
3. Read the per-criterion observed pass rate (here: 0.935 over 1000 samples
   for `output-valid-json`)
4. Run 100 samples
5. Apply the Wilson lower bound at the configured confidence (default 0.95)
   to this run's observed rate; the test passes iff the lower bound clears
   the baseline rate
6. Report pass/fail with the statistical context (baseline rate, observed
   rate, Wilson bound, intent)

---

## Understanding Results

### When the Test Passes

```
═══════════════════════════════════════════════════════════════
PUnit PASSED: jsonGenerationMeetsBaseline
  Observed pass rate: 94.0% (94/100) >= min pass rate: 91.6%
  Threshold derived from: usecase.json.generation.yaml (93.5% baseline)
  Elapsed: 45234ms
═══════════════════════════════════════════════════════════════
```

**Interpretation:** The observed 94% is consistent with the 93.5% baseline, accounting for sampling variance. No evidence of degradation.

### When the Test Fails

```
═══════════════════════════════════════════════════════════════
PUnit FAILED: jsonGenerationMeetsBaseline
  Observed pass rate: 85.0% (85/100) < min pass rate: 91.6%
  Shortfall: 6.6% below threshold

  CONTEXT:
    Confidence: 95%
    Interpretation: Evidence suggests degradation from baseline.
    False positive probability: 5%

  SUGGESTED ACTIONS:
    1. Investigate recent changes
    2. Re-run experiment if change was intentional
    3. Increase sample size if false positive suspected
═══════════════════════════════════════════════════════════════
```

**Interpretation:** The observed 85% is statistically inconsistent with the 93.5% baseline. There's a 5% chance this is a false positive.

### The Critical Qualification

A "FAILED" result does not mean "definitely broken."

It means: "The observed behavior is statistically inconsistent with the baseline at the configured confidence level."

With 95% confidence, if there's no real degradation, there's still a 5% chance of seeing a failure (false alarm). This is the fundamental trade-off of statistical testing.

---

## When to Re-Run Experiments

Update your spec when:

- **Major model updates**: New LLM version, significant prompt changes
- **Intentional improvements**: System should perform better
- **Baseline staleness**: Every 3-6 months for actively changing systems
- **After production incidents**: To recalibrate expectations

```bash
# Re-run experiment (overwrites existing spec)
./gradlew measure --tests "JsonGenerationExperiment"

# Review changes
git diff src/test/resources/punit/baselines/

# Commit updated spec
git commit -am "Update JSON generation baseline after prompt improvements"
```

---

## EXPLORE Mode: Configuration Discovery

When you have choices about how to configure a non-deterministic system (model, temperature, prompt), use EXPLORE mode to compare options:

```java
@Experiment
void compareModels() {
    PUnit.exploring(JsonGenerationServiceContract.sampling(PROMPTS, 20))
            .grid(
                    new Tuning("gpt-4o",       0.3),
                    new Tuning("gpt-4o-mini",  0.3))
            .experimentId("model-comparison-v1")
            .run();
}
```

```bash
./gradlew exp -Prun=MyExperiment.explore
```

EXPLORE is for **rapid feedback** before committing to expensive measurements. Compare results, then OPTIMIZE or MEASURE the winner.

---

## OPTIMIZE Mode: Factor Tuning

After EXPLORE identifies a promising configuration, use OPTIMIZE to fine-tune a specific factor:

```java
@Experiment
void optimizeTemperature() {
    PUnit.optimizing(JsonGenerationServiceContract.sampling(PROMPTS, 20))
            .initialFactors(new Tuning("gpt-4o-mini", 1.0))   // start high, optimize down
            .stepper((current, history) ->
                    current.temperature() <= 0.05
                            ? null
                            : current.temperature(current.temperature() - 0.1))
            .maximize(summary -> summary.passRate())
            .maxIterations(10)
            .noImprovementWindow(3)
            .run();
}
```

```bash
./gradlew exp -Prun=MyExperiment.optimizeTemperature
```

OPTIMIZE iteratively refines a **control factor** through mutation and evaluation. Use it to find the optimal temperature, prompt phrasing, or other continuous parameters before establishing your baseline with MEASURE.

---

## Summary

| Step            | Command             | Output                                  |
|-----------------|---------------------|-----------------------------------------|
| Define service contract | —                   | `ServiceContract<F, I, O>` class                |
| Run experiment  | `./gradlew exp`     | Baseline file                           |
| Commit baseline | `git commit`        | Version-controlled baseline             |
| Run tests       | `./gradlew test`    | Qualified verdicts (VERIFICATION/SMOKE) |

**The key insight:** PUnit doesn't eliminate uncertainty—it quantifies it. Every verdict comes with statistical context and a clear statement of intent, enabling informed decisions about whether to act on test results.

---

## Production Readiness

Taking a system characterized by uncertainty from prototype to production involves two distinct phases:

### Phase A: Prepartion

The goal is to find the best configuration and understand raw system behavior.

| Step | Activity                                 | Purpose                                          |
|------|------------------------------------------|--------------------------------------------------|
| 1    | **EXPLORE** factors (model, temperature) | Find the best configuration for your price-point |
| 2    | **OPTIMIZE** the system prompt           | Maximize reliability with iterative refinement   |
| 3    | **MEASURE** raw baseline                 | Quantify how well the optimized system performs  |

At the end of Phase A, you have an optimized configuration and a baseline that reflects the system's true reliability—warts and all.

### Phase B: Hardening

The goal is to improve user-facing reliability and establish regression protection.

| Step | Activity                      | Purpose                                                   |
|------|-------------------------------|-----------------------------------------------------------|
| 4    | Add **hardening mechanisms**  | Improve reliability without changing the underlying model |
| 5    | **MEASURE** hardened baseline | Quantify the improvement (and its cost)                   |
| 6    | Add **probabilistic tests**   | Protect against regression using both baselines           |

**Why measure before and after hardening?**

The pre-hardening baseline (Phase A) tells you how well the underlying system performs. The post-hardening baseline (Phase B) tells you what users actually experience. Keeping both baselines enables you to detect if the underlying model degrades—even when hardening masks it at the user level.

**What does hardening mean for LLMs?**

For LLM-based systems, hardening typically involves retrying failed invocations. A simple retry repeats the same request; a smarter approach includes the previous (invalid) response in the retry prompt, guiding the model toward a valid response. This improves user-facing reliability at the cost of additional tokens.

### When to Re-Run Each Phase

- **Re-run Phase A** when you change models, significantly modify prompts, or suspect the underlying system has changed.
- **Re-run Phase B** when you modify hardening logic or want to re-baseline user-facing reliability.
- **Run Phase B tests continuously** in CI to detect regressions.
