package org.mavai.punit.internal.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.LatencyResult;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.api.TokenTracker;
import static org.mavai.punit.api.criterion.Criteria.empirical;
import org.mavai.punit.api.criterion.Criteria;
import org.mavai.punit.api.spec.BaselineStatistics;
import org.mavai.punit.api.spec.Experiment;
import org.mavai.punit.api.spec.LatencyStatistics;
import org.mavai.punit.api.PercentileKey;
import org.mavai.punit.api.spec.PercentileLatency;
import org.mavai.punit.api.spec.ProbabilisticTest;
import org.mavai.punit.api.spec.ProbabilisticTestResult;
import org.mavai.punit.api.spec.Verdict;
import org.mavai.punit.internal.engine.baseline.BaselineRecord;
import org.mavai.punit.internal.engine.baseline.BaselineWriter;
import org.mavai.punit.internal.engine.baseline.FactorsFingerprint;
import org.mavai.punit.internal.engine.baseline.LatencyIndicator;
import org.mavai.punit.internal.engine.baseline.YamlBaselineProvider;
import org.mavai.punit.internal.runtime.BaselineEmitter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end pin for the empirical {@code PercentileLatency} pair:
 * the measure run writes a baseline file carrying latency
 * percentiles, and a paired probabilistic test reads that file and
 * evaluates a latency criterion against it.
 *
 * <p>Two layers of coverage:
 *
 * <ol>
 *   <li><b>Synthetic baseline pin</b> ({@link OperationalPin}) — writes
 *       a baseline by hand with known {@link LatencyStatistics} and runs
 *       a probabilistic test with {@code PercentileLatency.empirical(...)}.
 *       Deterministic; pins the criterion → baseline-reader → engine
 *       wiring against fabricated stats.</li>
 *   <li><b>Round-trip pin</b> ({@link RoundTripPin}) — runs a real
 *       measure experiment, lets {@link BaselineEmitter} write the
 *       file, then runs a probabilistic test against the emitted file.
 *       Pins the writer ↔ reader contract for the latency block, which
 *       is the link that gets implicitly assumed everywhere else.</li>
 * </ol>
 */
@DisplayName("Empirical PercentileLatency end-to-end — measure writer ↔ test reader")
class EmpiricalLatencyEndToEndIntegrationTest {

    record Factors(String model) { }

    private static final Factors FACTORS = new Factors("model-a");
    private static final String USE_CASE_ID = "latency-pin-use-case";

    /** Trivial contract — empirical pass-rate posture, succeeds on every input. */
    static class FastServiceContract implements ServiceContract<Factors, Integer, Integer> {
        @Override public String id() { return USE_CASE_ID; }
        @Override public Outcome<Integer> invoke(Integer input, TokenTracker tracker) {
            return Outcome.ok(input);
        }
        @Override public Criteria<Integer> criteria() { return empirical().passRate(); }
    }

    private static Sampling<Factors, Integer, Integer> sampling(int samples) {
        return Sampling.<Factors, Integer, Integer>builder()
                .serviceContractFactory(f -> new FastServiceContract())
                .inputs(1, 2, 3, 4, 5)
                .samples(samples)
                .build();
    }

    // ── Synthetic baseline pin ────────────────────────────────────────

    @Nested
    @DisplayName("Synthetic baseline — known LatencyStatistics → known verdict")
    class OperationalPin {

        @Test
        @DisplayName("baseline p95 well above any plausible observation → PASS")
        void empiricalLatencyPassesWhenObservedBelowBaseline(@TempDir Path baselineDir)
                throws IOException {
            // 1 minute — far higher than any sample in a microbench.
            writeLatencyBaseline(baselineDir, Duration.ofMinutes(1), 1000);

            ProbabilisticTest spec = ProbabilisticTest
                    .testing(sampling(20), FACTORS)
                    .criterion(PercentileLatency.<Integer>empirical(PercentileKey.P95))
                    .build();

            ProbabilisticTestResult result = (ProbabilisticTestResult)
                    new Engine(new YamlBaselineProvider(baselineDir)).run(spec);

            var latencyEntry = result.criterionResults().stream()
                    .filter(ec -> "percentile-latency".equals(ec.result().criterionName()))
                    .findFirst()
                    .orElseThrow();
            assertThat(latencyEntry.result().verdict()).isEqualTo(Verdict.PASS);
            assertThat(latencyEntry.result().detail())
                    .containsEntry("origin", "EMPIRICAL")
                    .containsEntry("baselineSampleCount", 1000)
                    .containsEntry("threshold.p95", 60_000L)
                    .containsKey("observed.p95");
        }

        @Test
        @DisplayName("baseline p95 = 0 → any non-trivial observation breaches → FAIL")
        void empiricalLatencyFailsWhenObservedAboveBaseline(@TempDir Path baselineDir)
                throws IOException {
            writeLatencyBaseline(baselineDir, Duration.ZERO, 1000);

            ProbabilisticTest spec = ProbabilisticTest
                    .testing(sampling(20), FACTORS)
                    .criterion(PercentileLatency.<Integer>empirical(PercentileKey.P95))
                    .build();

            ProbabilisticTestResult result = (ProbabilisticTestResult)
                    new Engine(new YamlBaselineProvider(baselineDir)).run(spec);

            var latencyEntry = result.criterionResults().stream()
                    .filter(ec -> "percentile-latency".equals(ec.result().criterionName()))
                    .findFirst()
                    .orElseThrow();
            assertThat(latencyEntry.result().verdict()).isEqualTo(Verdict.FAIL);
            assertThat(latencyEntry.result().detail()).containsKey("breach.p95");
        }

        @Test
        @DisplayName("no baseline file → INCONCLUSIVE-no-baseline")
        void empiricalLatencyYieldsInconclusiveWhenNoBaselineFile(@TempDir Path emptyDir) {
            ProbabilisticTest spec = ProbabilisticTest
                    .testing(sampling(20), FACTORS)
                    .criterion(PercentileLatency.<Integer>empirical(PercentileKey.P95))
                    .build();

            ProbabilisticTestResult result = (ProbabilisticTestResult)
                    new Engine(new YamlBaselineProvider(emptyDir)).run(spec);

            // The whole verdict is INCONCLUSIVE; the latency criterion
            // is one of the contributors.
            assertThat(result.verdict()).isEqualTo(Verdict.INCONCLUSIVE);
            var latencyEntry = result.criterionResults().stream()
                    .filter(ec -> "percentile-latency".equals(ec.result().criterionName()))
                    .findFirst()
                    .orElseThrow();
            assertThat(latencyEntry.result().verdict()).isEqualTo(Verdict.INCONCLUSIVE);
        }
    }

    // ── Round-trip pin ────────────────────────────────────────────────

    @Nested
    @DisplayName("Round-trip — measure-emitted baseline read back by paired test")
    class RoundTripPin {

        @Test
        @DisplayName("measure emits a latency block; paired empirical test consumes it without INCONCLUSIVE-no-baseline")
        void measureEmitsLatencyBlockTestReadsIt(@TempDir Path baselineDir) {
            // Step 1: run a measure experiment with the same Sampling
            //         shape the paired test will use. The Engine times
            //         every sample automatically.
            Sampling<Factors, Integer, Integer> measureSampling = sampling(60);
            Experiment measure = Experiment.measuring(measureSampling, FACTORS).build();
            new Engine().run(measure);

            // Step 2: emit the baseline file to the temp dir.
            BaselineEmitter.emit(measure, baselineDir);
            assertThat(baselineDir.toFile().list())
                    .as("BaselineEmitter must have written exactly one baseline file")
                    .hasSize(1);

            // Step 3: run a paired probabilistic test against the same
            //         sampling shape. Asserts P50 — the most stable
            //         percentile for small sample counts, so the test
            //         is not flaky against timing variance.
            ProbabilisticTest spec = ProbabilisticTest
                    .testing(sampling(20), FACTORS)
                    .criterion(PercentileLatency.<Integer>empirical(PercentileKey.P50))
                    .build();
            ProbabilisticTestResult result = (ProbabilisticTestResult)
                    new Engine(new YamlBaselineProvider(baselineDir)).run(spec);

            // The latency criterion must have *resolved* the baseline
            // — i.e. produced PASS or FAIL, not INCONCLUSIVE-no-baseline.
            // We do not assert PASS vs FAIL on the verdict itself
            // because that depends on cross-run timing variance; the
            // pin's point is the writer ↔ reader chain.
            var latencyEntry = result.criterionResults().stream()
                    .filter(ec -> "percentile-latency".equals(ec.result().criterionName()))
                    .findFirst()
                    .orElseThrow();
            assertThat(latencyEntry.result().verdict())
                    .as("empirical latency criterion must have resolved its baseline")
                    .isIn(Verdict.PASS, Verdict.FAIL);
            assertThat(latencyEntry.result().detail())
                    .containsEntry("origin", "EMPIRICAL")
                    .containsKey("threshold.p50")
                    .containsKey("observed.p50")
                    .containsKey("baselineSampleCount");
            assertThat((int) latencyEntry.result().detail().get("baselineSampleCount"))
                    .as("baseline must have recorded ≥ 1 sample for the latency block to emit")
                    .isGreaterThan(0);
        }
    }

    // ── Fixtures ──────────────────────────────────────────────────────

    /**
     * Writes a synthetic baseline whose {@code latency:} block carries
     * the supplied {@code pX} as the value for every percentile. The
     * pass-rate statistics block is omitted; the test criterion under
     * test is {@code PercentileLatency}, so the auto-injected pass-rate
     * criterion's INCONCLUSIVE-no-baseline does not affect the
     * latency-criterion entry we assert on. The functional verdict and
     * the latency verdict are independent contributors to the
     * composite, and we assert on the latency entry by name.
     */
    private static void writeLatencyBaseline(
            Path baselineDir, Duration pX, int sampleCount) throws IOException {
        String fingerprint = FactorsFingerprint.of(FactorBundle.of(FACTORS));
        LatencyResult percentiles = new LatencyResult(pX, pX, pX, pX, sampleCount);
        long[] sorted = new long[sampleCount];
        java.util.Arrays.fill(sorted, pX.toMillis());
        LatencyIndicator indicator = new LatencyIndicator(percentiles, sorted, sampleCount, sampleCount);
        BaselineRecord record = new BaselineRecord(
                USE_CASE_ID, "latencyBaseline", fingerprint,
                sampling(1).inputsIdentity(), sampleCount,
                Instant.parse("2026-04-26T15:30:00Z"),
                Map.<String, BaselineStatistics>of(
                        "percentile-latency",
                        LatencyStatistics.fromPercentiles(percentiles, sampleCount)),
                org.mavai.punit.api.covariate.CovariateProfile.empty(),
                indicator);
        new BaselineWriter().write(record, baselineDir);
    }
}
