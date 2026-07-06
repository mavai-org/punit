package org.mavai.punit.internal.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mavai.punit.api.spec.BaselineStatistics;
import org.mavai.punit.api.spec.PassRateStatistics;
import org.mavai.punit.api.spec.PerCriterionPassRateStatistics;
import org.mavai.punit.internal.engine.baseline.BaselineRecord;
import org.mavai.punit.api.spec.NormativeJudgement;
import org.mavai.punit.statistics.NormativeJudgementEvaluator.Judgement;
import org.mavai.punit.statistics.NormativeJudgementEvaluator.State;

/**
 * Console wording for normative judgement at experiment time. The
 * judgement is rendered against the characterisation, visually
 * distinguished from it, prominent on failure — and always worded as
 * the run's relation to the stipulation, never as a claim about the
 * service's validity.
 */
@DisplayName("Normative judgement rendering — relation to the stipulation, never a verdict on the service")
class NormativeJudgementRendererTest {

    private static BaselineRecord record(List<NormativeJudgement> judgements) {
        Map<String, BaselineStatistics> stats = Map.of(
                "bernoulli-pass-rate",
                new PerCriterionPassRateStatistics(Map.of(
                        "transaction-succeeds", new PassRateStatistics(0.983, 1000))));
        return new BaselineRecord(
                "payment-gateway", "measure", "a1b2c3d4", "deadbeef",
                1000, Instant.parse("2026-07-05T00:00:00Z"), stats,
                org.mavai.punit.api.covariate.CovariateProfile.empty(),
                org.mavai.punit.internal.engine.baseline.LatencyIndicator.empty(),
                0, judgements);
    }

    private static NormativeJudgement judgement(State state) {
        return new NormativeJudgement(
                "transaction-succeeds",
                new Judgement(state, 0.99, 0.95, 0.983,
                        state == State.UNSUPPORTABLE ? Double.NaN : 0.9744, 268),
                Optional.of("SLA, Payment Provider SLA v2.0 §4.1"));
    }

    @Test
    @DisplayName("renders a met judgement in measured tones, with bound, stipulation, "
            + "and source")
    void rendersMetJudgement() {
        String out = NormativeJudgementRenderer.render(
                record(List.of(judgement(State.MET))));

        assertThat(out)
                .contains("[PUNIT-MEASURE] payment-gateway: 1000 samples")
                .contains("criterion \"transaction-succeeds\": observed 0.9830 (1000 samples)")
                .contains("normative judgement: met")
                .contains("95%-confident lower bound 0.9744 clears the stipulated 0.9900")
                .contains("(SLA, Payment Provider SLA v2.0 §4.1)");
    }

    @Test
    @DisplayName("renders a failed judgement prominently, as a relation to the "
            + "stipulation — the wording never condemns the service")
    void rendersFailedJudgementProminently() {
        String out = NormativeJudgementRenderer.render(
                record(List.of(judgement(State.FAILED))));

        assertThat(out)
                .contains("NORMATIVE JUDGEMENT: FAILED")
                .contains("stipulated 0.9900 (SLA, Payment Provider SLA v2.0 §4.1)")
                .contains("95%-confident lower bound 0.9744 does not clear the stipulation");
        assertThat(out)
                .as("the judgement states the relation to the stipulation and "
                        + "implies nothing about the service's validity")
                .doesNotContain("invalid", "broken", "degraded", "violat");
    }

    @Test
    @DisplayName("renders an unsupportable judgement with the feasible minimum, "
            + "instead of silently omitting it or rendering a bound anyway")
    void rendersUnsupportableJudgementWithFeasibleMinimum() {
        String out = NormativeJudgementRenderer.render(
                record(List.of(judgement(State.UNSUPPORTABLE))));

        assertThat(out)
                .contains("NORMATIVE JUDGEMENT: UNSUPPORTABLE at this sample size")
                .contains("requires at least 268 samples")
                .contains("no judgement rendered");
    }

    @Test
    @DisplayName("renders nothing for a record with no normative judgements — a "
            + "purely empirical measure's output is unchanged")
    void rendersNothingWithoutJudgements() {
        assertThat(NormativeJudgementRenderer.render(record(List.of()))).isEmpty();
    }
}
