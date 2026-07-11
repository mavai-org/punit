package org.mavai.punit.statistics.conformance;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.criterion.Criteria;
import org.mavai.punit.api.spec.BaselineStatistics;
import org.mavai.punit.api.spec.CriterionResult;
import org.mavai.punit.api.spec.PerCriterionPassRateStatistics;
import org.mavai.punit.api.spec.ProbabilisticTest;
import org.mavai.punit.api.spec.ProbabilisticTestResult;
import org.mavai.punit.api.spec.Verdict;
import org.mavai.punit.internal.engine.Engine;
import org.mavai.punit.internal.engine.baseline.BaselineRecord;
import org.mavai.punit.internal.engine.baseline.BaselineWriter;
import org.mavai.punit.internal.engine.baseline.FactorsFingerprint;
import org.mavai.punit.internal.engine.baseline.YamlBaselineProvider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.mavai.punit.api.criterion.Criteria.empirical;
import static org.mavai.punit.api.criterion.Criteria.meeting;

/**
 * Drives one {@code regression_decision} fixture case through punit's
 * production verdict path — the code path a real probabilistic test
 * takes from threshold to verdict — and returns the judged criterion
 * result.
 *
 * <p><b>REGRESSION procedure</b>: a baseline with exactly the case's
 * {@code (baseline_successes, baseline_trials)} is written to disk the
 * way a measure run persists one, an empirical pass-rate contract at
 * the case's confidence is sampled {@code test_samples} times against a
 * scripted service delivering exactly {@code observed_successes}
 * passes, and the engine resolves the baseline, derives the threshold
 * inside the framework, and judges. Nothing statistical is recomputed
 * on the test side.
 *
 * <p><b>COMPLIANCE procedure</b>: the same drive with a declared
 * (contractual) pass-rate threshold and no baseline.
 *
 * <p>Early termination is disabled on the spec so the scripted sample
 * schedule always runs to its full count — the fixture's {@code K} and
 * {@code n} must reach the criterion untruncated.
 */
final class DecisionRuleProductionPath {

    record Judged(Verdict verdict, Map<String, Object> detail) { }

    private static final String USE_CASE_ID = "decision-rule-conformance-use-case";

    record Factors(String scenario) { }

    private static final Factors FACTORS = new Factors("oracle-scenario");

    private DecisionRuleProductionPath() { }

    static Judged judgeRegression(
            int baselineSuccesses, int baselineTrials,
            int testSamples, double confidence, int observedSuccesses) throws IOException {
        Path baselineDir = Files.createTempDirectory("punit-conformance-baseline");
        try {
            writeBaseline(baselineDir, baselineSuccesses, baselineTrials);
            var sampling = sampling(testSamples, scriptedContract(
                    observedSuccesses,
                    empirical().<String>passRate().atConfidence(confidence)
                            .where("scripted response is ok", "ok"::equals)));
            ProbabilisticTest spec = ProbabilisticTest.testing(sampling, FACTORS)
                    .disableEarlyTermination()
                    .build();
            var result = (ProbabilisticTestResult)
                    new Engine(new YamlBaselineProvider(baselineDir)).run(spec);
            return judgedFrom(result);
        } finally {
            deleteRecursively(baselineDir);
        }
    }

    static Judged judgeCompliance(
            double threshold, int testSamples, double confidence, int observedSuccesses) {
        // punit's authoring surface accepts no confidence adjunct on a
        // declared threshold (`.atConfidence(...) cannot compose with
        // .meeting(...)`) — the criterion runs at the framework default.
        // Every compliance fixture case is published at that default, so
        // nothing is lost; a future fixture at another confidence would
        // need the authoring surface extended first.
        if (confidence != 0.95) {
            throw new IllegalArgumentException(
                    "compliance fixture case at confidence " + confidence
                            + " is not expressible on punit's declared-threshold "
                            + "authoring surface (fixed at the 0.95 default)");
        }
        var sampling = sampling(testSamples, scriptedContract(
                observedSuccesses,
                meeting().<String>passRate(threshold)
                        .where("scripted response is ok", "ok"::equals)));
        ProbabilisticTest spec = ProbabilisticTest.testing(sampling, FACTORS)
                .disableEarlyTermination()
                .build();
        var result = (ProbabilisticTestResult) new Engine().run(spec);
        return judgedFrom(result);
    }

    private static Judged judgedFrom(ProbabilisticTestResult result) {
        CriterionResult criterion = result.criterionResults().get(0).result();
        return new Judged(result.verdict(), criterion.detail());
    }

    /**
     * A service delivering exactly {@code successes} passing responses
     * first, then failing ones. The scripted failure is a postcondition
     * failure (the response fails the criterion's {@code where} clause),
     * the shape a typical stochastic-service test produces — the sample
     * is counted as a failure on both the run-level and the
     * per-criterion accounting bases.
     *
     * <p>Deliberately NOT an {@code Outcome.fail} apply-level failure:
     * punit's per-criterion verdict derivation excludes apply-level
     * failures from its counting basis, which is a separate accounting
     * question from the decision rule this suite pins. See the
     * decision-rule conformance findings.
     */
    private static ServiceContract<Factors, Integer, String> scriptedContract(
            int successes, Criteria<String> criteria) {
        AtomicInteger invocation = new AtomicInteger();
        return new ServiceContract<>() {
            @Override public String id() { return USE_CASE_ID; }
            @Override public Criteria<String> criteria() { return criteria; }
            @Override public Outcome<String> invoke(Integer input, TokenTracker tracker) {
                return invocation.incrementAndGet() <= successes
                        ? Outcome.ok("ok")
                        : Outcome.ok("scripted failing response");
            }
        };
    }

    private static Sampling<Factors, Integer, String> sampling(
            int samples, ServiceContract<Factors, Integer, String> contract) {
        return Sampling.<Factors, Integer, String>builder()
                .serviceContractFactory(f -> contract)
                .inputs(1, 2, 3)
                .samples(samples)
                .build();
    }

    /**
     * Persists the case's baseline exactly as a measure run would: the
     * observed pass rate and sample count under the pass-rate criterion
     * key, with an inputs identity matching the paired test's sampling.
     */
    private static void writeBaseline(
            Path baselineDir, int baselineSuccesses, int baselineTrials) throws IOException {
        double passRate = (double) baselineSuccesses / baselineTrials;
        var probeSampling = sampling(1, scriptedContract(1, empirical().<String>passRate()));
        BaselineRecord record = new BaselineRecord(
                USE_CASE_ID, "measureBaseline",
                FactorsFingerprint.of(FactorBundle.of(FACTORS)),
                probeSampling.inputsIdentity(), baselineTrials,
                Instant.parse("2026-07-10T00:00:00Z"),
                Map.<String, BaselineStatistics>of(
                        "bernoulli-pass-rate",
                        PerCriterionPassRateStatistics.of("contract", passRate, baselineTrials)));
        new BaselineWriter().write(record, baselineDir);
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
