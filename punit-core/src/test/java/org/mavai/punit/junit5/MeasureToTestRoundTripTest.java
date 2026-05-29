package org.mavai.punit.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.InputSupplier;
import org.mavai.punit.api.NoFactors;
import org.mavai.punit.api.spec.BaselineStatistics;
import org.mavai.punit.api.spec.PassRateStatistics;
import org.mavai.punit.api.spec.PerCriterionPassRateStatistics;
import org.mavai.punit.internal.engine.baseline.BaselineRecord;
import org.mavai.punit.internal.engine.baseline.BaselineWriter;
import org.mavai.punit.internal.engine.baseline.FactorsFingerprint;
import org.mavai.punit.junit5.testsubjects.RoundTripSubjects;
import org.mavai.punit.internal.engine.baseline.BaselineResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

@DisplayName("End-to-end: @Experiment writes a baseline; @ProbabilisticTest reads it")
class MeasureToTestRoundTripTest {

    private static final String JUNIT_ENGINE_ID = "junit-jupiter";
    private static final String USE_CASE_ID = "round-trip-use-case";

    private String savedProperty;

    @BeforeEach
    void clearProperty() {
        savedProperty = System.getProperty(BaselineResolver.BASELINE_DIR_PROPERTY);
    }

    @AfterEach
    void restoreProperty() {
        if (savedProperty == null) {
            System.clearProperty(BaselineResolver.BASELINE_DIR_PROPERTY);
        } else {
            System.setProperty(BaselineResolver.BASELINE_DIR_PROPERTY, savedProperty);
        }
    }

    // ── Emission side: @Experiment writes the baseline file ────────

    @Test
    @DisplayName("PUnit.measuring(...).run() writes a baseline YAML when a baseline directory is configured")
    void measureWritesBaseline(@TempDir Path baselineDir) throws IOException {
        System.setProperty(BaselineResolver.BASELINE_DIR_PROPERTY, baselineDir.toString());

        Events events = run(RoundTripSubjects.PassingMeasure.class);
        events.assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));

        try (Stream<Path> files = Files.list(baselineDir)) {
            assertThat(files.filter(p -> p.toString().endsWith(".yaml")))
                    .as("baseline YAML emitted under %s", baselineDir)
                    .isNotEmpty();
        }
    }

    // ── Resolution side: @ProbabilisticTest reads the baseline ─────

    @Test
    @DisplayName("@ProbabilisticTest finds the baseline at the configured directory and produces a verdict")
    void empiricalReadsHandWrittenBaseline(@TempDir Path baselineDir) throws IOException {
        // Hand-write a baseline at p=0.5 — chosen so the test's Wilson lower
        // bound (on always-passing samples) clears it, producing PASS. Using
        // a baseline-emitted file would tie the test to whatever rate the
        // emitter recorded, and an always-passes service contract at n=20 has Wilson
        // lower bound ≈ 0.88 — clears 0.5 comfortably; cannot clear 1.0.
        writeBaselineAt(baselineDir, 0.5, 50);
        System.setProperty(BaselineResolver.BASELINE_DIR_PROPERTY, baselineDir.toString());

        Events events = run(RoundTripSubjects.EmpiricalAgainstBaseline.class);
        events.assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));
    }

    @Test
    @DisplayName("full pipeline: emission → resolution → assertion in two JUnit invocations")
    void fullRoundTrip(@TempDir Path baselineDir) throws IOException {
        // Phase 1 — emit a baseline via PUnit.measuring(...).run().
        // The service contract here passes always; the recorded rate will be 1.0,
        // which is too tight a threshold for a Wilson-bound test to clear.
        // We overwrite with a hand-written 0.5 baseline so Phase 2 can pass.
        System.setProperty(BaselineResolver.BASELINE_DIR_PROPERTY, baselineDir.toString());
        Events emitEvents = run(RoundTripSubjects.PassingMeasure.class);
        emitEvents.assertStatistics(stats -> stats.started(1).succeeded(1));
        try (Stream<Path> files = Files.list(baselineDir)) {
            assertThat(files.anyMatch(p -> p.toString().endsWith(".yaml")))
                    .as("emission produced a baseline file")
                    .isTrue();
        }

        // Phase 1.5 — overwrite with a baseline whose rate the Wilson bound
        // can clear. Real-world authors would simply observe a lower rate
        // through their service contract; this in-test substitution decouples the
        // pipeline-under-test from the use-case design.
        writeBaselineAt(baselineDir, 0.5, 50);

        // Phase 2 — empirical test resolves the baseline and produces PASS.
        Events testEvents = run(RoundTripSubjects.EmpiricalAgainstBaseline.class);
        testEvents.assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));
    }

    // ── Helpers ────────────────────────────────────────────────────

    private static void writeBaselineAt(Path dir, double passRate, int sampleCount)
            throws IOException {
        BaselineRecord record = new BaselineRecord(
                USE_CASE_ID,
                "hand-written",
                FactorsFingerprint.of(FactorBundle.of(new NoFactors())),
                InputSupplier.from(() -> List.of(1, 2, 3)).identity(),
                sampleCount,
                Instant.parse("2026-04-28T12:00:00Z"),
                Map.<String, BaselineStatistics>of(
                        "bernoulli-pass-rate",
                        PerCriterionPassRateStatistics.of("contract", passRate, sampleCount)));
        new BaselineWriter().write(record, dir);
    }

    private static Events run(Class<?> testClass) {
        return EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(testClass))
                .execute()
                .testEvents();
    }
}
