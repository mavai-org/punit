package org.mavai.punit.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import java.util.LinkedHashMap;

import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.spec.FailureCount;
import org.mavai.punit.api.spec.FailureExemplar;
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@DisplayName("VerdictXmlWriter")
class VerdictXmlWriterTest {

    private final VerdictXmlWriter writer = new VerdictXmlWriter();

    @Nested
    @DisplayName("minimal verdict")
    class MinimalVerdict {

        @Test
        @DisplayName("serialises to verdict-record root element")
        void rp07RootElement() throws Exception {
            ProbabilisticTestVerdict verdict = minimalVerdict(true, PUnitVerdict.PASS);

            Document doc = writeAndParse(verdict);
            Element root = doc.getDocumentElement();

            assertThat(root.getLocalName()).isEqualTo("verdict-record");
            assertThat(root.getAttribute("version")).isEqualTo("1.0");
            assertThat(root.hasAttribute("generator")).isTrue();
            assertThat(root.getAttribute("generator")).startsWith("punit");
        }

        @Test
        @DisplayName("includes identity with use-case-id and test-name")
        void includesIdentity() throws Exception {
            ProbabilisticTestVerdict verdict = minimalVerdict(true, PUnitVerdict.PASS);

            Document doc = writeAndParse(verdict);
            Element identity = firstElement(doc, "identity");

            assertThat(identity.getAttribute("use-case-id")).isEqualTo("com.example.MyTest");
            assertThat(identity.getAttribute("test-name")).isEqualTo("shouldPass");
        }

        @Test
        @DisplayName("includes execution summary attributes")
        void includesExecution() throws Exception {
            ProbabilisticTestVerdict verdict = minimalVerdict(true, PUnitVerdict.PASS);

            Document doc = writeAndParse(verdict);
            Element exec = firstElement(doc, "execution");

            assertThat(exec.getAttribute("planned-samples")).isEqualTo("100");
            assertThat(exec.getAttribute("successes")).isEqualTo("95");
            assertThat(exec.getAttribute("intent")).isEqualTo("VERIFICATION");
            assertThat(exec.hasAttribute("min-pass-rate")).isFalse();
            assertThat(exec.hasAttribute("observed-pass-rate")).isFalse();
        }

        @Test
        @DisplayName("includes verdict element with value and reason")
        void includesVerdictElement() throws Exception {
            ProbabilisticTestVerdict verdict = minimalVerdict(true, PUnitVerdict.PASS);

            Document doc = writeAndParse(verdict);
            Element v = firstElement(doc, "verdict");

            assertThat(v.getAttribute("value")).isEqualTo("PASS");
            assertThat(v.hasAttribute("junit-passed")).isFalse();
            assertThat(v.hasAttribute("punit-verdict")).isFalse();
        }

        @Test
        @DisplayName("omits functional element when absent")
        void omitsFunctionalWhenAbsent() throws Exception {
            ProbabilisticTestVerdict verdict = minimalVerdict(true, PUnitVerdict.PASS);

            Document doc = writeAndParse(verdict);
            NodeList nodes = doc.getElementsByTagNameNS(VerdictXmlWriter.NAMESPACE, "functional");

            assertThat(nodes.getLength()).isZero();
        }

        @Test
        @DisplayName("omits latency element when absent")
        void omitsLatencyWhenAbsent() throws Exception {
            ProbabilisticTestVerdict verdict = minimalVerdict(true, PUnitVerdict.PASS);

            Document doc = writeAndParse(verdict);
            NodeList nodes = doc.getElementsByTagNameNS(VerdictXmlWriter.NAMESPACE, "latency");

            assertThat(nodes.getLength()).isZero();
        }

        @Test
        @DisplayName("omits pacing and environment when absent")
        void omitsPacingAndEnvironmentWhenAbsent() throws Exception {
            ProbabilisticTestVerdict verdict = minimalVerdict(true, PUnitVerdict.PASS);

            Document doc = writeAndParse(verdict);

            assertThat(doc.getElementsByTagNameNS(VerdictXmlWriter.NAMESPACE, "pacing").getLength()).isZero();
            assertThat(doc.getElementsByTagNameNS(VerdictXmlWriter.NAMESPACE, "environment").getLength()).isZero();
        }

        @Test
        @DisplayName("includes correlation-id attribute")
        void includesCorrelationId() throws Exception {
            ProbabilisticTestVerdict verdict = minimalVerdict(true, PUnitVerdict.PASS);

            Document doc = writeAndParse(verdict);
            Element root = doc.getDocumentElement();

            assertThat(root.getAttribute("correlation-id")).isEqualTo("v:test01");
        }
    }

    @Nested
    @DisplayName("functional dimension")
    class FunctionalDimensionTests {

        @Test
        @DisplayName("serialises functional dimension when present")
        void serialisesFunctionalDimension() throws Exception {
            ProbabilisticTestVerdict verdict = verdictWithFunctional();

            Document doc = writeAndParse(verdict);
            Element func = firstElement(doc, "functional");

            assertThat(func.getAttribute("successes")).isEqualTo("95");
            assertThat(func.getAttribute("failures")).isEqualTo("5");
            assertThat(func.getAttribute("pass-rate")).isEqualTo("0.9500");
        }
    }

    @Nested
    @DisplayName("latency dimension")
    class LatencyDimensionTests {

        @Test
        @DisplayName("serialises latency with observed percentiles")
        void serialisesLatency() throws Exception {
            ProbabilisticTestVerdict verdict = verdictWithLatency();

            Document doc = writeAndParse(verdict);
            Element lat = firstElement(doc, "latency");

            assertThat(lat.getAttribute("successful-samples")).isEqualTo("90");
            // strict-violations / advisory-violations are emitted as
            // zero — the dimension at the verdict layer is descriptive
            // only; gating happens at the criterion layer.
            assertThat(lat.getAttribute("strict-violations")).isEqualTo("0");
            assertThat(lat.getAttribute("advisory-violations")).isEqualTo("0");

            // Observed percentiles
            NodeList observed = doc.getElementsByTagNameNS(VerdictXmlWriter.NAMESPACE, "percentile");
            assertThat(observed.getLength()).isGreaterThanOrEqualTo(4);

            // No <evaluations> block: punit's emission does not carry
            // per-percentile assertion data — the schema permits it
            // for future framework implementations but punit produces
            // descriptive-only output.
            NodeList evals = doc.getElementsByTagNameNS(VerdictXmlWriter.NAMESPACE, "evaluations");
            assertThat(evals.getLength()).isZero();
        }

        @Test
        @DisplayName("omits latency element when skipped")
        void omitsLatencyWhenSkipped() throws Exception {
            ProbabilisticTestVerdict verdict = verdictWithSkippedLatency();

            Document doc = writeAndParse(verdict);
            NodeList nodes = doc.getElementsByTagNameNS(VerdictXmlWriter.NAMESPACE, "latency");

            assertThat(nodes.getLength()).isZero();
        }
    }

    @Nested
    @DisplayName("statistics")
    class StatisticsTests {

        @Test
        @DisplayName("serialises statistical analysis with threshold and origin")
        void serialisesStatistics() throws Exception {
            ProbabilisticTestVerdict verdict = minimalVerdict(true, PUnitVerdict.PASS);

            Document doc = writeAndParse(verdict);
            Element stats = firstElement(doc, "statistics");

            assertThat(stats.getAttribute("confidence-level")).isEqualTo("0.95");
            assertThat(stats.getAttribute("standard-error")).isNotEmpty();
            assertThat(stats.hasAttribute("threshold")).isTrue();
            assertThat(stats.hasAttribute("threshold-origin")).isTrue();
        }

        @Test
        @DisplayName("emits one-sided wilson-lower; never emits ci-upper")
        void emitsWilsonLowerNotCiUpper() throws Exception {
            ProbabilisticTestVerdict verdict = minimalVerdict(true, PUnitVerdict.PASS);

            Document doc = writeAndParse(verdict);
            Element stats = firstElement(doc, "statistics");

            assertThat(stats.hasAttribute("wilson-lower")).isTrue();
            assertThat(stats.getAttribute("wilson-lower"))
                    .isEqualTo(String.valueOf(verdict.statistics().wilsonLower()));
            assertThat(stats.hasAttribute("ci-upper")).isFalse();
            assertThat(stats.hasAttribute("ci-lower")).isFalse();
        }

        @Test
        @DisplayName("serialises baseline as sibling of statistics")
        void serialisesBaseline() throws Exception {
            ProbabilisticTestVerdict verdict = verdictWithBaseline();

            Document doc = writeAndParse(verdict);

            // Baseline should be a direct child of verdict-record, not nested in statistics
            Element root = doc.getDocumentElement();
            NodeList baselines = root.getElementsByTagNameNS(VerdictXmlWriter.NAMESPACE, "baseline");
            assertThat(baselines.getLength()).isEqualTo(1);
            Element baseline = (Element) baselines.item(0);
            assertThat(baseline.getParentNode().getLocalName()).isEqualTo("verdict-record");
            assertThat(baseline.getAttribute("source-file")).isEqualTo("my-spec.yaml");
            assertThat(baseline.getAttribute("samples")).isEqualTo("1000");
        }
    }

    @Nested
    @DisplayName("covariates")
    class CovariateTests {

        @Test
        @DisplayName("serialises aligned covariates")
        void serialisesAligned() throws Exception {
            ProbabilisticTestVerdict verdict = minimalVerdict(true, PUnitVerdict.PASS);

            Document doc = writeAndParse(verdict);
            Element cov = firstElement(doc, "covariates");

            assertThat(cov.getAttribute("aligned")).isEqualTo("true");
        }

        @Test
        @DisplayName("serialises misaligned covariates with observed-value attribute")
        void serialisesMisaligned() throws Exception {
            ProbabilisticTestVerdict verdict = verdictWithMisalignment();

            Document doc = writeAndParse(verdict);
            Element cov = firstElement(doc, "covariates");

            assertThat(cov.getAttribute("aligned")).isEqualTo("false");

            NodeList misalignments = doc.getElementsByTagNameNS(VerdictXmlWriter.NAMESPACE, "misalignment");
            assertThat(misalignments.getLength()).isEqualTo(1);
            Element m = (Element) misalignments.item(0);
            assertThat(m.getAttribute("key")).isEqualTo("model");
            assertThat(m.getAttribute("baseline-value")).isEqualTo("gpt-4");
            assertThat(m.getAttribute("observed-value")).isEqualTo("gpt-4o");
        }
    }

    @Nested
    @DisplayName("provenance")
    class ProvenanceTests {

        @Test
        @DisplayName("serialises provenance with expiration")
        void serialisesProvenance() throws Exception {
            ProbabilisticTestVerdict verdict = verdictWithProvenance();

            Document doc = writeAndParse(verdict);
            Element prov = firstElement(doc, "provenance");

            assertThat(prov.getAttribute("origin")).isEqualTo("SLA");
            assertThat(prov.getAttribute("contract-ref")).isEqualTo("SLA-PAY-001");

            Element exp = firstElement(doc, "expiration");
            assertThat(exp.getAttribute("status")).isEqualTo("EXPIRING_SOON");
            assertThat(exp.getAttribute("requires-warning")).isEqualTo("true");
        }
    }

    @Nested
    @DisplayName("pacing")
    class PacingTests {

        @Test
        @DisplayName("serialises pacing when present")
        void serialisesPacing() throws Exception {
            ProbabilisticTestVerdict verdict = fullVerdict();

            Document doc = writeAndParse(verdict);
            Element pacing = firstElement(doc, "pacing");

            assertThat(pacing.getAttribute("max-rps")).isEqualTo("10");
            assertThat(pacing.getAttribute("max-concurrent")).isEqualTo("4");
            assertThat(pacing.getAttribute("effective-rps")).isEqualTo("10");
        }
    }

    @Nested
    @DisplayName("environment")
    class EnvironmentTests {

        @Test
        @DisplayName("serialises environment metadata entries")
        void serialisesEnvironment() throws Exception {
            ProbabilisticTestVerdict verdict = fullVerdict();

            Document doc = writeAndParse(verdict);
            NodeList entries = doc.getElementsByTagNameNS(VerdictXmlWriter.NAMESPACE, "entry");

            assertThat(entries.getLength()).isEqualTo(1);
        }

        @Test
        @DisplayName("omits environment element when metadata is empty")
        void omitsWhenEmpty() throws Exception {
            ProbabilisticTestVerdict verdict = minimalVerdict(true, PUnitVerdict.PASS);

            Document doc = writeAndParse(verdict);
            NodeList nodes = doc.getElementsByTagNameNS(VerdictXmlWriter.NAMESPACE, "environment");

            assertThat(nodes.getLength()).isZero();
        }
    }

    @Nested
    @DisplayName("termination")
    class TerminationTests {

        @Test
        @DisplayName("serialises completed termination")
        void serialisesCompleted() throws Exception {
            ProbabilisticTestVerdict verdict = minimalVerdict(true, PUnitVerdict.PASS);

            Document doc = writeAndParse(verdict);
            Element term = firstElement(doc, "termination");

            assertThat(term.getAttribute("reason")).isEqualTo("COMPLETED");
            assertThat(term.hasAttribute("detail")).isFalse();
        }

        @Test
        @DisplayName("maps budget exhaustion to standard reason")
        void serialisesBudgetExhaustion() throws Exception {
            ProbabilisticTestVerdict verdict = verdictWithBudgetExhaustion();

            Document doc = writeAndParse(verdict);
            Element term = firstElement(doc, "termination");

            assertThat(term.getAttribute("reason")).isEqualTo("TIME_BUDGET_EXHAUSTED");
            assertThat(term.getAttribute("detail")).isEqualTo("Time budget exceeded");
        }
    }

    @Nested
    @DisplayName("cost")
    class CostTests {

        @Test
        @DisplayName("serialises cost in XML format")
        void serialisesCost() throws Exception {
            ProbabilisticTestVerdict verdict = minimalVerdict(true, PUnitVerdict.PASS);

            Document doc = writeAndParse(verdict);
            Element cost = firstElement(doc, "cost");

            assertThat(cost.hasAttribute("total-time-ms")).isTrue();
            assertThat(cost.hasAttribute("total-tokens")).isTrue();
            assertThat(cost.hasAttribute("avg-time-per-sample-ms")).isTrue();
            assertThat(cost.hasAttribute("avg-tokens-per-sample")).isTrue();
            // PUnit-specific budget attributes should be absent
            assertThat(cost.hasAttribute("method-tokens")).isFalse();
            assertThat(cost.hasAttribute("token-mode")).isFalse();
        }
    }

    @Nested
    @DisplayName("warnings")
    class WarningsTests {

        @Test
        @DisplayName("serialises caveats as warnings")
        void serialisesCaveats() throws Exception {
            ProbabilisticTestVerdict verdict = verdictWithCaveats();

            Document doc = writeAndParse(verdict);
            NodeList warnings = doc.getElementsByTagNameNS(VerdictXmlWriter.NAMESPACE, "warning");

            assertThat(warnings.getLength()).isEqualTo(1);
            Element w = (Element) warnings.item(0);
            assertThat(w.getAttribute("code")).isEqualTo("CAVEAT");
            assertThat(w.getTextContent()).isEqualTo("Covariate aligned");
        }
    }

    @Nested
    @DisplayName("postcondition failures")
    class PostconditionFailures {

        @Test
        @DisplayName("emits clauses in declaration (insertion) order with retained exemplars")
        void emitsClausesInDeclarationOrder() throws Exception {
            LinkedHashMap<String, FailureCount> hist = new LinkedHashMap<>();
            hist.put("Response not empty", new FailureCount(2, List.of(
                    new FailureExemplar("instr-1", "blank"),
                    new FailureExemplar("instr-2", "blank"))));
            hist.put("Valid JSON", new FailureCount(8, List.of(
                    new FailureExemplar("instr-7", "missing actions"),
                    new FailureExemplar("instr-9", "unexpected token"),
                    new FailureExemplar("instr-12", "trailing comma"))));
            ProbabilisticTestVerdict verdict = verdictWithPostconditionFailures(hist);

            Document doc = writeAndParse(verdict);
            Element section = firstElement(doc, "postcondition-failures");
            NodeList clauses = section.getElementsByTagNameNS(VerdictXmlWriter.NAMESPACE, "clause");

            assertThat(clauses.getLength()).isEqualTo(2);
            // Declaration (insertion) order is preserved — not sorted by count
            assertThat(((Element) clauses.item(0)).getAttribute("description"))
                    .isEqualTo("Response not empty");
            assertThat(((Element) clauses.item(0)).getAttribute("count")).isEqualTo("2");
            assertThat(((Element) clauses.item(1)).getAttribute("description"))
                    .isEqualTo("Valid JSON");
            assertThat(((Element) clauses.item(1)).getAttribute("count")).isEqualTo("8");

            NodeList exemplars0 = ((Element) clauses.item(0))
                    .getElementsByTagNameNS(VerdictXmlWriter.NAMESPACE, "exemplar");
            assertThat(exemplars0.getLength()).isEqualTo(2);
            assertThat(((Element) exemplars0.item(0)).getAttribute("input")).isEqualTo("instr-1");
            assertThat(((Element) exemplars0.item(0)).getAttribute("reason")).isEqualTo("blank");

            NodeList exemplars1 = ((Element) clauses.item(1))
                    .getElementsByTagNameNS(VerdictXmlWriter.NAMESPACE, "exemplar");
            assertThat(exemplars1.getLength()).isEqualTo(3);
        }

        @Test
        @DisplayName("omits postcondition-failures element when histogram is empty")
        void omitsWhenEmpty() throws Exception {
            ProbabilisticTestVerdict verdict = minimalVerdict(true, PUnitVerdict.PASS);

            Document doc = writeAndParse(verdict);
            NodeList section = doc.getElementsByTagNameNS(
                    VerdictXmlWriter.NAMESPACE, "postcondition-failures");

            assertThat(section.getLength()).isZero();
        }

        @Test
        @DisplayName("escapes XML special characters in input and reason attributes")
        void escapesXmlSpecialChars() throws Exception {
            LinkedHashMap<String, FailureCount> hist = new LinkedHashMap<>();
            hist.put("clause with <chevrons> & \"quotes\"", new FailureCount(1, List.of(
                    new FailureExemplar("input <bad>", "reason has \" & < & >"))));
            ProbabilisticTestVerdict verdict = verdictWithPostconditionFailures(hist);

            // writeAndParse round-trips the XML; the parser will fail if
            // the writer didn't escape correctly.
            Document doc = writeAndParse(verdict);
            Element clause = (Element) doc.getElementsByTagNameNS(
                    VerdictXmlWriter.NAMESPACE, "clause").item(0);
            Element exemplar = (Element) clause.getElementsByTagNameNS(
                    VerdictXmlWriter.NAMESPACE, "exemplar").item(0);

            assertThat(clause.getAttribute("description"))
                    .isEqualTo("clause with <chevrons> & \"quotes\"");
            assertThat(exemplar.getAttribute("input")).isEqualTo("input <bad>");
            assertThat(exemplar.getAttribute("reason")).isEqualTo("reason has \" & < & >");
        }

        @Test
        @DisplayName("clause with zero exemplars renders count without children")
        void clauseWithZeroExemplars() throws Exception {
            LinkedHashMap<String, FailureCount> hist = new LinkedHashMap<>();
            hist.put("noisy clause", new FailureCount(42, List.of()));
            ProbabilisticTestVerdict verdict = verdictWithPostconditionFailures(hist);

            Document doc = writeAndParse(verdict);
            Element clause = (Element) doc.getElementsByTagNameNS(
                    VerdictXmlWriter.NAMESPACE, "clause").item(0);

            assertThat(clause.getAttribute("count")).isEqualTo("42");
            NodeList exemplars = clause.getElementsByTagNameNS(
                    VerdictXmlWriter.NAMESPACE, "exemplar");
            assertThat(exemplars.getLength()).isZero();
        }

        @Test
        @DisplayName("verdict with postcondition failures validates against verdict-1.0 XSD")
        void validatesAgainstXsd() throws Exception {
            LinkedHashMap<String, FailureCount> hist = new LinkedHashMap<>();
            hist.put("Response not empty", new FailureCount(3, List.of(
                    new FailureExemplar("a", "blank"))));
            ProbabilisticTestVerdict verdict = verdictWithPostconditionFailures(hist);

            String xml = writeToString(verdict);

            validateAgainstSchema(xml);
        }
    }

    @Nested
    @DisplayName("postcondition standings")
    class PostconditionStandingsElement {

        @Test
        @DisplayName("a verdict with standings steps to version 1.4 and validates against the XSD")
        void standingsValidateAgainstSchema14() throws Exception {
            String xml = writeToString(verdictWithStandings());
            assertThat(xml).contains("version=\"1.4\"");
            assertThat(xml).contains("<postcondition-standings>");
            assertThat(xml).contains("optional-slack=\"2\"");
            assertThat(xml).contains(
                    "input-index=\"1\" check=\"terms are the agreed set\" optional=\"true\" "
                            + "passed=\"12\" failed=\"6\" skipped=\"2\"");
            validateAgainstSchema14(xml);
        }

        @Test
        @DisplayName("the standings round-trip through punit's own reader")
        void standingsRoundTrip() throws Exception {
            ProbabilisticTestVerdict original = verdictWithStandings();
            String xml = writeToString(original);
            ProbabilisticTestVerdict read = new VerdictXmlReader()
                    .read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            assertThat(read.postconditionStandings()).isPresent();
            assertThat(read.postconditionStandings().get())
                    .isEqualTo(original.postconditionStandings().get());
        }

        @Test
        @DisplayName("the standings element is descriptive — no verdict vocabulary, no intervals")
        void standingsAreDescriptive() throws Exception {
            String xml = writeToString(verdictWithStandings());
            String element = xml.substring(
                    xml.indexOf("<postcondition-standings>"),
                    xml.indexOf("</postcondition-standings>"));
            assertThat(element).doesNotContain("threshold", "confidence", "bound", "verdict");
        }

        @Test
        @DisplayName("a verdict without standings stays at its prior version")
        void noStandingsNoVersionStep() throws Exception {
            String xml = writeToString(minimalVerdict(true, PUnitVerdict.PASS));
            assertThat(xml).doesNotContain("postcondition-standings");
            assertThat(xml).contains("version=\"1.0\"");
        }
    }

    @Nested
    @DisplayName("schema validation")
    class SchemaValidation {

        @Test
        @DisplayName("minimal verdict validates against verdict-1.0 XSD")
        void minimalVerdictValidatesAgainstXsd() throws Exception {
            ProbabilisticTestVerdict verdict = minimalVerdict(true, PUnitVerdict.PASS);

            String xml = writeToString(verdict);

            validateAgainstSchema(xml);
        }

        @Test
        @DisplayName("full verdict validates against verdict-1.0 XSD")
        void fullVerdictValidatesAgainstXsd() throws Exception {
            ProbabilisticTestVerdict verdict = fullVerdict();

            String xml = writeToString(verdict);

            validateAgainstSchema(xml);
        }
    }

    @Nested
    @DisplayName("per-criterion XML surface (1.2)")
    class PerCriterionXmlTests {

        @Test
        @DisplayName("K=1: emits single criterion row, composite matches verdict")
        void k1Emission() throws Exception {
            org.mavai.punit.verdict.PerCriterionStructure pc =
                    new org.mavai.punit.verdict.PerCriterionStructure(
                            List.of(new org.mavai.punit.verdict.CriterionRow(
                                    "only",
                                    org.mavai.punit.api.spec.Verdict.PASS,
                                    100, 0, 0,
                                    1.0, 0.85)),
                            org.mavai.punit.api.spec.Verdict.PASS);
            ProbabilisticTestVerdict verdict = verdictWithPerCriterion(
                    pc, PUnitVerdict.PASS);

            Document doc = writeAndParse(verdict);
            Element root = doc.getDocumentElement();
            Element perCriterion = firstElement(doc, "per-criterion");

            assertThat(root.getAttribute("version")).isEqualTo("1.2");
            NodeList rows = perCriterion.getElementsByTagNameNS(
                    VerdictXmlWriter.NAMESPACE, "criterion");
            assertThat(rows.getLength()).isEqualTo(1);
            Element row = (Element) rows.item(0);
            assertThat(row.getAttribute("id")).isEqualTo("only");
            assertThat(row.getAttribute("verdict")).isEqualTo("PASS");
            assertThat(row.getAttribute("pass")).isEqualTo("100");
            assertThat(row.getAttribute("total")).isEqualTo("100");
            assertThat(row.getAttribute("observed-rate")).isEqualTo("1");
            assertThat(row.getAttribute("threshold")).isEqualTo("0.85");

            Element composite = (Element) perCriterion.getElementsByTagNameNS(
                    VerdictXmlWriter.NAMESPACE, "composite").item(0);
            assertThat(composite.getAttribute("value")).isEqualTo("PASS");
        }

        @Test
        @DisplayName("K>1 hiding result: composite FAIL, criteria rows in declaration order")
        void k2Emission() throws Exception {
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
            ProbabilisticTestVerdict verdict = verdictWithPerCriterion(
                    pc, PUnitVerdict.FAIL);

            Document doc = writeAndParse(verdict);
            Element perCriterion = firstElement(doc, "per-criterion");
            NodeList rows = perCriterion.getElementsByTagNameNS(
                    VerdictXmlWriter.NAMESPACE, "criterion");

            assertThat(rows.getLength()).isEqualTo(2);
            assertThat(((Element) rows.item(0)).getAttribute("id")).isEqualTo("good");
            assertThat(((Element) rows.item(1)).getAttribute("id")).isEqualTo("bad");
            assertThat(((Element) rows.item(1)).getAttribute("verdict")).isEqualTo("FAIL");

            Element composite = (Element) perCriterion.getElementsByTagNameNS(
                    VerdictXmlWriter.NAMESPACE, "composite").item(0);
            assertThat(composite.getAttribute("value")).isEqualTo("FAIL");

            // <legacy-aggregate> was the 1.1 transitional element;
            // 1.2 emitters do not write it.
            assertThat(perCriterion.getElementsByTagNameNS(
                    VerdictXmlWriter.NAMESPACE, "legacy-aggregate").getLength())
                    .isZero();
        }

        @Test
        @DisplayName("NaN observed-rate / threshold: attributes omitted")
        void nanAttributesOmitted() throws Exception {
            org.mavai.punit.verdict.PerCriterionStructure pc =
                    new org.mavai.punit.verdict.PerCriterionStructure(
                            List.of(new org.mavai.punit.verdict.CriterionRow(
                                    "no-samples",
                                    org.mavai.punit.api.spec.Verdict.INCONCLUSIVE,
                                    0, 0, 0,
                                    Double.NaN, Double.NaN)),
                            org.mavai.punit.api.spec.Verdict.INCONCLUSIVE);
            ProbabilisticTestVerdict verdict = verdictWithPerCriterion(
                    pc, PUnitVerdict.INCONCLUSIVE);

            Document doc = writeAndParse(verdict);
            Element perCriterion = firstElement(doc, "per-criterion");
            Element row = (Element) perCriterion.getElementsByTagNameNS(
                    VerdictXmlWriter.NAMESPACE, "criterion").item(0);

            assertThat(row.hasAttribute("observed-rate")).isFalse();
            assertThat(row.hasAttribute("threshold")).isFalse();
            assertThat(row.getAttribute("total")).isEqualTo("0");
        }

        @Test
        @DisplayName("absent per-criterion: no <per-criterion> element, version stays 1.0")
        void absentPerCriterionStaysAt10() throws Exception {
            ProbabilisticTestVerdict verdict = minimalVerdict(true, PUnitVerdict.PASS);

            Document doc = writeAndParse(verdict);
            Element root = doc.getDocumentElement();

            assertThat(root.getAttribute("version")).isEqualTo("1.0");
            assertThat(root.getElementsByTagNameNS(
                    VerdictXmlWriter.NAMESPACE, "per-criterion").getLength())
                    .isZero();
        }

        @Test
        @DisplayName("emitter output validates against verdict-1.2 schema")
        void validatesAgainst12Schema() throws Exception {
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
            ProbabilisticTestVerdict verdict = verdictWithPerCriterion(
                    pc, PUnitVerdict.FAIL);

            String xml = writeToString(verdict);

            validateAgainstSchema12(xml);
        }

        @Test
        @DisplayName("1.0-only emission still validates against verdict-1.2 schema (additive)")
        void absentPerCriterionValidatesAgainst12Schema() throws Exception {
            ProbabilisticTestVerdict verdict = minimalVerdict(true, PUnitVerdict.PASS);

            String xml = writeToString(verdict);

            validateAgainstSchema12(xml);
        }
    }

    @Nested
    @DisplayName("service contract ID")
    class ServiceContractIdTests {

        @Test
        @DisplayName("uses use-case-id when present")
        void includesServiceContractId() throws Exception {
            ProbabilisticTestVerdict verdict = verdictWithServiceContractId();

            Document doc = writeAndParse(verdict);
            Element identity = firstElement(doc, "identity");

            assertThat(identity.getAttribute("use-case-id")).isEqualTo("payment-gateway");
        }

        @Test
        @DisplayName("falls back to class-name when use-case-id absent")
        void fallsBackToClassName() throws Exception {
            ProbabilisticTestVerdict verdict = minimalVerdict(true, PUnitVerdict.PASS);

            Document doc = writeAndParse(verdict);
            Element identity = firstElement(doc, "identity");

            assertThat(identity.getAttribute("use-case-id")).isEqualTo("com.example.MyTest");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private ProbabilisticTestVerdict minimalVerdict(boolean passed, PUnitVerdict punitVerdict) {
        String verdictReason = switch (punitVerdict) {
            case PASS -> "0.9500 >= 0.9000";
            case FAIL -> "0.9500 < 0.9000";
            case INCONCLUSIVE -> "covariate misalignment";
        };
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
                verdictReason
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

    private ProbabilisticTestVerdict verdictWithPostconditionFailures(
            Map<String, FailureCount> hist) {
        ProbabilisticTestVerdict base = minimalVerdict(true, PUnitVerdict.PASS);
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                base.functional(), base.latency(), base.statistics(), base.covariates(),
                base.cost(), base.pacing(), base.provenance(), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict(),
                base.verdictReason(),
                hist
        );
    }

    private ProbabilisticTestVerdict verdictWithCaveats() {
        ProbabilisticTestVerdict base = minimalVerdict(true, PUnitVerdict.PASS);
        StatisticalAnalysis stats = new StatisticalAnalysis(
                0.95, 0.0218, 0.8948,
                Optional.of(2.29), Optional.of(0.011),
                Optional.empty(), Optional.empty(),
                List.of("Covariate aligned")
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

    private Document writeAndParse(ProbabilisticTestVerdict verdict) throws Exception {
        String xml = writeToString(verdict);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private String writeToString(ProbabilisticTestVerdict verdict) throws XMLStreamException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writer.write(verdict, baos);
        return baos.toString(StandardCharsets.UTF_8);
    }

    private Element firstElement(Document doc, String localName) {
        NodeList nodes = doc.getElementsByTagNameNS(VerdictXmlWriter.NAMESPACE, localName);
        assertThat(nodes.getLength()).as("Expected element <%s> to exist", localName).isGreaterThan(0);
        return (Element) nodes.item(0);
    }

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

    private void validateAgainstSchema(String xml) throws Exception {
        SchemaFactory schemaFactory = SchemaFactory.newInstance(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
        try (InputStream xsdStream = getClass().getResourceAsStream("/org/mavai/punit/report/verdict-1.0.xsd")) {
            assertThat(xsdStream).as("XSD resource must be available").isNotNull();
            Schema schema = schemaFactory.newSchema(new StreamSource(xsdStream));
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
        }
    }

    private void validateAgainstSchema14(String xml) throws Exception {
        SchemaFactory schemaFactory = SchemaFactory.newInstance(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
        try (InputStream xsdStream = getClass().getResourceAsStream("/org/mavai/punit/report/verdict-1.4.xsd")) {
            assertThat(xsdStream).as("XSD 1.4 resource must be available").isNotNull();
            Schema schema = schemaFactory.newSchema(new StreamSource(xsdStream));
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
        }
    }

    private ProbabilisticTestVerdict verdictWithStandings() {
        ProbabilisticTestVerdict base = minimalVerdict(true, PUnitVerdict.PASS);
        var standings = new org.mavai.punit.api.spec.PostconditionStandings(List.of(
                new org.mavai.punit.api.spec.PostconditionStandings.CriterionStandings(
                        "extraction-matches",
                        Optional.of("2"),
                        List.of(
                                new org.mavai.punit.api.spec.PostconditionStandings.Row(
                                        0, "premium is exact", false, 18, 2, 0),
                                new org.mavai.punit.api.spec.PostconditionStandings.Row(
                                        1, "terms are the agreed set", true, 12, 6, 2)))));
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                base.functional(), base.latency(), base.statistics(), base.covariates(),
                base.cost(), base.pacing(), base.provenance(), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict(),
                base.verdictReason(), base.postconditionFailures(), base.perCriterion(),
                Optional.of(standings));
    }

    private void validateAgainstSchema12(String xml) throws Exception {
        SchemaFactory schemaFactory = SchemaFactory.newInstance(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
        try (InputStream xsdStream = getClass().getResourceAsStream("/org/mavai/punit/report/verdict-1.2.xsd")) {
            assertThat(xsdStream).as("XSD 1.2 resource must be available").isNotNull();
            Schema schema = schemaFactory.newSchema(new StreamSource(xsdStream));
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
        }
    }
}
