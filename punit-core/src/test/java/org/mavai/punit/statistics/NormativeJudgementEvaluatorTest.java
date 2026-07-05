package org.mavai.punit.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mavai.punit.statistics.NormativeJudgementEvaluator.Judgement;
import org.mavai.punit.statistics.NormativeJudgementEvaluator.State;

@DisplayName("Normative judgement at experiment time — statistical evaluation")
class NormativeJudgementEvaluatorTest {

    private static final BinomialProportionEstimator ESTIMATOR =
            new BinomialProportionEstimator();

    @Test
    @DisplayName("judges met when the Wilson lower bound at the run's sample count "
            + "clears the stipulated threshold")
    void metWhenBoundClearsThreshold() {
        Judgement judgement = NormativeJudgementEvaluator.judge(980, 1000, 0.9, 0.95);

        assertThat(judgement.state()).isEqualTo(State.MET);
        assertThat(judgement.observedRate()).isEqualTo(0.98);
        assertThat(judgement.stipulatedThreshold()).isEqualTo(0.9);
        assertThat(judgement.confidence()).isEqualTo(0.95);
        assertThat(judgement.lowerBound())
                .as("the bound is the estimator's Wilson one-sided lower bound — "
                        + "no parallel arithmetic")
                .isEqualTo(ESTIMATOR.lowerBound(980, 1000, 0.95))
                .isGreaterThanOrEqualTo(0.9);
    }

    @Test
    @DisplayName("judges failed when the bound does not clear the threshold, even "
            + "though the observed rate does — the judgement is bound-based")
    void failedWhenBoundDoesNotClearThreshold() {
        // Observed 0.91 exceeds the stipulated 0.9, but the 95%-confident
        // lower bound at n=1000 sits below it: evidence, not point estimate.
        Judgement judgement = NormativeJudgementEvaluator.judge(910, 1000, 0.9, 0.95);

        assertThat(judgement.state()).isEqualTo(State.FAILED);
        assertThat(judgement.observedRate()).isGreaterThan(0.9);
        assertThat(judgement.lowerBound())
                .isEqualTo(ESTIMATOR.lowerBound(910, 1000, 0.95))
                .isLessThan(0.9);
    }

    @Test
    @DisplayName("judges unsupportable, with the feasible minimum stated, when even a "
            + "perfect observation could not clear the threshold at this sample count")
    void unsupportableWhenSampleCountCannotSupportThreshold() {
        Judgement judgement = NormativeJudgementEvaluator.judge(50, 50, 0.99, 0.95);

        assertThat(judgement.state()).isEqualTo(State.UNSUPPORTABLE);
        assertThat(judgement.feasibleMinimumSamples())
                .as("the feasible minimum comes from the existing feasibility machinery")
                .isEqualTo(VerificationFeasibilityEvaluator
                        .evaluate(50, 0.99, 0.95).minimumSamples())
                .isGreaterThan(50);
        assertThat(judgement.lowerBound())
                .as("no bound is asserted for an unsupportable judgement")
                .isNaN();
    }

    @Test
    @DisplayName("agrees with the feasibility machinery about where supportability begins")
    void supportabilityBoundaryMatchesFeasibilityEvaluator() {
        int minimum = VerificationFeasibilityEvaluator
                .evaluate(1, 0.95, 0.95).minimumSamples();

        assertThat(NormativeJudgementEvaluator
                .judge(minimum, minimum, 0.95, 0.95).state())
                .isEqualTo(State.MET);
        assertThat(NormativeJudgementEvaluator
                .judge(minimum - 1, minimum - 1, 0.95, 0.95).state())
                .isEqualTo(State.UNSUPPORTABLE);
    }

    @Test
    @DisplayName("a zero threshold is degenerate and trivially met")
    void zeroThresholdTriviallyMet() {
        Judgement judgement = NormativeJudgementEvaluator.judge(0, 10, 0.0, 0.95);

        assertThat(judgement.state()).isEqualTo(State.MET);
        assertThat(judgement.observedRate()).isZero();
    }

    @Test
    @DisplayName("rejects out-of-range inputs as defects")
    void rejectsOutOfRangeInputs() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> NormativeJudgementEvaluator.judge(1, 0, 0.9, 0.95))
                .withMessageContaining("samples");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> NormativeJudgementEvaluator.judge(11, 10, 0.9, 0.95))
                .withMessageContaining("successes");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> NormativeJudgementEvaluator.judge(-1, 10, 0.9, 0.95))
                .withMessageContaining("successes");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> NormativeJudgementEvaluator.judge(5, 10, 1.0, 0.95))
                .withMessageContaining("stipulatedThreshold");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> NormativeJudgementEvaluator.judge(5, 10, 0.9, 1.0))
                .withMessageContaining("confidence");
    }
}
