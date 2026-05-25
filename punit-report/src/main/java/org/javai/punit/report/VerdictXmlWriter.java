package org.javai.punit.report;

import java.io.OutputStream;
import java.util.Map;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.javai.punit.api.spec.FailureCount;
import org.javai.punit.api.spec.FailureExemplar;
import org.javai.punit.verdict.ProbabilisticTestVerdict;
import org.javai.punit.verdict.ProbabilisticTestVerdict.*;

/**
 * Serialises a {@link ProbabilisticTestVerdict} to the verdict XML
 * interchange format.
 *
 * <p>The output conforms to the {@code http://javai.org/verdict/1.0}
 * namespace and the {@code verdict-1.0.xsd} schema bundled with this
 * module.
 *
 * <p>PUnit-specific extensions not in the verdict-XML standard
 * (pacing, environment, expiration, correlation ID, JUnit pass
 * status) are not serialised. The verdict model in punit-core
 * remains unchanged — this class maps from the punit record to the
 * cross-project XML format.
 */
// javai-ref: JVI-6506GYF — do not remove (resolves in javai-orchestrator)
// javai-ref: JVI-DQWKY4Z — do not remove (resolves in javai-orchestrator)
public final class VerdictXmlWriter {

    /**
     * standard namespace.
     */
    static final String NAMESPACE = "http://javai.org/verdict/1.0";
    static final String VERSION_1_0 = "1.0";
    static final String VERSION_1_2 = "1.2";

    private static final XMLOutputFactory OUTPUT_FACTORY = XMLOutputFactory.newFactory();

    /**
     * Resolves the generator string from the package manifest.
     * Falls back to {@code "punit"} if the version is unavailable.
     */
    private static String resolveGenerator() {
        String version = VerdictXmlWriter.class.getPackage().getImplementationVersion();
        return version != null ? "punit/" + version : "punit";
    }

    /**
     * Writes the verdict as XML to the given output stream.
     * The stream is not closed by this method.
     */
    public void write(ProbabilisticTestVerdict verdict, OutputStream out) throws XMLStreamException {
        XMLStreamWriter w = OUTPUT_FACTORY.createXMLStreamWriter(out, "UTF-8");
        try {
            w.writeStartDocument("UTF-8", "1.0");
            writeVerdictRecord(w, verdict);
            w.writeEndDocument();
            w.flush();
        } finally {
            w.close();
        }
    }

    private void writeVerdictRecord(XMLStreamWriter w, ProbabilisticTestVerdict v) throws XMLStreamException {
        w.writeStartElement("verdict-record");
        w.writeDefaultNamespace(NAMESPACE);
        // version="1.2" when <per-criterion> content is populated;
        // "1.0" otherwise. Consumers inspecting the attribute remain
        // correctly informed about which optional elements may appear.
        w.writeAttribute("version",
                v.perCriterion().isPresent() ? VERSION_1_2 : VERSION_1_0);
        w.writeAttribute("timestamp", v.timestamp().toString());
        w.writeAttribute("generator", resolveGenerator());
        if (v.correlationId() != null && !v.correlationId().isEmpty()) {
            w.writeAttribute("correlation-id", v.correlationId());
        }

        writeIdentity(w, v.identity());
        writeExecution(w, v.execution());
        v.functional().ifPresent(f -> writeFunctionalUnchecked(w, f));
        v.latency().ifPresent(l -> writeLatencyUnchecked(w, l));
        writeStatistics(w, v);
        writeCovariates(w, v.covariates());
        v.provenance().ifPresent(p -> writeProvenanceUnchecked(w, p));
        v.statistics().baseline().ifPresent(b -> writeBaselineUnchecked(w, b));
        writeTermination(w, v.termination());
        writeCost(w, v.cost(), v.execution());
        writeWarnings(w, v.statistics());
        v.pacing().ifPresent(p -> writePacingUnchecked(w, p));
        writeEnvironment(w, v.environmentMetadata());
        writePostconditionFailures(w, v.postconditionFailures());
        writePerCriterion(w, v);
        writeVerdictElement(w, v);

        w.writeEndElement();
    }

    private void writePerCriterion(XMLStreamWriter w, ProbabilisticTestVerdict v)
            throws XMLStreamException {
        if (v.perCriterion().isEmpty()) {
            return;
        }
        org.javai.punit.verdict.PerCriterionStructure pc = v.perCriterion().get();
        w.writeStartElement("per-criterion");
        for (org.javai.punit.verdict.CriterionRow row : pc.criteria()) {
            w.writeStartElement("criterion");
            w.writeAttribute("id", row.criterionId());
            w.writeAttribute("verdict", row.verdict().name());
            w.writeAttribute("pass", Integer.toString(row.pass()));
            w.writeAttribute("fail", Integer.toString(row.fail()));
            w.writeAttribute("inconclusive", Integer.toString(row.inconclusive()));
            w.writeAttribute("total", Integer.toString(row.total()));
            if (!Double.isNaN(row.observedRate())) {
                w.writeAttribute("observed-rate", formatDouble(row.observedRate()));
            }
            if (!Double.isNaN(row.threshold())) {
                w.writeAttribute("threshold", formatDouble(row.threshold()));
            }
            w.writeEndElement();
        }
        w.writeStartElement("composite");
        w.writeAttribute("value", pc.composite().name());
        w.writeEndElement();
        w.writeEndElement();
    }

    private void writeIdentity(XMLStreamWriter w, TestIdentity id) throws XMLStreamException {
        w.writeStartElement("identity");
        // verdict-XML: use-case-id is required. Map from serviceContractId if present, else className.
        String serviceContractId = id.serviceContractId().orElse(id.className());
        w.writeAttribute("use-case-id", serviceContractId);
        w.writeAttribute("test-name", id.methodName());
        w.writeEndElement();
    }

    private void writeExecution(XMLStreamWriter w, ExecutionSummary exec) throws XMLStreamException {
        w.writeStartElement("execution");
        w.writeAttribute("planned-samples", Integer.toString(exec.plannedSamples()));
        w.writeAttribute("samples-executed", Integer.toString(exec.samplesExecuted()));
        w.writeAttribute("successes", Integer.toString(exec.successes()));
        w.writeAttribute("failures", Integer.toString(exec.failures()));
        w.writeAttribute("elapsed-ms", Long.toString(exec.elapsedMs()));
        w.writeAttribute("intent", exec.intent().name());
        w.writeAttribute("confidence", formatDouble(exec.resolvedConfidence()));
        if (exec.warmup() > 0) {
            w.writeAttribute("warmup", Integer.toString(exec.warmup()));
        }
        w.writeEndElement();
    }

    private void writeFunctional(XMLStreamWriter w, FunctionalDimension func) throws XMLStreamException {
        w.writeStartElement("functional");
        w.writeAttribute("successes", Integer.toString(func.successes()));
        w.writeAttribute("failures", Integer.toString(func.failures()));
        w.writeAttribute("pass-rate", formatRate(func.passRate()));
        w.writeEndElement();
    }

    private void writeLatency(XMLStreamWriter w, LatencyDimension lat) throws XMLStreamException {
        if (lat.skipped()) {
            return; // the verdict-XML standard omits latency when not applicable
        }
        w.writeStartElement("latency");
        w.writeAttribute("successful-samples", Integer.toString(lat.successfulSamples()));
        // The latency dimension at the verdict layer is descriptive
        // only; declared-threshold gating happens at the criterion
        // layer via PercentileLatency. strict-violations and
        // advisory-violations therefore stay at zero in punit's
        // emission. The schema retains them for future framework
        // implementations that wire per-percentile evaluation data
        // into the verdict-XML envelope.
        w.writeAttribute("strict-violations", "0");
        w.writeAttribute("advisory-violations", "0");

        // Observed percentiles
        w.writeStartElement("observed");
        writePercentileObserved(w, "p50", lat.p50Ms());
        writePercentileObserved(w, "p90", lat.p90Ms());
        writePercentileObserved(w, "p95", lat.p95Ms());
        writePercentileObserved(w, "p99", lat.p99Ms());
        w.writeEndElement();

        w.writeEndElement();
    }

    private void writePercentileObserved(XMLStreamWriter w, String label, long valueMs) throws XMLStreamException {
        w.writeStartElement("percentile");
        w.writeAttribute("label", label);
        w.writeAttribute("value-ms", Long.toString(valueMs));
        w.writeEndElement();
    }

    private void writeStatistics(XMLStreamWriter w, ProbabilisticTestVerdict v) throws XMLStreamException {
        StatisticalAnalysis stats = v.statistics();
        w.writeStartElement("statistics");
        w.writeAttribute("confidence-level", formatDouble(stats.confidenceLevel()));
        w.writeAttribute("standard-error", formatDouble(stats.standardError()));
        w.writeAttribute("wilson-lower", formatDouble(stats.wilsonLower()));
        w.writeAttribute("threshold", formatRate(v.execution().minPassRate()));
        String thresholdOrigin = v.provenance()
                .map(SpecProvenance::thresholdOriginName)
                .orElse("UNSPECIFIED");
        w.writeAttribute("threshold-origin", thresholdOrigin);
        stats.testStatistic().ifPresent(t -> writeAttributeUnchecked(w, "test-statistic", formatDouble(t)));
        stats.pValue().ifPresent(p -> writeAttributeUnchecked(w, "p-value", formatDouble(p)));
        w.writeEndElement();
    }

    private void writeBaseline(XMLStreamWriter w, BaselineSummary b) throws XMLStreamException {
        w.writeStartElement("baseline");
        w.writeAttribute("source-file", b.sourceFile());
        w.writeAttribute("generated-at", b.generatedAt().toString());
        w.writeAttribute("samples", Integer.toString(b.baselineSamples()));
        w.writeAttribute("baseline-rate", formatRate(b.baselineRate()));
        w.writeAttribute("derived-threshold", formatRate(b.derivedThreshold()));
        w.writeEndElement();
    }

    private void writeCovariates(XMLStreamWriter w, CovariateStatus cov) throws XMLStreamException {
        w.writeStartElement("covariates");
        w.writeAttribute("aligned", Boolean.toString(cov.aligned()));
        for (Misalignment m : cov.misalignments()) {
            w.writeStartElement("misalignment");
            w.writeAttribute("key", m.covariateKey());
            w.writeAttribute("baseline-value", m.baselineValue());
            w.writeAttribute("observed-value", m.testValue());
            w.writeEndElement();
        }
        w.writeEndElement();
    }

    private void writeCost(XMLStreamWriter w, CostSummary cost, ExecutionSummary exec) throws XMLStreamException {
        w.writeStartElement("cost");
        w.writeAttribute("total-time-ms", Long.toString(exec.elapsedMs()));
        w.writeAttribute("total-tokens", Long.toString(cost.methodTokensConsumed()));
        long avgTimeMs = exec.samplesExecuted() > 0
                ? exec.elapsedMs() / exec.samplesExecuted() : 0;
        w.writeAttribute("avg-time-per-sample-ms", Long.toString(avgTimeMs));
        long avgTokens = exec.samplesExecuted() > 0
                ? cost.methodTokensConsumed() / exec.samplesExecuted() : 0;
        w.writeAttribute("avg-tokens-per-sample", Long.toString(avgTokens));
        w.writeEndElement();
    }

    private void writeProvenance(XMLStreamWriter w, SpecProvenance prov) throws XMLStreamException {
        w.writeStartElement("provenance");
        w.writeAttribute("origin", prov.thresholdOriginName());
        if (prov.contractRef() != null) {
            w.writeAttribute("contract-ref", prov.contractRef());
        }
        if (prov.specFilename() != null) {
            w.writeAttribute("spec-filename", prov.specFilename());
        }
        prov.expiration().ifPresent(e -> writeExpirationUnchecked(w, e));
        w.writeEndElement();
    }

    private void writeExpiration(XMLStreamWriter w, ExpirationInfo info) throws XMLStreamException {
        w.writeStartElement("expiration");
        w.writeAttribute("status", expirationStatusName(info.status()));
        info.expiresAt().ifPresent(t -> writeAttributeUnchecked(w, "expires-at", t.toString()));
        w.writeAttribute("requires-warning", Boolean.toString(info.status().requiresWarning()));
        w.writeEndElement();
    }

    private void writePacing(XMLStreamWriter w, PacingSummary p) throws XMLStreamException {
        w.writeStartElement("pacing");
        w.writeAttribute("max-rps", formatDouble(p.maxRequestsPerSecond()));
        w.writeAttribute("max-rpm", formatDouble(p.maxRequestsPerMinute()));
        w.writeAttribute("max-concurrent", Integer.toString(p.maxConcurrentRequests()));
        w.writeAttribute("effective-min-delay-ms", Long.toString(p.effectiveMinDelayMs()));
        w.writeAttribute("effective-concurrency", Integer.toString(p.effectiveConcurrency()));
        w.writeAttribute("effective-rps", formatDouble(p.effectiveRps()));
        w.writeEndElement();
    }

    private void writeEnvironment(XMLStreamWriter w, Map<String, String> metadata) throws XMLStreamException {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        w.writeStartElement("environment");
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            w.writeStartElement("entry");
            w.writeAttribute("key", entry.getKey());
            w.writeAttribute("value", entry.getValue());
            w.writeEndElement();
        }
        w.writeEndElement();
    }

    /**
     * Emits {@code <postcondition-failures>} per the verdict-1.0 schema. Iteration
     * order of the map is preserved so clauses appear in the order the
     * contract declared them (the typed pipeline delivers a
     * {@link java.util.LinkedHashMap}). The writer emits whatever exemplars
     * the producer retained — the engine caps the list at three per clause.
     */
    private void writePostconditionFailures(XMLStreamWriter w, Map<String, FailureCount> byClause)
            throws XMLStreamException {
        if (byClause == null || byClause.isEmpty()) {
            return;
        }
        w.writeStartElement("postcondition-failures");
        for (Map.Entry<String, FailureCount> entry : byClause.entrySet()) {
            FailureCount bucket = entry.getValue();
            w.writeStartElement("clause");
            w.writeAttribute("description", entry.getKey());
            w.writeAttribute("count", Integer.toString(bucket.count()));
            for (FailureExemplar ex : bucket.exemplars()) {
                w.writeStartElement("exemplar");
                w.writeAttribute("input", ex.input());
                w.writeAttribute("reason", ex.reason());
                w.writeEndElement();
            }
            w.writeEndElement();
        }
        w.writeEndElement();
    }

    private void writeTermination(XMLStreamWriter w, Termination term) throws XMLStreamException {
        w.writeStartElement("termination");
        w.writeAttribute("reason", mapTerminationReason(term.reason()));
        term.details().ifPresent(d -> writeAttributeUnchecked(w, "detail", d));
        w.writeEndElement();
    }

    private void writeWarnings(XMLStreamWriter w, StatisticalAnalysis stats) throws XMLStreamException {
        if (stats.caveats().isEmpty()) {
            return;
        }
        w.writeStartElement("warnings");
        for (String caveat : stats.caveats()) {
            w.writeStartElement("warning");
            w.writeAttribute("code", "CAVEAT");
            w.writeCharacters(caveat);
            w.writeEndElement();
        }
        w.writeEndElement();
    }

    private void writeVerdictElement(XMLStreamWriter w, ProbabilisticTestVerdict v) throws XMLStreamException {
        w.writeStartElement("verdict");
        w.writeAttribute("value", v.punitVerdict().name());
        if (v.verdictReason() != null && !v.verdictReason().isEmpty()) {
            w.writeAttribute("reason", v.verdictReason());
        }
        w.writeEndElement();
    }

    /**
     * Maps punit's termination reasons to verdict XML standard reasons.
     */
    private static String mapTerminationReason(org.javai.punit.verdict.TerminationReason reason) {
        return switch (reason) {
            case COMPLETED, MAX_ITERATIONS, NO_IMPROVEMENT,
                 SCORE_THRESHOLD_REACHED -> "COMPLETED";
            case IMPOSSIBILITY -> "FAILURE_INEVITABLE";
            case SUCCESS_GUARANTEED -> "SUCCESS_GUARANTEED";
            case METHOD_TIME_BUDGET_EXHAUSTED, CLASS_TIME_BUDGET_EXHAUSTED,
                 SUITE_TIME_BUDGET_EXHAUSTED,
                 OPTIMIZATION_TIME_BUDGET_EXHAUSTED -> "TIME_BUDGET_EXHAUSTED";
            case METHOD_TOKEN_BUDGET_EXHAUSTED, CLASS_TOKEN_BUDGET_EXHAUSTED,
                 SUITE_TOKEN_BUDGET_EXHAUSTED,
                 OPTIMIZATION_TOKEN_BUDGET_EXHAUSTED -> "TOKEN_BUDGET_EXHAUSTED";
            case MUTATION_FAILURE, SCORING_FAILURE -> "COMPLETED";
        };
    }

    // ── Formatting ───────────────────────────────────────────────────────

    private static String formatDouble(double value) {
        if (value == (long) value) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    private static String formatRate(double value) {
        return String.format("%.4f", value);
    }

    // ── Unchecked wrappers (XMLStreamException → RuntimeException) ───────

    private static void writeAttributeUnchecked(XMLStreamWriter w, String name, String value) {
        try {
            w.writeAttribute(name, value);
        } catch (XMLStreamException e) {
            throw new XmlWriteException("Failed to write attribute: " + name, e);
        }
    }

    private void writeFunctionalUnchecked(XMLStreamWriter w, FunctionalDimension f) {
        try {
            writeFunctional(w, f);
        } catch (XMLStreamException e) {
            throw new XmlWriteException("Failed to write functional dimension", e);
        }
    }

    private void writeLatencyUnchecked(XMLStreamWriter w, LatencyDimension l) {
        try {
            writeLatency(w, l);
        } catch (XMLStreamException e) {
            throw new XmlWriteException("Failed to write latency dimension", e);
        }
    }

    private void writeProvenanceUnchecked(XMLStreamWriter w, SpecProvenance p) {
        try {
            writeProvenance(w, p);
        } catch (XMLStreamException e) {
            throw new XmlWriteException("Failed to write provenance", e);
        }
    }

    private void writeBaselineUnchecked(XMLStreamWriter w, BaselineSummary b) {
        try {
            writeBaseline(w, b);
        } catch (XMLStreamException e) {
            throw new XmlWriteException("Failed to write baseline", e);
        }
    }

    private void writeExpirationUnchecked(XMLStreamWriter w, ExpirationInfo e) {
        try {
            writeExpiration(w, e);
        } catch (XMLStreamException ex) {
            throw new XmlWriteException("Failed to write expiration", ex);
        }
    }

    private void writePacingUnchecked(XMLStreamWriter w, PacingSummary p) {
        try {
            writePacing(w, p);
        } catch (XMLStreamException e) {
            throw new XmlWriteException("Failed to write pacing", e);
        }
    }

    // ── ExpirationStatus mapping ─────────────────────────────────────────

    private static String expirationStatusName(org.javai.punit.verdict.ExpirationStatus status) {
        return switch (status) {
            case org.javai.punit.verdict.ExpirationStatus.NoExpiration n -> "NO_EXPIRATION";
            case org.javai.punit.verdict.ExpirationStatus.Valid v -> "VALID";
            case org.javai.punit.verdict.ExpirationStatus.ExpiringSoon s -> "EXPIRING_SOON";
            case org.javai.punit.verdict.ExpirationStatus.ExpiringImminently i -> "EXPIRING_IMMINENTLY";
            case org.javai.punit.verdict.ExpirationStatus.Expired e -> "EXPIRED";
        };
    }
}
