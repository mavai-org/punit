package org.mavai.punit.internal.reporting;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.mavai.punit.api.spec.BaselineStatistics;
import org.mavai.punit.api.spec.NormativeJudgement;
import org.mavai.punit.api.spec.PassRateStatistics;
import org.mavai.punit.api.spec.PerCriterionPassRateStatistics;
import org.mavai.punit.internal.engine.baseline.BaselineRecord;
import org.mavai.punit.statistics.NormativeJudgementEvaluator;

/**
 * Renders a measure run's normative judgements for the experiment's
 * console output — the experiment-time verdict for normative
 * criteria, displayed against (never instead of) the measured
 * characterisation.
 *
 * <p>Wording discipline: every judgement line states the run's
 * relation to the stipulation — observed rate, confidence bound,
 * stipulated threshold, source reference — and implies nothing about
 * the service's validity. A failed judgement at measure time can be
 * entirely normal (an aspirational bar measured mid-development, a
 * fresh configuration characterised before tuning), so the language
 * is "does not clear the stipulation", never "the service is
 * broken". A failed or unsupportable judgement is rendered in
 * upper case for prominence; a met judgement in lower case.
 *
 * <p>This class formats pre-computed data only; the judgement itself
 * is computed upstream (statistics package) before the record
 * reaches this renderer.
 */
// mavai-ref: JVI-51ASAR0 — do not remove (resolves in mavai-orchestrator)
public final class NormativeJudgementRenderer {

    private NormativeJudgementRenderer() { }

    /**
     * Render the record's characterisation-plus-judgement block.
     * Returns an empty string when the record carries no normative
     * judgements — a measure over a purely empirical contract prints
     * nothing new.
     */
    public static String render(BaselineRecord record) {
        Objects.requireNonNull(record, "record");
        if (record.normativeJudgements().isEmpty()) {
            return "";
        }
        Map<String, PassRateStatistics> characterisation = passRateByCriterion(record);
        StringBuilder sb = new StringBuilder(String.format(
                "[PUNIT-MEASURE] %s: %d samples%n",
                record.serviceContractId(), record.sampleCount()));
        for (NormativeJudgement judgement : record.normativeJudgements()) {
            sb.append(criterionBlock(
                    judgement, characterisation.get(judgement.criterionId())));
        }
        sb.append(String.format("  baseline: %s", record.filename()));
        return sb.toString();
    }

    /**
     * One criterion's two-line block: the measured characterisation,
     * then the judgement rendered against it.
     */
    private static String criterionBlock(
            NormativeJudgement judgement, PassRateStatistics observed) {
        String characterisation = observed != null
                ? String.format("observed %s (%d samples)",
                        rate(observed.observedPassRate()), observed.sampleCount())
                : String.format("observed %s", rate(judgement.judgement().observedRate()));
        return String.format("  criterion \"%s\": %s%n  %s%n",
                judgement.criterionId(), characterisation, judgementLine(judgement));
    }

    private static String judgementLine(NormativeJudgement entry) {
        NormativeJudgementEvaluator.Judgement j = entry.judgement();
        String source = entry.stipulationRef()
                .map(ref -> " (" + ref + ")")
                .orElse("");
        return switch (j.state()) {
            case MET -> String.format(
                    "normative judgement: met — %s-confident lower bound %s clears the stipulated %s%s",
                    confidencePercent(j.confidence()), rate(j.lowerBound()),
                    rate(j.stipulatedThreshold()), source);
            case FAILED -> String.format(
                    "NORMATIVE JUDGEMENT: FAILED — stipulated %s%s; %s-confident lower bound %s does not clear the stipulation",
                    rate(j.stipulatedThreshold()), source,
                    confidencePercent(j.confidence()), rate(j.lowerBound()));
            case UNSUPPORTABLE -> String.format(
                    "NORMATIVE JUDGEMENT: UNSUPPORTABLE at this sample size — judging the stipulated %s%s at %s confidence requires at least %d samples; no judgement rendered",
                    rate(j.stipulatedThreshold()), source,
                    confidencePercent(j.confidence()), j.feasibleMinimumSamples());
        };
    }

    private static Map<String, PassRateStatistics> passRateByCriterion(BaselineRecord record) {
        Map<String, PassRateStatistics> byCriterion = new LinkedHashMap<>();
        for (BaselineStatistics statistics : record.statisticsByCriterionName().values()) {
            if (statistics instanceof PerCriterionPassRateStatistics perCriterion) {
                byCriterion.putAll(perCriterion.byCriterion());
            }
        }
        return byCriterion;
    }

    private static String rate(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String confidencePercent(double confidence) {
        double percent = confidence * 100.0;
        if (percent == Math.rint(percent)) {
            return String.format(Locale.ROOT, "%.0f%%", percent);
        }
        return String.format(Locale.ROOT, "%s%%", percent);
    }
}
