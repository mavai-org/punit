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
        StringBuilder sb = new StringBuilder();
        sb.append("[PUNIT-MEASURE] ").append(record.serviceContractId())
                .append(": ").append(record.sampleCount()).append(" samples\n");
        for (NormativeJudgement judgement : record.normativeJudgements()) {
            PassRateStatistics observed = characterisation.get(judgement.criterionId());
            sb.append("  criterion \"").append(judgement.criterionId()).append("\": ");
            if (observed != null) {
                sb.append("observed ").append(rate(observed.observedPassRate()))
                        .append(" (").append(observed.sampleCount()).append(" samples)");
            } else {
                sb.append("observed ").append(rate(judgement.judgement().observedRate()));
            }
            sb.append('\n');
            sb.append("  ").append(judgementLine(judgement)).append('\n');
        }
        sb.append("  baseline: ").append(record.filename());
        return sb.toString();
    }

    private static String judgementLine(NormativeJudgement entry) {
        NormativeJudgementEvaluator.Judgement j = entry.judgement();
        String source = entry.stipulationRef()
                .map(ref -> " (" + ref + ")")
                .orElse("");
        return switch (j.state()) {
            case MET -> "normative judgement: met — "
                    + confidencePercent(j.confidence()) + "-confident lower bound "
                    + rate(j.lowerBound()) + " clears the stipulated "
                    + rate(j.stipulatedThreshold()) + source;
            case FAILED -> "NORMATIVE JUDGEMENT: FAILED — stipulated "
                    + rate(j.stipulatedThreshold()) + source + "; "
                    + confidencePercent(j.confidence()) + "-confident lower bound "
                    + rate(j.lowerBound()) + " does not clear the stipulation";
            case UNSUPPORTABLE -> "NORMATIVE JUDGEMENT: UNSUPPORTABLE at this sample size — "
                    + "judging the stipulated " + rate(j.stipulatedThreshold()) + source
                    + " at " + confidencePercent(j.confidence())
                    + " confidence requires at least " + j.feasibleMinimumSamples()
                    + " samples; no judgement rendered";
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
