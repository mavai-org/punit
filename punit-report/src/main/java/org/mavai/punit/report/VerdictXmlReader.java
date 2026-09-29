package org.mavai.punit.report;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.mavai.punit.api.TestIntent;
import org.mavai.punit.verdict.TokenMode;
import org.mavai.punit.verdict.TerminationReason;
import org.mavai.punit.api.ServiceContractAttributes;
import org.mavai.punit.verdict.ProbabilisticTestVerdict;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.*;
import org.mavai.punit.statistics.ConfigurationError;
import org.mavai.punit.statistics.DecisionRule;
import org.mavai.punit.verdict.LatencyEvaluation;
import org.mavai.punit.verdict.PUnitVerdict;
import org.mavai.punit.verdict.RegressionDisclosure;
import org.mavai.punit.verdict.TestDecision;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Deserialises a {@link ProbabilisticTestVerdict} from verdict XML.
 *
 * <p>Reads the {@code http://mavai.org/verdict/1.0} format and maps it back
 * to the punit verdict model. PUnit-specific fields not present in the verdict-XML standard
 * (pacing, environment, expiration, correlation ID) receive default values.
 *
 * <p>Uses DOM parsing since individual verdict files are small.
 */
// mavai-ref: JVI-DQWKY4Z — do not remove (resolves in mavai-orchestrator)
public final class VerdictXmlReader {

    private static final DocumentBuilderFactory FACTORY = DocumentBuilderFactory.newInstance();

    static {
        FACTORY.setNamespaceAware(true);
    }

    /**
     * Reads a verdict from the given input stream.
     * The stream is not closed by this method.
     */
    public ProbabilisticTestVerdict read(InputStream in) {
        try {
            DocumentBuilder builder = FACTORY.newDocumentBuilder();
            Document doc = builder.parse(in);
            return readVerdictRecord(doc.getDocumentElement());
        } catch (Exception e) {
            throw new XmlReadException("Failed to read verdict XML", e);
        }
    }

    private ProbabilisticTestVerdict readVerdictRecord(Element root) {
        Instant timestamp = Instant.parse(root.getAttribute("timestamp"));
        String correlationId = optionalAttribute(root, "correlation-id")
                .orElse("");

        TestIdentity identity = readIdentity(firstElement(root, "identity"));
        ExecutionSummary execution = readExecution(firstElement(root, "execution"));
        Optional<FunctionalDimension> functional = optionalElement(root, "functional")
                .map(this::readFunctional);
        Optional<LatencyDimension> latency = optionalElement(root, "latency")
                .map(this::readLatency);
        StatisticalAnalysis statistics = readStatistics(root);
        CovariateStatus covariates = readCovariates(firstElement(root, "covariates"));
        Optional<SpecProvenance> provenance = optionalElement(root, "provenance")
                .map(this::readProvenance);
        Optional<PacingSummary> pacing = optionalElement(root, "pacing")
                .map(this::readPacing);
        Termination termination = readTermination(firstElement(root, "termination"));
        Map<String, String> environment = readEnvironment(root);

        Element verdictEl = firstElement(root, "verdict");
        // A 1.7 record refused before any sample ran carries its
        // configuration errors and no verdict value; its verdict is read
        // as INCONCLUSIVE, and the decision states the refusal.
        List<ConfigurationError> configurationErrors = optionalAttribute(verdictEl, "configuration-error")
                .map(list -> java.util.Arrays.stream(list.trim().split("\\s+"))
                        .map(ConfigurationError::valueOf)
                        .toList())
                .orElse(List.of());
        PUnitVerdict punitVerdict = optionalAttribute(verdictEl, "value")
                .map(PUnitVerdict::valueOf)
                .orElse(PUnitVerdict.INCONCLUSIVE);
        String verdictReason = optionalAttribute(verdictEl, "reason").orElse("");
        boolean junitPassed = punitVerdict != PUnitVerdict.FAIL && configurationErrors.isEmpty();
        TestDecision decision = new TestDecision(
                configurationErrors,
                configurationErrors.isEmpty() ? Optional.empty() : termination.details(),
                optionalAttribute(verdictEl, "decision-rule").flatMap(DecisionRule::fromId),
                List.of(),
                java.util.OptionalDouble.empty(),
                java.util.OptionalDouble.empty());

        // The reader is permissive: a <legacy-aggregate> element that
        // appears inside <per-criterion> (from a 1.1 emitter still in
        // service) is silently ignored. The transitional surface was
        // removed in verdict-1.2.xsd; older emitters' output continues
        // to parse without surprise.
        Optional<org.mavai.punit.verdict.PerCriterionStructure> perCriterion =
                optionalElement(root, "per-criterion")
                        .flatMap(this::readPerCriterionStructure);

        Optional<org.mavai.punit.api.spec.PostconditionStandings> standings =
                optionalElement(root, "postcondition-standings")
                        .map(this::readPostconditionStandings);

        return new ProbabilisticTestVerdict(
                correlationId, timestamp, identity, execution,
                functional, latency, statistics, covariates,
                new CostSummary(0, 0, 0, TokenMode.NONE, Optional.empty(), Optional.empty()),
                pacing,
                provenance,
                termination,
                environment,
                junitPassed, punitVerdict, verdictReason,
                Map.of(),
                perCriterion,
                standings,
                decision
        );
    }

    /**
     * The postcondition standings element (schema revision 1.3; 1.4's
     * structured-row attributes are read permissively and ignored —
     * the counts and identities are the binding content this reader
     * consumes).
     */
    private org.mavai.punit.api.spec.PostconditionStandings readPostconditionStandings(
            Element standingsEl) {
        java.util.List<org.mavai.punit.api.spec.PostconditionStandings.CriterionStandings>
                criteria = new java.util.ArrayList<>();
        NodeList criterionNodes = standingsEl.getElementsByTagNameNS(
                VerdictXmlWriter.NAMESPACE, "criterion");
        for (int i = 0; i < criterionNodes.getLength(); i++) {
            Element criterionEl = (Element) criterionNodes.item(i);
            java.util.List<org.mavai.punit.api.spec.PostconditionStandings.Row> rows =
                    new java.util.ArrayList<>();
            NodeList rowNodes = criterionEl.getElementsByTagNameNS(
                    VerdictXmlWriter.NAMESPACE, "row");
            for (int j = 0; j < rowNodes.getLength(); j++) {
                Element rowEl = (Element) rowNodes.item(j);
                rows.add(new org.mavai.punit.api.spec.PostconditionStandings.Row(
                        Integer.parseInt(rowEl.getAttribute("input-index")),
                        rowEl.getAttribute("check"),
                        Boolean.parseBoolean(rowEl.getAttribute("optional")),
                        Integer.parseInt(rowEl.getAttribute("passed")),
                        Integer.parseInt(rowEl.getAttribute("failed")),
                        Integer.parseInt(rowEl.getAttribute("skipped"))));
            }
            criteria.add(new org.mavai.punit.api.spec.PostconditionStandings.CriterionStandings(
                    criterionEl.getAttribute("name"),
                    optionalAttribute(criterionEl, "optional-slack"),
                    rows));
        }
        return new org.mavai.punit.api.spec.PostconditionStandings(criteria);
    }

    private Optional<org.mavai.punit.verdict.PerCriterionStructure> readPerCriterionStructure(
            Element perCriterionEl) {
        NodeList rows = perCriterionEl.getElementsByTagNameNS(
                VerdictXmlWriter.NAMESPACE, "criterion");
        Optional<Element> compositeEl = optionalElement(perCriterionEl, "composite");
        if (rows.getLength() == 0 && compositeEl.isEmpty()) {
            return Optional.empty();
        }
        java.util.List<org.mavai.punit.verdict.CriterionRow> criteria =
                new java.util.ArrayList<>(rows.getLength());
        for (int i = 0; i < rows.getLength(); i++) {
            Element r = (Element) rows.item(i);
            org.mavai.punit.api.spec.Verdict v = org.mavai.punit.api.spec.Verdict.valueOf(
                    r.getAttribute("verdict"));
            int pass = Integer.parseInt(r.getAttribute("pass"));
            int fail = Integer.parseInt(r.getAttribute("fail"));
            int inc = Integer.parseInt(r.getAttribute("inconclusive"));
            double observed = optionalAttribute(r, "observed-rate")
                    .map(Double::parseDouble).orElse(Double.NaN);
            double threshold = optionalAttribute(r, "threshold")
                    .map(Double::parseDouble).orElse(Double.NaN);
            criteria.add(new org.mavai.punit.verdict.CriterionRow(
                    r.getAttribute("id"), v, pass, fail, inc, observed, threshold,
                    optionalAttribute(r, "decision-rule").flatMap(DecisionRule::fromId)));
        }
        org.mavai.punit.api.spec.Verdict composite = compositeEl
                .map(e -> org.mavai.punit.api.spec.Verdict.valueOf(e.getAttribute("value")))
                // Fallback: when an emitter wrote <criterion> rows without
                // a <composite> (a defensive read; producing emitters always
                // pair the two), recompute from the rows.
                .orElseGet(() -> {
                    java.util.List<org.mavai.punit.api.spec.Verdict> vs =
                            new java.util.ArrayList<>(criteria.size());
                    for (var row : criteria) vs.add(row.verdict());
                    return org.mavai.punit.api.spec.Verdict.aggregate(vs);
                });
        return Optional.of(new org.mavai.punit.verdict.PerCriterionStructure(
                criteria, composite));
    }

    private TestIdentity readIdentity(Element el) {
        String serviceContractId = el.getAttribute("use-case-id");
        Optional<String> testName = optionalAttribute(el, "test-name");
        // Map verdict-XML identity back to punit's class-name / method-name model
        String className = serviceContractId;
        String methodName = testName.orElse(serviceContractId);
        return new TestIdentity(className, methodName, Optional.of(serviceContractId));
    }

    private ExecutionSummary readExecution(Element el) {
        int plannedSamples = Integer.parseInt(el.getAttribute("planned-samples"));
        int samplesExecuted = Integer.parseInt(el.getAttribute("samples-executed"));
        int successes = Integer.parseInt(el.getAttribute("successes"));
        int failures = Integer.parseInt(el.getAttribute("failures"));
        long elapsedMs = Long.parseLong(el.getAttribute("elapsed-ms"));
        TestIntent intent = TestIntent.valueOf(el.getAttribute("intent"));
        double confidence = Double.parseDouble(el.getAttribute("confidence"));
        int warmup = optionalAttribute(el, "warmup")
                .map(Integer::parseInt).orElse(0);
        double observedPassRate = samplesExecuted > 0
                ? (double) successes / samplesExecuted : 0.0;
        return new ExecutionSummary(
                plannedSamples, samplesExecuted, successes, failures,
                0.0, // min-pass-rate not in the verdict-XML standard, filled from statistics threshold
                observedPassRate,
                elapsedMs, Optional.empty(), intent, confidence,
                new ServiceContractAttributes(warmup)
        );
    }

    private FunctionalDimension readFunctional(Element el) {
        return new FunctionalDimension(
                Integer.parseInt(el.getAttribute("successes")),
                Integer.parseInt(el.getAttribute("failures")),
                Double.parseDouble(el.getAttribute("pass-rate"))
        );
    }

    private LatencyDimension readLatency(Element el) {
        int successfulSamples = Integer.parseInt(el.getAttribute("successful-samples"));

        // Read observed percentiles
        long p50 = 0, p90 = 0, p95 = 0, p99 = 0;
        Optional<Element> observedEl = optionalElement(el, "observed");
        if (observedEl.isPresent()) {
            NodeList percentiles = observedEl.get().getElementsByTagNameNS(
                    VerdictXmlWriter.NAMESPACE, "percentile");
            for (int i = 0; i < percentiles.getLength(); i++) {
                Element p = (Element) percentiles.item(i);
                long valueMs = Long.parseLong(p.getAttribute("value-ms"));
                switch (p.getAttribute("label")) {
                    case "p50" -> p50 = valueMs;
                    case "p90" -> p90 = valueMs;
                    case "p95" -> p95 = valueMs;
                    case "p99" -> p99 = valueMs;
                    default -> {}
                }
            }
        }

        Optional<org.mavai.punit.api.spec.Verdict> verdict = optionalAttribute(el, "verdict")
                .map(org.mavai.punit.api.spec.Verdict::valueOf);
        List<LatencyEvaluation> evaluations = new ArrayList<>();
        Optional<Element> evaluationsEl = optionalElement(el, "evaluations");
        if (evaluationsEl.isPresent()) {
            NodeList nodes = evaluationsEl.get().getElementsByTagNameNS(
                    VerdictXmlWriter.NAMESPACE, "evaluation");
            for (int i = 0; i < nodes.getLength(); i++) {
                readEvaluation((Element) nodes.item(i)).ifPresent(evaluations::add);
            }
        }
        return new LatencyDimension(
                successfulSamples, successfulSamples, false, Optional.empty(),
                p50, p90, p95, p99, Math.max(Math.max(p95, p99), p50),
                List.of(), "passing-samples", verdict, evaluations
        );
    }

    /**
     * One enforced latency evaluation. Advisory evaluations (which punit
     * never writes) and evaluations no rule decided are read past.
     */
    private Optional<LatencyEvaluation> readEvaluation(Element e) {
        if (!"strict".equals(e.getAttribute("mode"))) {
            return Optional.empty();
        }
        Optional<DecisionRule> rule = optionalAttribute(e, "decision-rule").flatMap(DecisionRule::fromId);
        if (rule.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new LatencyEvaluation(
                e.getAttribute("percentile"),
                optionalLong(e, "observed-ms"),
                optionalLong(e, "threshold-ms"),
                "explicit".equals(e.getAttribute("provenance"))
                        ? LatencyEvaluation.Provenance.EXPLICIT
                        : LatencyEvaluation.Provenance.BASELINE_DERIVED,
                LatencyEvaluation.Status.valueOf(e.getAttribute("status")),
                optionalAttribute(e, "baseline-confidence")
                        .map(v -> java.util.OptionalDouble.of(Double.parseDouble(v)))
                        .orElse(java.util.OptionalDouble.empty()),
                optionalInt(e, "baseline-rank"),
                optionalInt(e, "baseline-n"),
                rule.get(),
                optionalInt(e, "within-threshold"),
                optionalInt(e, "required-within"),
                false));
    }

    private java.util.OptionalLong optionalLong(Element e, String name) {
        return optionalAttribute(e, name)
                .map(v -> java.util.OptionalLong.of(Long.parseLong(v)))
                .orElse(java.util.OptionalLong.empty());
    }

    private java.util.OptionalInt optionalInt(Element e, String name) {
        return optionalAttribute(e, name)
                .map(v -> java.util.OptionalInt.of(Integer.parseInt(v)))
                .orElse(java.util.OptionalInt.empty());
    }

    private StatisticalAnalysis readStatistics(Element root) {
        Optional<BaselineSummary> baseline = optionalElement(root, "baseline")
                .map(this::readBaseline);
        List<String> caveats = readWarnings(root);
        Optional<Element> statistics = optionalElement(root, "statistics");
        if (statistics.isEmpty()) {
            // The element is optional: a refused record has none.
            return new StatisticalAnalysis(0.0, 0.0, 0.0, Optional.empty(), baseline, caveats);
        }
        Element el = statistics.get();
        String wilsonLowerAttr = el.getAttribute("wilson-lower");
        if (wilsonLowerAttr == null || wilsonLowerAttr.isEmpty()) {
            throw new IllegalArgumentException(
                    "verdict <statistics> element is missing required attribute 'wilson-lower' "
                            + "(the verdict XML schema requires it on every statistics element)");
        }
        Optional<RegressionDisclosure> regression = Optional.empty();
        if (el.hasAttribute("size-at-assumed-common-rate") || el.hasAttribute("design-power")) {
            regression = Optional.of(new RegressionDisclosure(
                    optionalDouble(el, "size-at-assumed-common-rate"),
                    optionalDouble(el, "design-alternative-rate"),
                    optionalDouble(el, "design-power"),
                    optionalDouble(el, "resolved-test-power"),
                    java.util.OptionalDouble.empty()));
        }
        return new StatisticalAnalysis(
                Double.parseDouble(el.getAttribute("confidence-level")),
                Double.parseDouble(el.getAttribute("standard-error")),
                Double.parseDouble(wilsonLowerAttr),
                Optional.empty(), // threshold-derivation
                baseline,
                caveats,
                regression
        );
    }

    private java.util.OptionalDouble optionalDouble(Element e, String name) {
        return optionalAttribute(e, name)
                .map(v -> java.util.OptionalDouble.of(Double.parseDouble(v)))
                .orElse(java.util.OptionalDouble.empty());
    }

    private BaselineSummary readBaseline(Element el) {
        return new BaselineSummary(
                el.getAttribute("source-file"),
                Instant.parse(el.getAttribute("generated-at")),
                Integer.parseInt(el.getAttribute("samples")),
                0, // successes not in the verdict-XML standard baseline
                Double.parseDouble(el.getAttribute("baseline-rate")),
                Double.parseDouble(el.getAttribute("derived-threshold"))
        );
    }

    private CovariateStatus readCovariates(Element el) {
        boolean aligned = Boolean.parseBoolean(el.getAttribute("aligned"));
        List<Misalignment> misalignments = new ArrayList<>();
        NodeList nodes = el.getElementsByTagNameNS(VerdictXmlWriter.NAMESPACE, "misalignment");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element m = (Element) nodes.item(i);
            misalignments.add(new Misalignment(
                    m.getAttribute("key"),
                    m.getAttribute("baseline-value"),
                    m.getAttribute("observed-value")
            ));
        }
        return new CovariateStatus(aligned, misalignments, Map.of(), Map.of());
    }

    private SpecProvenance readProvenance(Element el) {
        String origin = el.getAttribute("origin");
        String contractRef = el.hasAttribute("contract-ref") ? el.getAttribute("contract-ref") : null;
        String specFilename = el.hasAttribute("spec-filename") ? el.getAttribute("spec-filename") : null;
        Optional<ExpirationInfo> expiration = optionalElement(el, "expiration")
                .map(this::readExpiration);
        return new SpecProvenance(origin, contractRef, specFilename,
                expiration, Optional.empty());
    }

    private ExpirationInfo readExpiration(Element el) {
        String statusName = el.getAttribute("status");
        Optional<Instant> expiresAt = optionalAttribute(el, "expires-at").map(Instant::parse);
        org.mavai.punit.verdict.ExpirationStatus status = parseExpirationStatus(statusName);
        return new ExpirationInfo(status, expiresAt);
    }

    private org.mavai.punit.verdict.ExpirationStatus parseExpirationStatus(String name) {
        return switch (name) {
            case "NO_EXPIRATION" -> org.mavai.punit.verdict.ExpirationStatus.noExpiration();
            case "VALID" -> org.mavai.punit.verdict.ExpirationStatus.valid(Duration.ZERO);
            case "EXPIRING_SOON" -> org.mavai.punit.verdict.ExpirationStatus.expiringSoon(Duration.ZERO, 0.0);
            case "EXPIRING_IMMINENTLY" -> org.mavai.punit.verdict.ExpirationStatus.expiringImminently(Duration.ZERO, 0.0);
            case "EXPIRED" -> org.mavai.punit.verdict.ExpirationStatus.expired(Duration.ZERO);
            default -> throw new XmlReadException("Unknown expiration status: " + name, null);
        };
    }

    private PacingSummary readPacing(Element el) {
        return new PacingSummary(
                Double.parseDouble(el.getAttribute("max-rps")),
                Double.parseDouble(el.getAttribute("max-rpm")),
                0.0, // max-rph not in the verdict-XML standard
                Integer.parseInt(el.getAttribute("max-concurrent")),
                Long.parseLong(el.getAttribute("effective-min-delay-ms")),
                Integer.parseInt(el.getAttribute("effective-concurrency")),
                Double.parseDouble(el.getAttribute("effective-rps"))
        );
    }

    private Map<String, String> readEnvironment(Element root) {
        Optional<Element> envEl = optionalElement(root, "environment");
        if (envEl.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        NodeList entries = envEl.get().getElementsByTagNameNS(VerdictXmlWriter.NAMESPACE, "entry");
        for (int i = 0; i < entries.getLength(); i++) {
            Element entry = (Element) entries.item(i);
            result.put(entry.getAttribute("key"), entry.getAttribute("value"));
        }
        return result;
    }

    private Termination readTermination(Element el) {
        String reason = el.getAttribute("reason");
        Optional<String> detail = optionalAttribute(el, "detail");
        TerminationReason mapped = switch (reason) {
            case "COMPLETED" -> TerminationReason.COMPLETED;
            case "FAILURE_INEVITABLE" -> TerminationReason.IMPOSSIBILITY;
            case "SUCCESS_GUARANTEED" -> TerminationReason.SUCCESS_GUARANTEED;
            case "TIME_BUDGET_EXHAUSTED" -> TerminationReason.METHOD_TIME_BUDGET_EXHAUSTED;
            case "TOKEN_BUDGET_EXHAUSTED" -> TerminationReason.METHOD_TOKEN_BUDGET_EXHAUSTED;
            case "CONFIGURATION_REFUSED" -> TerminationReason.CONFIGURATION_REFUSED;
            default -> TerminationReason.COMPLETED;
        };
        return new Termination(mapped, detail);
    }

    private List<String> readWarnings(Element root) {
        Optional<Element> warningsEl = optionalElement(root, "warnings");
        if (warningsEl.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        NodeList warnings = warningsEl.get().getElementsByTagNameNS(
                VerdictXmlWriter.NAMESPACE, "warning");
        for (int i = 0; i < warnings.getLength(); i++) {
            result.add(warnings.item(i).getTextContent());
        }
        return result;
    }

    // ── DOM helpers ──────────────────────────────────────────────────────

    private Element firstElement(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS(VerdictXmlWriter.NAMESPACE, localName);
        if (nodes.getLength() == 0) {
            throw new XmlReadException("Missing required element: " + localName, null);
        }
        return (Element) nodes.item(0);
    }

    private Optional<Element> optionalElement(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS(VerdictXmlWriter.NAMESPACE, localName);
        if (nodes.getLength() == 0) {
            return Optional.empty();
        }
        return Optional.of((Element) nodes.item(0));
    }

    private Optional<String> optionalAttribute(Element el, String name) {
        if (el.hasAttribute(name)) {
            return Optional.of(el.getAttribute(name));
        }
        return Optional.empty();
    }
}
