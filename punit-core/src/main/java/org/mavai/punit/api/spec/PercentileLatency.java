package org.mavai.punit.api.spec;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.mavai.punit.api.LatencyResult;
import org.mavai.punit.api.LatencySpec;
import org.mavai.punit.api.PercentileKey;
import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.ThresholdOrigin;

/**
 * A percentile-latency criterion. Reads {@link LatencyStatistics} from
 * the resolved baseline when in an empirical mode.
 *
 * <p>Three factory forms parallel {@code PassRate}:
 * <ul>
 *   <li>{@link #meeting(LatencySpec, ThresholdOrigin)} — contractual;
 *       per-percentile ceilings come from the LatencySpec, origin is
 *       declared explicitly (non-empirical).</li>
 *   <li>{@link #empirical(PercentileKey, PercentileKey...)} — default
 *       closest-match resolution; thresholds for the asserted
 *       percentiles are derived from the baseline at evaluate
 *       time.</li>
 *   <li>{@link #empiricalFrom(Supplier, PercentileKey, PercentileKey...)}
 *       — pinned baseline.</li>
 * </ul>
 *
 * <p>The conventional authoring path is the contract-side surface:
 * {@code empirical().atMost(P95).atMost(P99)} (empirical) or
 * {@code meeting().atMost(P95, ofMillis(500)).contractRef(SLA, "...")}
 * (contractual), declared on the contract's
 * {@link org.mavai.punit.api.Contract#latency()} sibling. The
 * framework's auto-injection then routes the contract's posture
 * through {@code SpecCriterionDeriver} to a {@code PercentileLatency}
 * instance — authors do not call these factories directly. Use them
 * at the test site only when overlaying (or, post-override-feature,
 * replacing) the contract-declared latency criterion.
 */
public final class PercentileLatency<OT> implements Criterion<OT, LatencyStatistics> {

    private static final String NAME = "percentile-latency";

    private enum Mode { CONTRACTUAL, EMPIRICAL_DEFAULT, EMPIRICAL_PINNED }

    private final Mode mode;
    private final LatencySpec declaredSpec;
    private final ThresholdOrigin origin;
    private final EnumSet<PercentileKey> assertedPercentiles;
    private final Supplier<Experiment> baselineSupplier;
    private final double confidence;

    private PercentileLatency(
            Mode mode,
            LatencySpec declaredSpec,
            ThresholdOrigin origin,
            EnumSet<PercentileKey> assertedPercentiles,
            Supplier<Experiment> baselineSupplier,
            double confidence) {
        this.mode = mode;
        this.declaredSpec = declaredSpec;
        this.origin = origin;
        this.assertedPercentiles = assertedPercentiles;
        this.baselineSupplier = baselineSupplier;
        this.confidence = confidence;
    }

    public static <OT> PercentileLatency<OT> meeting(LatencySpec spec, ThresholdOrigin origin) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(origin, "origin");
        if (!spec.hasAnyThreshold()) {
            throw new IllegalArgumentException(
                    "LatencySpec must assert at least one percentile — call .p50Millis(...), "
                            + ".p90Millis(...), .p95Millis(...), or .p99Millis(...) on the builder");
        }
        if (origin == ThresholdOrigin.EMPIRICAL) {
            throw new IllegalArgumentException(
                    "ThresholdOrigin.EMPIRICAL is reserved for the empirical factories; "
                            + "call PercentileLatency.empirical(...) or .empiricalFrom(...) instead");
        }
        return new PercentileLatency<>(
                Mode.CONTRACTUAL, spec, origin,
                assertedFromSpec(spec), null,
                org.mavai.punit.statistics.StatisticalDefaults.DEFAULT_CONFIDENCE);
    }

    public static <OT> PercentileLatency<OT> empirical(PercentileKey first, PercentileKey... rest) {
        return empirical(
                org.mavai.punit.statistics.StatisticalDefaults.DEFAULT_CONFIDENCE, first, rest);
    }

    public static <OT> PercentileLatency<OT> empirical(
            double confidence, PercentileKey first, PercentileKey... rest) {
        validateConfidence(confidence);
        EnumSet<PercentileKey> asserted = toEnumSet(first, rest);
        return new PercentileLatency<>(
                Mode.EMPIRICAL_DEFAULT, null, ThresholdOrigin.EMPIRICAL, asserted, null, confidence);
    }

    public static <OT> PercentileLatency<OT> empiricalFrom(
            Supplier<Experiment> baseline,
            PercentileKey first,
            PercentileKey... rest) {
        Objects.requireNonNull(baseline, "baseline");
        EnumSet<PercentileKey> asserted = toEnumSet(first, rest);
        return new PercentileLatency<>(
                Mode.EMPIRICAL_PINNED, null, ThresholdOrigin.EMPIRICAL, asserted, baseline,
                org.mavai.punit.statistics.StatisticalDefaults.DEFAULT_CONFIDENCE);
    }

    private static void validateConfidence(double c) {
        if (Double.isNaN(c) || c <= 0.0 || c >= 1.0) {
            throw new IllegalArgumentException(
                    "confidence must be in (0, 1), got " + c);
        }
    }

    /** The configured confidence level for the empirical upper-bound construction. */
    public double confidence() {
        return confidence;
    }

    /** The baseline supplier when pinned; empty otherwise. */
    public Optional<Supplier<Experiment>> baselineSupplier() {
        return Optional.ofNullable(baselineSupplier);
    }

    /**
     * The percentiles this criterion asserts against. Read by the
     * preflight feasibility check to compare against the planned
     * sample count.
     */
    public EnumSet<PercentileKey> assertedPercentiles() {
        return EnumSet.copyOf(assertedPercentiles);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Class<LatencyStatistics> statisticsType() {
        return LatencyStatistics.class;
    }

    @Override
    public boolean isEmpirical() {
        return mode != Mode.CONTRACTUAL;
    }

    @Override
    public Map<String, Object> empiricalDetail() {
        if (mode == Mode.CONTRACTUAL) {
            return Map.of();
        }
        return Map.of("assertedPercentiles", assertedCsv());
    }

    @Override
    public CriterionResult evaluate(EvaluationContext<OT, LatencyStatistics> ctx) {
        Objects.requireNonNull(ctx, "ctx");
        SampleSummary<OT> summary = ctx.summary();
        if (summary.total() == 0) {
            return inconclusive("zero samples taken", Map.of());
        }

        Map<PercentileKey, Duration> thresholds;
        Map<PercentileKey, Integer> thresholdRanks = new EnumMap<>(PercentileKey.class);
        Map<PercentileKey, Long> baselinePercentileMs = new EnumMap<>(PercentileKey.class);
        EnumSet<PercentileKey> saturated = EnumSet.noneOf(PercentileKey.class);
        ThresholdOrigin resolvedOrigin;
        Integer baselineSampleCount = null;

        if (mode == Mode.CONTRACTUAL) {
            thresholds = thresholdsFromSpec(declaredSpec);
            resolvedOrigin = origin;
        } else {
            LatencyStatistics stats = ctx.baseline().orElse(null);
            if (stats == null) {
                return EmpiricalChecks.noBaseline(NAME, empiricalDetail());
            }
            Map<String, Object> empiricalDetail = Map.of("assertedPercentiles", assertedCsv());
            // Inputs-identity rule precedes the sample-size rule: an identity
            // mismatch is a more fundamental violation, so its diagnostic
            // wins when both would fire.
            String baselineIdentity = ctx.baselineInputsIdentity().orElseThrow(() ->
                    new IllegalStateException(
                            "baseline statistics were resolved but baselineInputsIdentity is empty — "
                                    + "the BaselineProvider is producing inconsistent state"));
            Optional<CriterionResult> identityViolation = EmpiricalChecks.inputsIdentityMatch(
                    NAME, ctx.testInputsIdentity(), baselineIdentity, empiricalDetail);
            if (identityViolation.isPresent()) {
                return identityViolation.get();
            }
            Optional<CriterionResult> sizeViolation = EmpiricalChecks.sampleSizeConstraint(
                    NAME, summary.total(), stats.sampleCount(), empiricalDetail);
            if (sizeViolation.isPresent()) {
                return sizeViolation.get();
            }
            thresholds = thresholdsFromBaseline(
                    stats, thresholdRanks, baselinePercentileMs, saturated);
            resolvedOrigin = ThresholdOrigin.EMPIRICAL;
            baselineSampleCount = stats.sampleCount();
        }

        LatencyResult observed = summary.latencyResult();
        List<PercentileKey> breachedKeys = new ArrayList<>();
        Map<PercentileKey, Duration> observedByKey = new EnumMap<>(PercentileKey.class);
        for (PercentileKey key : assertedPercentiles) {
            Duration obs = key.observed(observed);
            observedByKey.put(key, obs);
            Duration threshold = thresholds.get(key);
            if (threshold != null && obs.compareTo(threshold) > 0) {
                breachedKeys.add(key);
            }
        }

        // Saturation routing (companion §12.4.2 / §12.5.2.1): under
        // VERIFICATION the methodology requires INCONCLUSIVE — no
        // finite-sample distribution-free upper bound is available at
        // the configured confidence. Under SMOKE, the advisory
        // t_{(n)} is reported and PASS/FAIL proceeds on it.
        boolean anySaturated = !saturated.isEmpty();
        if (anySaturated && ctx.intent() == TestIntent.VERIFICATION) {
            Map<String, Object> satDetail = new LinkedHashMap<>();
            satDetail.put("assertedPercentiles", assertedCsv());
            satDetail.put("origin", resolvedOrigin.name());
            satDetail.put("confidence", confidence);
            satDetail.put("baselineSampleCount", baselineSampleCount);
            for (PercentileKey key : saturated) {
                satDetail.put("saturated." + key.detailKey(), true);
            }
            String reason = String.format(
                    "no finite-sample upper bound available at confidence=%s for percentile(s) %s "
                            + "with baseline n=%d; verdict INCONCLUSIVE per Statistical Companion §12.5.2.1",
                    confidence, saturated.stream().map(PercentileKey::detailKey)
                            .collect(Collectors.joining(",")),
                    baselineSampleCount == null ? 0 : baselineSampleCount);
            return new CriterionResult(NAME, Verdict.INCONCLUSIVE, reason, satDetail);
        }

        Verdict verdict = breachedKeys.isEmpty() ? Verdict.PASS : Verdict.FAIL;

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("assertedPercentiles", assertedCsv());
        detail.put("origin", resolvedOrigin.name());
        for (PercentileKey key : assertedPercentiles) {
            detail.put("observed." + key.detailKey(), observedByKey.get(key).toMillis());
            Duration t = thresholds.get(key);
            if (t != null) {
                detail.put("threshold." + key.detailKey(), t.toMillis());
            }
            Integer rank = thresholdRanks.get(key);
            if (rank != null) {
                detail.put("threshold." + key.detailKey() + ".rank", rank);
            }
            Long basePct = baselinePercentileMs.get(key);
            if (basePct != null) {
                detail.put("threshold." + key.detailKey() + ".baselinePercentile", basePct);
            }
        }
        for (PercentileKey key : breachedKeys) {
            detail.put("breach." + key.detailKey(), observedByKey.get(key).toMillis());
        }
        if (mode != Mode.CONTRACTUAL) {
            detail.put("baselineSampleCount", baselineSampleCount);
            detail.put("confidence", confidence);
            for (PercentileKey key : saturated) {
                detail.put("saturated." + key.detailKey(), true);
            }
        }

        String explanation = breachedKeys.isEmpty()
                ? String.format("all %d asserted percentiles met (origin=%s)",
                        assertedPercentiles.size(), resolvedOrigin)
                : String.format("%d of %d asserted percentiles breached (origin=%s): %s",
                        breachedKeys.size(), assertedPercentiles.size(), resolvedOrigin,
                        breachedKeys.stream().map(PercentileKey::detailKey)
                                .collect(Collectors.joining(", ")));

        return new CriterionResult(NAME, verdict, explanation, detail);
    }

    private CriterionResult inconclusive(String reason, Map<String, Object> detail) {
        return new CriterionResult(NAME, Verdict.INCONCLUSIVE, reason, detail);
    }

    private String assertedCsv() {
        return assertedPercentiles.stream().map(PercentileKey::detailKey)
                .collect(Collectors.joining(","));
    }

    private static EnumSet<PercentileKey> toEnumSet(PercentileKey first, PercentileKey... rest) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(rest, "rest");
        EnumSet<PercentileKey> set = EnumSet.of(first);
        for (PercentileKey k : rest) {
            if (k == null) {
                throw new NullPointerException("asserted percentiles must not contain null");
            }
            set.add(k);
        }
        return set;
    }

    private static EnumSet<PercentileKey> assertedFromSpec(LatencySpec spec) {
        EnumSet<PercentileKey> set = EnumSet.noneOf(PercentileKey.class);
        for (PercentileKey key : PercentileKey.values()) {
            if (key.ceilingMillis(spec).isPresent()) {
                set.add(key);
            }
        }
        return set;
    }

    private static Map<PercentileKey, Duration> thresholdsFromSpec(LatencySpec spec) {
        Map<PercentileKey, Duration> map = new EnumMap<>(PercentileKey.class);
        for (PercentileKey key : PercentileKey.values()) {
            OptionalLong millis = key.ceilingMillis(spec);
            if (millis.isPresent()) {
                map.put(key, Duration.ofMillis(millis.getAsLong()));
            }
        }
        return map;
    }

    /**
     * Per asserted percentile, derive the exact distribution-free
     * upper confidence bound on the baseline quantile via Statistical
     * Companion §12.4.2's binomial order-statistic construction. The
     * detail-map sidecars ({@code thresholdRanks},
     * {@code baselinePercentileMs}) are populated as a side-effect so
     * the result surfaces the derivation metadata alongside the
     * threshold.
     */
    private Map<PercentileKey, Duration> thresholdsFromBaseline(
            LatencyStatistics stats,
            Map<PercentileKey, Integer> thresholdRanks,
            Map<PercentileKey, Long> baselinePercentileMs,
            EnumSet<PercentileKey> saturatedPercentiles) {
        long[] sortedMs = stats.sortedLatenciesMs();
        double[] sortedDouble = new double[sortedMs.length];
        for (int i = 0; i < sortedMs.length; i++) {
            sortedDouble[i] = sortedMs[i];
        }
        Map<PercentileKey, Duration> map = new EnumMap<>(PercentileKey.class);
        for (PercentileKey key : assertedPercentiles) {
            org.mavai.punit.statistics.LatencyThresholdDeriver.Threshold derived =
                    org.mavai.punit.statistics.LatencyThresholdDeriver.derive(
                            sortedDouble, key.value(), confidence);
            map.put(key, Duration.ofMillis((long) derived.threshold()));
            thresholdRanks.put(key, derived.rank());
            baselinePercentileMs.put(key, (long) derived.baselinePercentile());
            if (derived.saturated()) {
                saturatedPercentiles.add(key);
            }
        }
        return map;
    }
}
