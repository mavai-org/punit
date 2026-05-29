package org.mavai.punit.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.xml.stream.XMLStreamException;

import org.mavai.punit.api.TestIntent;
import org.mavai.punit.verdict.TokenMode;
import org.mavai.punit.verdict.ExpirationStatus;
import org.mavai.punit.verdict.TerminationReason;
import org.mavai.punit.api.ServiceContractAttributes;
import org.mavai.punit.verdict.ProbabilisticTestVerdict;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.*;
import org.mavai.punit.verdict.PUnitVerdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("VerdictXmlReader")
class VerdictXmlReaderTest {

    private final VerdictXmlWriter writer = new VerdictXmlWriter();
    private final VerdictXmlReader reader = new VerdictXmlReader();

    @Nested
    @DisplayName("round-trip: minimal verdict")
    class MinimalRoundTrip {

        @Test
        @DisplayName("preserves timestamp")
        void preservesTimestamp() throws Exception {
            ProbabilisticTestVerdict original = minimalVerdict(true, PUnitVerdict.PASS);

            ProbabilisticTestVerdict result = roundTrip(original);

            assertThat(result.timestamp()).isEqualTo(Instant.parse("2026-03-11T14:30:00Z"));
        }

        @Test
        @DisplayName("preserves identity via verdict-XML identity mapping")
        void preservesIdentity() throws Exception {
            ProbabilisticTestVerdict original = minimalVerdict(true, PUnitVerdict.PASS);

            ProbabilisticTestVerdict result = roundTrip(original);

            // Class name without use-case-id maps to use-case-id in the verdict-XML standard
            assertThat(result.identity().serviceContractId()).contains("com.example.MyTest");
            assertThat(result.identity().methodName()).isEqualTo("shouldPass");
        }

        @Test
        @DisplayName("preserves execution summary")
        void preservesExecution() throws Exception {
            ProbabilisticTestVerdict original = minimalVerdict(true, PUnitVerdict.PASS);

            ProbabilisticTestVerdict result = roundTrip(original);

            ExecutionSummary exec = result.execution();
            assertThat(exec.plannedSamples()).isEqualTo(100);
            assertThat(exec.samplesExecuted()).isEqualTo(100);
            assertThat(exec.successes()).isEqualTo(95);
            assertThat(exec.failures()).isEqualTo(5);
            assertThat(exec.elapsedMs()).isEqualTo(150);
            assertThat(exec.intent()).isEqualTo(TestIntent.VERIFICATION);
            assertThat(exec.resolvedConfidence()).isEqualTo(0.95);
        }

        @Test
        @DisplayName("preserves verdict")
        void preservesVerdict() throws Exception {
            ProbabilisticTestVerdict original = minimalVerdict(true, PUnitVerdict.PASS);

            ProbabilisticTestVerdict result = roundTrip(original);

            assertThat(result.punitVerdict()).isEqualTo(PUnitVerdict.PASS);
            assertThat(result.verdictReason()).isEqualTo("0.9500 >= 0.9000");
        }

        @Test
        @DisplayName("preserves statistics")
        void preservesStatistics() throws Exception {
            ProbabilisticTestVerdict original = minimalVerdict(true, PUnitVerdict.PASS);

            ProbabilisticTestVerdict result = roundTrip(original);

            StatisticalAnalysis stats = result.statistics();
            assertThat(stats.confidenceLevel()).isEqualTo(0.95);
            assertThat(stats.standardError()).isEqualTo(0.0218);
            assertThat(stats.wilsonLower())
                    .isEqualTo(original.statistics().wilsonLower());
            assertThat(stats.testStatistic()).isPresent();
            assertThat(stats.pValue()).isPresent();
        }

        @Test
        @DisplayName("preserves correlation ID")
        void preservesCorrelationId() throws Exception {
            ProbabilisticTestVerdict original = minimalVerdict(true, PUnitVerdict.PASS);

            ProbabilisticTestVerdict result = roundTrip(original);

            assertThat(result.correlationId()).isEqualTo("v:test01");
        }

        @Test
        @DisplayName("optional fields absent when not provided")
        void optionalFieldsAbsent() throws Exception {
            ProbabilisticTestVerdict original = minimalVerdict(true, PUnitVerdict.PASS);

            ProbabilisticTestVerdict result = roundTrip(original);

            assertThat(result.functional()).isEmpty();
            assertThat(result.latency()).isEmpty();
            assertThat(result.pacing()).isEmpty();
            assertThat(result.environmentMetadata()).isEmpty();
        }
    }

    @Nested
    @DisplayName("round-trip: functional dimension")
    class FunctionalRoundTrip {

        @Test
        @DisplayName("preserves functional dimension")
        void preservesFunctional() throws Exception {
            ProbabilisticTestVerdict original = verdictWithFunctional();

            ProbabilisticTestVerdict result = roundTrip(original);

            assertThat(result.functional()).isPresent();
            FunctionalDimension func = result.functional().get();
            assertThat(func.successes()).isEqualTo(95);
            assertThat(func.failures()).isEqualTo(5);
            assertThat(func.passRate()).isEqualTo(0.95);
        }
    }

    @Nested
    @DisplayName("round-trip: latency dimension")
    class LatencyRoundTrip {

        @Test
        @DisplayName("preserves latency with evaluations")
        void preservesLatency() throws Exception {
            ProbabilisticTestVerdict original = verdictWithLatency();

            ProbabilisticTestVerdict result = roundTrip(original);

            assertThat(result.latency()).isPresent();
            LatencyDimension lat = result.latency().get();
            assertThat(lat.successfulSamples()).isEqualTo(90);
            assertThat(lat.skipped()).isFalse();
            assertThat(lat.p95Ms()).isEqualTo(420);
        }

        @Test
        @DisplayName("omits latency when skipped")
        void omitsSkippedLatency() throws Exception {
            ProbabilisticTestVerdict original = verdictWithSkippedLatency();

            ProbabilisticTestVerdict result = roundTrip(original);

            // Skipped latency is not emitted in the verdict-XML standard
            assertThat(result.latency()).isEmpty();
        }
    }

    @Nested
    @DisplayName("round-trip: baseline and covariates")
    class BaselineAndCovariateRoundTrip {

        @Test
        @DisplayName("preserves baseline")
        void preservesBaseline() throws Exception {
            ProbabilisticTestVerdict original = verdictWithBaseline();

            ProbabilisticTestVerdict result = roundTrip(original);

            assertThat(result.statistics().baseline()).isPresent();
            BaselineSummary b = result.statistics().baseline().get();
            assertThat(b.sourceFile()).isEqualTo("my-spec.yaml");
            assertThat(b.baselineSamples()).isEqualTo(1000);
            assertThat(b.baselineRate()).isEqualTo(0.94);
            assertThat(b.derivedThreshold()).isEqualTo(0.92);
        }

        @Test
        @DisplayName("preserves misaligned covariates")
        void preservesMisalignment() throws Exception {
            ProbabilisticTestVerdict original = verdictWithMisalignment();

            ProbabilisticTestVerdict result = roundTrip(original);

            assertThat(result.covariates().aligned()).isFalse();
            assertThat(result.covariates().misalignments()).hasSize(1);
            Misalignment m = result.covariates().misalignments().get(0);
            assertThat(m.covariateKey()).isEqualTo("model");
            assertThat(m.baselineValue()).isEqualTo("gpt-4");
            assertThat(m.testValue()).isEqualTo("gpt-4o");
        }
    }

    @Nested
    @DisplayName("round-trip: provenance")
    class ProvenanceRoundTrip {

        @Test
        @DisplayName("preserves provenance origin and contract ref")
        void preservesProvenance() throws Exception {
            ProbabilisticTestVerdict original = verdictWithProvenance();

            ProbabilisticTestVerdict result = roundTrip(original);

            assertThat(result.provenance()).isPresent();
            SpecProvenance prov = result.provenance().get();
            assertThat(prov.thresholdOriginName()).isEqualTo("SLA");
            assertThat(prov.contractRef()).isEqualTo("SLA-PAY-001");
        }

        @Test
        @DisplayName("preserves expiration within provenance")
        void preservesExpiration() throws Exception {
            ProbabilisticTestVerdict original = verdictWithProvenance();

            ProbabilisticTestVerdict result = roundTrip(original);

            assertThat(result.provenance()).isPresent();
            assertThat(result.provenance().get().expiration()).isPresent();
            ExpirationInfo exp = result.provenance().get().expiration().get();
            assertThat(exp.status().requiresWarning()).isTrue();
            assertThat(exp.expiresAt()).contains(Instant.parse("2026-04-01T00:00:00Z"));
        }
    }

    @Nested
    @DisplayName("round-trip: pacing")
    class PacingRoundTrip {

        @Test
        @DisplayName("preserves pacing configuration")
        void preservesPacing() throws Exception {
            ProbabilisticTestVerdict original = fullVerdict();

            ProbabilisticTestVerdict result = roundTrip(original);

            assertThat(result.pacing()).isPresent();
            PacingSummary p = result.pacing().get();
            assertThat(p.maxRequestsPerSecond()).isEqualTo(10.0);
            assertThat(p.maxRequestsPerMinute()).isEqualTo(600.0);
            assertThat(p.maxConcurrentRequests()).isEqualTo(4);
            assertThat(p.effectiveMinDelayMs()).isEqualTo(100);
            assertThat(p.effectiveConcurrency()).isEqualTo(4);
            assertThat(p.effectiveRps()).isEqualTo(10.0);
        }
    }

    @Nested
    @DisplayName("round-trip: environment")
    class EnvironmentRoundTrip {

        @Test
        @DisplayName("preserves environment metadata")
        void preservesEnvironment() throws Exception {
            ProbabilisticTestVerdict original = fullVerdict();

            ProbabilisticTestVerdict result = roundTrip(original);

            assertThat(result.environmentMetadata()).hasSize(1);
            assertThat(result.environmentMetadata()).containsEntry("environment", "staging");
        }
    }

    @Nested
    @DisplayName("round-trip: termination")
    class TerminationRoundTrip {

        @Test
        @DisplayName("preserves budget exhaustion termination")
        void preservesBudgetExhaustion() throws Exception {
            ProbabilisticTestVerdict original = verdictWithBudgetExhaustion();

            ProbabilisticTestVerdict result = roundTrip(original);

            assertThat(result.termination().reason()).isEqualTo(TerminationReason.METHOD_TIME_BUDGET_EXHAUSTED);
            assertThat(result.termination().details()).contains("Time budget exceeded");
        }
    }

    @Nested
    @DisplayName("round-trip: service contract ID")
    class ServiceContractIdRoundTrip {

        @Test
        @DisplayName("preserves service contract ID when present")
        void preservesServiceContractId() throws Exception {
            ProbabilisticTestVerdict original = verdictWithServiceContractId();

            ProbabilisticTestVerdict result = roundTrip(original);

            assertThat(result.identity().serviceContractId()).contains("payment-gateway");
        }
    }

    @Nested
    @DisplayName("round-trip: full verdict")
    class FullRoundTrip {

        @Test
        @DisplayName("round-trips a full verdict preserving verdict-XML fields")
        void roundTripsFullVerdict() throws Exception {
            ProbabilisticTestVerdict original = fullVerdict();

            ProbabilisticTestVerdict result = roundTrip(original);

            assertThat(result.identity().serviceContractId()).contains("payment-gateway");
            assertThat(result.functional()).isPresent();
            assertThat(result.latency()).isPresent();
            assertThat(result.provenance()).isPresent();
            assertThat(result.statistics().baseline()).isPresent();
            assertThat(result.punitVerdict()).isEqualTo(PUnitVerdict.PASS);
        }
    }

    @Nested
    @DisplayName("required attributes")
    class RequiredAttributes {

        @Test
        @DisplayName("rejects document missing wilson-lower with a clear diagnostic")
        void rejectsMissingWilsonLower() throws Exception {
            // Write a normal verdict and then strip the wilson-lower
            // attribute from <statistics>. The reader must refuse it with
            // a message that names the missing attribute.
            ProbabilisticTestVerdict original = minimalVerdict(true, PUnitVerdict.PASS);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writer.write(original, baos);
            String xml = baos.toString(StandardCharsets.UTF_8);
            String stripped = xml.replaceAll("\\s+wilson-lower=\"[^\"]*\"", "");

            assertThatThrownBy(() -> reader.read(
                    new ByteArrayInputStream(stripped.getBytes(StandardCharsets.UTF_8))))
                    .isInstanceOf(XmlReadException.class)
                    .hasRootCauseInstanceOf(IllegalArgumentException.class)
                    .getRootCause()
                    .hasMessageContaining("wilson-lower");
        }
    }

    @Nested
    @DisplayName("per-criterion 1.2 surface round-trip")
    class PerCriterionRoundTrip {

        @Test
        @DisplayName("K=1 per-criterion bundle round-trips with preserved row, composite")
        void k1RoundTrip() throws Exception {
            org.mavai.punit.verdict.PerCriterionStructure pc =
                    new org.mavai.punit.verdict.PerCriterionStructure(
                            List.of(new org.mavai.punit.verdict.CriterionRow(
                                    "only",
                                    org.mavai.punit.api.spec.Verdict.PASS,
                                    100, 0, 0, 1.0, 0.85)),
                            org.mavai.punit.api.spec.Verdict.PASS);
            ProbabilisticTestVerdict original = verdictWithPerCriterion(pc, PUnitVerdict.PASS);

            ProbabilisticTestVerdict result = roundTrip(original);

            assertThat(result.perCriterion()).isPresent();
            assertThat(result.perCriterion().get().criteria()).hasSize(1);
            assertThat(result.perCriterion().get().criteria().get(0).criterionId())
                    .isEqualTo("only");
            assertThat(result.perCriterion().get().composite())
                    .isEqualTo(org.mavai.punit.api.spec.Verdict.PASS);
        }

        @Test
        @DisplayName("K>1 hiding result: rows in order, composite FAIL")
        void k2RoundTrip() throws Exception {
            org.mavai.punit.verdict.PerCriterionStructure pc =
                    new org.mavai.punit.verdict.PerCriterionStructure(
                            List.of(
                                    new org.mavai.punit.verdict.CriterionRow(
                                            "good",
                                            org.mavai.punit.api.spec.Verdict.PASS,
                                            100, 0, 0, 1.0, 0.85),
                                    new org.mavai.punit.verdict.CriterionRow(
                                            "bad",
                                            org.mavai.punit.api.spec.Verdict.FAIL,
                                            60, 40, 0, 0.60, 0.85)),
                            org.mavai.punit.api.spec.Verdict.FAIL);
            ProbabilisticTestVerdict original = verdictWithPerCriterion(pc, PUnitVerdict.FAIL);

            ProbabilisticTestVerdict result = roundTrip(original);

            assertThat(result.perCriterion()).isPresent();
            var criteria = result.perCriterion().get().criteria();
            assertThat(criteria).hasSize(2);
            assertThat(criteria.get(0).criterionId()).isEqualTo("good");
            assertThat(criteria.get(1).criterionId()).isEqualTo("bad");
            assertThat(criteria.get(1).verdict())
                    .isEqualTo(org.mavai.punit.api.spec.Verdict.FAIL);
            assertThat(criteria.get(1).observedRate()).isEqualTo(0.60);
            assertThat(criteria.get(1).threshold()).isEqualTo(0.85);
            assertThat(result.perCriterion().get().composite())
                    .isEqualTo(org.mavai.punit.api.spec.Verdict.FAIL);
        }

        @Test
        @DisplayName("NaN observed-rate / threshold round-trip as NaN")
        void nanRoundTrip() throws Exception {
            org.mavai.punit.verdict.PerCriterionStructure pc =
                    new org.mavai.punit.verdict.PerCriterionStructure(
                            List.of(new org.mavai.punit.verdict.CriterionRow(
                                    "empty",
                                    org.mavai.punit.api.spec.Verdict.INCONCLUSIVE,
                                    0, 0, 0,
                                    Double.NaN, Double.NaN)),
                            org.mavai.punit.api.spec.Verdict.INCONCLUSIVE);
            ProbabilisticTestVerdict original = verdictWithPerCriterion(pc, PUnitVerdict.INCONCLUSIVE);

            ProbabilisticTestVerdict result = roundTrip(original);

            var row = result.perCriterion().get().criteria().get(0);
            assertThat(Double.isNaN(row.observedRate())).isTrue();
            assertThat(Double.isNaN(row.threshold())).isTrue();
        }

        @Test
        @DisplayName("absent per-criterion round-trips as empty Optional")
        void absentRoundTrip() throws Exception {
            ProbabilisticTestVerdict original = minimalVerdict(true, PUnitVerdict.PASS);

            ProbabilisticTestVerdict result = roundTrip(original);

            assertThat(result.perCriterion()).isEmpty();
        }

        @Test
        @DisplayName("stray <legacy-aggregate> from a 1.1 emitter is ignored at parse")
        void legacyAggregateIgnored() throws Exception {
            // Hand-rolled 1.1-shaped XML carrying a <legacy-aggregate>
            // child. The permissive reader parses the <per-criterion>
            // bundle without surprise; the legacy element is silently
            // discarded.
            String xml = """
                    <verdict-record xmlns="http://javai.org/verdict/1.0"
                                    version="1.1"
                                    timestamp="2026-03-11T14:30:00Z"
                                    generator="test">
                      <identity use-case-id="com.example.Test" test-name="t"/>
                      <execution planned-samples="1" samples-executed="1"
                                 successes="1" failures="0" elapsed-ms="0"
                                 intent="VERIFICATION" confidence="0.95"/>
                      <statistics confidence-level="0.95" standard-error="0"
                                  wilson-lower="0" threshold="0.5"
                                  threshold-origin="SLA"/>
                      <covariates aligned="true"/>
                      <termination reason="COMPLETED"/>
                      <per-criterion>
                        <criterion id="only" verdict="FAIL"
                                   pass="60" fail="40" inconclusive="0" total="100"
                                   observed-rate="0.60" threshold="0.85"/>
                        <composite value="FAIL"/>
                        <legacy-aggregate value="PASS"/>
                      </per-criterion>
                      <verdict value="FAIL"/>
                    </verdict-record>
                    """;
            ProbabilisticTestVerdict result = reader.read(
                    new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            assertThat(result.perCriterion()).isPresent();
            assertThat(result.perCriterion().get().composite())
                    .isEqualTo(org.mavai.punit.api.spec.Verdict.FAIL);
            // The <legacy-aggregate> element exists no more — no field
            // on the parsed verdict record surfaces it.
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private ProbabilisticTestVerdict verdictWithPerCriterion(
            org.mavai.punit.verdict.PerCriterionStructure perCriterion,
            PUnitVerdict punitVerdict) {
        ProbabilisticTestVerdict base = minimalVerdict(
                punitVerdict != PUnitVerdict.FAIL, punitVerdict);
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(),
                base.identity(), base.execution(),
                base.functional(), base.latency(), base.statistics(),
                base.covariates(), base.cost(), base.pacing(),
                base.provenance(), base.termination(),
                base.environmentMetadata(), base.junitPassed(),
                base.punitVerdict(), base.verdictReason(),
                base.postconditionFailures(),
                Optional.of(perCriterion));
    }

    private ProbabilisticTestVerdict roundTrip(ProbabilisticTestVerdict verdict) throws XMLStreamException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writer.write(verdict, baos);
        return reader.read(new ByteArrayInputStream(baos.toByteArray()));
    }

    private ProbabilisticTestVerdict minimalVerdict(boolean passed, PUnitVerdict punitVerdict) {
        return new ProbabilisticTestVerdict(
                "v:test01",
                Instant.parse("2026-03-11T14:30:00Z"),
                new TestIdentity("com.example.MyTest", "shouldPass", Optional.empty()),
                new ExecutionSummary(100, 100, 95, 5, 0.9, 0.95, 150,
                        Optional.empty(), TestIntent.VERIFICATION, 0.95, ServiceContractAttributes.DEFAULT),
                Optional.empty(),
                Optional.empty(),
                new StatisticalAnalysis(0.95, 0.0218, 0.8948,
                        Optional.of(2.29), Optional.of(0.011),
                        Optional.empty(), Optional.empty(), List.of()),
                CovariateStatus.allAligned(),
                new CostSummary(0, 0, 0, TokenMode.NONE, Optional.empty(), Optional.empty()),
                Optional.empty(),
                Optional.empty(),
                new Termination(TerminationReason.COMPLETED, Optional.empty()),
                Map.of(),
                passed,
                punitVerdict,
                punitVerdict == PUnitVerdict.PASS ? "0.9500 >= 0.9000"
                        : punitVerdict == PUnitVerdict.INCONCLUSIVE ? "covariate misalignment"
                        : "0.8000 < 0.9000"
        );
    }

    private ProbabilisticTestVerdict verdictWithFunctional() {
        ProbabilisticTestVerdict base = minimalVerdict(true, PUnitVerdict.PASS);
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                Optional.of(new FunctionalDimension(95, 5, 0.95)),
                base.latency(), base.statistics(), base.covariates(), base.cost(),
                base.pacing(), base.provenance(), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict(),
                base.verdictReason()
        );
    }

    private ProbabilisticTestVerdict verdictWithLatency() {
        ProbabilisticTestVerdict base = minimalVerdict(true, PUnitVerdict.PASS);
        LatencyDimension latency = new LatencyDimension(
                90, 100, false, Optional.empty(),
                120, 340, 420, 810, 1250,
                List.of("Small sample")
        );
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                base.functional(), Optional.of(latency),
                base.statistics(), base.covariates(), base.cost(),
                base.pacing(), base.provenance(), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict(),
                base.verdictReason()
        );
    }

    private ProbabilisticTestVerdict verdictWithSkippedLatency() {
        ProbabilisticTestVerdict base = minimalVerdict(true, PUnitVerdict.PASS);
        LatencyDimension latency = new LatencyDimension(
                0, 100, true, Optional.of("No successes"),
                0, 0, 0, 0, 0,
                List.of()
        );
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                base.functional(), Optional.of(latency),
                base.statistics(), base.covariates(), base.cost(),
                base.pacing(), base.provenance(), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict(),
                base.verdictReason()
        );
    }

    private ProbabilisticTestVerdict verdictWithBaseline() {
        ProbabilisticTestVerdict base = minimalVerdict(true, PUnitVerdict.PASS);
        StatisticalAnalysis stats = new StatisticalAnalysis(
                0.95, 0.0218, 0.8948,
                Optional.of(2.29), Optional.of(0.011),
                Optional.of("Wilson score lower bound"),
                Optional.of(new BaselineSummary(
                        "my-spec.yaml", Instant.parse("2026-02-15T00:00:00Z"),
                        1000, 940, 0.94, 0.92)),
                List.of()
        );
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                base.functional(), base.latency(),
                stats, base.covariates(), base.cost(),
                base.pacing(), base.provenance(), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict(),
                base.verdictReason()
        );
    }

    private ProbabilisticTestVerdict verdictWithMisalignment() {
        ProbabilisticTestVerdict base = minimalVerdict(false, PUnitVerdict.INCONCLUSIVE);
        CovariateStatus cov = new CovariateStatus(false,
                List.of(new Misalignment("model", "gpt-4", "gpt-4o")),
                Map.of(), Map.of());
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                base.functional(), base.latency(),
                base.statistics(), cov, base.cost(),
                base.pacing(), base.provenance(), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict(),
                base.verdictReason()
        );
    }

    private ProbabilisticTestVerdict verdictWithProvenance() {
        ProbabilisticTestVerdict base = minimalVerdict(true, PUnitVerdict.PASS);
        ExpirationStatus expiringStatus = ExpirationStatus.expiringSoon(
                java.time.Duration.ofDays(7), 0.20);
        SpecProvenance prov = new SpecProvenance("SLA", "SLA-PAY-001", "payment-gateway.yaml",
                Optional.of(new ExpirationInfo(expiringStatus,
                        Optional.of(Instant.parse("2026-04-01T00:00:00Z")))));
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                base.functional(), base.latency(),
                base.statistics(), base.covariates(), base.cost(),
                base.pacing(), Optional.of(prov), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict(),
                base.verdictReason()
        );
    }

    private ProbabilisticTestVerdict verdictWithBudgetExhaustion() {
        ProbabilisticTestVerdict base = minimalVerdict(false, PUnitVerdict.FAIL);
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                base.functional(), base.latency(),
                base.statistics(), base.covariates(), base.cost(),
                base.pacing(), base.provenance(),
                new Termination(TerminationReason.METHOD_TIME_BUDGET_EXHAUSTED,
                        Optional.of("Time budget exceeded")),
                base.environmentMetadata(), false, PUnitVerdict.FAIL,
                "budget exhausted"
        );
    }

    private ProbabilisticTestVerdict verdictWithServiceContractId() {
        ProbabilisticTestVerdict base = minimalVerdict(true, PUnitVerdict.PASS);
        TestIdentity identity = new TestIdentity(
                "com.example.MyTest", "shouldPass", Optional.of("payment-gateway"));
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), identity, base.execution(),
                base.functional(), base.latency(),
                base.statistics(), base.covariates(), base.cost(),
                base.pacing(), base.provenance(), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict(),
                base.verdictReason()
        );
    }

    private ProbabilisticTestVerdict fullVerdict() {
        ProbabilisticTestVerdict base = verdictWithLatency();
        TestIdentity identity = new TestIdentity(
                "com.example.PaymentTest", "shouldMeetSla", Optional.of("payment-gateway"));
        ExpirationStatus expiringStatus = ExpirationStatus.valid(java.time.Duration.ofDays(30));
        SpecProvenance prov = new SpecProvenance("SLA", "SLA-PAY-001", "payment-gateway.yaml",
                Optional.of(new ExpirationInfo(expiringStatus, Optional.of(Instant.parse("2026-04-15T00:00:00Z")))));
        StatisticalAnalysis stats = new StatisticalAnalysis(
                0.95, 0.0218, 0.8948,
                Optional.of(2.29), Optional.of(0.011),
                Optional.of("Wilson score lower bound"),
                Optional.of(new BaselineSummary("payment-gateway.yaml",
                        Instant.parse("2026-02-15T00:00:00Z"), 1000, 940, 0.94, 0.92)),
                List.of("Covariate aligned")
        );
        PacingSummary pacing = new PacingSummary(10.0, 600.0, 36000.0, 4, 100, 4, 10.0);

        return new ProbabilisticTestVerdict(
                "v:full01", Instant.parse("2026-03-11T14:30:00Z"),
                identity, base.execution(),
                Optional.of(new FunctionalDimension(95, 5, 0.95)),
                base.latency(),
                stats, CovariateStatus.allAligned(),
                new CostSummary(500, 30000, 10000, TokenMode.DYNAMIC,
                        Optional.of(new BudgetSnapshot(60000, 15000, 50000, 2000)),
                        Optional.empty()),
                Optional.of(pacing), Optional.of(prov),
                new Termination(TerminationReason.COMPLETED, Optional.empty()),
                Map.of("environment", "staging"),
                true, PUnitVerdict.PASS,
                "0.9500 >= 0.9000"
        );
    }
}
