package org.mavai.punit.api.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PerCriterionVerdicts derivation")
class PerCriterionVerdictsTest {

    private static EvaluatedCriterion legacyPass(double threshold) {
        return new EvaluatedCriterion(
                new CriterionResult(
                        "bernoulli-pass-rate",
                        Verdict.PASS,
                        "ok",
                        Map.of("threshold", threshold)),
                CriterionRole.REQUIRED);
    }

    private static EvaluatedCriterion legacyInconclusive() {
        return new EvaluatedCriterion(
                new CriterionResult(
                        "bernoulli-pass-rate",
                        Verdict.INCONCLUSIVE,
                        "no baseline",
                        Map.of()),
                CriterionRole.REQUIRED);
    }

    @Test
    @DisplayName("empty per-criterion counts yields empty evaluation, composite PASS")
    void emptyCountsIsEmpty() {
        PerCriterionEvaluation eval = PerCriterionVerdicts.derive(
                List.of(legacyPass(0.80)), List.of());
        assertThat(eval.perCriterionVerdicts()).isEmpty();
        assertThat(eval.compositeVerdict()).isEqualTo(Verdict.PASS);
    }

    @Test
    @DisplayName("K=1 isomorphism — sole per-criterion verdict matches legacy verdict (PASS)")
    void k1IsomorphismPass() {
        // 90 of 100 passed, threshold 0.80 → 0.90 >= 0.80 → PASS, mirrors legacy.
        PerCriterionEvaluation eval = PerCriterionVerdicts.derive(
                List.of(legacyPass(0.80)),
                List.of(new CriterionSampleCounts("only", 90, 10, 0)));
        assertThat(eval.perCriterionVerdicts()).hasSize(1);
        PerCriterionVerdict row = eval.perCriterionVerdicts().get(0);
        assertThat(row.criterionId()).isEqualTo("only");
        assertThat(row.verdict()).isEqualTo(Verdict.PASS);
        assertThat(row.observed()).isEqualTo(0.9);
        assertThat(row.threshold()).isEqualTo(0.80);
        assertThat(eval.compositeVerdict()).isEqualTo(Verdict.PASS);
    }

    @Test
    @DisplayName("K=1 isomorphism — observed below threshold yields FAIL")
    void k1IsomorphismFail() {
        PerCriterionEvaluation eval = PerCriterionVerdicts.derive(
                List.of(legacyPass(0.95)),
                List.of(new CriterionSampleCounts("only", 80, 20, 0)));
        assertThat(eval.perCriterionVerdicts().get(0).verdict()).isEqualTo(Verdict.FAIL);
        assertThat(eval.compositeVerdict()).isEqualTo(Verdict.FAIL);
    }

    @Test
    @DisplayName("transform-failure samples count in the denominator as fails")
    void transformFailureSamplesCountAsFails() {
        // 80 PASS, 10 condition-fail, 10 transform-fail — denominator
        // 100, observed 0.80. Both kinds of fail count as a non-pass.
        PerCriterionEvaluation eval = PerCriterionVerdicts.derive(
                List.of(legacyPass(0.85)),
                List.of(new CriterionSampleCounts("crit", 80, 10, 10)));
        PerCriterionVerdict row = eval.perCriterionVerdicts().get(0);
        assertThat(row.observed()).isEqualTo(0.80);
        assertThat(row.verdict()).isEqualTo(Verdict.FAIL);
    }

    @Test
    @DisplayName("legacy INCONCLUSIVE propagates to every per-criterion verdict")
    void legacyInconclusivePropagates() {
        PerCriterionEvaluation eval = PerCriterionVerdicts.derive(
                List.of(legacyInconclusive()),
                List.of(
                        new CriterionSampleCounts("a", 100, 0, 0),
                        new CriterionSampleCounts("b", 100, 0, 0)));
        assertThat(eval.perCriterionVerdicts())
                .allMatch(r -> r.verdict() == Verdict.INCONCLUSIVE);
        assertThat(eval.compositeVerdict()).isEqualTo(Verdict.INCONCLUSIVE);
    }

    @Test
    @DisplayName("K>1 hiding result — one criterion fails, others pass, composite FAILs")
    void hidingResultExposed() {
        // The "hiding result" the methodology warns against: a single
        // criterion is genuinely failing but flat-aggregation would
        // pass because the other criteria push the overall pass-rate
        // above threshold. The composite — being FAIL-dominant —
        // surfaces the failure.
        PerCriterionEvaluation eval = PerCriterionVerdicts.derive(
                List.of(legacyPass(0.85)),
                List.of(
                        new CriterionSampleCounts("good-1", 100, 0, 0),
                        new CriterionSampleCounts("bad", 60, 40, 0),
                        new CriterionSampleCounts("good-2", 100, 0, 0)));
        List<Verdict> verdicts = eval.perCriterionVerdicts().stream()
                .map(PerCriterionVerdict::verdict).toList();
        assertThat(verdicts).containsExactly(Verdict.PASS, Verdict.FAIL, Verdict.PASS);
        assertThat(eval.compositeVerdict()).isEqualTo(Verdict.FAIL);
    }

    @Test
    @DisplayName("zero-sample criterion yields INCONCLUSIVE")
    void zeroSampleCriterionInconclusive() {
        PerCriterionEvaluation eval = PerCriterionVerdicts.derive(
                List.of(legacyPass(0.80)),
                List.of(new CriterionSampleCounts("empty", 0, 0, 0)));
        assertThat(eval.perCriterionVerdicts().get(0).verdict())
                .isEqualTo(Verdict.INCONCLUSIVE);
    }
}
