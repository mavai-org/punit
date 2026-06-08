# RP07 verdict-XML reference fixtures

Canonical reference XML for representative verdict states, intended
as the cross-framework conformance oracle for the RP07 wire format.

Each consuming framework (punit, feotest, future baseltest)
reproduces the per-case inputs through its own pipeline, serialises
the resulting verdict via its own RP07 emitter, and asserts the
result is semantically equivalent to the case's `expected.xml`.

The schema these documents conform to is the sibling
[`verdict-1.0.xsd`](../verdict-1.0.xsd); the prose specification
is [`../README.md`](../README.md).

## Equivalence semantics

**Exact equivalence under XML C14N canonicalisation.** Verdict XML
is structural; semantic equivalence is not approximate. Consuming
frameworks must compare via canonicalisation (XML C14N) or a
structural diff (e.g. XMLUnit) before asserting equality.

Raw byte-equality is meaningful only when both sides follow the
canonical formatting convention below — never assume your framework
emits canonical bytes by default, and always normalise before
comparing.

## Canonical formatting convention

Each `expected.xml` in this suite is hand-authored against a single
canonical convention. Consumers that emit bytes following the same
convention can compare without canonicalisation; consumers that
don't must canonicalise first.

- UTF-8 encoding declared on the XML declaration line.
- LF line endings, one final newline at end of file.
- Two-space indentation per nesting level.
- Each element starts on its own line; attributes after the first
  are indented to align with the first attribute on the opening
  tag's continuation line.
- The default namespace `http://mavai.org/verdict/1.0` is declared
  on the root `<verdict-record>` element only; no prefix
  declarations on descendant elements.
- Attributes appear in schema declaration order (the order they
  appear in `verdict-1.0.xsd` for each complex type).
- Child elements appear in schema declaration order despite
  `<xs:all>` permitting any order — declared order is the canonical
  one.
- The following elements are emitted *always* when their parent
  verdict record is produced — even when the contained data is
  trivially default — to keep the wire format shape-stable across
  records: `<execution>`, `<statistics>`, `<covariates>`,
  `<provenance>`, `<termination>`, `<cost>`. Optional attributes
  on these elements may be empty strings when no value is
  applicable (e.g. `<provenance origin="SLA" contract-ref=""
  spec-filename=""/>`).
- Other optional elements (`<functional>`, `<latency>`,
  `<baseline>`, `<postcondition-failures>`, `<factors>`,
  `<warnings>`, `<pacing>`, `<environment>`,
  `<failure-distribution>`) are emitted only when their dimension
  carries content. When omitted they are not emitted as empty
  elements.
- *Rate-shaped* `xs:double` values — `pass-rate` (functional),
  `threshold` (statistics), `baseline-rate` and `derived-threshold`
  (baseline) — are formatted to four fractional digits with
  trailing-zero padding (e.g. `0.9500`, `0.9000`). The fixed width
  is the visual cue for "probability/rate in `[0, 1]`."
- All other `xs:double` values (e.g. `standard-error`,
  `wilson-lower`, `test-statistic`, `p-value`, `confidence`) are
  the minimal decimal representation: integer when integral, else
  the shortest round-trippable form (`0.95`, `0.0218`, `1.6667`,
  `-1.6667`). Trailing zeros beyond what carries precision are
  not added.
- `xs:long` and `xs:int` values are bare decimal integers, no
  thousands separators.
- `xs:dateTime` values are in UTC with the `Z` suffix
  (`2026-05-11T12:00:00Z`).
- `xs:boolean` values are `true` / `false`.
- `<identity>`'s `test-name` is the method name only (the
  bare framework-test method symbol). The class is conveyed via
  `use-case-id`. Frameworks that distinguish class and method in
  their test identity model select one for `test-name`; the
  reference convention uses the method.

## Cases

| Case | Verdict | Demonstrates |
|------|---------|--------------|
| [`pass_functional_and_latency`](pass_functional_and_latency/) | PASS | Functional + latency dimensions both populated; PASS at the composite level. Exercises the descriptive-latency block, the threshold-vs-observed comparator on functional, and the multi-dimension envelope. |
| [`fail_with_statistical_analysis`](fail_with_statistical_analysis/) | FAIL | Functional dimension fails the threshold; `<statistics>` block carries the z-test statistic, p-value, Wilson lower bound, standard error. Exercises the full statistical-analysis serialisation under a FAIL composite. |
| [`inconclusive_covariate_misalignment`](inconclusive_covariate_misalignment/) | INCONCLUSIVE | Covariate misalignment produces INCONCLUSIVE; the `<covariates>` block carries the non-empty misalignment list. Exercises the misalignment-driven trichotomy branch (distinct from budget-exhaustion INCONCLUSIVE). |
| [`pass_baseline_derived_threshold`](pass_baseline_derived_threshold/) | PASS | Functional threshold derived from a baseline (`EMPIRICAL` origin); `<provenance>` carries the spec filename and contract reference; `<baseline>` records the baseline summary. |

## Per-case input shape

Each case directory contains:

- `inputs.json` — the per-case framework inputs sufficient for a
  consuming framework to reproduce the verdict.
- `expected.xml` — the canonical RP07 serialisation of that verdict.

The `inputs.json` shape:

```json5
{
  "framework_test_identity": {
    "class_name": "...",
    "method_name": "...",
    "use_case_id": "..."
  },
  "criteria": [
    { "kind": "PassRate", "threshold": <num>, "origin": "<ThresholdOriginEnum>" },
    { "kind": "PercentileLatency", "spec": { "p<n>_ms": <num>, ... }, "origin": "..." }
  ],
  "factors": { /* arbitrary factor record, may be empty */ },
  "covariates": {
    "baseline": { /* key-value pairs */ },
    "observed": { /* key-value pairs */ }
  },
  "confidence": <num>,
  "samples_planned": <int>,
  "samples_executed": <int>,
  "elapsed_ms": <int>,
  "samples_summary": {
    "successes": <int>,
    "failures": <int>,
    "postcondition_failures": [
      { "clause": "<description>", "count": <int>, "exemplars": [<input-strings>] }
    ],
    "latency_distribution_ms": {
      "p50": <int>, "p90": <int>, "p95": <int>, "p99": <int>
    }
  }
}
```

The `samples_summary` form is deliberately abstract — a consuming
framework's test synthesises a sample stream that produces this
summary, rather than the fixture enumerating every per-sample
record. The verdict the framework computes from the synthesised
stream is what the conformance contract checks against
`expected.xml`.

## Validation

Every `expected.xml` must validate against the sibling XSD:

```sh
xmllint --noout --schema verdict-1.0.xsd fixtures/*/expected.xml
```

CI runs this on every push and PR that touches these fixtures. A
failing fixture is a fixture defect; fix the fixture before merge.
